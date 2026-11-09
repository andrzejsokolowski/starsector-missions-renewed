package missionsrenewed.missions.hauling;

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
 * Carry the client's cargo to another market before the deadline. The cargo is handed over on signing,
 * and docking at the destination with all of it on board completes the job (HaulingDelivery).
 */
public class HaulingContract extends ContractIntel {

    private final String commodityId;
    private final int units;
    private final MarketAPI origin;
    private final MarketAPI destination;

    HaulingContract(int tier, PersonAPI client, MarketAPI origin, MarketAPI destination, String commodityId, int units,
                    int advance, int onSuccess, int penalty, float durationDays) {
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

    public float getDurationDays() {
        return durationDays;
    }

    @Override
    protected void onAccepted(TextPanelAPI text) {
        Global.getSector().getPlayerFleet().getCargo().addCommodity(commodityId, units);
        if (text != null) AddRemoveCommodity.addCommodityGainText(commodityId, units, text);
    }

    @Override
    protected boolean shouldCancel() {
        return destination == null || !destination.isInEconomy() || destination.getPrimaryEntity() == null;
    }

    /** Called when the player docks at a market. Completes the job when this is the destination and the cargo is all there. */
    public void playerDockedAt(MarketAPI market, InteractionDialogAPI dialog) {
        if (!isActive() || market != destination) return;
        TextPanelAPI text = dialog == null ? null : dialog.getTextPanel();
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        int carried = (int) cargo.getCommodityQuantity(commodityId);
        String name = getCommodity().getLowerCaseName();
        if (carried < units) {
            String msg = "The client's agent at " + destination.getName() + " counts " + Misc.getWithDGS(carried)
                    + " of " + Misc.getWithDGS(units) + " units of " + name + " and refuses a short delivery.";
            if (text != null) {
                text.addPara(msg, Misc.getNegativeHighlightColor());
            } else {
                Global.getSector().getCampaignUI().addMessage(msg, Misc.getNegativeHighlightColor());
            }
            return;
        }
        cargo.removeCommodity(commodityId, units);
        if (text != null) {
            text.addPara("Dock crews at " + destination.getName() + " unload the contracted " + name
                    + " and the client's agent signs off on the delivery.");
            AddRemoveCommodity.addCommodityLossText(commodityId, units, text);
        }
        end(State.COMPLETED, text);
    }

    // ----- intel screen -----

    @Override
    protected String getJobName() {
        return "Hauling Contract";
    }

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
        info.addPara("%s units of " + getCommodity().getLowerCaseName() + " to %s", pad, tc, h,
                Misc.getWithDGS(units), destination.getName())
                .setHighlightColors(h, destination.getFaction().getBaseUIColor());
    }

    @Override
    protected void addDescriptionTop(TooltipMakerAPI info, float width) {
        info.addImages(width, 80f, 10f, 20f, getCommodity().getIconName(), client.getPortraitSprite());
        info.addPara(client.getNameString() + " of " + origin.getName() + " has trusted you with "
                        + Misc.getWithDGS(units) + " units of " + getCommodity().getLowerCaseName()
                        + ", to be delivered to " + where(destination) + ".", 10f);
    }

    /** "Jangala in the Corvus star system", or just the name outside a star system. */
    static String where(MarketAPI market) {
        if (market.getStarSystem() == null) return market.getName();
        return market.getName() + " in the " + market.getStarSystem().getNameWithLowercaseType();
    }
}
