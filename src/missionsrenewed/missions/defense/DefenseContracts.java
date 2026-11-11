package missionsrenewed.missions.defense;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import missionsrenewed.Settings;
import missionsrenewed.missions.MissionFamily;
import missionsrenewed.missions.Places;
import missionsrenewed.missions.combat.CombatContracts;
import missionsrenewed.missions.combat.EnemyFleets;
import missionsrenewed.tiers.Tier;

import java.util.Random;

/** Builds defense offers: a market of the client's faction near the bar, an attack on a known day. */
public final class DefenseContracts {

    private static final float ADVANCE_SHARE = 0.1f;
    private static final float PENALTY_SHARE = 0.25f;
    /** Days the attackers stay after they arrive. The contract runs out with them. */
    private static final float HOLD_DAYS = 20f;

    private DefenseContracts() {}

    public static DefenseContract build(MarketAPI origin, Tier tier, Random random) {
        WeightedRandomPicker<MarketAPI> targets = new WeightedRandomPicker<>(random);
        float maxLY = 12f + 3f * tier.level;
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market.isHidden() || !market.isInEconomy() || market.isPlayerOwned() || market.getPrimaryEntity() == null) continue;
            if (market.getFaction() != origin.getFaction() || market.getSize() < 3) continue;
            if (Misc.getDistanceLY(origin.getPrimaryEntity(), market.getPrimaryEntity()) > maxLY) continue;
            targets.add(market, market.getSize());
        }
        MarketAPI target = targets.pick();
        if (target == null) return null;

        String enemy = EnemyFleets.pickEnemy(tier.level, origin.getFaction(), true, random);
        if (enemy == null) return null;
        int deployment = Math.max(6, Math.round(tier.rollDeployment(random) * Settings.strengthMult()));

        int reward = Places.roundTo(tier.rollReward(random) * CombatContracts.payShare(tier.level) * Settings.rewardMult()
                * Settings.payMult(MissionFamily.DEFENSE), 500);
        int advance = Places.roundTo(reward * ADVANCE_SHARE, 500);
        int penalty = Places.roundTo(reward * PENALTY_SHARE, 500);
        float trip = Places.tripDays(origin.getPrimaryEntity().getLocationInHyperspace(),
                target.getPrimaryEntity().getLocationInHyperspace());
        float arrival = (float) Math.ceil(Math.max(15f, trip * 1.5f + 10f));

        PersonAPI client = origin.getFaction().createRandomPerson(random);
        client.setRankId(Ranks.CITIZEN);
        client.setPostId(Ranks.POST_STATION_COMMANDER);
        return new DefenseContract(tier.level, client, origin, target, enemy, deployment, random.nextLong(), arrival,
                advance, reward - advance, penalty, arrival + HOLD_DAYS);
    }
}
