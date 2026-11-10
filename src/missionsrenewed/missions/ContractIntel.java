package missionsrenewed.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.ReputationActionResponsePlugin.ReputationAdjustmentResult;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.CustomRepImpact;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.contacts.ContactIntel;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import missionsrenewed.Settings;

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
 * Subclasses say what the job is, how it is offered in the bar, and when it succeeds. Investments turn
 * the money around: a negative advance (the stake) and a payout when the deadline comes.
 */
public abstract class ContractIntel extends BaseIntelPlugin {

    /** Most contracts a player can hold at once (design answer 12). */
    public static final int MAX_OPEN = 3;

    protected static final Object BUTTON_ABANDON = new String("mr_abandon");
    protected static final Object BUTTON_TEST_COMPLETE = new String("mr_test_complete");
    protected static final Object BUTTON_TEST_FAIL = new String("mr_test_fail");

    public enum State { ACTIVE, COMPLETED, FAILED, ABANDONED, CANCELLED }

    protected final int tier;
    protected final PersonAPI client;
    protected final String factionId;
    /** Credits paid to the player on signing. Negative when the player pays (an investment's stake). */
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

    public int getTier() {
        return tier;
    }

    public PersonAPI getClientPerson() {
        return client;
    }

    public FactionAPI getClientFaction() {
        return Global.getSector().getFaction(factionId);
    }

    public float getDurationDays() {
        return durationDays;
    }

    // ----- the offer in the bar -----

    /** What the client says when the player looks at the contract. */
    public abstract void addOfferText(TextPanelAPI text);

    /** The terms box under the client's words: what the job asks, then the money (addTerms). */
    public abstract void addOfferTerms(TooltipMakerAPI info);

    /** Why the player cannot sign right now (no room for the cargo, not enough credits), or null. */
    public String cannotSignReason() {
        return null;
    }

    /** The line after the player signs. */
    public abstract String signText();

    /** Signs the contract: registers it, hands over whatever the job needs and settles the advance. */
    public void accept(TextPanelAPI text) {
        Global.getSector().getIntelManager().addIntel(this, true);
        Global.getSector().addScript(this);
        setImportant(true);
        onAccepted(text);
        if (advance > 0) {
            Global.getSector().getPlayerFleet().getCargo().getCredits().add(advance);
            if (text != null) AddRemoveCommodity.addCreditsGainText(advance, text);
        } else if (advance < 0) {
            Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(-advance);
            if (text != null) AddRemoveCommodity.addCreditsLossText(-advance, text);
        }
    }

    /** What accepting hands over, for example the cargo of a hauling job. */
    protected abstract void onAccepted(TextPanelAPI text);

    // ----- running -----

    @Override
    protected void advanceImpl(float amount) {
        if (state != State.ACTIVE) return;
        elapsedDays += Global.getSector().getClock().convertToDays(amount);
        if (shouldCancel()) {
            end(State.CANCELLED, null);
        } else if (elapsedDays >= durationDays) {
            onDeadline();
        }
    }

    /** The deadline passed. Most jobs fail. An investment pays out. */
    protected void onDeadline() {
        end(State.FAILED, null);
    }

    /** True when the job can no longer be done through no fault of the player (the destination is gone). */
    protected abstract boolean shouldCancel();

    /** Ends the contract and settles credits and reputation for the result. */
    protected void end(State result, TextPanelAPI text) {
        if (state != State.ACTIVE) return;
        state = result;
        setImportant(false);
        int credits = creditsOnEnd(result);
        settledCredits = credits;
        if (credits > 0) {
            Global.getSector().getPlayerFleet().getCargo().getCredits().add(credits);
            if (text != null) AddRemoveCommodity.addCreditsGainText(credits, text);
        } else if (credits < 0) {
            // A penalty is taken even when it leaves the player in debt (design answer 11).
            Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(-credits);
            if (text != null) AddRemoveCommodity.addCreditsLossText(-credits, text);
        }
        float rep = repOnEnd(result);
        if (rep != 0f) settledRep = adjustRep(rep, text);
        if (result == State.COMPLETED) offerContact(text);
        onEnded(result);
        sendUpdateIfPlayerHasIntel(result, text);
        endAfterDelay();
    }

    /** Credits for the player when the contract ends: positive is paid out, negative is taken. */
    protected int creditsOnEnd(State result) {
        switch (result) {
            case COMPLETED: return onSuccess;
            case FAILED:
            case ABANDONED: return -penalty;
            default: return 0;
        }
    }

    /** Reputation change with the client's faction when the contract ends. */
    protected float repOnEnd(State result) {
        switch (result) {
            case COMPLETED: return successRep();
            case FAILED:
            case ABANDONED: return -failureRep();
            default: return 0f;
        }
    }

    /** The market the client works from, for the contact offer. Null when there is none. */
    protected MarketAPI clientMarket() {
        return null;
    }

