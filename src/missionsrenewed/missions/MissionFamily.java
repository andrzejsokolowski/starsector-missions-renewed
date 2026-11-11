package missionsrenewed.missions;

/**
 * What a mission asks of the player's fleet. The tier scales whichever of these the family uses.
 * The design answers are on ticket MSR-1.
 */
public enum MissionFamily {
    /** Beat hostile fleets. Scales with fleet points. */
    COMBAT("combat", true),
    /** Carry the client's cargo from one market to another. Scales with cargo units. */
    HAULING("hauling", true),
    /** Deliver fuel. Scales with fuel units, and the fleet burns fuel on the way. */
    FUEL_RUN("fuel run", true),
    /** Carry passengers. Scales with crew capacity. */
    CREW_TRANSPORT("crew transport", true),
    /** Raid or garrison jobs. Scales with marines. */
    MARINES("marine operation", true),
    /** Put up credits now, get them back later with interest. Scales with the stake. */
    INVESTMENT("investment", true),
    /** Hand over or sell goods (metals, colony items, weapons) for a payout. Scales with the value asked for. */
    CONTRIBUTION("contribution", true),
    /** Haul material to a far location, often outside the core worlds. Scales with cargo and distance. */
    EXPEDITION("expedition", true),
    /**
     * Dropped in 0.4.6 (MSR-12: it played like hauling). Never offered again. The value and SmugglingContract
     * stay so saves with an open smuggling contract or a rolled offer still load.
     */
    SMUGGLING("smuggling", false),
    /** A fleet is coming to attack a market of the client's faction: be there and beat it. Scales with DP. */
    DEFENSE("defense", true),
    /** Pay for the location of one named ship: a derelict to recover, or a fleet flagship to take. Scales with ship DP. */
    SHIP_LEAD("famous ship lead", true),
    /** A client buys a given hull from you, or sells you a ship below its value. Scales with ship DP. */
    SHIP_DEAL("ship deal", true),
    /** Capture a market for the client's faction. Tier 3 and up, and only with Nexerelin. Scales with market size. */
    CONQUEST("conquest", true, 3),
    /** Pay for the location of a debris field full of salvage. Scales with the loot. */
    SALVAGE_LEAD("salvage lead", true);

    /** The name shown in the bar and in the contract text. */
    public final String displayName;
    /** Whether bars offer this type yet. Only types with a written mission are offered. */
    public final boolean playable;
    /** Lowest tier this type is offered at. */
    public final int minTier;

    MissionFamily(String displayName, boolean playable) {
        this(displayName, playable, 0);
    }

    MissionFamily(String displayName, boolean playable, int minTier) {
        this.displayName = displayName;
        this.playable = playable;
        this.minTier = minTier;
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
