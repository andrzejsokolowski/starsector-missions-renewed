package missionsrenewed.missions.combat;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import missionsrenewed.Settings;
import missionsrenewed.missions.Places;
import missionsrenewed.tiers.Tier;

import java.util.Random;

/** Builds combat contract offers. */
public final class CombatContracts {

    private static final float ADVANCE_SHARE = 0.1f;
    private static final float PENALTY_SHARE = 0.25f;
    /**
     * Share of the tier's reward a combat contract pays, by tier. The enemy fleets are small for the money
     * at the top (500 DP at tier 7), so high tiers pay less than hauling-size rewards would suggest.
     */
    private static final float[] PAY_SHARE = {1f, 1f, 1f, 1f, 0.75f, 0.6f, 0.5f, 0.4f};

    private CombatContracts() {}

    public static CombatContract build(MarketAPI origin, Tier tier, Random random) {
        String enemy = EnemyFleets.pickEnemy(tier.level, origin.getFaction(), random);
        if (enemy == null) return null;
        StarSystemAPI system = Places.pickSystem(origin, 2f, 8f + 3f * tier.level, false, random);
        if (system == null) return null;
        SectorEntityToken anchor = Places.pickPlanet(system, random);
        if (anchor == null) return null;

        float share = PAY_SHARE[Math.max(0, Math.min(PAY_SHARE.length - 1, tier.level))];
        int reward = Places.roundTo(tier.rollReward(random) * share * Settings.rewardMult(), 500);
        int fleetPoints = Math.max(6, Math.round(tier.rollDeployment(random) * Settings.strengthMult()));
        int advance = Places.roundTo(reward * ADVANCE_SHARE, 500);
        int penalty = Places.roundTo(reward * PENALTY_SHARE, 500);
        float days = (float) Math.ceil(Places.tripDays(origin.getPrimaryEntity().getLocationInHyperspace(),
                system.getLocation()) * 2f + 45f);

        PersonAPI client = origin.getFaction().createRandomPerson(random);
        client.setRankId(Ranks.CITIZEN);
        client.setPostId(Ranks.POST_FLEET_COMMANDER);
        return new CombatContract(tier.level, client, origin, anchor, enemy, fleetPoints, random.nextLong(),
                advance, reward - advance, penalty, Math.max(40f, days));
    }
}
