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

/**
 * Put up a stake now, get it back with profit after 5 to 12 months (design answer 7 on MSR-1). Only
 * honest factions offer these, and they always pay. Pulling out early returns half the stake. If the
 * client's market stops existing, its faction refunds the stake.
 *
 * The money runs through ContractIntel with a negative advance (the stake) and the payout as onSuccess.
 */
public class InvestmentContract extends ContractIntel {

    private final MarketAPI origin;
    private final int stake;
    private final int profit;
    private final int months;

    InvestmentContract(int tier, PersonAPI client, MarketAPI origin, int stake, int profit, int months) {
        super(tier, client, -stake, stake + profit, 0, months * 30f);
        this.origin = origin;
        this.stake = stake;
        this.profit = profit;
        this.months = months;
    }

    @Override
    protected MarketAPI clientMarket() {
        return origin;
    }

    private int withdrawal() {
        return stake / 2;
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
        end(State.COMPLETED, null);
    }

    @Override
    protected int creditsOnEnd(State result) {
        switch (result) {
            case COMPLETED: return stake + profit;
            case ABANDONED: return withdrawal();
            case CANCELLED: return stake;
            // Only reachable through the test button: the stake is simply gone.
            default: return 0;
        }
    }

    @Override
    protected float repOnEnd(State result) {
        return result == State.COMPLETED ? successRep() : 0f;
    }

    // ----- offer -----

    @Override
    public void addOfferText(TextPanelAPI text) {
        text.addPara(client.getNameString() + " invests on behalf of " + getClientFaction().getDisplayNameWithArticle()
                + " and is short of partners for the next venture.");
        text.addPara("\"Put up " + Misc.getDGSCredits(stake) + " now. In " + months + " months the venture pays back "
                + Misc.getDGSCredits(stake + profit) + ". Pull out early and you get half your stake back, no more.\"");
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
        info.addPara("Early withdrawal returns: %s", 0f, h, Misc.getDGSCredits(withdrawal()));
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
    protected String successLabel() {
        return "payout";
    }

    @Override
    protected String daysLabel() {
        return "until payout";
    }

    @Override
    protected String abandonLabel() {
        return "Withdraw early";
    }

    @Override
    protected void addAbandonPrompt(TooltipMakerAPI prompt) {
        prompt.addPara("Pulling out now returns %s of your %s stake.", 0f, Misc.getHighlightColor(),
                Misc.getDGSCredits(withdrawal()), Misc.getDGSCredits(stake));
    }

    @Override
    protected String endText() {
        switch (state) {
            case COMPLETED: return "The venture paid out as promised.";
            case ABANDONED: return "You pulled out of the venture early.";
            case CANCELLED: return "The venture died with " + origin.getName() + ". " + getClientFaction().getDisplayNameWithArticle()
                    + " refunded your stake.";
            case FAILED: return "The venture collapsed and took your stake with it.";
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
                + origin.getName() + ".", 10f);
    }
}
