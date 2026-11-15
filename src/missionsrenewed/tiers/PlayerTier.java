package missionsrenewed.tiers;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import missionsrenewed.Settings;

import java.util.List;

/**
 * Where the player stands on the tier table, so the bar roll can lean toward jobs that fit.
 *
 * Three measures: the fleet's total deployment points (not mothballed) against deploymentPoints min, colony
 * income per month against reward min, credits on hand against stake min. Each is placed smoothly on the table
 * (400k income sits a quarter of the way from tier 5's 300k to tier 6's 700k: 5.25) and the three are blended by
 * weight, fleet 50, income 30, credits 20 by default (LunaLib mr_tierWeight*, Andrzej 2026-11-15: fleet strength
 * matters most, but a rich administrator who just lost half his fleet still won't take a 50k job). Before 0.9.0
 * the highest of the three won.
 */
public final class PlayerTier {

    private PlayerTier() {}

    /** The player's tier, or -1 when there is no sector or no table (title screen, broken file). */
    public static int estimate() {
        float blend = blended();
        return blend < 0f ? -1 : Math.round(blend);
    }

    /** The weighted blend from 0 to 7, or -1 when there is no sector or no table. */
    public static float blended() {
        SectorAPI sector = Global.getSector();
        List<Tier> tiers = TierTable.all();
        if (sector == null || tiers.isEmpty()) return -1f;

        float deployment = 0f;
        CampaignFleetAPI fleet = sector.getPlayerFleet();
        if (fleet != null) {
            for (com.fs.starfarer.api.fleet.FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
                if (!member.isMothballed()) deployment += member.getHullSpec().getSuppliesToRecover();
            }
        }

        float income = 0f;
        for (MarketAPI market : sector.getEconomy().getMarketsCopy()) {
            if (market.isPlayerOwned()) income += market.getNetIncome();
        }

        float credits = 0f;
        if (fleet != null && fleet.getCargo() != null) credits = fleet.getCargo().getCredits().get();

        float[] fleetMins = new float[tiers.size()];
        float[] incomeMins = new float[tiers.size()];
        float[] creditMins = new float[tiers.size()];
        for (int i = 0; i < tiers.size(); i++) {
            fleetMins[i] = tiers.get(i).deploymentMin;
            incomeMins[i] = tiers.get(i).rewardMin;
            creditMins[i] = tiers.get(i).stakeMin;
        }

        float wFleet = Settings.tierWeightFleet(), wIncome = Settings.tierWeightIncome(), wCredits = Settings.tierWeightCredits();
        float total = wFleet + wIncome + wCredits;
        if (total <= 0f) {
            wFleet = 1f;
            total = 1f;
        }
        return (wFleet * smooth(deployment, fleetMins) + wIncome * smooth(income, incomeMins)
                + wCredits * smooth(credits, creditMins)) / total;
    }

    /**
     * Where a value sits on the table: the highest tier whose minimum it reaches, plus how far it is toward the next
     * tier's minimum. 0 below tier 1's minimum, at most the top tier.
     */
    static float smooth(float value, float[] mins) {
        int top = mins.length - 1;
        for (int t = top; t >= 1; t--) {
            if (value < mins[t]) continue;
            if (t == top) return top;
            float span = mins[t + 1] - mins[t];
            float frac = span > 0f ? (value - mins[t]) / span : 0f;
            return t + Math.max(0f, Math.min(1f, frac));
        }
        float span = mins.length > 1 ? mins[1] - mins[0] : 0f;
        if (span <= 0f || value <= mins[0]) return 0f;
        return Math.max(0f, Math.min(1f, (value - mins[0]) / span));
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
