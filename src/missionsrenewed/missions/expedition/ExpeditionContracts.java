package missionsrenewed.missions.expedition;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import missionsrenewed.Settings;
import missionsrenewed.missions.Places;
import missionsrenewed.missions.combat.EnemyFleets;
import missionsrenewed.tiers.Tier;

import java.util.Random;

/** Builds expedition offers. */
public final class ExpeditionContracts {

    /** No supplies or fuel: the fleet would burn them. */
    private static final String[] GOODS = {
            Commodities.HEAVY_MACHINERY, Commodities.METALS, Commodities.FOOD, Commodities.DOMESTIC_GOODS,
            Commodities.ORGANICS, Commodities.VOLATILES, Commodities.RARE_METALS
    };
    private static final float MAX_GOODS_VALUE_SHARE = 0.6f;
    private static final float ADVANCE_SHARE = 0.2f;
    private static final float MIN_PENALTY_SHARE = 0.5f;
    /** Far jobs pay a little more than hauling of the same tier. */
    private static final float DISTANCE_BONUS = 1.2f;

    private ExpeditionContracts() {}

    public static ExpeditionContract build(MarketAPI origin, Tier tier, Random random) {
        StarSystemAPI system = Places.pickSystem(origin, 8f, 15f + 5f * tier.level, true, random);
        if (system == null) return null;
        SectorEntityToken planet = Places.pickPlanet(system, random);
        if (planet == null) return null;

        int reward = Places.roundTo(tier.rollReward(random) * DISTANCE_BONUS * Settings.rewardMult(), 500);
        String good = GOODS[random.nextInt(GOODS.length)];
        float price = Global.getSettings().getCommoditySpec(good).getBasePrice();
        int units = (int) (Math.min(tier.rollCargo(random) * 0.6f, reward * MAX_GOODS_VALUE_SHARE / price) / 10f) * 10;
        if (units < 10) return null;

        int advance = Places.roundTo(reward * ADVANCE_SHARE, 500);
        int penalty = Places.roundTo(Math.max(reward * MIN_PENALTY_SHARE, advance + price * units), 500);
        float days = (float) Math.ceil(Places.tripDays(origin.getPrimaryEntity().getLocationInHyperspace(),
                system.getLocation()) * 2f + 40f);

        float chance = Math.max(0.05f, Math.min(0.9f, 0.1f + 0.09f * tier.level + (random.nextFloat() - 0.5f) * 0.1f));
        boolean ambush = random.nextFloat() < chance;
        String enemy = EnemyFleets.pickEnemy(tier.level, origin.getFaction(), random);
        if (enemy == null) enemy = "faction:" + Factions.PIRATES;
        int ambushPoints = Math.max(6, Math.round(tier.rollDeployment(random) * 0.6f * Settings.strengthMult()));

        PersonAPI client = origin.getFaction().createRandomPerson(random);
        client.setRankId(Ranks.CITIZEN);
        client.setPostId(Ranks.POST_ENTREPRENEUR);
        return new ExpeditionContract(tier.level, client, origin, planet, good, units, chance, ambush, enemy, ambushPoints,
                random.nextLong(), advance, reward - advance, penalty, Math.max(40f, days));
    }
}
