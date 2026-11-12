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
        if (specialId == null) return null;
        try {
            return Global.getSettings().getSpecialItemSpec(specialId);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The item's name, or a stand-in when the mod that added it is gone. */
    private String specialName() {
        SpecialItemSpecAPI spec = special();
        return spec == null ? "colony item" : spec.getName();
    }

    /** The asked-for item no longer exists (its mod was removed): the contract is void. */
    @Override
    protected boolean shouldCancel() {
        return super.shouldCancel() || (specialId != null && special() == null);
    }

    private SpecialItemData specialData() {
        return new SpecialItemData(specialId, null);
    }

    /** "units of metals" or "Pristine Nanoforge". */
    private String goodsName() {
        return specialId != null ? specialName() : getCommodity().getLowerCaseName();
    }

    @Override
    protected boolean suppliedByClient() {
        return false;
    }

    @Override
    protected String goodsPhrase() {
        return specialId != null ? specialName() : "units of " + getCommodity().getLowerCaseName();
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
            text.addParagraph("Lost " + specialName(), Misc.getNegativeHighlightColor());
            text.setFontInsignia();
        }
    }

    @Override
    public String getIcon() {
        if (specialId == null) return super.getIcon();
        SpecialItemSpecAPI spec = special();
        return spec != null ? spec.getIconName() : Global.getSettings().getSpriteName("intel", "important_accepted_mission");
    }

    @Override
    protected String getJobName() {
        return "Contribution";
    }

    @Override
    public void addOfferText(TextPanelAPI text) {
        if (specialId != null) {
            text.addPara("\"Our consortium at " + destination.getName() + " has the money for a " + goodsName()
                    + ", but nobody will sell us one.\"");
            text.addPara("\"Find a working one and bring it to " + destination.getName() + " within "
                    + Math.round(durationDays) + " days. I won't ask where you got it.\"");
            return;
        }
        text.addPara("\"We're building at " + destination.getName() + ", and the local suppliers ran out of "
                + goodsName() + " a month ago. I need " + Misc.getWithDGS(units) + " units of it there within "
                + Math.round(durationDays) + " days.\"");
        text.addPara("\"Buy it wherever it's cheap and haul it over. The project pays well, but it can't wait.\"");
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
