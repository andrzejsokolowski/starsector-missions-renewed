package missionsrenewed;

import lunalib.lunaSettings.LunaSettings;

/** LunaLib values, read live. A missing value falls back to the default in LunaSettings.csv. */
public final class Settings {

    public static final String MOD_ID = "missions_renewed";

    private Settings() {}

    /** Chance from 0 to 1 that a bar has a tiered mission on offer. */
    public static float barChance() {
        return integer("mr_barChance", 50) / 100f;
    }

    /** How strongly the tier roll leans toward the player's own tier: 0 is fully random, 1 doubles the match. */
    public static float playerLean() {
        return integer("mr_playerLean", 100) / 100f;
    }

    public static float rewardMult() {
        return decimal("mr_rewardMult", 1f);
    }

    /** Whether bars offer this job type (LunaLib mr_job_<type>, on by default). */
    public static boolean jobEnabled(missionsrenewed.missions.MissionFamily family) {
        return bool("mr_job_" + family.name().toLowerCase(java.util.Locale.ROOT), true);
    }

    /** Lowest tier bars offer. Swapped with the highest when set above it. */
    public static int minTier() {
        return Math.min(integer("mr_minTier", 0), integer("mr_maxTier", 7));
    }

    /** Highest tier bars offer. */
    public static int maxTier() {
        return Math.max(integer("mr_minTier", 0), integer("mr_maxTier", 7));
    }

    /** Test mode: every bar offers any contract on request, and the intel screen can finish or fail one at once. */
    public static boolean testMode() {
        return bool("mr_testMode", false);
    }

    private static boolean bool(String key, boolean fallback) {
        try {
            Boolean value = LunaSettings.getBoolean(MOD_ID, key);
            return value == null ? fallback : value;
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static int integer(String key, int fallback) {
        try {
            Integer value = LunaSettings.getInt(MOD_ID, key);
            return value == null ? fallback : value;
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static float decimal(String key, float fallback) {
        try {
            Double value = LunaSettings.getDouble(MOD_ID, key);
            return value == null ? fallback : value.floatValue();
        } catch (Throwable t) {
            return fallback;
        }
    }
}
