package missionsrenewed.missions.delivery;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI.CargoItemType;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Bring goods the player finds or buys to a project at another market (design answers 8 and 9 on MSR-1).
 * Nothing is handed over. The pay covers the goods about twice over. At tiers 4 to 6 the project can
 * ask for one colony item instead: sold, never lent.
 */
public class ContributionContract extends DeliveryContract {

    /** A colony item asked for instead of a commodity, or null. */
    private final String specialId;

    public ContributionContract(int tier, PersonAPI client, MarketAPI origin, MarketAPI destination, String commodityId,
                                String specialId, int units, int advance, int onSuccess, int penalty, float durationDays) {
        super(tier, client, origin, destination, commodityId, units, advance, onSuccess, penalty, durationDays);
        this.specialId = specialId;
    }

    private SpecialItemSpecAPI special() {
        return specialId == null ? null : Global.getSettings().getSpecialItemSpec(specialId);
    }

    private SpecialItemData specialData() {
        return new SpecialItemData(specialId, null);
    }

    /** "units of metals" or "Pristine Nanoforge". */
    private String goodsName() {
        return specialId != null ? special().getName() : getCommodity().getLowerCaseName();
    }

    @Override
    protected boolean suppliedByClient() {
        return false;
    }

    @Override
    protected String goodsPhrase() {
        return specialId != null ? special().getName() : "units of " + getCommodity().getLowerCaseName();
    }

    @Override
    protected int carried() {
        if (specialId != null) return (int) playerCargo().getQuantity(CargoItemType.SPECIAL, specialData());
        return super.carried();
    }

    @Override
    protected void removeDelivered(TextPanelAPI text) {
        if (specialId == null) {
            super.removeDelivered(text);
            return;
        }
        playerCargo().removeItems(CargoItemType.SPECIAL, specialData(), units);
        if (text != null) {
            text.setFontSmallInsignia();
            text.addParagraph("Lost " + special().getName(), Misc.getNegativeHighlightColor());
            text.setFontInsignia();
        }
    }

    @Override
    public String getIcon() {
        return specialId != null ? special().getIconName() : super.getIcon();
    }

    @Override
    protected String getJobName() {
        return "Contribution";
    }

    @Override
    public void addOfferText(TextPanelAPI text) {
        if (specialId != null) {
            text.addPara(client.getNameString() + " speaks for a consortium at " + destination.getName()
                    + " that has the capital for a " + goodsName() + " and no way to find one.");
            text.addPara("\"Bring a working " + goodsName() + " to " + destination.getName() + " within "
                    + Math.round(durationDays) + " days. Where it came from is nobody's business.\"");
            return;
        }
        text.addPara(client.getNameString() + " is gathering " + goodsName() + " for a construction project at "
                + destination.getName() + ", and the local suppliers have run dry.");
        text.addPara("\"" + Misc.getWithDGS(units) + " units at " + destination.getName() + " within "
                + Math.round(durationDays) + " days. Buy it, dig it up, salvage it, I do not care. The project pays well.\"");
    }

    @Override
    public void addOfferTerms(TooltipMakerAPI info) {
        if (specialId != null) {
            info.addPara("Wanted: one %s, which you must find yourself", 0f, Misc.getHighlightColor(), goodsName());
            info.addPara("Destination: %s, %s light years away", 0f, Misc.getHighlightColor(), destination.getName(),
                    Misc.getRoundedValueMaxOneAfterDecimal(getDistanceLY()));
            info.addPara("Deadline: %s days", 0f, Misc.getHighlightColor(), "" + Math.round(durationDays));
            addTerms(info, 10f);
            return;
        }
        addDeliveryTerms(info, "Wanted: %s units of " + goodsName() + ", which you must find yourself");
    }

    @Override
    public String signText() {
        return "You sign. Somewhere at " + destination.getName() + ", a quartermaster adds your name to a list.";
    }

    @Override
    protected String shortText(int have) {
        if (specialId != null) {
            return "The consortium's agent at " + destination.getName() + " is waiting for a " + goodsName() + ". You have none to show.";
        }
        return "The project quartermaster at " + destination.getName() + " counts " + Misc.getWithDGS(have) + " of "
                + Misc.getWithDGS(units) + " units of " + goodsName() + " and sends you back for the rest.";
    }

    @Override
    protected String deliveredText() {
        if (specialId != null) {
            return "Technicians at " + destination.getName() + " crate the " + goodsName() + " with more care than they show their own children.";
        }
        return "The project quartermaster at " + destination.getName() + " signs for the " + goodsName() + " and the payment clears.";
    }

    @Override
    protected String descriptionText() {
        if (specialId != null) {
            return client.getNameString() + " of " + origin.getName() + " wants a " + goodsName() + " brought to "
                    + where(destination) + ".";
        }
        return client.getNameString() + " of " + origin.getName() + " wants " + Misc.getWithDGS(units) + " units of "
                + goodsName() + " brought to a construction project at " + where(destination) + ".";
    }
}
