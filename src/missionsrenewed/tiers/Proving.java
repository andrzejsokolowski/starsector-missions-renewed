package missionsrenewed.tiers;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import missionsrenewed.Settings;

/**
 * "Proving yourself" (LunaLib, MSR-25): clients only trust a captain with work one tier above what the captain
 * has already done. Completed contracts are counted per tier in sector memory; a contract of a higher tier also
 * counts for every tier below it.
 */
public final class Proving {

    private static final String KEY = "$missions_renewed_done_tier_";
    private static final int TOP_TIER = 7;

    private Proving() {}

    private static MemoryAPI memory() {
        return Global.getSector().getMemoryWithoutUpdate();
    }

    public static void recordCompleted(int tier) {
        if (Global.getSector() == null || tier < 0) return;
        String key = KEY + tier;
        memory().set(key, memory().getInt(key) + 1);
    }

    /** Contracts completed at this tier or any higher one. */
    public static int completedAtOrAbove(int tier) {
        int total = 0;
        for (int t = tier; t <= TOP_TIER; t++) total += memory().getInt(KEY + t);
        return total;
    }

    /** The highest tier bars offer this captain: 7 with the setting off, else one above the highest tier proven. */
    public static int highestUnlocked() {
        if (!Settings.proving()) return TOP_TIER;
        int need = Settings.provingCount();
        int unlocked = 0;
        for (int t = 0; t < TOP_TIER; t++) {
            if (completedAtOrAbove(t) < need) break;
            unlocked = t + 1;
        }
        return unlocked;
    }

    /** Contracts still needed at tier `unlocked` or above to open the next tier. */
    public static int stillNeeded() {
        int unlocked = highestUnlocked();
        if (unlocked >= TOP_TIER) return 0;
        return Math.max(0, Settings.provingCount() - completedAtOrAbove(unlocked));
    }
}
