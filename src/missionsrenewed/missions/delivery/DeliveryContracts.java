package missionsrenewed.missions.delivery;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import missionsrenewed.Settings;
import missionsrenewed.missions.MissionFamily;
import missionsrenewed.missions.hauling.HaulingContract;
import missionsrenewed.tiers.Tier;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Builds hauling, fuel run, crew transport and contribution offers from a tier. */
public final class DeliveryContracts {

    /**
     * Goods a hauling client hands over. No supplies or fuel (the fleet would burn them) and nothing
     * commonly illegal. A destination must allow the good anyway.
     */
    private static final String[] HAULING_GOODS = {
            Commodities.ORE, Commodities.FOOD, Commodities.ORGANICS, Commodities.METALS,
            Commodities.DOMESTIC_GOODS, Commodities.RARE_ORE, Commodities.HEAVY_MACHINERY,
            Commodities.RARE_METALS, Commodities.VOLATILES
    };
    /** Goods a construction project asks for. The player finds them, so supplies are fine here. */
    private static final String[] CONTRIBUTION_GOODS = {
            Commodities.METALS, Commodities.HEAVY_MACHINERY, Commodities.RARE_METALS, Commodities.SUPPLIES,
            Commodities.VOLATILES, Commodities.ORGANICS, Commodities.FOOD, Commodities.DOMESTIC_GOODS,
            Commodities.HAND_WEAPONS
    };
    /** Handed-over goods stay under this share of the total pay, so running off with them never pays (design answer 6). */
    private static final float MAX_GOODS_VALUE_SHARE = 0.6f;
    /**
     * Hauling, fuel runs and crew transport rarely go wrong, so they pay a third of the tier's reward. Cargo
     * amounts still come from the full reward, and the penalty still covers the goods, so running off
     * with them never pays.
     */
    private static final float EASY_PAY_SHARE = 1f / 3f;
    /** A contribution pays this many times the goods' base value. */
    private static final float CONTRIBUTION_MARKUP = 2f;
    /** Colony items are asked for at these tiers, when one is priced between a sixth and two thirds of the pay. */
    private static final int ITEM_MIN_TIER = 4, ITEM_MAX_TIER = 6;
    private static final float ITEM_CHANCE = 0.35f;
    private static final float ADVANCE_SHARE = 0.2f;
    private static final float CONTRIBUTION_ADVANCE_SHARE = 0.1f;
    private static final float MIN_PENALTY_SHARE = 0.5f;
    private static final float CONTRIBUTION_PENALTY_SHARE = 0.3f;
    private static final float MIN_DISTANCE_LY = 3f;
    /** Deadlines assume a loaded fleet at this burn, then double the trip and add slack. */
    private static final float PLANNING_BURN = 8f;

    private DeliveryContracts() {}

