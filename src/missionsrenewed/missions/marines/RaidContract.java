package missionsrenewed.missions.marines;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import missionsrenewed.missions.ContractIntel;
import missionsrenewed.missions.delivery.DeliveryContract;

import java.awt.Color;

/**
 * A marine raid (design answer 14 on MSR-1): disrupt one named industry at a market hostile to the client.
 * The target is picked so its ground defence fits the tier. RaidListener reports the game's own raid
 * results. Peace between the two factions, or the target market dying, voids the contract.
 */
public class RaidContract extends ContractIntel {

    private final MarketAPI origin;
    private final MarketAPI target;
    private final String industryId;
    private final String industryName;
    private final int defence;

    RaidContract(int tier, PersonAPI client, MarketAPI origin, MarketAPI target, Industry industry, int defence,
                 int advance, int onSuccess, int penalty, float durationDays) {
        super(tier, client, advance, onSuccess, penalty, durationDays);
        this.origin = origin;
        this.target = target;
        this.industryId = industry.getId();
        this.industryName = industry.getCurrentName();
        this.defence = defence;
    }

    @Override
    protected MarketAPI clientMarket() {
        return origin;
    }

    @Override
    protected String contactType() {
        return com.fs.starfarer.api.impl.campaign.ids.Tags.CONTACT_MILITARY;
    }

    @Override
    protected void onAccepted(TextPanelAPI text) {}

    @Override
    protected boolean shouldCancel() {
        return target == null || !target.isInEconomy() || target.getPrimaryEntity() == null
                || !target.getFaction().isHostileTo(factionId);
    }

    /** From RaidListener: a raid to disrupt finished at market. */
    void raided(MarketAPI market, Industry industry, InteractionDialogAPI dialog) {
        if (!isActive() || market != target) return;
        boolean named = industry != null && industryId.equals(industry.getId());
        boolean namedIsGone = target.getIndustry(industryId) == null;
        if (!named && !namedIsGone) return;
        TextPanelAPI text = dialog == null ? null : dialog.getTextPanel();
        if (text != null) text.addPara("Somewhere at " + origin.getName() + ", " + client.getNameString() + " reads the damage report and smiles.");
        end(State.COMPLETED, text);
    }

    // ----- offer -----

    @Override
    public void addOfferText(TextPanelAPI text) {
        text.addPara(client.getNameString() + " wants the " + industryName + " at " + target.getName()
                + " out of action, and would rather nobody from " + getClientFaction().getDisplayNameWithArticle() + " be seen doing it.");
        text.addPara("\"Put marines on the ground at " + target.getName() + " and break the " + industryName + " within "
                + Math.round(durationDays) + " days. Bring enough of them. The garrison there is not asleep.\"");
    }

    @Override
    public void addOfferTerms(TooltipMakerAPI info) {
        Color h = Misc.getHighlightColor();
        info.addPara("Target: the %s at %s, " + target.getFaction().getDisplayNameWithArticle(), 0f, h, industryName, target.getName());
        info.addPara("Ground defences: about %s", 0f, h, Misc.getWithDGS(defence));
        info.addPara("Location: %s, %s light years away", 0f, h, DeliveryContract.where(target),
                Misc.getRoundedValueMaxOneAfterDecimal(Misc.getDistanceLY(origin.getPrimaryEntity(), target.getPrimaryEntity())));
        info.addPara("Deadline: %s days", 0f, h, "" + Math.round(durationDays));
        info.addPara("Raiding costs standing with %s.", 0f, Misc.getNegativeHighlightColor(), target.getFaction().getDisplayNameWithArticle());
        addTerms(info, 10f);
    }

    @Override
    public String signText() {
        return "You sign. " + client.getNameString() + " slides over a datachip of " + target.getName() + "'s garrison rosters.";
    }

    // ----- intel screen -----

    @Override
    protected String getJobName() {
        return "Raid Contract";
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getCommoditySpec("marines").getIconName();
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return target == null ? null : target.getPrimaryEntity();
    }

    @Override
    protected void addJobBullets(TooltipMakerAPI info, Color tc, float pad) {
        Color h = Misc.getHighlightColor();
        info.addPara("Disrupt the %s at %s", pad, tc, h, industryName, target.getName())
                .setHighlightColors(h, target.getFaction().getBaseUIColor());
    }

    @Override
    protected void addDescriptionTop(TooltipMakerAPI info, float width) {
        info.addImages(width, 80f, 10f, 20f, getIcon(), client.getPortraitSprite());
        info.addPara(client.getNameString() + " of " + origin.getName() + " wants the " + industryName + " at "
                + DeliveryContract.where(target) + " disrupted by a marine raid. Its ground defences stand at about "
                + Misc.getWithDGS(defence) + ".", 10f);
    }

    @Override
    protected String endText() {
        switch (state) {
            case COMPLETED: return "The " + industryName + " at " + target.getName() + " is out of action.";
            case CANCELLED: return "The contract is void: " + target.getName() + " is no longer an enemy of the client.";
            default: return super.endText();
        }
    }
}
