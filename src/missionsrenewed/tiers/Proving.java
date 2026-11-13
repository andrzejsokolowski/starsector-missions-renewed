package missionsrenewed.tiers;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import missionsrenewed.Settings;

/**
 * "Proving yourself" (LunaLib, MSR-25): tiers open in steps of mr_provingStep (default 2, Andrzej 2026-11-13).
 * With 2: tiers 0 and 1 are open from the start; finishing mr_provingCount contracts of tier 0 or higher opens 2
 * and 3; that many of tier 2 or higher opens 4 and 5; that many of tier 4 or higher opens 6 and 7. Completed
 * contracts are counted per tier in sector memory; a contract of a higher tier also counts for every tier below.
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
        for (int t = Math.max(0, tier); t <= TOP_TIER; t++) total += memory().getInt(KEY + t);
        return total;
    }

    /** The lowest tier of the step the captain is working through: contracts at or above it open the next step. */
    public static int provingFrom() {
        int step = Settings.provingStep();
        int need = Settings.provingCount();
        int from = 0;
        while (from + step - 1 < TOP_TIER && completedAtOrAbove(from) >= need) from += step;
        return from;
    }

    /** The highest tier bars offer this captain: 7 with the setting off. */
    public static int highestUnlocked() {
        if (!Settings.proving()) return TOP_TIER;
        return Math.min(TOP_TIER, provingFrom() + Settings.provingStep() - 1);
    }

    /** The highest tier the next step opens. */
    public static int nextUnlocked() {
        return Math.min(TOP_TIER, highestUnlocked() + Settings.provingStep());
    }

    /** Contracts still needed at tier provingFrom() or above to open the next step. */
    public static int stillNeeded() {
        if (highestUnlocked() >= TOP_TIER) return 0;
        return Math.max(0, Settings.provingCount() - completedAtOrAbove(provingFrom()));
    }
}
