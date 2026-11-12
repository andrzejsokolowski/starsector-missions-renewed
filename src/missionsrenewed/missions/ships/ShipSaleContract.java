package missionsrenewed.missions.ships;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import missionsrenewed.missions.ContractIntel;

import java.awt.Color;
import java.util.Random;

/**
 * A client sells the player one ship below its value, with S-mods by tier. Most are used, with 1 to 4 D-mods and
 * a lower price; a few are pristine. Signing pays the price (a negative advance) and puts the ship in the fleet;
 * the contract is complete at once.
 */
public class ShipSaleContract extends ContractIntel {

    private final MarketAPI origin;
    private final String variantId;
    private final int sMods;
    /** D-mods on the ship. 0 (pristine) for sales offered before 0.8.1. */
    private final int dMods;
    /** Picks which D-mods, the same on the offer screen and on the ship handed over. */
    private final long conditionSeed;
    private final String shipName;
    private final int price;

    ShipSaleContract(int tier, PersonAPI client, MarketAPI origin, String variantId, int sMods, int dMods, long conditionSeed,
                     String shipName, int price) {
        super(tier, client, -price, 0, 0, 1f);
        this.origin = origin;
        this.variantId = variantId;
        this.sMods = sMods;
        this.dMods = dMods;
        this.conditionSeed = conditionSeed;
        this.shipName = shipName;
        this.price = price;
    }

    /** The ship as the offer shows it. Not the one handed over: signing builds that one fresh. */
    private transient FleetMemberAPI shown;

    private ShipHullSpecAPI hull() {
        return Global.getSettings().getVariant(variantId).getHullSpec();
    }

    /** The ship as sold: the stock variant with its S-mods, then its D-mods. */
    private FleetMemberAPI build() {
        ShipVariantAPI variant = Hulls.withSMods(variantId, sMods);
        if (dMods > 0) {
            DModManager.addDMods(variant, false, dMods, new Random(conditionSeed));
            DModManager.setDHull(variant);
        }
        return Hulls.member(variant, shipName);
    }

    private String condition() {
        if (dMods <= 0) return "pristine";
        return dMods == 1 ? "used, 1 D-mod" : "used, " + dMods + " D-mods";
    }

    @Override
    public void showOfferVisual(InteractionDialogAPI dialog) {
        if (shown == null) shown = build();
        dialog.getVisualPanel().showFleetMemberInfo(shown, true);
    }

    @Override
    protected boolean countsAsProof() {
        return false;
    }

    @Override
    protected MarketAPI clientMarket() {
        return origin;
    }

    @Override
    public String cannotSignReason() {
        float credits = Global.getSector().getPlayerFleet().getCargo().getCredits().get();
        if (credits < price) {
            return "The price is " + Misc.getDGSCredits(price) + ", you have " + Misc.getDGSCredits(Math.max(0f, credits)) + ".";
        }
        int ships = Global.getSector().getPlayerFleet().getFleetData().getNumMembers();
        if (ships >= Global.getSettings().getMaxShipsInFleet()) return "Your fleet has no room for another ship.";
        return null;
    }

    @Override
    protected void onAccepted(TextPanelAPI text) {
        FleetMemberAPI member = build();
        Global.getSector().getPlayerFleet().getFleetData().addFleetMember(member);
        if (text != null) {
            text.setFontSmallInsignia();
            text.addParagraph("Gained " + shipName + ", " + Hulls.aClassName(hull()), Misc.getPositiveHighlightColor());
            text.setFontInsignia();
        }
        end(State.COMPLETED, null);
    }

    @Override
    protected boolean shouldCancel() {
        return false;
    }

    @Override
    protected float repOnEnd(State result) {
        return 0f;
    }

    // ----- offer -----

    @Override
    public void addOfferText(TextPanelAPI text) {
        String state;
        if (dMods <= 0) state = "Never seen a day of real damage. ";
        else if (dMods <= 2) state = "She's taken a few hits, I won't pretend otherwise, and the price says so. ";
        else state = "She's been through hell and it shows. But she still flies, and you won't find one cheaper. ";
        text.addPara("\"I have " + Hulls.aClassName(hull()) + " to sell, the " + shipName + ". " + state
                + Misc.getDGSCredits(price) + " and it is yours.\"");
    }

    @Override
    public void addOfferTerms(TooltipMakerAPI info) {
        Color h = Misc.getHighlightColor();
        info.addPara("Ship: %s, %s", 0f, h, shipName, Hulls.className(hull()));
        info.addPara("Condition: %s", 0f, dMods > 0 ? Misc.getNegativeHighlightColor() : h, condition());
        if (sMods > 0) info.addPara("S-mods: %s", 0f, h, "" + sMods);
        info.addPara("Hull value: %s", 0f, h, Misc.getDGSCredits(hull().getBaseValue()));
        addTerms(info, 10f);
    }

    @Override
    public void addTerms(TooltipMakerAPI info, float pad) {
        info.addPara("Price: %s", pad, Misc.getNegativeHighlightColor(), Misc.getDGSCredits(price));
    }

    @Override
    public String signText() {
        return "You pay. " + client.getNameString() + " hands over the " + shipName + "'s command codes.";
    }

    // ----- intel screen -----

    @Override
    protected String getJobName() {
        return "Ship Purchase";
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
        info.addPara("The %s", pad, tc, Misc.getHighlightColor(), shipName);
    }

    @Override
    protected void addDescriptionTop(TooltipMakerAPI info, float width) {
        info.addImage(hull().getSpriteName(), width, 80f, 10f);
        info.addPara("You bought the " + shipName + ", " + Hulls.aClassName(hull()) + ", from " + client.getNameString()
                + " of " + origin.getName() + " for " + Misc.getDGSCredits(price) + ".", 10f);
    }

    @Override
    protected String endText() {
        return "You bought the " + shipName + ".";
    }
}
