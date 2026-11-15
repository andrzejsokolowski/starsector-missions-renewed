package missionsrenewed.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.lwjgl.util.vector.Vector2f;

import java.util.Random;

/** Star systems and planets for jobs that happen away from markets: combat targets and expedition camps. */
public final class Places {

    /** Deadlines assume a loaded fleet at this burn. */
    private static final float PLANNING_BURN = 8f;

    private Places() {}

    /** Systems a mission can use at all: not hidden, not abyssal, not cut off from hyperspace. */
    public static boolean usable(StarSystemAPI system) {
        if (system == null || system.getHyperspaceAnchor() == null) return false;
        return !system.hasTag(Tags.THEME_HIDDEN) && !system.hasTag(Tags.SYSTEM_ABYSSAL)
                && !system.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER);
    }

    public static boolean uncolonised(StarSystemAPI system) {
        return Global.getSector().getEconomy().getMarkets(system).isEmpty();
    }

    /**
     * Whether contracts may send the player into this system. Dangerous systems are fine (Andrzej, 2026-11-15);
     * left out are only systems a story owns: already used for a story, flagged as no target for random missions,
     * the planetkiller system and the game's special-themed systems.
     */
    public static boolean contractSafe(StarSystemAPI system) {
        if (system.hasTag(Tags.THEME_SPECIAL) || system.hasTag(Tags.PK_SYSTEM)) return false;
        return !system.hasTag(Tags.SYSTEM_ALREADY_USED_FOR_STORY) && !system.hasTag(Tags.NOT_RANDOM_MISSION_TARGET);
    }

    /**
     * A system something dangerous lives in: marked unsafe by the game or a mod (Remnant and derelict themes,
     * DME's Blade Breakers), a Remnant stronghold, or a Blade Breaker system.
     */
    public static boolean dangerous(StarSystemAPI system) {
        if (system.hasTag(Tags.THEME_UNSAFE) || system.hasTag(Tags.THEME_REMNANT_MAIN)
                || system.hasTag(Tags.THEME_REMNANT_RESURGENT)) return true;
        for (String tag : system.getTags()) {
            if (tag.startsWith("theme_breakers")) return true;
        }
        return false;
    }

    /**
     * A system between minLY and maxLY from origin, preferring uncolonised ones (no patrols to get in the
     * way). Only systems that pass contractSafe. Null when nothing is in range.
     */
    public static StarSystemAPI pickSystem(MarketAPI origin, float minLY, float maxLY, boolean uncolonisedOnly, Random random) {
        return pickSystem(origin, minLY, maxLY, uncolonisedOnly, 1f, random);
    }

    /** As pickSystem, with dangerous systems weighted dangerWeight against 1 for the others. */
    public static StarSystemAPI pickSystem(MarketAPI origin, float minLY, float maxLY, boolean uncolonisedOnly,
                                           float dangerWeight, Random random) {
        Vector2f from = origin.getPrimaryEntity().getLocationInHyperspace();
        WeightedRandomPicker<StarSystemAPI> empty = new WeightedRandomPicker<>(random);
        WeightedRandomPicker<StarSystemAPI> any = new WeightedRandomPicker<>(random);
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (!usable(system) || !contractSafe(system) || pickPlanetCount(system) == 0) continue;
            float ly = Misc.getDistanceLY(from, system.getLocation());
            if (ly < minLY || ly > maxLY) continue;
            float weight = dangerous(system) ? dangerWeight : 1f;
            if (uncolonised(system)) empty.add(system, weight); else any.add(system, weight);
        }
        if (!empty.isEmpty()) return empty.pick();
        return uncolonisedOnly ? null : any.pick();
    }

    private static int pickPlanetCount(StarSystemAPI system) {
        int count = 0;
        for (PlanetAPI planet : system.getPlanets()) {
            if (!planet.isStar()) count++;
        }
        return count;
    }

    /** A planet (not a star) in the system, or null. */
    public static SectorEntityToken pickPlanet(StarSystemAPI system, Random random) {
        WeightedRandomPicker<SectorEntityToken> planets = new WeightedRandomPicker<>(random);
        for (PlanetAPI planet : system.getPlanets()) {
            if (!planet.isStar()) planets.add(planet);
        }
        return planets.pick();
    }

    /** Days for a loaded fleet to fly from one hyperspace point to another. */
    public static float tripDays(Vector2f fromHyper, Vector2f toHyper) {
        float unitsPerDay = Misc.getSpeedForBurnLevel(PLANNING_BURN) * Global.getSector().getClock().getSecondsPerDay();
        return Misc.getDistance(fromHyper, toHyper) / Math.max(1f, unitsPerDay);
    }

    /** "Askonia" for a planet's system, or the entity's own name in hyperspace. */
    public static String systemName(SectorEntityToken entity) {
        if (entity.getStarSystem() == null) return entity.getName();
        return entity.getStarSystem().getNameWithLowercaseType();
    }

    public static int roundTo(float value, int step) {
        return Math.max(step, Math.round(value / step) * step);
    }
}
