package missionsrenewed.missions.hauling;

import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import missionsrenewed.missions.delivery.DeliveryContract;

/**
 * Carry the client's cargo to another market before the deadline. The cargo is handed over on signing.
 * Kept in this package under this name because 0.1.0 saves hold it.
 */
public class HaulingContract extends DeliveryContract {

    public HaulingContract(int tier, PersonAPI client, MarketAPI origin, MarketAPI destination, String commodityId, int units,
                           int advance, int onSuccess, int penalty, float durationDays) {
        super(tier, client, origin, destination, commodityId, units, advance, onSuccess, penalty, durationDays);
    }

    @Override
    protected boolean suppliedByClient() {
        return true;
    }

    @Override
    protected String getJobName() {
        return "Hauling Contract";
    }

    @Override
    public void addOfferText(TextPanelAPI text) {
        String good = getCommodity().getLowerCaseName();
        text.addPara(client.getNameString() + " has a hold's worth of " + good
                + " that needs to be somewhere else, and no patience for slow captains.");
        text.addPara("\"" + Misc.getWithDGS(units) + " units, my crates, your holds. Delivered to "
                + destination.getName() + " within " + Math.round(durationDays)
                + " days. Short or late, and the breach clause applies.\"");
    }

    @Override
    public void addOfferTerms(TooltipMakerAPI info) {
        addDeliveryTerms(info, "Cargo: %s units of " + getCommodity().getLowerCaseName() + ", supplied by the client");
    }

    @Override
    public String signText() {
        return "You sign. Dock crews start moving the crates into your holds before the ink is dry.";
    }

    @Override
    protected String deliveredText() {
        return "Dock crews at " + destination.getName() + " unload the contracted " + getCommodity().getLowerCaseName()
                + " and the client's agent signs off on the delivery.";
    }

    @Override
    protected String descriptionText() {
        return client.getNameString() + " of " + origin.getName() + " has trusted you with " + Misc.getWithDGS(units)
                + " units of " + getCommodity().getLowerCaseName() + ", to be delivered to " + where(destination) + ".";
    }
}
