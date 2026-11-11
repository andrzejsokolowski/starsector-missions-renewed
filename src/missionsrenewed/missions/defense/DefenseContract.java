package missionsrenewed.missions.defense;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import missionsrenewed.missions.ContractIntel;
import missionsrenewed.missions.combat.EnemyFleets;
import missionsrenewed.missions.delivery.DeliveryContract;

import java.awt.Color;
import java.util.Random;

/**
 * A fleet attacks a market of the client's faction on a known day. The player has to be there and beat it
 * (destroy it or leave it with a quarter of its DP or less). The market's station, if it has one, fights
 * alongside. Beaten without the player: void. Still there when the deadline comes: failed.
 */
public class DefenseContract extends ContractIntel implements FleetEventListener {

    private static final String IMPORTANT_REASON = "missions_renewed_defense";
    private static final float BEATEN_SHARE = 0.25f;

    private final MarketAPI origin;
    private final MarketAPI target;
    private final String enemyKey;
    private final int deployment;
    private final long seed;
    /** Days after signing when the attack arrives. */
    private final float arrivalDays;

    private CampaignFleetAPI fleet;
    private boolean spawned;
    private boolean spawnFailed;
    private boolean playerFought;
    private float startDeployment;

    DefenseContract(int tier, PersonAPI client, MarketAPI origin, MarketAPI target, String enemyKey, int deployment, long seed,
                    float arrivalDays, int advance, int onSuccess, int penalty, float durationDays) {
        super(tier, client, advance, onSuccess, penalty, durationDays);
        this.origin = origin;
        this.target = target;
        this.enemyKey = enemyKey;
        this.deployment = deployment;
        this.seed = seed;
        this.arrivalDays = arrivalDays;
    }

    private String defenses() {
        Industry station = Misc.getStationIndustry(target);
        return station == null ? "no station" : "the " + station.getCurrentName();
    }

    private int daysToAttack() {
        return Math.max(0, Math.round(arrivalDays - elapsedDays));
    }

    @Override
    protected MarketAPI clientMarket() {
        return origin;
    }

    @Override
    protected String contactType() {
        return Tags.CONTACT_MILITARY;
    }

    @Override
    protected void onAccepted(TextPanelAPI text) {}

    @Override
    protected boolean shouldCancel() {
        return spawnFailed || target == null || !target.isInEconomy() || target.getPrimaryEntity() == null
                || !target.getFactionId().equals(factionId);
    }

    @Override
    protected void advanceImpl(float amount) {
        super.advanceImpl(amount);
        if (!isActive() || spawned || elapsedDays < arrivalDays) return;
        spawned = true;
        fleet = EnemyFleets.spawnAttacking(enemyKey, tier, deployment, target.getPrimaryEntity(), EnemyFleets.ANONYMOUS_NAME,
                Math.max(5f, durationDays - elapsedDays), new Random(seed));
        if (fleet == null) {
            spawnFailed = true;
            return;
        }
        startDeployment = Math.max(1f, EnemyFleets.deployment(fleet));
        fleet.addEventListener(this);
        Misc.makeImportant(fleet, IMPORTANT_REASON);
        Global.getSector().getCampaignUI().addMessage("A hostile fleet has arrived at " + target.getName() + ".",
                Misc.getNegativeHighlightColor());
    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI f, CampaignFleetAPI primaryWinner, BattleAPI battle) {
        if (f != fleet || !isActive()) return;
        if (battle.isPlayerInvolved()) playerFought = true;
        if (playerFought && (f.isEmpty() || EnemyFleets.deployment(f) <= startDeployment * BEATEN_SHARE)) end(State.COMPLETED, null);
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
        text.addPara("\"A fleet is gathering to attack " + target.getName() + ". It will be there in about "
                + Math.round(arrivalDays) + " days.\"");
        text.addPara("\"Be at " + target.getName() + " when it comes and destroy it.\"");
    }

    @Override
    public void addOfferTerms(TooltipMakerAPI info) {
        Color h = Misc.getHighlightColor();
        info.addPara("Defend: %s, %s light years away", 0f, h, DeliveryContract.where(target),
                Misc.getRoundedValueMaxOneAfterDecimal(Misc.getDistanceLY(origin.getPrimaryEntity(), target.getPrimaryEntity())));
        info.addPara("Defenses: %s", 0f, h, defenses());
        info.addPara("Attack expected in: %s days", 0f, h, "" + Math.round(arrivalDays));
        info.addPara("Deadline: %s days", 0f, h, "" + Math.round(durationDays));
        addTerms(info, 10f);
    }

    @Override
    public String signText() {
        return "You sign. " + client.getNameString() + " marks " + target.getName() + " on your map.";
    }

    // ----- intel screen -----

    @Override
    protected String getJobName() {
        return "Defense Contract";
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("intel", "hostilities");
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return target == null ? null : target.getPrimaryEntity();
    }

    @Override
    protected void addJobBullets(TooltipMakerAPI info, Color tc, float pad) {
        Color h = Misc.getHighlightColor();
        if (!spawned) {
            info.addPara("Attack on %s in %s days", pad, tc, h, target.getName(), "" + daysToAttack());
        } else {
            info.addPara("Destroy the fleet attacking %s", pad, tc, h, target.getName());
        }
    }

    @Override
    protected void addDescriptionTop(TooltipMakerAPI info, float width) {
        info.addImage(client.getPortraitSprite(), width, 80f, 10f);
        info.addPara(client.getNameString() + " of " + origin.getName() + " wants " + DeliveryContract.where(target)
                + " defended. Defenses: " + defenses() + ".", 10f);
    }

    @Override
    protected String endText() {
        switch (state) {
            case COMPLETED: return "The attack on " + target.getName() + " is beaten.";
            case CANCELLED: return "The contract is void.";
            case FAILED: return "The attackers were still at " + target.getName() + " when the contract ran out.";
            default: return super.endText();
        }
    }
}
