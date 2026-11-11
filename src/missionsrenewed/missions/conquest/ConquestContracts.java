package missionsrenewed.missions.conquest;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import missionsrenewed.Settings;
import missionsrenewed.missions.MissionFamily;
import missionsrenewed.missions.Places;
import missionsrenewed.tiers.Tier;

import java.util.Random;

/** Builds conquest offers (tier 3 and up, Nexerelin only): a hostile market whose size fits the tier. */
public final class ConquestContracts {

    private static final float ADVANCE_SHARE = 0.1f;
    private static final float PENALTY_SHARE = 0.25f;
    /** Market sizes per tier, from tier 3: [min, max]. */
    private static final int[][] SIZES = {{3, 4}, {4, 5}, {5, 6}, {6, 7}, {7, 99}};

    private ConquestContracts() {}

    /** Nexerelin adds the invasions that make a market capturable. Without it, no conquests. */
    public static boolean canOffer(MarketAPI market) {
        return Global.getSettings().getModManager().isModEnabled("nexerelin");
    }

    public static ConquestContract build(MarketAPI origin, Tier tier, Random random) {
        if (tier.level < MissionFamily.CONQUEST.minTier || !canOffer(origin)) return null;
        int[] size = SIZES[Math.min(SIZES.length - 1, tier.level - MissionFamily.CONQUEST.minTier)];
        WeightedRandomPicker<MarketAPI> targets = new WeightedRandomPicker<>(random);
        float maxLY = 10f + 4f * tier.level;
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market == origin || market.isHidden() || !market.isInEconomy() || market.isPlayerOwned()) continue;
            if (market.getPrimaryEntity() == null || Factions.PLAYER.equals(market.getFactionId())) continue;
            if (market.getSize() < size[0] || market.getSize() > size[1]) continue;
            if (!market.getFaction().isHostileTo(origin.getFaction())) continue;
            if (Misc.getDistanceLY(origin.getPrimaryEntity(), market.getPrimaryEntity()) > maxLY) continue;
            targets.add(market, 1f);
        }
        MarketAPI target = targets.pick();
        if (target == null) return null;

        int reward = Places.roundTo(tier.rollReward(random) * Settings.rewardMult() * Settings.payMult(MissionFamily.CONQUEST), 500);
        int advance = Places.roundTo(reward * ADVANCE_SHARE, 500);
        int penalty = Places.roundTo(reward * PENALTY_SHARE, 500);
        float days = (float) Math.ceil(Places.tripDays(origin.getPrimaryEntity().getLocationInHyperspace(),
                target.getPrimaryEntity().getLocationInHyperspace()) * 2f + 120f);

        PersonAPI client = origin.getFaction().createRandomPerson(random);
        client.setRankId(Ranks.CITIZEN);
        client.setPostId(Ranks.POST_FLEET_COMMANDER);
        return new ConquestContract(tier.level, client, origin, target, Math.round(MarketCMD.getDefenderStr(target)),
                advance, reward - advance, penalty, days);
    }
}
