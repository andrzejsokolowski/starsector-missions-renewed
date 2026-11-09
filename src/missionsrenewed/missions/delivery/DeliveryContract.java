package missionsrenewed.missions.delivery;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import missionsrenewed.missions.ContractIntel;

import java.awt.Color;

/**
 * Bring a number of units of one commodity to a destination market before the deadline. Docking there
 * with all of it on board completes the job (DeliveryListener). Hauling, fuel runs and crew transport
 * hand the goods over on signing. Contributions leave the player to find them.
 *
 * The fields were HaulingContract's in 0.1.0. They live here now, under the same names, so a 0.1.0
 * save still loads its hauling contracts.
 */
public abstract class DeliveryContract extends ContractIntel {

    protected final String commodityId;
    protected final int units;
    protected final MarketAPI origin;
    protected final MarketAPI destination;

    protected DeliveryContract(int tier, PersonAPI client, MarketAPI origin, MarketAPI destination, String commodityId,
                               int units, int advance, int onSuccess, int penalty, float durationDays) {
        super(tier, client, advance, onSuccess, penalty, durationDays);
        this.origin = origin;
        this.destination = destination;
        this.commodityId = commodityId;
        this.units = units;
    }

    public MarketAPI getDestination() {
        return destination;
    }

    public int getUnits() {
        return units;
    }

    public CommoditySpecAPI getCommodity() {
        return Global.getSettings().getCommoditySpec(commodityId);
    }

    public float getDistanceLY() {
        return Misc.getDistanceLY(origin.getPrimaryEntity(), destination.getPrimaryEntity());
    }

    @Override
    protected MarketAPI clientMarket() {
        return origin;
    }

    /** True when the client fills the player's holds on signing. */
    protected abstract boolean suppliedByClient();

    /** Room the fleet has for this job's goods: cargo space, fuel tanks or crew quarters. */
    protected float roomLeft() {
        return Global.getSector().getPlayerFleet().getCargo().getSpaceLeft();
    }

    /** "units of ore", "units of fuel", "crew". */
    protected String goodsPhrase() {
        return "units of " + getCommodity().getLowerCaseName();
    }

    /** How the room is described when it is short, for the greyed-out Sign button. */
    protected String roomWord() {
        return "Your holds have room for %s more units";
    }

    /** How much of the goods the fleet carries now. */
    protected int carried() {
        return (int) Global.getSector().getPlayerFleet().getCargo().getCommodityQuantity(commodityId);
    }

    /** Takes the delivered goods out of the fleet and shows it. */
    protected void removeDelivered(TextPanelAPI text) {
        Global.getSector().getPlayerFleet().getCargo().removeCommodity(commodityId, units);
        if (text != null) AddRemoveCommodity.addCommodityLossText(commodityId, units, text);
    }

    /** The line when the destination takes the goods. */
    protected abstract String deliveredText();

    /** The line when the player arrives short. */
    protected String shortText(int have) {
        return "The client's agent at " + destination.getName() + " counts " + Misc.getWithDGS(have) + " of "
                + Misc.getWithDGS(units) + " " + goodsPhrase() + " and refuses a short delivery.";
    }

    @Override
    public String cannotSignReason() {
        if (!suppliedByClient()) return null;
        float room = roomLeft();
        if (room >= units) return null;
        return String.format(roomWord(), Misc.getWithDGS((int) room)) + ", the job needs " + Misc.getWithDGS(units) + ".";
    }

    @Override
    protected void onAccepted(TextPanelAPI text) {
        if (!suppliedByClient()) return;
        Global.getSector().getPlayerFleet().getCargo().addCommodity(commodityId, units);
        if (text != null) AddRemoveCommodity.addCommodityGainText(commodityId, units, text);
    }

    @Override
    protected boolean shouldCancel() {
        return destination == null || !destination.isInEconomy() || destination.getPrimaryEntity() == null;
    }

    /** Called when the player docks at a market. Completes the job when this is the destination and the goods are all there. */
    public void playerDockedAt(MarketAPI market, InteractionDialogAPI dialog) {
        if (!isActive() || market != destination) return;
        TextPanelAPI text = dialog == null ? null : dialog.getTextPanel();
        int have = carried();
        if (have < units) {
            String msg = shortText(have);
            if (text != null) {
                text.addPara(msg, Misc.getNegativeHighlightColor());
            } else {
                Global.getSector().getCampaignUI().addMessage(msg, Misc.getNegativeHighlightColor());
            }
            return;
        }
        if (text != null) text.addPara(deliveredText());
        removeDelivered(text);
        end(State.COMPLETED, text);
    }

    @Override
    protected void testComplete() {
        if (carried() >= units) removeDelivered(null);
        end(State.COMPLETED, null);
    }

    // ----- offer -----

    /** The common part of the terms box: goods, destination, deadline, then the money. */
    protected void addDeliveryTerms(TooltipMakerAPI info, String goodsLine) {
        Color h = Misc.getHighlightColor();
        info.addPara(goodsLine, 0f, h, Misc.getWithDGS(units));
        info.addPara("Destination: %s, %s light years away", 0f, h, destination.getName(),
                Misc.getRoundedValueMaxOneAfterDecimal(getDistanceLY()));
        info.addPara("Deadline: %s days", 0f, h, "" + Math.round(durationDays));
        addTerms(info, 10f);
    }

    // ----- intel screen -----

    @Override
    public String getIcon() {
        return getCommodity().getIconName();
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return destination == null ? null : destination.getPrimaryEntity();
    }

    @Override
    protected void addJobBullets(TooltipMakerAPI info, Color tc, float pad) {
        Color h = Misc.getHighlightColor();
        info.addPara("%s " + goodsPhrase() + " to %s", pad, tc, h, Misc.getWithDGS(units), destination.getName())
                .setHighlightColors(h, destination.getFaction().getBaseUIColor());
    }

    @Override
    protected void addDescriptionTop(TooltipMakerAPI info, float width) {
        info.addImages(width, 80f, 10f, 20f, getIcon(), client.getPortraitSprite());
        info.addPara(descriptionText(), 10f);
    }

    /** The opening paragraph of the intel description. */
    protected abstract String descriptionText();

    /** "Jangala in the Corvus star system", or just the name outside a star system. */
    public static String where(MarketAPI market) {
        if (market.getStarSystem() == null) return market.getName();
        return market.getName() + " in the " + market.getStarSystem().getNameWithLowercaseType();
    }

    /** Short for the cargo object of the player's fleet. */
    protected static CargoAPI playerCargo() {
        return Global.getSector().getPlayerFleet().getCargo();
    }
}
