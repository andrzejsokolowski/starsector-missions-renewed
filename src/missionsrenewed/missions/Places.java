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
     * A system between minLY and maxLY from origin, preferring uncolonised ones (no patrols to get in the
     * way). Remnant strongholds are left out. Null when nothing is in range.
     */
    public static StarSystemAPI pickSystem(MarketAPI origin, float minLY, float maxLY, boolean uncolonisedOnly, Random random) {
        Vector2f from = origin.getPrimaryEntity().getLocationInHyperspace();
        WeightedRandomPicker<StarSystemAPI> empty = new WeightedRandomPicker<>(random);
        WeightedRandomPicker<StarSystemAPI> any = new WeightedRandomPicker<>(random);
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (!usable(system) || pickPlanetCount(system) == 0) continue;
            if (system.hasTag(Tags.THEME_REMNANT_MAIN) || system.hasTag(Tags.THEME_REMNANT_RESURGENT)) continue;
            float ly = Misc.getDistanceLY(from, system.getLocation());
            if (ly < minLY || ly > maxLY) continue;
            if (uncolonised(system)) empty.add(system); else any.add(system);
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
