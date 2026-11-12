package missionsrenewed.missions.ships;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI.ShipTypeHints;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Ships for famous ship leads and ship deals, picked by deployment points per tier from every installed
 * mod's stock variants. Left out: stations, modules, ships with modules, unboardable and hidden hulls,
 * civilian hulls, automated hulls (they need a skill to fly) and D-hulls.
 */
public final class Hulls {

    /** [min, max] deployment points of the ship per tier, 0 to 7. */
    private static final float[][] DP_BAND = {
            {3, 8}, {6, 12}, {10, 18}, {15, 26}, {22, 35}, {30, 45}, {40, 60}, {50, 999}
    };

    private static List<String> usable;

    private Hulls() {}

    public static float minDP(int tier) {
        return DP_BAND[clamp(tier)][0];
    }

    public static float maxDP(int tier) {
        return DP_BAND[clamp(tier)][1];
    }

    private static int clamp(int tier) {
        return Math.max(0, Math.min(DP_BAND.length - 1, tier));
    }

    /** Deployment points of a hull, as the refit screen shows them. */
    public static float dp(ShipHullSpecAPI hull) {
        return hull.getSuppliesToRecover();
    }

    /** Every usable stock variant, worked out once per game start. */
    private static List<String> usableVariants() {
        if (usable != null) return usable;
        List<String> result = new ArrayList<>();
        for (String id : Global.getSettings().getAllVariantIds()) {
            try {
                if (usable(id)) result.add(id);
            } catch (RuntimeException e) {
                // A broken modded variant: leave it out.
            }
        }
        usable = result;
        return usable;
    }

    private static boolean usable(String id) {
        {
            ShipVariantAPI variant = Global.getSettings().getVariant(id);
            if (variant == null || variant.isFighter() || variant.isEmptyHullVariant() || !variant.isStockVariant()) return false;
            ShipHullSpecAPI hull = variant.getHullSpec();
            if (hull == null || hull.isDHull() || dp(hull) <= 0f) return false;
            Set<ShipTypeHints> hints = hull.getHints();
            if (hints.contains(ShipTypeHints.STATION) || hints.contains(ShipTypeHints.MODULE)
                    || hints.contains(ShipTypeHints.SHIP_WITH_MODULES) || hints.contains(ShipTypeHints.UNBOARDABLE)
                    || hints.contains(ShipTypeHints.HIDE_IN_CODEX) || hints.contains(ShipTypeHints.CIVILIAN)) return false;
            return !Misc.isAutomated(variant);
        }
    }

    /**
     * A stock variant whose hull fits the tier's DP band, or null. With baseHulls, only those hulls (by base
     * hull id) count. Each hull is equally likely, however many variants it has.
     */
    public static String pickVariant(int tier, Collection<String> baseHulls, Random random) {
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(random);
        java.util.Map<String, Integer> perHull = new java.util.HashMap<>();
        List<String> fitting = new ArrayList<>();
        for (String id : usableVariants()) {
            ShipHullSpecAPI hull = Global.getSettings().getVariant(id).getHullSpec();
            float dp = dp(hull);
            if (dp < minDP(tier) || dp > maxDP(tier)) continue;
            if (baseHulls != null && !baseHulls.contains(hull.getBaseHullId())) continue;
            fitting.add(id);
            perHull.merge(hull.getBaseHullId(), 1, Integer::sum);
        }
        for (String id : fitting) {
            picker.add(id, 1f / perHull.get(Global.getSettings().getVariant(id).getHullSpec().getBaseHullId()));
        }
        return picker.pick();
    }

    /**
     * A famous ship for a lead: a warship (no freighters, tankers, liners or troop transports) in the tier's DP band.
     * From tier 4, pirate and Luddic Path refits are left out and better hulls, by their value, are more likely:
     * a tier 7 lead is a dreadnought, not a patched-up freighter (Andrzej, 2026-11-12). Falls back to pickVariant.
     */
    public static String pickFamous(int tier, Random random) {
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(random);
        java.util.Map<String, Integer> perHull = new java.util.HashMap<>();
        List<String> fitting = new ArrayList<>();
        for (String id : usableVariants()) {
            ShipHullSpecAPI hull = Global.getSettings().getVariant(id).getHullSpec();
            float dp = dp(hull);
            if (dp < minDP(tier) || dp > maxDP(tier)) continue;
            Set<ShipTypeHints> hints = hull.getHints();
            if (hints.contains(ShipTypeHints.FREIGHTER) || hints.contains(ShipTypeHints.TANKER)
                    || hints.contains(ShipTypeHints.LINER) || hints.contains(ShipTypeHints.TRANSPORT)) continue;
            if (tier >= 4 && isRefit(hull)) continue;
            fitting.add(id);
            perHull.merge(hull.getBaseHullId(), 1, Integer::sum);
        }
        float exponent = tier / 4f;
        for (String id : fitting) {
            ShipHullSpecAPI hull = Global.getSettings().getVariant(id).getHullSpec();
            float value = Math.max(1000f, hull.getBaseValue()) / 100000f;
            picker.add(id, (float) Math.pow(value, exponent) / perHull.get(hull.getBaseHullId()));
        }
        String pick = picker.pick();
        return pick != null ? pick : pickVariant(tier, null, random);
    }

    /** A pirate or Luddic Path refit, by the hull's manufacturer. */
    private static boolean isRefit(ShipHullSpecAPI hull) {
        String maker = hull.getManufacturer();
        if (maker == null) return false;
        maker = maker.toLowerCase(java.util.Locale.ROOT);
        return maker.contains("pirate") || maker.contains("path");
    }

    /** A copy of the variant the game treats as its own (not the stock one), with up to sMods hull mods made S-mods. */
    public static ShipVariantAPI withSMods(String variantId, int sMods) {
        ShipVariantAPI variant = Global.getSettings().getVariant(variantId).clone();
        variant.setSource(VariantSource.REFIT);
        variant.setHullVariantId(variantId + "_mr_" + Misc.genUID());
        int made = 0;
        for (String mod : new ArrayList<>(variant.getNonBuiltInHullmods())) {
            if (made >= sMods) break;
            if (variant.getSMods().contains(mod)) continue;
            variant.addPermaMod(mod, true);
            made++;
        }
        return variant;
    }

    /** S-mods a famous or sold ship carries at this tier: none up to tier 2, then 1, 1, 2, 2, 3. */
    public static int sModsForTier(int tier) {
        if (tier <= 2) return 0;
        if (tier <= 4) return 1;
        if (tier <= 6) return 2;
        return 3;
    }

    /** A fleet member for the variant, named, at full combat readiness. */
    public static FleetMemberAPI member(ShipVariantAPI variant, String name) {
        FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);
        if (name != null) member.setShipName(name);
        member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());
        return member;
    }

    /** A ship name in the independent style, from the given random. */
    public static String shipName(Random random) {
        return Global.getSector().getFaction(Factions.INDEPENDENT).pickRandomShipName(random);
    }

    /** "an Onslaught-class battleship", "a Wolf-class frigate". */
    public static String aClassName(ShipHullSpecAPI hull) {
        String name = className(hull);
        return ("AEIOU".indexOf(Character.toUpperCase(name.charAt(0))) >= 0 ? "an " : "a ") + name;
    }

    /** "Eagle-class cruiser". */
    public static String className(ShipHullSpecAPI hull) {
        return hull.getHullNameWithDashClass() + " " + hull.getDesignation().toLowerCase(java.util.Locale.ROOT);
    }
}
