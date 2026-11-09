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
        text.addPara(client.getNameString() + " holds a fuel contract with " + destination.getName()
                + " and a tanker that broke down three systems short.");
        text.addPara("\"" + Misc.getWithDGS(units) + " units, pumped into your tanks here. Every unit is metered on arrival at "
                + destination.getName() + ", within " + Math.round(durationDays)
                + " days. What you burn on the way, you make up yourself.\"");
    }

    @Override
    public void addOfferTerms(TooltipMakerAPI info) {
        addDeliveryTerms(info, "Fuel: %s units, pumped into your tanks by the client");
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
