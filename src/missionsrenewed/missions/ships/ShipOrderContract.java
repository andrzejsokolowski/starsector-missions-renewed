package missionsrenewed.missions.ships;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import missionsrenewed.missions.ContractIntel;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * A client orders a hull from the player: bring one to the client's market and hand it over at the bar
 * (the board event offers it while this contract is open). Any condition, any loadout, never the flagship.
 */
public class ShipOrderContract extends ContractIntel {

    private final MarketAPI origin;
    private final String baseHullId;

    ShipOrderContract(int tier, PersonAPI client, MarketAPI origin, String baseHullId, int onSuccess, int penalty, float durationDays) {
        super(tier, client, 0, onSuccess, penalty, durationDays);
        this.origin = origin;
        this.baseHullId = baseHullId;
    }

    public MarketAPI getMarket() {
        return origin;
    }

    public ShipHullSpecAPI hull() {
        return Global.getSettings().getHullSpec(baseHullId);
    }

    /** Ships in the player's fleet that fill the order. The flagship is never one of them. */
    public List<FleetMemberAPI> matchingShips() {
        List<FleetMemberAPI> result = new ArrayList<>();
        for (FleetMemberAPI member : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
            if (member.isFlagship() || member.getHullSpec() == null) continue;
            if (baseHullId.equals(member.getHullSpec().getBaseHullId())) result.add(member);
        }
        return result;
    }

    /** From the bar: the player picked this ship. It leaves the fleet and the contract is paid. */
    public void handOver(FleetMemberAPI member, TextPanelAPI text) {
        if (!isActive()) return;
        Global.getSector().getPlayerFleet().getFleetData().removeFleetMember(member);
        if (text != null) {
            text.setFontSmallInsignia();
            text.addParagraph("Lost " + member.getShipName() + ", " + Hulls.aClassName(member.getHullSpec()), Misc.getNegativeHighlightColor());
            text.setFontInsignia();
        }
        end(State.COMPLETED, text);
    }

    @Override
    protected MarketAPI clientMarket() {
        return origin;
    }

    @Override
    protected void onAccepted(TextPanelAPI text) {}

    @Override
    protected boolean shouldCancel() {
        return origin == null || !origin.isInEconomy() || origin.getPrimaryEntity() == null;
    }

    // ----- offer -----

    @Override
    public void addOfferText(TextPanelAPI text) {
        text.addPara("\"I need " + Hulls.aClassName(hull()) + ". Bring one here within " + Math.round(durationDays)
                + " days and it is " + Misc.getDGSCredits(onSuccess) + ".\"");
    }

    @Override
    public void addOfferTerms(TooltipMakerAPI info) {
        Color h = Misc.getHighlightColor();
        info.addPara("Wanted: %s, any condition", 0f, h, Hulls.aClassName(hull()));
        info.addPara("Hand it over at the bar in %s", 0f, h, origin.getName());
        info.addPara("Deadline: %s days", 0f, h, "" + Math.round(durationDays));
        addTerms(info, 10f);
    }

    @Override
    public void addTerms(TooltipMakerAPI info, float pad) {
        info.addPara("Payment on hand-over: %s", pad, Misc.getHighlightColor(), Misc.getDGSCredits(onSuccess));
        info.addPara("Contract failed: %s", 0f, Misc.getNegativeHighlightColor(), Misc.getDGSCredits(penalty));
    }

    @Override
    public String signText() {
        return "You sign. " + client.getNameString() + " sends you the hull's specification.";
    }

    // ----- intel screen -----

    @Override
    protected String getJobName() {
        return "Ship Order";
    }

    @Override
    public String getIcon() {
        return hull().getSpriteName();
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return origin == null ? null : origin.getPrimaryEntity();
    }

    @Override
    protected void addJobBullets(TooltipMakerAPI info, Color tc, float pad) {
        info.addPara("Bring %s to %s", pad, tc, Misc.getHighlightColor(), Hulls.aClassName(hull()), origin.getName());
    }

    @Override
    protected void addDescriptionTop(TooltipMakerAPI info, float width) {
        info.addImages(width, 80f, 10f, 20f, hull().getSpriteName(), client.getPortraitSprite());
        info.addPara(client.getNameString() + " of " + origin.getName() + " wants " + Hulls.aClassName(hull())
                + ", handed over at the bar there.", 10f);
    }

    @Override
    protected String endText() {
        if (state == State.COMPLETED) return "You sold " + Hulls.aClassName(hull()) + " to " + client.getNameString() + ".";
        if (state == State.CANCELLED) return "The contract is void: " + origin.getName() + " is gone.";
        return super.endText();
    }
}
