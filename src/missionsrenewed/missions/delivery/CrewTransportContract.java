package missionsrenewed.missions.delivery;

import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Carry the client's contract workers to another market. They ride as crew, so they take crew quarters
 * and crew lost in battle count against the delivery (design answer 14 on MSR-1).
 */
public class CrewTransportContract extends DeliveryContract {

    public CrewTransportContract(int tier, PersonAPI client, MarketAPI origin, MarketAPI destination, int units,
                                 int advance, int onSuccess, int penalty, float durationDays) {
        super(tier, client, origin, destination, Commodities.CREW, units, advance, onSuccess, penalty, durationDays);
    }

    @Override
    protected boolean suppliedByClient() {
        return true;
    }

    @Override
    protected float roomLeft() {
        return playerCargo().getMaxPersonnel() - playerCargo().getTotalPersonnel();
    }

    @Override
    protected String goodsPhrase() {
        return "workers";
    }

    @Override
    protected String roomWord() {
        return "Your crew quarters have room for %s more people";
    }

    @Override
    protected String getJobName() {
        return "Crew Transport";
    }

    @Override
    public void addOfferText(TextPanelAPI text) {
        text.addPara("\"" + destination.getName() + " paid for " + Misc.getWithDGS(units)
                + " of my workers, and they're packed and ready. They need a ride, and they need it within "
                + Math.round(durationDays) + " days.\"");
        text.addPara("\"Feed them, keep the air on, and don't lose anybody. If they don't all walk off at "
                + destination.getName() + ", that's on you.\"");
    }

    @Override
    public void addOfferTerms(TooltipMakerAPI info) {
        addDeliveryTerms(info, "Passengers: %s workers, boarding here");
    }

    @Override
    public String signText() {
        return "You sign. A line of workers with duffel bags files up the boarding ramp.";
    }

    @Override
    protected String shortText(int have) {
        return "The labour office at " + destination.getName() + " checks the manifest against your crew rolls: "
                + Misc.getWithDGS(have) + " aboard, " + Misc.getWithDGS(units) + " owed. They will not sign for a short crew.";
    }

    @Override
    protected String deliveredText() {
        return "The workers file down the ramp at " + destination.getName() + " and the labour office signs the manifest.";
    }

    @Override
    protected String descriptionText() {
        return client.getNameString() + " of " + origin.getName() + " has put " + Misc.getWithDGS(units)
                + " contract workers aboard your fleet, bound for " + where(destination) + ".";
    }
}
