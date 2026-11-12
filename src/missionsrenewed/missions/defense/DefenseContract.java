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
import missionsrenewed.missions.combat.FleetPreview;
import missionsrenewed.missions.delivery.DeliveryContract;

import java.awt.Color;
import java.util.Random;

/**
 * A fleet gathers outside a market of the client's faction. It spawns when the contract is signed and waits,
 * left alone by other fleets, until the player enters the system; then it attacks the market. The player has
 * to beat it (destroy it or leave it with a quarter of its DP or less). The market's station, if it has one,
 * fights alongside. Beaten without the player: void. The player never came, or the attackers are still
 * there at the deadline: failed. Contracts signed before 0.8.0 spawn their fleet on the next update.
 */
public class DefenseContract extends ContractIntel implements FleetEventListener {

    private static final String IMPORTANT_REASON = "missions_renewed_defense";
    private static final float BEATEN_SHARE = 0.25f;

    private final MarketAPI origin;
    private final MarketAPI target;
    private final String enemyKey;
    private final int deployment;
    private final long seed;
    /** Days after signing when the attack arrived, before 0.8.0. Kept for old saves; no longer used. */
    private final float arrivalDays;

    private CampaignFleetAPI fleet;
    private boolean spawned;
    private boolean spawnFailed;
    private boolean playerFought;
    private float startDeployment;
    /** True once the gathered fleet has been sent at the market. */
    private boolean attacking;
    /** The fleet built for the offer, not yet in space. Rebuilt from the seed after a reload. */
    private transient CampaignFleetAPI prepared;

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

    private CampaignFleetAPI prepared() {
        if (prepared == null) {
            prepared = EnemyFleets.build(enemyKey, tier, deployment, target.getPrimaryEntity(), EnemyFleets.ANONYMOUS_NAME, new Random(seed));
        }
        return prepared;
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
    protected void onAccepted(TextPanelAPI text) {
        spawn();
    }

    private void spawn() {
        spawned = true;
        CampaignFleetAPI built = prepared();
        prepared = null;
        fleet = built == null ? null : EnemyFleets.placeGathering(built, target.getPrimaryEntity(),
                Math.max(5f, durationDays - elapsedDays), new Random(seed + 1));
        if (fleet == null) {
            spawnFailed = true;
            return;
        }
        startDeployment = Math.max(1f, EnemyFleets.deployment(fleet));
        fleet.addEventListener(this);
        Misc.makeImportant(fleet, IMPORTANT_REASON);
    }

    @Override
    protected boolean shouldCancel() {
        return spawnFailed || target == null || !target.isInEconomy() || target.getPrimaryEntity() == null
                || !target.getFactionId().equals(factionId);
    }

    @Override
    protected void advanceImpl(float amount) {
        super.advanceImpl(amount);
        if (!isActive()) return;
        if (!spawned) {
            spawn();
            return;
        }
        if (attacking || fleet == null || fleet.getContainingLocation() == null) return;
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || player.getContainingLocation() != fleet.getContainingLocation()) return;
        attacking = true;
        EnemyFleets.startAttack(fleet, target.getPrimaryEntity(), Math.max(5f, durationDays - elapsedDays));
        Global.getSector().getCampaignUI().addMessage("The fleet outside " + target.getName() + " is moving to attack.",
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
        text.addPara("\"Our pickets spotted a fleet massing outside " + target.getName() + ". It's sitting just out of range of our "
                + "guns, waiting for something. Whatever it's waiting for, I don't want to find out.\"");
        text.addPara("\"Get to " + target.getName() + " and break them before they move. They'll come at the colony the moment "
                + "you show up, so come ready. You have " + Math.round(durationDays) + " days.\"");
    }

    @Override
    public void addOfferTerms(TooltipMakerAPI info) {
        Color h = Misc.getHighlightColor();
        info.addPara("Defend: %s, %s light years away", 0f, h, DeliveryContract.where(target),
                Misc.getRoundedValueMaxOneAfterDecimal(Misc.getDistanceLY(origin.getPrimaryEntity(), target.getPrimaryEntity())));
        info.addPara("Defenses: %s", 0f, h, defenses());
        info.addPara("Deadline: %s days", 0f, h, "" + Math.round(durationDays));
        FleetPreview.add(info, prepared(), getClientFaction().getBaseUIColor(), 10f);
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
        if (!attacking) {
            info.addPara("Destroy the fleet gathering outside %s", pad, tc, h, target.getName());
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
            case FAILED: return attacking ? "The attackers were still at " + target.getName() + " when the contract ran out."
                    : "You never came, and the fleet outside " + target.getName() + " was still there when the contract ran out.";
            default: return super.endText();
        }
    }
}
