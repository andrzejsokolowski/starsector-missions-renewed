package missionsrenewed.tiers;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

/**
 * Where the player stands on the tier table, so the bar roll can lean toward jobs that fit.
 *
 * Three measures, each placed on the table by the highest tier whose minimum it reaches:
 * fleet points against fleetPoints min (a fair fight), colony income per month against reward min
 * (a job worth about a month of income), credits on hand against stake min (an investment the player
 * can afford). The player tier is the best of the three, because jobs test different things and a
 * rich trader with a small fleet should still see big hauling and investment jobs.
 */
public final class PlayerTier {

    private PlayerTier() {}

    /** The player's tier, or -1 when there is no sector or no table (title screen, broken file). */
    public static int estimate() {
        SectorAPI sector = Global.getSector();
        if (sector == null || TierTable.all().isEmpty()) return -1;

        float fleetPoints = 0f;
        CampaignFleetAPI fleet = sector.getPlayerFleet();
        if (fleet != null) fleetPoints = fleet.getFleetPoints();

        float income = 0f;
        for (MarketAPI market : sector.getEconomy().getMarketsCopy()) {
            if (market.isPlayerOwned()) income += market.getNetIncome();
        }

        float credits = 0f;
        if (fleet != null && fleet.getCargo() != null) credits = fleet.getCargo().getCredits().get();

        int best = 0;
        for (Tier tier : TierTable.all()) {
            if (fleetPoints >= tier.fleetPointsMin || income >= tier.rewardMin || credits >= tier.stakeMin) {
                best = Math.max(best, tier.level);
            }
        }
        return best;
    }

    /**
     * How much more likely a tier is for a player at playerTier. lean 1 doubles the matching tier and
     * multiplies its neighbours by 1.5. Tiers further away keep their base weight, so every tier stays
     * possible.
     */
    public static float leanMult(int tierLevel, int playerTier, float lean) {
        if (playerTier < 0 || lean <= 0f) return 1f;
        int distance = Math.abs(tierLevel - playerTier);
        if (distance == 0) return 1f + lean;
        if (distance == 1) return 1f + lean / 2f;
        return 1f;
    }
}
