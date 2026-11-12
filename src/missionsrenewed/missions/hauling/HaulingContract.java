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
        text.addPara("\"I've got " + Misc.getWithDGS(units) + " units of " + good + " that need to be at "
                + destination.getName() + " within " + Math.round(durationDays) + " days. My crates, your holds.\"");
        text.addPara("\"We don't take captains who show up late, or who get ideas about selling my cargo on the side. "
                + "Try either and you'll pay the breach fee, and I will make sure you pay it.\"");
    }

    @Override
    public void addOfferTerms(TooltipMakerAPI info) {
        addDeliveryTerms(info, "Cargo: %s units of " + getCommodity().getLowerCaseName() + ", supplied by " + client.getNameString());
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
