package missionsrenewed.compat;

import com.fs.starfarer.api.fleet.FleetMemberAPI;
import starship_legends.RepRecord;

/** The only class that names Starship Legends types. Called through Legends, and only when that mod is enabled. */
final class LegendsBridge {

    private LegendsBridge() {}

    /** The ordinal of the ship's reputation tier: 0 unknown up to 5 legendary. */
    static int fame(FleetMemberAPI member) {
        if (!RepRecord.existsFor(member)) return 0;
        RepRecord record = RepRecord.get(member);
        return record == null || record.getTier() == null ? 0 : record.getTier().ordinal();
    }
}
