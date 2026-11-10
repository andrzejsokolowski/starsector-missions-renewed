package missionsrenewed.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import missionsrenewed.missions.combat.CombatContracts;
import missionsrenewed.missions.delivery.DeliveryContracts;
import missionsrenewed.missions.expedition.ExpeditionContracts;
import missionsrenewed.missions.investment.InvestmentContracts;
import missionsrenewed.missions.marines.RaidContracts;
import missionsrenewed.tiers.Tier;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** One place that knows which builder makes which job type. */
public final class Contracts {

    private Contracts() {}

    /** Whether this market's bar can offer the type at all (investments need an honest faction). */
    public static boolean canOffer(MissionFamily family, MarketAPI market) {
        if (!family.playable) return false;
        if (family == MissionFamily.INVESTMENT) return InvestmentContracts.canOffer(market);
        return true;
    }

    /** The types this market's bar can offer. */
    public static List<MissionFamily> offeredAt(MarketAPI market) {
        List<MissionFamily> result = new ArrayList<>();
        for (MissionFamily family : MissionFamily.values()) {
            if (canOffer(family, market)) result.add(family);
        }
        return result;
    }

    /**
     * build, but a builder that throws (another mod's odd market or faction, say) gives no offer instead
     * of crashing the bar. The error goes to starsector.log.
     */
    public static ContractIntel safeBuild(MissionFamily family, MarketAPI origin, Tier tier, Random random) {
        try {
            return build(family, origin, tier, random);
        } catch (RuntimeException e) {
            Global.getLogger(Contracts.class).error("Missions - Renewed: could not build a tier " + tier.level + " "
                    + family.displayName + " at " + (origin == null ? "null" : origin.getId()), e);
            return null;
        }
    }

    /** A contract of this type and tier offered at origin, or null when nothing fits (no destination in range). */
    public static ContractIntel build(MissionFamily family, MarketAPI origin, Tier tier, Random random) {
        if (!canOffer(family, origin)) return null;
        switch (family) {
            case HAULING:
            case FUEL_RUN:
            case CREW_TRANSPORT:
            case CONTRIBUTION:
                return DeliveryContracts.build(family, origin, tier, random);
            case SMUGGLING:
                return DeliveryContracts.build(family, origin, tier, random);
            case INVESTMENT:
                return InvestmentContracts.build(origin, tier, random);
            case COMBAT:
                return CombatContracts.build(origin, tier, random);
            case MARINES:
                return RaidContracts.build(origin, tier, random);
            case EXPEDITION:
                return ExpeditionContracts.build(origin, tier, random);
            default:
                return null;
        }
    }
}
