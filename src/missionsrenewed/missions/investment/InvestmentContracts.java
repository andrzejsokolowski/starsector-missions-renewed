package missionsrenewed.missions.investment;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.util.Misc;
import missionsrenewed.Settings;
import missionsrenewed.tiers.Tier;

import java.util.Random;

/** Builds investment offers. */
public final class InvestmentContracts {

    private static final int MIN_MONTHS = 5, MAX_MONTHS = 12;
    /** Profit for an investment of this many months is the tier's reward times months / this. */
    private static final float MONTHS_FOR_FULL_REWARD = 8f;

    private InvestmentContracts() {}

    /** Honest factions only: no pirates, no Pathers (design answer 7). */
    public static boolean canOffer(MarketAPI market) {
        FactionAPI faction = market.getFaction();
        if (faction == null || Misc.isPirateFaction(faction)) return false;
        return !Factions.LUDDIC_PATH.equals(faction.getId()) && market.getSize() >= 4;
    }

    /** Profit multiplier and hidden failure chance [min, max] per risk: guaranteed, safe, risky. */
    private static final float[] PROFIT_MULT = {0.6f, 1f, 2.2f};
    private static final float[][] FAIL_CHANCE = {{0f, 0f}, {0.05f, 0.15f}, {0.3f, 0.5f}};

    public static InvestmentContract build(MarketAPI origin, Tier tier, Random random) {
        if (!canOffer(origin)) return null;
        int months = MIN_MONTHS + random.nextInt(MAX_MONTHS - MIN_MONTHS + 1);
        int stake = roundTo(tier.rollStake(random), 1000);
        float roll = random.nextFloat();
        int risk = roll < 0.25f ? InvestmentContract.GUARANTEED : roll < 0.7f ? InvestmentContract.SAFE : InvestmentContract.RISKY;
        float fail = FAIL_CHANCE[risk][0] + random.nextFloat() * (FAIL_CHANCE[risk][1] - FAIL_CHANCE[risk][0]);
        int profit = roundTo(tier.rollReward(random) * Settings.rewardMult() * PROFIT_MULT[risk]
                * Settings.payMult(missionsrenewed.missions.MissionFamily.INVESTMENT) * months / MONTHS_FOR_FULL_REWARD, 500);
        PersonAPI client = origin.getFaction().createRandomPerson(random);
        client.setRankId(Ranks.CITIZEN);
        client.setPostId(Ranks.POST_INVESTOR);
        return new InvestmentContract(tier.level, client, origin, stake, profit, months, risk, fail, random.nextLong());
    }

    private static int roundTo(float value, int step) {
        return Math.max(step, Math.round(value / step) * step);
    }
}