    /**
     * A satisfied client may become a contact through the game's own contact system (design answer 8 on
     * MSR-1). The chance grows with the tier. The game decides the rest, as for its own missions.
     */
    private void offerContact(TextPanelAPI text) {
        MarketAPI market = clientMarket();
        if (market == null || !market.isInEconomy() || client == null || tier < 1) return;
        try {
            client.setMarket(market);
            // The game's contacts offer work by type. Without one the contact would never have any.
            if (!client.hasTag(Tags.CONTACT_TRADE) && !client.hasTag(Tags.CONTACT_MILITARY) && !client.hasTag(Tags.CONTACT_UNDERWORLD)) {
                client.addTag(contactType());
            }
            Global.getSector().getImportantPeople().addPerson(client);
            ContactIntel.addPotentialContact(0.15f + 0.05f * tier, client, market, text);
        } catch (RuntimeException e) {
            Global.getLogger(ContractIntel.class).warn("Missions - Renewed: contact offer failed", e);
        }
    }

    /** The kind of contact this client becomes: trade by default. */
    protected String contactType() {
        return Tags.CONTACT_TRADE;
    }

    /** Clean-up for the job. */
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

    /** Test mode: finish the job as if done. Delivery jobs also take the goods. */
    protected void testComplete() {
        end(State.COMPLETED, null);
    }

    // ----- intel screen -----

    protected abstract String getJobName();

    @Override
    public String getName() {
        String name = "Tier " + tier + " " + getJobName();
        switch (state) {
            case COMPLETED: return name + " - Completed";
            case FAILED: return name + " - Failed";
            case ABANDONED: return name + " - " + abandonedWord();
            case CANCELLED: return name + " - Cancelled";
            default: return name;
        }
    }

    protected String abandonedWord() {
        return "Abandoned";
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
            info.addPara("%s " + successLabel(), 0f, tc, h, Misc.getDGSCredits(onSuccess));
            addDays(info, daysLabel(), durationDays - elapsedDays, tc, 0f);
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

    protected String successLabel() {
        return "on completion";
    }

    protected String daysLabel() {
        return "left";
    }

    /** What the job asks, as bullet lines, for example "2,400 units of ore to Jangala". */
    protected abstract void addJobBullets(TooltipMakerAPI info, Color tc, float pad);

    /** The money terms, shared by the bar offer and the intel description. */
    public void addTerms(TooltipMakerAPI info, float pad) {
        Color h = Misc.getHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();
        info.addPara("Advance on signing: %s", pad, h, Misc.getDGSCredits(advance));
        info.addPara("Payment on completion: %s", 0f, h, Misc.getDGSCredits(onSuccess));
        info.addPara("Contract failed: %s", 0f, bad, Misc.getDGSCredits(penalty));
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;
        addDescriptionTop(info, width);
        if (state == State.ACTIVE) {
            addBullets(info, ListInfoMode.IN_DESC);
            addTerms(info, opad);
            Color base = getFactionForUIColors().getBaseUIColor();
            Color dark = getFactionForUIColors().getDarkUIColor();
            info.addButton(abandonLabel(), BUTTON_ABANDON, base, dark, (int) width, 20f, opad * 2f);
            if (Settings.testMode()) {
                info.addPara("Test mode:", Misc.getGrayColor(), opad);
                info.addButton("Complete now", BUTTON_TEST_COMPLETE, base, dark, (int) width, 20f, 3f);
                info.addButton("Fail now", BUTTON_TEST_FAIL, base, dark, (int) width, 20f, 3f);
            }
        } else {
            info.addPara(endText(), opad);
            addBullets(info, ListInfoMode.IN_DESC);
        }
    }

    protected String abandonLabel() {
        return "Abandon the contract";
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
        if (buttonId == BUTTON_TEST_COMPLETE || buttonId == BUTTON_TEST_FAIL) return false;
        return buttonId == BUTTON_ABANDON || super.doesButtonHaveConfirmDialog(buttonId);
    }

    @Override
    public void createConfirmationPrompt(Object buttonId, TooltipMakerAPI prompt) {
        if (buttonId == BUTTON_ABANDON) {
            addAbandonPrompt(prompt);
            return;
        }
        super.createConfirmationPrompt(buttonId, prompt);
    }

    protected void addAbandonPrompt(TooltipMakerAPI prompt) {
        prompt.addPara("Walking away costs %s and standing with %s.", 0f, Misc.getNegativeHighlightColor(),
                Misc.getDGSCredits(penalty), getClientFaction().getDisplayNameWithArticle());
    }

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        if (buttonId == BUTTON_ABANDON) {
            end(State.ABANDONED, null);
            ui.updateUIForItem(this);
            return;
        }
        if (buttonId == BUTTON_TEST_COMPLETE) {
            testComplete();
            ui.updateUIForItem(this);
            return;
        }
        if (buttonId == BUTTON_TEST_FAIL) {
            end(State.FAILED, null);
            ui.updateUIForItem(this);
            return;
        }
        super.buttonPressConfirmed(buttonId, ui);
    }
}
