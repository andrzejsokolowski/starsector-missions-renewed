package missionsrenewed.missions;

/**
 * What a mission asks of the player's fleet. The tier scales whichever of these the family uses.
 * No mission content exists yet: the families and their missions are settled with Andrzej first.
 */
public enum MissionFamily {
    /** Beat hostile fleets. Scales with fleet points. */
    COMBAT,
    /** Carry cargo from one market to another. Scales with cargo units. */
    HAULING,
    /** Put up credits now, get them back later with interest. Scales with the stake. */
    INVESTMENT,
    /** Hand over goods (metals, colony items, weapons) for a payout. Scales with the value asked for. */
    CONTRIBUTION,
    /** Haul material to a far location, often outside the core worlds. Scales with cargo and distance. */
    EXPEDITION
}