    /** A contract of this family offered at origin, or null when nothing fits. */
    public static DeliveryContract build(MissionFamily family, MarketAPI origin, Tier tier, Random random) {
        FactionAPI clientFaction = origin.getFaction();
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
            DeliveryContract contract = null;
            switch (family) {
                case HAULING: contract = hauling(origin, destination, tier, reward, random); break;
                case FUEL_RUN: contract = suppliedRun(family, origin, destination, Commodities.FUEL, tier.rollFuel(random), tier, reward, random); break;
                case CREW_TRANSPORT: contract = suppliedRun(family, origin, destination, Commodities.CREW, tier.rollCrew(random), tier, reward, random); break;
                case CONTRIBUTION: contract = contribution(origin, destination, tier, reward, random); break;
                case SMUGGLING: contract = smuggling(origin, destination, tier, reward, random); break;
                case MARINES: contract = garrison(origin, destination, tier, reward, random); break;
                default: return null;
            }
            if (contract != null) return contract;
        }
        return null;
    }

    private static HaulingContract hauling(MarketAPI origin, MarketAPI destination, Tier tier, int reward, Random random) {
        int units = roundTo(tier.rollCargo(random), 10);
        String good = pickHaulingGood(origin, destination, units, reward, random);
        if (good == null) return null;
        int pay = roundTo(reward * EASY_PAY_SHARE, 500);
        int advance = roundTo(pay * ADVANCE_SHARE, 500);
        return new HaulingContract(tier.level, client(origin, Ranks.POST_MERCHANT, random), origin, destination, good, units,
                advance, pay - advance, handedOverPenalty(pay, advance, good, units), deadlineDays(origin, destination));
    }

    /** Fuel runs and crew transport: the client's own fuel or workers, capped so they are worth less than the pay. */
    private static DeliveryContract suppliedRun(MissionFamily family, MarketAPI origin, MarketAPI destination, String good,
                                                float rolled, Tier tier, int reward, Random random) {
        if (origin.isIllegal(good) || destination.isIllegal(good)) return null;
        float cap = reward * MAX_GOODS_VALUE_SHARE / price(good);
        int units = (int) (Math.min(rolled, cap) / 10f) * 10;
        if (units < 10) return null;
        int pay = roundTo(reward * EASY_PAY_SHARE, 500);
        int advance = roundTo(pay * ADVANCE_SHARE, 500);
        int penalty = handedOverPenalty(pay, advance, good, units);
        float days = deadlineDays(origin, destination);
        if (family == MissionFamily.FUEL_RUN) {
            return new FuelRunContract(tier.level, client(origin, Ranks.POST_SUPPLY_OFFICER, random), origin, destination,
                    units, advance, pay - advance, penalty, days);
        }
        return new CrewTransportContract(tier.level, client(origin, Ranks.POST_CREW_BOSS, random), origin, destination,
                units, advance, pay - advance, penalty, days);
    }

    private static ContributionContract contribution(MarketAPI origin, MarketAPI destination, Tier tier, int reward, Random random) {
        int advance = roundTo(reward * CONTRIBUTION_ADVANCE_SHARE, 500);
        int penalty = roundTo(Math.max(reward * CONTRIBUTION_PENALTY_SHARE, advance * 2f), 500);
        PersonAPI client = client(origin, Ranks.POST_SUPPLY_MANAGER, random);

        if (tier.level >= ITEM_MIN_TIER && tier.level <= ITEM_MAX_TIER && random.nextFloat() < ITEM_CHANCE) {
            String item = pickColonyItem(reward, random);
            if (item != null) {
                // Colony items are found, not bought: a long deadline, the trip still counted.
                float days = Math.max(180f, deadlineDays(origin, destination) + 120f);
                return new ContributionContract(tier.level, client, origin, destination, null, item, 1,
                        advance, reward - advance, penalty, days);
            }
        }

        WeightedRandomPicker<String> goods = new WeightedRandomPicker<>(random);
        for (String good : CONTRIBUTION_GOODS) {
            if (destination.isIllegal(good)) continue;
            int units = contributionUnits(good, reward);
            if (units >= Math.max(10f, tier.cargoMin * 0.25f) && units <= tier.cargoMax) goods.add(good);
        }
        if (goods.isEmpty()) return null;
        String good = goods.pick();
        // Time to buy the goods as well as to fly them.
        float days = deadlineDays(origin, destination) + 30f;
        return new ContributionContract(tier.level, client, origin, destination, good, null, contributionUnits(good, reward),
                advance, reward - advance, penalty, days);
    }

    /** Goods smuggled to a market where they are illegal (design answer 8: illegal jobs). */
    private static final String[] SMUGGLED_GOODS = {Commodities.HAND_WEAPONS, Commodities.DRUGS, Commodities.ORGANS};

    private static SmugglingContract smuggling(MarketAPI origin, MarketAPI destination, Tier tier, int reward, Random random) {
        if (destination.getFaction() == origin.getFaction()) return null;
        WeightedRandomPicker<String> goods = new WeightedRandomPicker<>(random);
        for (String good : SMUGGLED_GOODS) {
            if (destination.isIllegal(good)) goods.add(good);
        }
        if (goods.isEmpty()) return null;
        String good = goods.pick();
        float cap = reward * MAX_GOODS_VALUE_SHARE / price(good);
        int units = (int) (Math.min(tier.rollCargo(random) * 0.5f, cap) / 10f) * 10;
        if (units < 10) return null;
        int advance = roundTo(reward * ADVANCE_SHARE, 500);
        return new SmugglingContract(tier.level, client(origin, Ranks.POST_SMUGGLER, random), origin, destination, good, units,
                advance, reward - advance, handedOverPenalty(reward, advance, good, units), deadlineDays(origin, destination));
    }

    /** Garrison duty: the player's own marines, to a market of the client's faction. */
    private static GarrisonContract garrison(MarketAPI origin, MarketAPI destination, Tier tier, int reward, Random random) {
        if (destination.getFaction() != origin.getFaction() || destination.getSize() < 4) return null;
        int units = contributionUnits(Commodities.MARINES, reward);
        int advance = roundTo(reward * CONTRIBUTION_ADVANCE_SHARE, 500);
        int penalty = roundTo(Math.max(reward * CONTRIBUTION_PENALTY_SHARE, advance * 2f), 500);
        return new GarrisonContract(tier.level, client(origin, Ranks.POST_MARINE_SQUAD_LEADER, random), origin, destination,
                units, advance, reward - advance, penalty, deadlineDays(origin, destination) + 30f);
    }

    private static int contributionUnits(String good, int reward) {
        return Math.max(10, Math.round(reward / (price(good) * CONTRIBUTION_MARKUP) / 10f) * 10);
    }

    /** A colony item (any mod's) priced between a sixth and two thirds of the pay, or null. */
    private static String pickColonyItem(int reward, Random random) {
        WeightedRandomPicker<String> items = new WeightedRandomPicker<>(random);
        for (SpecialItemSpecAPI spec : Global.getSettings().getAllSpecialItemSpecs()) {
            if (!spec.hasTag("colony_item")) continue;
            float value = spec.getBasePrice();
            if (value <= 0f || value > reward / 1.5f || value < reward / 6f) continue;
            items.add(spec.getId());
        }
        return items.isEmpty() ? null : items.pick();
    }

    /** Penalty for a job with handed-over goods: at least half the pay, and more than the advance plus the goods. */
    private static int handedOverPenalty(int reward, int advance, String good, int units) {
        return roundTo(Math.max(reward * MIN_PENALTY_SHARE, advance + price(good) * units), 500);
    }

    private static PersonAPI client(MarketAPI origin, String post, Random random) {
        PersonAPI client = origin.getFaction().createRandomPerson(random);
        client.setRankId(Ranks.CITIZEN);
        client.setPostId(post);
        return client;
    }

    private static boolean isDestination(MarketAPI origin, MarketAPI market, FactionAPI clientFaction) {
        if (market == origin || market.isHidden() || !market.isInEconomy() || market.isPlayerOwned()) return false;
        if (market.getPrimaryEntity() == null || market.getSize() < 3) return false;
        if (market.getFaction().isHostileTo(Factions.PLAYER)) return false;
        return !market.getFaction().isHostileTo(clientFaction);
    }

    /** A hauling good legal at both ends whose value stays under the cap, or the cheapest legal one. */
    private static String pickHaulingGood(MarketAPI origin, MarketAPI destination, int units, int reward, Random random) {
        List<String> legal = new ArrayList<>();
        WeightedRandomPicker<String> fitting = new WeightedRandomPicker<>(random);
        for (String good : HAULING_GOODS) {
            if (origin.isIllegal(good) || destination.isIllegal(good)) continue;
            legal.add(good);
            if (price(good) * units <= reward * MAX_GOODS_VALUE_SHARE) fitting.add(good);
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

    static int roundTo(float value, int step) {
        return Math.max(step, Math.round(value / step) * step);
    }
}
