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

    public static float rewardMult() {
        return decimal("mr_rewardMult", 1f);
    }

    public static float strengthMult() {
        return decimal("mr_strengthMult", 1f);
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
