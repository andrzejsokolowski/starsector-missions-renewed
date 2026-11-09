package missionsrenewed.missions;

/**
 * What a mission asks of the player's fleet. The tier scales whichever of these the family uses.
 * No mission content exists yet: the design answers are on ticket MSR-1.
 */
public enum MissionFamily {
    /** Beat hostile fleets. Scales with fleet points. */
    COMBAT("combat"),
    /** Carry the client's cargo from one market to another. Scales with cargo units. */
    HAULING("hauling"),
    /** Deliver fuel. Scales with fuel units, and the fleet burns fuel on the way. */
    FUEL_RUN("fuel run"),
    /** Carry passengers. Scales with crew capacity. */
    CREW_TRANSPORT("crew transport"),
    /** Raid or garrison jobs. Scales with marines. */
    MARINES("marines"),
    /** Put up credits now, get them back later with interest. Scales with the stake. */
    INVESTMENT("investment"),
    /** Hand over or sell goods (metals, colony items, weapons) for a payout. Scales with the value asked for. */
    CONTRIBUTION("contribution"),
    /** Haul material to a far location, often outside the core worlds. Scales with cargo and distance. */
    EXPEDITION("expedition");

    /** The name shown in the bar and in the contract text. */
    public final String displayName;

    MissionFamily(String displayName) {
        this.displayName = displayName;
    }
}
