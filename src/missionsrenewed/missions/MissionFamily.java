package missionsrenewed.missions;

/**
 * What a mission asks of the player's fleet. The tier scales whichever of these the family uses.
 * Combat, marines and expeditions are not written yet. The design answers are on ticket MSR-1.
 */
public enum MissionFamily {
    /** Beat hostile fleets. Scales with fleet points. */
    COMBAT("combat", false),
    /** Carry the client's cargo from one market to another. Scales with cargo units. */
    HAULING("hauling", true),
    /** Deliver fuel. Scales with fuel units, and the fleet burns fuel on the way. */
    FUEL_RUN("fuel run", true),
    /** Carry passengers. Scales with crew capacity. */
    CREW_TRANSPORT("crew transport", true),
    /** Raid or garrison jobs. Scales with marines. */
    MARINES("marines", false),
    /** Put up credits now, get them back later with interest. Scales with the stake. */
    INVESTMENT("investment", true),
    /** Hand over or sell goods (metals, colony items, weapons) for a payout. Scales with the value asked for. */
    CONTRIBUTION("contribution", true),
    /** Haul material to a far location, often outside the core worlds. Scales with cargo and distance. */
    EXPEDITION("expedition", false);

    /** The name shown in the bar and in the contract text. */
    public final String displayName;
    /** Whether bars offer this type yet. Only types with a written mission are offered. */
    public final boolean playable;

    MissionFamily(String displayName, boolean playable) {
        this.displayName = displayName;
        this.playable = playable;
    }

    /** The types bars offer. */
    public static java.util.List<MissionFamily> playableFamilies() {
        java.util.List<MissionFamily> result = new java.util.ArrayList<>();
        for (MissionFamily family : values()) {
            if (family.playable) result.add(family);
        }
        return result;
    }
}
