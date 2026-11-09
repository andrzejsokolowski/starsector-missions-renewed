package missionsrenewed.missions.marines;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import missionsrenewed.Settings;
import missionsrenewed.missions.MissionFamily;
import missionsrenewed.missions.Places;
import missionsrenewed.missions.delivery.DeliveryContracts;
import missionsrenewed.missions.ContractIntel;
import missionsrenewed.tiers.Tier;

import java.util.Random;

/** Builds marine operation offers: a raid when a fitting target exists, otherwise (or half the time) garrison duty. */
public final class RaidContracts {

    private static final float ADVANCE_SHARE = 0.1f;
    private static final float PENALTY_SHARE = 0.25f;

    private RaidContracts() {}

    public static ContractIntel build(MarketAPI origin, Tier tier, Random random) {
        boolean raidFirst = random.nextFloat() < 0.6f;
        ContractIntel first = raidFirst ? raid(origin, tier, random) : DeliveryContracts.build(MissionFamily.MARINES, origin, tier, random);
        if (first != null) return first;
        return raidFirst ? DeliveryContracts.build(MissionFamily.MARINES, origin, tier, random) : raid(origin, tier, random);
    }

    /** A raid on a market hostile to the client whose ground defence lies in the tier's marine range. */
    static RaidContract raid(MarketAPI origin, Tier tier, Random random) {
        WeightedRandomPicker<MarketAPI> targets = new WeightedRandomPicker<>(random);
        float maxLY = 10f + 4f * tier.level;
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market == origin || market.isHidden() || !market.isInEconomy() || market.isPlayerOwned()) continue;
            if (market.getPrimaryEntity() == null || Factions.PLAYER.equals(market.getFactionId())) continue;
            if (!market.getFaction().isHostileTo(origin.getFaction())) continue;
            if (Misc.getDistanceLY(origin.getPrimaryEntity(), market.getPrimaryEntity()) > maxLY) continue;
            float defence = MarketCMD.getDefenderStr(market);
            if (defence < tier.marinesMin || defence > tier.marinesMax) continue;
            if (pickIndustry(market, random) == null) continue;
            targets.add(market, 1f);
        }
        MarketAPI target = targets.pick();
        if (target == null) return null;
        Industry industry = pickIndustry(target, random);

        int reward = Places.roundTo(tier.rollReward(random) * Settings.rewardMult(), 500);
        int advance = Places.roundTo(reward * ADVANCE_SHARE, 500);
        int penalty = Places.roundTo(reward * PENALTY_SHARE, 500);
        float days = (float) Math.ceil(Places.tripDays(origin.getPrimaryEntity().getLocationInHyperspace(),
                target.getPrimaryEntity().getLocationInHyperspace()) * 2f + 45f);

        PersonAPI client = origin.getFaction().createRandomPerson(random);
        client.setRankId(Ranks.CITIZEN);
        client.setPostId(Ranks.POST_MARINE_SQUAD_LEADER);
        return new RaidContract(tier.level, client, origin, target, industry, Math.round(MarketCMD.getDefenderStr(target)),
                advance, reward - advance, penalty, Math.max(40f, days));
    }

    /** An industry the raid can disrupt, industries before structures. Never the population itself. */
    private static Industry pickIndustry(MarketAPI market, Random random) {
        WeightedRandomPicker<Industry> picker = new WeightedRandomPicker<>(random);
        for (Industry industry : market.getIndustries()) {
            if (!industry.canBeDisrupted() || industry.isDisrupted()) continue;
            if (Industries.POPULATION.equals(industry.getId())) continue;
            picker.add(industry, industry.isIndustry() ? 3f : 1f);
        }
        return picker.pick();
    }
}
