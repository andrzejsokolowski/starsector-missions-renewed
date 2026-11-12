package missionsrenewed.missions.ships;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import missionsrenewed.compat.Legends;
import missionsrenewed.missions.ContractIntel;

import java.awt.Color;

/**
 * A client wants one particular ship from the player's fleet and pays a markup for it, more for a ship Starship
 * Legends rates as well known. Signing hands the ship over on the spot and pays; the contract is complete at once.
 * Never the flagship. Replaces the 0.6.0 ship order (ShipOrderContract, kept for old saves).
 */
public class ShipWantedContract extends ContractIntel {

    private final MarketAPI origin;
    private final String memberId;
    private final String shipName;
    private final String hullId;
    private final int fame;
    private final int pay;

    ShipWantedContract(int tier, PersonAPI client, MarketAPI origin, FleetMemberAPI member, int fame, int pay) {
        super(tier, client, pay, 0, 0, 1f);
        this.origin = origin;
        this.memberId = member.getId();
        this.shipName = member.getShipName();
        this.hullId = member.getHullSpec().getHullId();
        this.fame = fame;
        this.pay = pay;
    }

    /** The wanted ship, if it is still in the player's fleet. */
    private FleetMemberAPI member() {
        for (FleetMemberAPI m : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
            if (m.getId().equals(memberId)) return m;
        }
        return null;
    }

    private String className() {
        return Hulls.className(Global.getSettings().getHullSpec(hullId));
    }

    @Override
    public void showOfferVisual(InteractionDialogAPI dialog) {
        FleetMemberAPI member = member();
        if (member != null) {
            dialog.getVisualPanel().showFleetMemberInfo(member, true);
        } else {
            super.showOfferVisual(dialog);
        }
    }

    @Override
    protected MarketAPI clientMarket() {
        return origin;
    }

    @Override
    public String cannotSignReason() {
        FleetMemberAPI member = member();
        if (member == null) return "The " + shipName + " is no longer in your fleet.";
        if (member.isFlagship()) return "The " + shipName + " is your flagship.";
        return null;
    }

    @Override
    protected void onAccepted(TextPanelAPI text) {
        FleetMemberAPI member = member();
        if (member != null) {
            if (member.getCaptain() != null && !member.getCaptain().isDefault()) member.setCaptain(null);
            Global.getSector().getPlayerFleet().getFleetData().removeFleetMember(member);
            if (text != null) {
                text.setFontSmallInsignia();
                text.addParagraph("Lost " + shipName + ", " + Hulls.aClassName(member.getHullSpec()), Misc.getNegativeHighlightColor());
                text.setFontInsignia();
            }
        }
        end(State.COMPLETED, null);
    }

    @Override
    protected boolean shouldCancel() {
        return false;
    }

    @Override
    protected boolean countsAsProof() {
        return false;
    }

    // ----- offer -----

    @Override
    public void addOfferText(TextPanelAPI text) {
        text.addPara("\"I've had my eye on the " + shipName + " since you docked. " + Misc.ucFirst(Hulls.aClassName(
                Global.getSettings().getHullSpec(hullId))) + ", just what I've been looking for.\"");
        if (fame >= 4) {
            text.addPara("\"Everyone's heard what that ship has done. I want her, and I'll pay " + Misc.getDGSCredits(pay)
                    + " for her, right now. Nobody else will offer you that.\"");
        } else if (fame >= 2) {
            text.addPara("\"I've heard a few stories about her, too. " + Misc.getDGSCredits(pay)
                    + ", right now, well over what she's worth.\"");
        } else {
            text.addPara("\"" + Misc.getDGSCredits(pay) + ", right now. That's well over what she's worth.\"");
        }
    }

    @Override
    public void addOfferTerms(TooltipMakerAPI info) {
        Color h = Misc.getHighlightColor();
        info.addPara("Ship: %s, %s", 0f, h, shipName, className());
        if (fame > 0) info.addPara("Reputation: %s", 0f, h, Legends.fameName(fame));
        info.addPara("Hull value: %s", 0f, h, Misc.getDGSCredits(Global.getSettings().getHullSpec(hullId).getBaseValue()));
        addTerms(info, 10f);
    }

    @Override
    public void addTerms(TooltipMakerAPI info, float pad) {
        info.addPara("Payment on hand-over: %s", pad, Misc.getHighlightColor(), Misc.getDGSCredits(pay));
    }

    @Override
    public String signText() {
        return "You sign. " + client.getNameString() + "'s crew is aboard the " + shipName + " before the ink is dry.";
    }

    // ----- intel screen -----

    @Override
    protected String getJobName() {
        return "Ship Sale";
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getHullSpec(hullId).getSpriteName();
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return origin == null ? null : origin.getPrimaryEntity();
    }

    @Override
    protected void addJobBullets(TooltipMakerAPI info, Color tc, float pad) {
        info.addPara("The %s", pad, tc, Misc.getHighlightColor(), shipName);
    }

    @Override
    protected void addDescriptionTop(TooltipMakerAPI info, float width) {
        info.addImage(getIcon(), width, 80f, 10f);
        info.addPara("You sold the " + shipName + ", " + className() + ", to " + client.getNameString() + " of " + origin.getName()
                + " for " + Misc.getDGSCredits(pay) + ".", 10f);
    }

    @Override
    protected String endText() {
        return "You sold the " + shipName + ".";
    }
}
