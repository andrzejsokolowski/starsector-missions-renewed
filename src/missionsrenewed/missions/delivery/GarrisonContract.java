package missionsrenewed.missions.delivery;

import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Garrison duty (design answer 14 on MSR-1): bring your own marines to a market of the client's faction,
 * where they sign on to the garrison for good. The pay covers their worth about twice over.
 */
public class GarrisonContract extends DeliveryContract {

    public GarrisonContract(int tier, PersonAPI client, MarketAPI origin, MarketAPI destination, int units,
                            int advance, int onSuccess, int penalty, float durationDays) {
        super(tier, client, origin, destination, Commodities.MARINES, units, advance, onSuccess, penalty, durationDays);
    }

    @Override
    protected String contactType() {
        return com.fs.starfarer.api.impl.campaign.ids.Tags.CONTACT_MILITARY;
    }

    @Override
    protected boolean suppliedByClient() {
        return false;
    }

    @Override
    protected String goodsPhrase() {
        return "marines";
    }

    @Override
    protected String getJobName() {
        return "Garrison Contract";
    }

    @Override
    public void addOfferText(TextPanelAPI text) {
        text.addPara("\"" + destination.getName() + " needs a garrison, and we're short of anyone who can hold a rifle. "
                + "I hear you have marines.\"");
        text.addPara("\"Bring me " + Misc.getWithDGS(units) + " of them at " + destination.getName() + " within "
                + Math.round(durationDays) + " days. They'll sign on with us for good, and we pay well for soldiers "
                + "who already know which end of the gun to point.\"");
    }

    @Override
    public void addOfferTerms(TooltipMakerAPI info) {
        addDeliveryTerms(info, "Wanted: %s of your own marines, to stay with the garrison");
    }

    @Override
    public String signText() {
        return "You sign. " + client.getNameString() + " salutes, a little stiffly, and wires the advance.";
    }

    @Override
    protected String shortText(int have) {
        return "The garrison commander at " + destination.getName() + " counts " + Misc.getWithDGS(have) + " of "
                + Misc.getWithDGS(units) + " marines and sends you back for the rest.";
    }

    @Override
    protected String deliveredText() {
        return "Your marines march down the ramp at " + destination.getName() + " and into the garrison's barracks.";
    }

    @Override
    protected String descriptionText() {
        return client.getNameString() + " of " + origin.getName() + " wants " + Misc.getWithDGS(units)
                + " of your marines for the garrison at " + where(destination) + ".";
    }
}
