package missionsrenewed.missions.hauling;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import missionsrenewed.Settings;
import missionsrenewed.tiers.Tier;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Builds hauling contract offers from a tier. */
public final class HaulingContracts {

    /**
     * Goods a client hands over. No supplies or fuel (the fleet would burn them) and nothing commonly
     * illegal. A destination must allow the good anyway.
     */
    private static final String[] GOODS = {
            Commodities.ORE, Commodities.FOOD, Commodities.ORGANICS, Commodities.METALS,
            Commodities.DOMESTIC_GOODS, Commodities.RARE_ORE, Commodities.HEAVY_MACHINERY,
            Commodities.RARE_METALS, Commodities.VOLATILES
    };
    /** The cargo's base value stays under this share of the total pay, so running off with it never pays (design answer 6). */
    private static final float MAX_CARGO_VALUE_SHARE = 0.6f;
    private static final float ADVANCE_SHARE = 0.2f;
    private static final float MIN_PENALTY_SHARE = 0.5f;
    private static final float MIN_DISTANCE_LY = 3f;
    /** Deadlines assume a loaded fleet at this burn, then double the trip and add slack. */
    private static final float PLANNING_BURN = 8f;

    private HaulingContracts() {}

    /** A contract offered at origin, or null when no destination fits. */
    public static HaulingContract build(MarketAPI origin, Tier tier, Random random) {
        FactionAPI clientFaction = origin.getFaction();
        int units = roundTo(tier.rollCargo(random), 10);
        int reward = roundTo(tier.rollReward(random) * Settings.rewardMult(), 500);

        WeightedRandomPicker<MarketAPI> destinations = new WeightedRandomPicker<>(random);
        float maxLY = 10f + 4f * tier.level;
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!isDestination(origin, market, clientFaction)) continue;
            float ly = Misc.getDistanceLY(origin.getPrimaryEntity(), market.getPrimaryEntity());
            if (ly < MIN_DISTANCE_LY || ly > maxLY) continue;
            destinations.add(market, market.getSize());
        }

        while (!destinations.isEmpty()) {
            MarketAPI destination = destinations.pickAndRemove();
            String good = pickGood(origin, destination, units, reward, random);
            if (good == null) continue;

            int advance = roundTo(reward * ADVANCE_SHARE, 500);
            int onSuccess = reward - advance;
            float cargoValue = Global.getSettings().getCommoditySpec(good).getBasePrice() * units;
            int penalty = roundTo(Math.max(reward * MIN_PENALTY_SHARE, advance + cargoValue), 500);

            PersonAPI client = clientFaction.createRandomPerson(random);
            client.setRankId(Ranks.CITIZEN);
            client.setPostId(Ranks.POST_MERCHANT);

            return new HaulingContract(tier.level, client, origin, destination, good, units,
                    advance, onSuccess, penalty, deadlineDays(origin, destination));
        }
        return null;
    }

    private static boolean isDestination(MarketAPI origin, MarketAPI market, FactionAPI clientFaction) {
        if (market == origin || market.isHidden() || !market.isInEconomy() || market.isPlayerOwned()) return false;
        if (market.getPrimaryEntity() == null || market.getSize() < 3) return false;
        if (market.getFaction().isHostileTo(Factions.PLAYER)) return false;
        return !market.getFaction().isHostileTo(clientFaction);
    }

    /** A good legal at both ends whose value stays under the cap, or the cheapest legal one. */
    private static String pickGood(MarketAPI origin, MarketAPI destination, int units, int reward, Random random) {
        List<String> legal = new ArrayList<>();
        WeightedRandomPicker<String> fitting = new WeightedRandomPicker<>(random);
        for (String good : GOODS) {
            if (origin.isIllegal(good) || destination.isIllegal(good)) continue;
            legal.add(good);
            if (Global.getSettings().getCommoditySpec(good).getBasePrice() * units <= reward * MAX_CARGO_VALUE_SHARE) {
                fitting.add(good);
            }
        }
        if (!fitting.isEmpty()) return fitting.pick();
        String cheapest = null;
        for (String good : legal) {
            if (cheapest == null || price(good) < price(cheapest)) cheapest = good;
        }
        return cheapest;
    }

    private static float price(String good) {
        return Global.getSettings().getCommoditySpec(good).getBasePrice();
    }

    /** Twice the trip at a hauler's burn plus 20 days, at least 30 days. */
    static float deadlineDays(MarketAPI origin, MarketAPI destination) {
        float distance = Misc.getDistance(origin.getPrimaryEntity().getLocationInHyperspace(),
                destination.getPrimaryEntity().getLocationInHyperspace());
        float unitsPerDay = Misc.getSpeedForBurnLevel(PLANNING_BURN) * Global.getSector().getClock().getSecondsPerDay();
        float tripDays = distance / Math.max(1f, unitsPerDay);
        return Math.max(30f, (float) Math.ceil(tripDays * 2f + 20f));
    }

    private static int roundTo(float value, int step) {
        return Math.max(step, Math.round(value / step) * step);
    }
}
