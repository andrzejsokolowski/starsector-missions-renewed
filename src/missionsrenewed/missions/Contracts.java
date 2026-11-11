package missionsrenewed.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import missionsrenewed.missions.combat.CombatContracts;
import missionsrenewed.missions.conquest.ConquestContracts;
import missionsrenewed.missions.defense.DefenseContracts;
import missionsrenewed.missions.lead.SalvageLeadContracts;
import missionsrenewed.missions.lead.ShipLeadContracts;
import missionsrenewed.missions.ships.ShipDealContracts;
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

    /** Whether this market's bar can offer the type at all (investments need an honest faction, conquest Nexerelin). */
    public static boolean canOffer(MissionFamily family, MarketAPI market) {
        if (!family.playable) return false;
        if (family == MissionFamily.INVESTMENT) return InvestmentContracts.canOffer(market);
        if (family == MissionFamily.CONQUEST) return ConquestContracts.canOffer(market);
        return true;
    }

    /** canOffer, and the tier is at least the type's lowest tier (conquest: 3). */
    public static boolean canOffer(MissionFamily family, MarketAPI market, int tier) {
        return tier >= family.minTier && canOffer(family, market);
    }

    /** The types this market's bar can offer at this tier. */
    public static List<MissionFamily> offeredAt(MarketAPI market, int tier) {
        List<MissionFamily> result = new ArrayList<>();
        for (MissionFamily family : MissionFamily.values()) {
            if (canOffer(family, market, tier)) result.add(family);
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
        if (!canOffer(family, origin, tier.level)) return null;
        switch (family) {
            case HAULING:
            case FUEL_RUN:
            case CREW_TRANSPORT:
            case CONTRIBUTION:
                return DeliveryContracts.build(family, origin, tier, random);
            case INVESTMENT:
                return InvestmentContracts.build(origin, tier, random);
            case COMBAT:
                return CombatContracts.build(origin, tier, random);
            case MARINES:
                return RaidContracts.build(origin, tier, random);
            case EXPEDITION:
                return ExpeditionContracts.build(origin, tier, random);
            case DEFENSE:
                return DefenseContracts.build(origin, tier, random);
            case SHIP_LEAD:
                return ShipLeadContracts.build(origin, tier, random);
            case SHIP_DEAL:
                return ShipDealContracts.build(origin, tier, random);
            case CONQUEST:
                return ConquestContracts.build(origin, tier, random);
            case SALVAGE_LEAD:
                return SalvageLeadContracts.build(origin, tier, random);
            default:
                return null;
        }
    }
}
