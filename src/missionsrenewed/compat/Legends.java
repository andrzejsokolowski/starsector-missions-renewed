package missionsrenewed.compat;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

/**
 * Starship Legends (optional): how well known a ship is. Only LegendsBridge touches that mod's classes, and it
 * is only loaded when the mod is enabled, so Missions - Renewed runs without it.
 */
public final class Legends {

    public static final String MOD_ID = "sun_starship_legends";
    private static final String[] NAMES = {"unknown", "rumored", "notable", "well known", "famous", "legendary"};
    /** What a client pays on top for a ship of each fame level, as a multiplier. */
    private static final float[] PAY_MULT = {1f, 1.1f, 1.2f, 1.35f, 1.6f, 2f};

    private Legends() {}

    public static boolean enabled() {
        return Global.getSettings().getModManager().isModEnabled(MOD_ID);
    }

    /** 0 (unknown or no Starship Legends) to 5 (legendary). */
    public static int fame(FleetMemberAPI member) {
        if (member == null || !enabled()) return 0;
        try {
            return Math.max(0, Math.min(NAMES.length - 1, LegendsBridge.fame(member)));
        } catch (Throwable e) {
            // A Starship Legends version with a different API: treat every ship as unknown.
            return 0;
        }
    }

    public static String fameName(int fame) {
        return NAMES[Math.max(0, Math.min(NAMES.length - 1, fame))];
    }

    public static float payMult(int fame) {
        return PAY_MULT[Math.max(0, Math.min(PAY_MULT.length - 1, fame))];
    }
}
