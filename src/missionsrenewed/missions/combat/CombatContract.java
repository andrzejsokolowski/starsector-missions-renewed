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
 * Destroy a hostile fleet lying in wait near a planet. The fleet is built from the contract's seed when the offer
 * is first shown (the offer shows a glimpse of it) and the same fleet is spawned when the contract is signed.
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
    /** The fleet's size in deployment points (fleet points in saves from before 0.4.3). */
    private final int fleetPoints;
    private final long seed;

    private CampaignFleetAPI fleet;
    /** The fleet built for the offer, not yet in space. Rebuilt from the seed after a reload. */
    private transient CampaignFleetAPI prepared;
    private int startFleetPoints;
    /** Deployment points when spawned. 0 in contracts signed before 0.4.3, which still count fleet points. */
    private float startDeployment;
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

    /** Who got away and brought the news. Picked from the seed, so it never changes. */
    private int witnessIndex() {
        return (int) Math.floorMod(seed, 4L);
    }

    private String witness() {
        return new String[] {"a trader", "a survey crew", "a salvager", "a mining barge"}[witnessIndex()];
    }

    private String sighting() {
        return new String[] {"A panicked trader came in two days ago", "A survey crew limped in last week",
                "A salvager came in on half an engine", "A mining barge made port yesterday, hull still smoking"}[witnessIndex()];
    }

    private String place() {
        return anchor.getName() + " in the " + Places.systemName(anchor);
    }

    @Override
    protected MarketAPI clientMarket() {
        return origin;
    }

    @Override
    protected String contactType() {
        return com.fs.starfarer.api.impl.campaign.ids.Tags.CONTACT_MILITARY;
    }

    private CampaignFleetAPI prepared() {
        if (prepared == null) prepared = EnemyFleets.build(enemyKey, tier, fleetPoints, anchor, EnemyFleets.ANONYMOUS_NAME, new Random(seed));
        return prepared;
    }

    @Override
    protected void onAccepted(TextPanelAPI text) {
        CampaignFleetAPI built = prepared();
        prepared = null;
        fleet = built == null ? null : EnemyFleets.placeOrbiting(built, anchor, durationDays + 60f, new Random(seed + 1));
        if (fleet == null) {
            spawnFailed = true;
            return;
        }
        startFleetPoints = Math.max(1, fleet.getFleetPoints());
        startDeployment = Math.max(1f, EnemyFleets.deployment(fleet));
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
        boolean beaten = startDeployment > 0f ? EnemyFleets.deployment(f) <= startDeployment * BEATEN_SHARE
                : f.getFleetPoints() <= startFleetPoints * BEATEN_SHARE;
        if (playerFought && (f.isEmpty() || beaten)) end(State.COMPLETED, null);
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
        text.addPara("\"" + sighting() + ". Said they were attacked near " + place() + " and barely escaped with their life.\"");
        text.addPara("\"Go there and destroy the fleet that attacked them. You have " + Math.round(durationDays) + " days.\"");
    }

    @Override
    public void addOfferTerms(TooltipMakerAPI info) {
        Color h = Misc.getHighlightColor();
        info.addPara("Target: the fleet that attacked " + witness(), 0f);
        info.addPara("Location: near %s, %s light years away", 0f, h, place(),
                Misc.getRoundedValueMaxOneAfterDecimal(Misc.getDistanceLY(origin.getPrimaryEntity(), anchor)));
        info.addPara("Deadline: %s days", 0f, h, "" + Math.round(durationDays));
        FleetPreview.add(info, prepared(), getClientFaction().getBaseUIColor(), 10f);
        addTerms(info, 10f);
    }

    @Override
    public String signText() {
        return "You sign. " + client.getNameString() + " sends " + witness() + "'s flight log to your flagship.";
    }

    // ----- intel screen -----

    @Override
    protected String getJobName() {
        return "Combat Contract";
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("intel", "distress_call");
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return anchor;
    }

    @Override
    protected void addJobBullets(TooltipMakerAPI info, Color tc, float pad) {
        Color h = Misc.getHighlightColor();
        info.addPara("Destroy the fleet near %s", pad, tc, h, anchor.getName());
    }

    @Override
    protected void addDescriptionTop(TooltipMakerAPI info, float width) {
        info.addImage(client.getPortraitSprite(), width, 80f, 10f);
        info.addPara(client.getNameString() + " of " + origin.getName() + " wants the fleet that attacked " + witness()
                + " near " + place() + " destroyed.", 10f);
    }

    @Override
    protected String endText() {
        switch (state) {
            case COMPLETED: return "The fleet near " + anchor.getName() + " is destroyed.";
            case CANCELLED: return "Someone else got to the fleet first. The contract is void.";
            default: return super.endText();
        }
    }
}
