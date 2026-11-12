package missionsrenewed.missions.investment;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import missionsrenewed.missions.ContractIntel;

import java.awt.Color;
import java.util.Random;

/**
 * Put up a stake now, get it back with profit after 5 to 12 months (design answer 7 on MSR-1). Only
 * honest factions offer these. There is no pulling out (MSR-7, 2026-11-12). Each venture has a hidden
 * chance to fail, which the client only describes in words: a guaranteed venture never fails, a safe one
 * rarely and returns half the stake when it does, a risky one fails often and takes the whole stake.
 * Riskier ventures promise more profit. If the client's market stops existing, its faction refunds the stake.
 *
 * The money runs through ContractIntel with a negative advance (the stake) and the payout as onSuccess.
 */
public class InvestmentContract extends ContractIntel {

    private final MarketAPI origin;
    private final int stake;
    private final int profit;
    private final int months;
    /** 0 guaranteed, 1 safe, 2 risky. Investments signed before 0.8.0 load as guaranteed. */
    private final int risk;
    /** Hidden chance the venture fails, rolled when the offer was made. */
    private final float failChance;
    private final long seed;
    /** The venture failed: what came back of the stake. */
    private int salvaged;

    public static final int GUARANTEED = 0, SAFE = 1, RISKY = 2;

    InvestmentContract(int tier, PersonAPI client, MarketAPI origin, int stake, int profit, int months, int risk,
                       float failChance, long seed) {
        super(tier, client, -stake, stake + profit, 0, months * 30f);
        this.origin = origin;
        this.stake = stake;
        this.profit = profit;
        this.months = months;
        this.risk = risk;
        this.failChance = failChance;
        this.seed = seed;
    }

    private String riskWord() {
        switch (risk) {
            case SAFE: return "safe";
            case RISKY: return "risky";
            default: return "guaranteed";
        }
    }

    @Override
    protected MarketAPI clientMarket() {
        return origin;
    }

    @Override
    public String cannotSignReason() {
        float credits = Global.getSector().getPlayerFleet().getCargo().getCredits().get();
        if (credits >= stake) return null;
        return "The stake is " + Misc.getDGSCredits(stake) + ", you have " + Misc.getDGSCredits(Math.max(0f, credits)) + ".";
    }

    @Override
    protected void onAccepted(TextPanelAPI text) {}

    @Override
    protected boolean shouldCancel() {
        return origin == null || !origin.isInEconomy();
    }

    @Override
    protected void onDeadline() {
        if (new Random(seed).nextFloat() < failChance) {
            salvaged = risk == SAFE ? stake / 2 : 0;
            end(State.FAILED, null);
        } else {
            end(State.COMPLETED, null);
        }
    }

    @Override
    protected int creditsOnEnd(State result) {
        switch (result) {
            case COMPLETED: return stake + profit;
            // Withdrawn before 0.8.0, when pulling out returned half the stake.
            case ABANDONED: return stake / 2;
            case CANCELLED: return stake;
            case FAILED: return salvaged;
            default: return 0;
        }
    }

    @Override
    protected boolean canAbandon() {
        return false;
    }

    @Override
    protected boolean countsAsProof() {
        return false;
    }

    @Override
    protected float repOnEnd(State result) {
        return result == State.COMPLETED ? successRep() : 0f;
    }

    // ----- offer -----

    @Override
    public void addOfferText(TextPanelAPI text) {
        text.addPara("\"I'm putting together a venture for " + getClientFaction().getDisplayNameWithArticle()
                + " and I'm short a partner. Put up " + Misc.getDGSCredits(stake) + " and in " + months
                + " months you get back " + Misc.getDGSCredits(stake + profit) + ".\"");
        switch (risk) {
            case SAFE:
                text.addPara("\"It's a safe bet. Not a sure one, nothing ever is, but safe. Once you're in, you're in until it pays.\"");
                break;
            case RISKY:
                text.addPara("\"I won't lie to you, it's risky. That's why it pays like this. If it goes under, "
                        + "your money goes with it, and once you're in, there's no getting out.\"");
                break;
            default:
                text.addPara("\"This one's as sure as anything gets. " + getClientFaction().getDisplayNameWithArticle()
                        + " stands behind it. Once you're in, you're in until it pays.\"");
        }
    }

    @Override
    public void addOfferTerms(TooltipMakerAPI info) {
        addTerms(info, 0f);
    }

    @Override
    public void addTerms(TooltipMakerAPI info, float pad) {
        Color h = Misc.getHighlightColor();
        info.addPara("Stake on signing: %s", pad, Misc.getNegativeHighlightColor(), Misc.getDGSCredits(stake));
        info.addPara("Payout after %s months: %s", 0f, h, "" + months, Misc.getDGSCredits(stake + profit));
        info.addPara("Risk: %s", 0f, risk == RISKY ? Misc.getNegativeHighlightColor() : h, Misc.ucFirst(riskWord()));
        info.addPara("No early withdrawal", 0f);
    }

    @Override
    public String signText() {
        return "You sign and transfer the stake. " + client.getNameString() + " shakes your hand a little too firmly.";
    }

    // ----- intel screen -----

    @Override
    protected String getJobName() {
        return "Investment";
    }

    @Override
    protected String abandonedWord() {
        return "Withdrawn";
    }

    @Override
    public String getName() {
        if (state == State.FAILED) return "Tier " + tier + " " + getJobName() + " - Lost";
        return super.getName();
    }

    @Override
    protected String successLabel() {
        return "payout";
    }

    @Override
    protected String daysLabel() {
        return "until payout";
    }


    @Override
    protected String endText() {
        switch (state) {
            case COMPLETED: return "The venture paid out as promised.";
            case ABANDONED: return "You pulled out of the venture early.";
            case CANCELLED: return "The venture died with " + origin.getName() + ". " + getClientFaction().getDisplayNameWithArticle()
                    + " refunded your stake.";
            case FAILED: return salvaged > 0 ? "The venture collapsed. " + client.getNameString() + " got half your stake back out of the wreck."
                    : "The venture collapsed and took your stake with it.";
            default: return "";
        }
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("intel", "monthly_income_report");
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return origin == null ? null : origin.getPrimaryEntity();
    }

    @Override
    protected void addJobBullets(TooltipMakerAPI info, Color tc, float pad) {
        info.addPara("%s staked at %s", pad, tc, Misc.getHighlightColor(), Misc.getDGSCredits(stake), origin.getName())
                .setHighlightColors(Misc.getHighlightColor(), origin.getFaction().getBaseUIColor());
    }

    @Override
    protected void addDescriptionTop(TooltipMakerAPI info, float width) {
        info.addImage(client.getPortraitSprite(), width, 80f, 10f);
        info.addPara("You have " + Misc.getDGSCredits(stake) + " in a venture run by " + client.getNameString() + " of "
                + origin.getName() + ". " + client.getNameString() + " called it " + riskWord() + ".", 10f);
    }
}
