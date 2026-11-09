package missionsrenewed.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.ReputationActionResponsePlugin.ReputationAdjustmentResult;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.CustomRepImpact;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * One accepted tiered contract. Pays like a Kerbal Space Program contract (design answer 11 on MSR-1):
 * an advance when signed, a payment on success, and a penalty on failure that comes out of the
 * player's credits even when that leaves them below zero. Failure and abandoning also cost reputation
 * with the client's faction. Every contract has a deadline.
 *
 * Subclasses say what the job is (HaulingContract) and when it succeeds.
 */
public abstract class ContractIntel extends BaseIntelPlugin {

    /** Most contracts a player can hold at once (design answer 12). */
    public static final int MAX_OPEN = 3;

    protected static final Object BUTTON_ABANDON = new String("mr_abandon");

    public enum State { ACTIVE, COMPLETED, FAILED, ABANDONED, CANCELLED }

    protected final int tier;
    protected final PersonAPI client;
    protected final String factionId;
    protected final int advance;
    protected final int onSuccess;
    protected final int penalty;
    protected final float durationDays;

    protected State state = State.ACTIVE;
    protected float elapsedDays = 0f;
    /** Credits actually moved and reputation actually changed when the contract ended, for the intel text. */
    protected int settledCredits = 0;
    protected ReputationAdjustmentResult settledRep;

    protected ContractIntel(int tier, PersonAPI client, int advance, int onSuccess, int penalty, float durationDays) {
        this.tier = tier;
        this.client = client;
        this.factionId = client.getFaction().getId();
        this.advance = advance;
        this.onSuccess = onSuccess;
        this.penalty = penalty;
        this.durationDays = durationDays;
    }

