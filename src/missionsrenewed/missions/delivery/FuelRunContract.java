package missionsrenewed.missions.delivery;

import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Deliver the client's fuel. It goes into the fleet's own tanks, so every jump on the way burns into
 * the delivery unless the player brings spare fuel (design answer 14 on MSR-1).
 */
public class FuelRunContract extends DeliveryContract {

    public FuelRunContract(int tier, PersonAPI client, MarketAPI origin, MarketAPI destination, int units,
                           int advance, int onSuccess, int penalty, float durationDays) {
        super(tier, client, origin, destination, Commodities.FUEL, units, advance, onSuccess, penalty, durationDays);
    }

    @Override
    protected boolean suppliedByClient() {
        return true;
    }

    @Override
    protected float roomLeft() {
        return playerCargo().getMaxFuel() - playerCargo().getFuel();
    }

    @Override
    protected int carried() {
        return (int) playerCargo().getFuel();
    }

    @Override
    protected String goodsPhrase() {
        return "units of fuel";
    }

    @Override
    protected String roomWord() {
        return "Your fuel tanks have room for %s more units";
    }

    @Override
    protected String getJobName() {
        return "Fuel Run";
    }

    @Override
    public void addOfferText(TextPanelAPI text) {
        text.addPara("\"My tanker broke down three systems short, and " + destination.getName() + " is still waiting on "
                + Misc.getWithDGS(units) + " units of fuel. I'll pump it into your tanks right here.\"");
        text.addPara("\"Get it there within " + Math.round(durationDays) + " days. They meter every unit when you dock, "
                + "so whatever you burn on the way, you replace out of your own pocket.\"");
    }

    @Override
    public void addOfferTerms(TooltipMakerAPI info) {
        addDeliveryTerms(info, "Fuel: %s units, pumped into your tanks by " + client.getNameString());
    }

    @Override
    public String signText() {
        return "You sign. Hoses snake out from the dock and the tank gauges start to climb.";
    }

    @Override
    protected String shortText(int have) {
        return "The depot master at " + destination.getName() + " reads your gauges: " + Misc.getWithDGS(have) + " of "
                + Misc.getWithDGS(units) + " units. Not enough to sign off the delivery.";
    }

    @Override
    protected String deliveredText() {
        return "The depot at " + destination.getName() + " drains the contracted fuel from your tanks and the meters agree.";
    }

    @Override
    protected String descriptionText() {
        return client.getNameString() + " of " + origin.getName() + " has filled your tanks with " + Misc.getWithDGS(units)
                + " units of fuel, owed to " + where(destination) + ". Every jump burns into it.";
    }
}
