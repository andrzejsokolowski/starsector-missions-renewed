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

    public static InvestmentContract build(MarketAPI origin, Tier tier, Random random) {
        if (!canOffer(origin)) return null;
        int months = MIN_MONTHS + random.nextInt(MAX_MONTHS - MIN_MONTHS + 1);
        int stake = roundTo(tier.rollStake(random), 1000);
        int profit = roundTo(tier.rollReward(random) * Settings.rewardMult()
                * Settings.payMult(missionsrenewed.missions.MissionFamily.INVESTMENT) * months / MONTHS_FOR_FULL_REWARD, 500);
        PersonAPI client = origin.getFaction().createRandomPerson(random);
        client.setRankId(Ranks.CITIZEN);
        client.setPostId(Ranks.POST_INVESTOR);
        return new InvestmentContract(tier.level, client, origin, stake, profit, months);
    }

    private static int roundTo(float value, int step) {
        return Math.max(step, Math.round(value / step) * step);
    }
}