    /** Contracts the player holds right now. */
    public static List<ContractIntel> open() {
        List<ContractIntel> result = new ArrayList<>();
        // getIntel(Class) matches one exact class, so walk all intel to catch every contract type.
        for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel()) {
            if (intel instanceof ContractIntel && ((ContractIntel) intel).isActive()) result.add((ContractIntel) intel);
        }
        return result;
    }

    public boolean isActive() {
        return state == State.ACTIVE && !isEnding() && !isEnded();
    }

    public PersonAPI getClientPerson() {
        return client;
    }

    public FactionAPI getClientFaction() {
        return Global.getSector().getFaction(factionId);
    }

    /** Signs the contract: registers it, hands over whatever the job needs and pays the advance. */
    public void accept(TextPanelAPI text) {
        Global.getSector().getIntelManager().addIntel(this, true);
        Global.getSector().addScript(this);
        setImportant(true);
        onAccepted(text);
        if (advance > 0) {
            Global.getSector().getPlayerFleet().getCargo().getCredits().add(advance);
            if (text != null) AddRemoveCommodity.addCreditsGainText(advance, text);
        }
    }

    /** What accepting hands over, for example the cargo of a hauling job. */
    protected abstract void onAccepted(TextPanelAPI text);

    @Override
    protected void advanceImpl(float amount) {
        if (state != State.ACTIVE) return;
        elapsedDays += Global.getSector().getClock().convertToDays(amount);
        if (shouldCancel()) {
            end(State.CANCELLED, null);
        } else if (elapsedDays >= durationDays) {
            end(State.FAILED, null);
        }
    }

    /** True when the job can no longer be done through no fault of the player (the destination is gone). */
    protected abstract boolean shouldCancel();

    /** Ends the contract: pays on success, charges the penalty on failure, nothing on cancel. */
    protected void end(State result, TextPanelAPI text) {
        if (state != State.ACTIVE) return;
        state = result;
        setImportant(false);
        if (result == State.COMPLETED) {
            settledCredits = onSuccess;
            Global.getSector().getPlayerFleet().getCargo().getCredits().add(onSuccess);
            if (text != null) AddRemoveCommodity.addCreditsGainText(onSuccess, text);
            settledRep = adjustRep(successRep(), text);
        } else if (result == State.FAILED || result == State.ABANDONED) {
            settledCredits = -penalty;
            // The penalty is taken even when it leaves the player in debt (design answer 11).
            Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(penalty);
            if (text != null) AddRemoveCommodity.addCreditsLossText(penalty, text);
            settledRep = adjustRep(-failureRep(), text);
        }
        onEnded(result);
        sendUpdateIfPlayerHasIntel(result, text);
        endAfterDelay();
    }

    /** Clean-up for the job, for example taking back nothing on cancel. */
    protected void onEnded(State result) {}

    protected float successRep() {
        return 0.01f + 0.005f * tier;
    }

    protected float failureRep() {
        return 0.02f + 0.01f * tier;
    }

    private ReputationAdjustmentResult adjustRep(float delta, TextPanelAPI text) {
        CustomRepImpact impact = new CustomRepImpact();
        impact.delta = delta;
        if (delta > 0) impact.limit = RepLevel.COOPERATIVE;
        return Global.getSector().adjustPlayerReputation(
                new RepActionEnvelope(RepActions.CUSTOM, impact, null, text, true), factionId);
    }

    // ----- intel screen -----

    protected abstract String getJobName();

    @Override
    public String getName() {
        String name = "Tier " + tier + " " + getJobName();
        switch (state) {
            case COMPLETED: return name + " - Completed";
            case FAILED: return name + " - Failed";
            case ABANDONED: return name + " - Abandoned";
            case CANCELLED: return name + " - Cancelled";
            default: return name;
        }
    }

    @Override
    public String getSmallDescriptionTitle() {
        return getName();
    }

    @Override
    public FactionAPI getFactionForUIColors() {
        return getClientFaction();
    }

    @Override
    public String getSortString() {
        return "Missions - Renewed";
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(Tags.INTEL_MISSIONS);
        if (state == State.ACTIVE) tags.add(Tags.INTEL_ACCEPTED);
        tags.add(factionId);
        return tags;
    }

    @Override
    public String getImportantIcon() {
        if (state == State.ACTIVE) return Global.getSettings().getSpriteName("intel", "important_accepted_mission");
        return super.getImportantIcon();
    }

    @Override
    public float getTimeRemainingFraction() {
        if (state != State.ACTIVE) return super.getTimeRemainingFraction();
        return Math.max(0f, 1f - elapsedDays / durationDays);
    }

    @Override
    public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        info.addPara(getName(), getTitleColor(mode), 0f);
        addBullets(info, mode);
    }

    /** The short lines under the title in the intel list and in updates. */
    protected void addBullets(TooltipMakerAPI info, ListInfoMode mode) {
        Color tc = getBulletColorForMode(mode);
        Color h = Misc.getHighlightColor();
        float pad = mode == ListInfoMode.IN_DESC ? 10f : 3f;
        bullet(info);
        if (state == State.ACTIVE) {
            addJobBullets(info, tc, pad);
            info.addPara("%s on completion", 0f, tc, h, Misc.getDGSCredits(onSuccess));
            addDays(info, "left", durationDays - elapsedDays, tc, 0f);
        } else {
            if (settledCredits > 0) {
                info.addPara("%s received", pad, tc, h, Misc.getDGSCredits(settledCredits));
            } else if (settledCredits < 0) {
                info.addPara("%s penalty paid", pad, tc, Misc.getNegativeHighlightColor(), Misc.getDGSCredits(-settledCredits));
            }
            if (settledRep != null) {
                CoreReputationPlugin.addAdjustmentMessage(settledRep.delta, getClientFaction(), null, null, null, info, tc, isSendingUpdate(), 0f);
            }
        }
        unindent(info);
    }

    /** What the job asks, as bullet lines, for example "2,400 units of ore to Jangala". */
    protected abstract void addJobBullets(TooltipMakerAPI info, Color tc, float pad);

    /** The contract terms, shared by the bar offer and the intel description. */
    public void addTerms(TooltipMakerAPI info, float pad) {
        Color h = Misc.getHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();
        info.addPara("Advance on signing: %s", pad, h, Misc.getDGSCredits(advance));
        info.addPara("Payment on completion: %s", 0f, h, Misc.getDGSCredits(onSuccess));
        info.addPara("Penalty for breach: %s", 0f, bad, Misc.getDGSCredits(penalty));
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;
        addDescriptionTop(info, width);
        if (state == State.ACTIVE) {
            addBullets(info, ListInfoMode.IN_DESC);
            addTerms(info, opad);
            info.addButton("Abandon the contract", BUTTON_ABANDON, getFactionForUIColors().getBaseUIColor(),
                    getFactionForUIColors().getDarkUIColor(), (int) width, 20f, opad * 2f);
        } else {
            info.addPara(endText(), opad);
            addBullets(info, ListInfoMode.IN_DESC);
        }
    }

    /** Images and the opening paragraph of the description. */
    protected abstract void addDescriptionTop(TooltipMakerAPI info, float width);

    protected String endText() {
        switch (state) {
            case COMPLETED: return "The contract is fulfilled.";
            case FAILED: return "The deadline passed. The client has filed the breach.";
            case ABANDONED: return "You walked away from the contract.";
            case CANCELLED: return "The contract is void: there is no one left to deliver to.";
            default: return "";
        }
    }

    @Override
    public boolean doesButtonHaveConfirmDialog(Object buttonId) {
        return buttonId == BUTTON_ABANDON || super.doesButtonHaveConfirmDialog(buttonId);
    }

    @Override
    public void createConfirmationPrompt(Object buttonId, TooltipMakerAPI prompt) {
        if (buttonId == BUTTON_ABANDON) {
            prompt.addPara("Walking away costs %s and standing with %s.", 0f, Misc.getNegativeHighlightColor(),
                    Misc.getDGSCredits(penalty), getClientFaction().getDisplayNameWithArticle());
            return;
        }
        super.createConfirmationPrompt(buttonId, prompt);
    }

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        if (buttonId == BUTTON_ABANDON) {
            end(State.ABANDONED, null);
            ui.updateUIForItem(this);
            return;
        }
        super.buttonPressConfirmed(buttonId, ui);
    }
}
