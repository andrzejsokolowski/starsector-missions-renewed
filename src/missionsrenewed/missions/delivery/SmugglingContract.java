package missionsrenewed.missions.delivery;

import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Carry the client's weapons, drugs or organs to a market where they are illegal (design answer 8 on
 * MSR-1: illegal jobs). Getting caught is the game's own business: a patrol that scans the fleet takes
 * the goods and costs standing with its faction, and then the delivery comes up short.
 */
public class SmugglingContract extends DeliveryContract {

    public SmugglingContract(int tier, PersonAPI client, MarketAPI origin, MarketAPI destination, String commodityId, int units,
                             int advance, int onSuccess, int penalty, float durationDays) {
        super(tier, client, origin, destination, commodityId, units, advance, onSuccess, penalty, durationDays);
    }

    @Override
    protected boolean suppliedByClient() {
        return true;
    }

    @Override
    protected String getJobName() {
        return "Smuggling Run";
    }

    private String good() {
        return getCommodity().getLowerCaseName();
    }

    @Override
    public void addOfferText(TextPanelAPI text) {
        text.addPara(client.getNameString() + " has friends at " + destination.getName() + " who need " + good()
                + ", and " + destination.getFaction().getDisplayNameWithArticle() + " would rather they did not get any.");
        text.addPara("\"" + Misc.getWithDGS(units) + " units, crated as something dull. Get them to " + destination.getName()
                + " within " + Math.round(durationDays) + " days. If a patrol opens the crates, you never met me.\"");
    }

    @Override
    public void addOfferTerms(TooltipMakerAPI info) {
        addDeliveryTerms(info, "Cargo: %s units of " + good() + ", illegal at the destination");
    }

    @Override
    public String signText() {
        return "You sign nothing. A handshake, a name to ask for, and the crates are aboard before the next shift change.";
    }

    @Override
    protected String shortText(int have) {
        return "The contact at " + destination.getName() + " counts " + Misc.getWithDGS(have) + " of " + Misc.getWithDGS(units)
                + " units and walks off without a word.";
    }

    @Override
    protected String deliveredText() {
        return "In a back corner of the docks at " + destination.getName() + ", the crates change hands and nobody looks twice.";
    }

    @Override
    protected String descriptionText() {
        return client.getNameString() + " of " + origin.getName() + " has slipped " + Misc.getWithDGS(units) + " units of "
                + good() + " into your holds, for friends at " + where(destination) + ". Patrols that scan your fleet will not be friendly.";
    }
}
