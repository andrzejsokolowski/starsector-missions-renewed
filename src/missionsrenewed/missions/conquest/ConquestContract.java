package missionsrenewed.missions.conquest;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import missionsrenewed.missions.ContractIntel;
import missionsrenewed.missions.delivery.DeliveryContract;

import java.awt.Color;

/**
 * Capture a market of a faction hostile to the client (Nexerelin's invasions). Done when the market passes
 * to the client's faction, the player's faction or the faction the player holds a commission with. Taken
 * by anyone else, peace between the two factions, or the market dying voids the contract.
 */
public class ConquestContract extends ContractIntel {

    private final MarketAPI origin;
    private final MarketAPI target;
    private final String ownerId;
    private final int defence;

    ConquestContract(int tier, PersonAPI client, MarketAPI origin, MarketAPI target, int defence,
                     int advance, int onSuccess, int penalty, float durationDays) {
        super(tier, client, advance, onSuccess, penalty, durationDays);
        this.origin = origin;
        this.target = target;
        this.ownerId = target.getFactionId();
        this.defence = defence;
    }

    private String owner() {
        return Global.getSector().getFaction(ownerId).getDisplayNameWithArticle();
    }

    @Override
    protected MarketAPI clientMarket() {
        return origin;
    }

    @Override
    protected String contactType() {
        return Tags.CONTACT_MILITARY;
    }

    @Override
    protected void onAccepted(TextPanelAPI text) {}

    /** True when the market changed hands to someone who counts for the client. */
    private boolean takenForClient() {
        String now = target.getFactionId();
        if (now.equals(ownerId)) return false;
        return now.equals(factionId) || now.equals(Factions.PLAYER) || now.equals(Misc.getCommissionFactionId());
    }

    @Override
    protected boolean shouldCancel() {
        if (target == null || !target.isInEconomy() || target.getPrimaryEntity() == null) return true;
        if (!target.getFactionId().equals(ownerId)) return !takenForClient();
        return !Global.getSector().getFaction(ownerId).isHostileTo(factionId);
    }

    @Override
    protected void advanceImpl(float amount) {
        if (isActive() && target != null && target.isInEconomy() && takenForClient()) {
            end(State.COMPLETED, null);
            return;
        }
        super.advanceImpl(amount);
    }

    // ----- offer -----

    @Override
    public void addOfferText(TextPanelAPI text) {
        text.addPara("\"" + getClientFaction().getDisplayNameWithArticle() + " wants " + target.getName() + " taken from "
                + owner() + ". Capture it within " + Math.round(durationDays) + " days.\"");
    }

    @Override
    public void addOfferTerms(TooltipMakerAPI info) {
        Color h = Misc.getHighlightColor();
        info.addPara("Target: %s, size %s, held by " + owner(), 0f, h, target.getName(), "" + target.getSize());
        info.addPara("Ground defenses: about %s", 0f, h, Misc.getWithDGS(defence));
        info.addPara("Location: %s, %s light years away", 0f, h, DeliveryContract.where(target),
                Misc.getRoundedValueMaxOneAfterDecimal(Misc.getDistanceLY(origin.getPrimaryEntity(), target.getPrimaryEntity())));
        info.addPara("Deadline: %s days", 0f, h, "" + Math.round(durationDays));
        addTerms(info, 10f);
    }

    @Override
    public String signText() {
        return "You sign. " + client.getNameString() + " sends you the garrison reports for " + target.getName() + ".";
    }

    // ----- intel screen -----

    @Override
    protected String getJobName() {
        return "Conquest Contract";
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("intel", "hostilities");
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return target == null ? null : target.getPrimaryEntity();
    }

    @Override
    protected void addJobBullets(TooltipMakerAPI info, Color tc, float pad) {
        info.addPara("Capture %s", pad, tc, Misc.getHighlightColor(), target.getName());
    }

    @Override
    protected void addDescriptionTop(TooltipMakerAPI info, float width) {
        info.addImage(client.getPortraitSprite(), width, 80f, 10f);
        info.addPara(client.getNameString() + " of " + origin.getName() + " wants " + DeliveryContract.where(target)
                + " captured from " + owner() + ". Ground defenses: about " + Misc.getWithDGS(defence) + ".", 10f);
    }

    @Override
    protected String endText() {
        if (state == State.COMPLETED) return target.getName() + " is captured.";
        if (state == State.CANCELLED) return "The contract is void.";
        return super.endText();
    }
}
