package missionsrenewed.missions.combat;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import missionsrenewed.missions.ContractIntel;
import missionsrenewed.missions.Places;

import java.awt.Color;
import java.util.Random;

/**
 * Destroy a hostile fleet lying in wait near a planet. The fleet is built when the contract is signed.
 * The job is done when the player fights it and it is destroyed or left with a quarter of its fleet
 * points or less. If someone else destroys it first, or it cannot be built, the contract is void.
 */
public class CombatContract extends ContractIntel implements FleetEventListener {

    private static final String IMPORTANT_REASON = "missions_renewed_combat";
    /** The target counts as beaten at this share of its starting fleet points. */
    private static final float BEATEN_SHARE = 0.25f;

    private final MarketAPI origin;
    private final SectorEntityToken anchor;
    private final String enemyKey;
    private final int fleetPoints;
    private final long seed;

    private CampaignFleetAPI fleet;
    private int startFleetPoints;
    private boolean playerFought;
    private boolean spawnFailed;

    CombatContract(int tier, PersonAPI client, MarketAPI origin, SectorEntityToken anchor, String enemyKey, int fleetPoints,
                   long seed, int advance, int onSuccess, int penalty, float durationDays) {
        super(tier, client, advance, onSuccess, penalty, durationDays);
        this.origin = origin;
        this.anchor = anchor;
        this.enemyKey = enemyKey;
        this.fleetPoints = fleetPoints;
        this.seed = seed;
    }

    private String enemy() {
        return EnemyFleets.enemyName(enemyKey);
    }

    private String article() {
        String e = enemy();
        return ("AEIOU".indexOf(Character.toUpperCase(e.charAt(0))) >= 0 ? "an " : "a ") + e;
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
    protected void onAccepted(TextPanelAPI text) {
        fleet = EnemyFleets.spawn(enemyKey, tier, fleetPoints, anchor, Misc.ucFirst(enemy()) + " Fleet",
                durationDays + 60f, new Random(seed));
        if (fleet == null) {
            spawnFailed = true;
            return;
        }
        startFleetPoints = Math.max(1, fleet.getFleetPoints());
        fleet.addEventListener(this);
        Misc.makeImportant(fleet, IMPORTANT_REASON);
    }

    @Override
    protected boolean shouldCancel() {
        return spawnFailed;
    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI f, CampaignFleetAPI primaryWinner, BattleAPI battle) {
        if (f != fleet || !isActive()) return;
        if (battle.isPlayerInvolved()) playerFought = true;
        if (playerFought && (f.isEmpty() || f.getFleetPoints() <= startFleetPoints * BEATEN_SHARE)) end(State.COMPLETED, null);
    }

    @Override
    public void reportFleetDespawnedToListener(CampaignFleetAPI f, FleetDespawnReason reason, Object param) {
        if (f != fleet || !isActive()) return;
        if (reason == FleetDespawnReason.DESTROYED_BY_BATTLE && playerFought) {
            end(State.COMPLETED, null);
        } else {
            end(State.CANCELLED, null);
        }
    }

    @Override
    protected void onEnded(State result) {
        if (fleet == null) return;
        Misc.makeUnimportant(fleet, IMPORTANT_REASON);
        // A target that outlived the contract leaves quietly unless it is in a fight right now.
        if (result != State.COMPLETED && result != State.CANCELLED && fleet.getContainingLocation() != null
                && fleet.getBattle() == null && !fleet.isEmpty()) {
            fleet.despawn(FleetDespawnReason.OTHER, null);
        }
    }

    @Override
    protected void testComplete() {
        if (fleet != null && fleet.getBattle() == null && fleet.getContainingLocation() != null) {
            Misc.makeUnimportant(fleet, IMPORTANT_REASON);
            CampaignFleetAPI gone = fleet;
            fleet = null;
            gone.despawn(FleetDespawnReason.OTHER, null);
        }
        end(State.COMPLETED, null);
    }

    // ----- offer -----

    @Override
    public void addOfferText(TextPanelAPI text) {
        text.addPara(client.getNameString() + " speaks for " + getClientFaction().getDisplayNameWithArticle()
                + ", and " + getClientFaction().getDisplayNameWithArticle() + " wants " + article() + " fleet gone.");
        text.addPara("\"It sits near " + anchor.getName() + " in the " + Places.systemName(anchor) + ", and it is not leaving on its own. Break it within "
                + Math.round(durationDays) + " days. How you do it is your business.\"");
    }

    @Override
    public void addOfferTerms(TooltipMakerAPI info) {
        Color h = Misc.getHighlightColor();
        info.addPara("Target: %s fleet of about %s fleet points", 0f, h, Misc.ucFirst(article()), Misc.getWithDGS(fleetPoints));
        info.addPara("Location: near %s, %s light years away", 0f, h, anchor.getName() + " in the " + Places.systemName(anchor),
                Misc.getRoundedValueMaxOneAfterDecimal(Misc.getDistanceLY(origin.getPrimaryEntity(), anchor)));
        info.addPara("Deadline: %s days", 0f, h, "" + Math.round(durationDays));
        addTerms(info, 10f);
    }

    @Override
    public String signText() {
        return "You sign. " + client.getNameString() + " sends the last known coordinates to your flagship.";
    }

    // ----- intel screen -----

    @Override
    protected String getJobName() {
        return "Combat Contract";
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("intel", "bounties");
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return anchor;
    }

    @Override
    protected void addJobBullets(TooltipMakerAPI info, Color tc, float pad) {
        Color h = Misc.getHighlightColor();
        info.addPara("%s fleet near %s", pad, tc, h, Misc.ucFirst(enemy()), anchor.getName());
    }

    @Override
    protected void addDescriptionTop(TooltipMakerAPI info, float width) {
        info.addImage(client.getPortraitSprite(), width, 80f, 10f);
        info.addPara(client.getNameString() + " of " + origin.getName() + " wants " + article() + " fleet of about "
                + Misc.getWithDGS(fleetPoints) + " fleet points broken near " + anchor.getName() + " in the "
                + Places.systemName(anchor) + ".", 10f);
    }

    @Override
    protected String endText() {
        switch (state) {
            case COMPLETED: return "The target fleet is broken.";
            case CANCELLED: return "The target fleet is gone, and the contract with it.";
            default: return super.endText();
        }
    }
}
