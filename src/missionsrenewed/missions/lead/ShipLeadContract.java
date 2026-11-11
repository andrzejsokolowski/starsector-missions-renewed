package missionsrenewed.missions.lead;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin.DerelictShipData;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.PerShipData;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.ShipCondition;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.ShipRecoverySpecialData;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import missionsrenewed.missions.ContractIntel;
import missionsrenewed.missions.Places;
import missionsrenewed.missions.combat.EnemyFleets;
import missionsrenewed.missions.ships.Hulls;

import java.awt.Color;
import java.util.Random;

/**
 * Pay for the location of one named ship. Either a pristine derelict to recover (guarded from tier 3), or the
 * flagship of a hostile fleet, which the game always offers for recovery once it is beaten. The prize is
 * the ship. The price of the lead is the advance, paid by the player. No payment, no penalty.
 */
public class ShipLeadContract extends ContractIntel implements FleetEventListener {

    private static final String IMPORTANT_REASON = "missions_renewed_ship_lead";

    private final MarketAPI origin;
    private final SectorEntityToken anchor;
    private final String variantId;
    private final int sMods;
    private final String shipName;
    /** True: the ship leads a fleet. False: it drifts as a derelict. */
    private final boolean flagship;
    /** The derelict's guards or the flagship's escort, as an enemy key, or null for no guards. */
    private final String escortKey;
    private final int escortDeployment;
    private final long seed;
    private final int fee;

    private SectorEntityToken derelict;
    private CampaignFleetAPI fleet;
    private String famousMemberId;
    private boolean playerFought;

    ShipLeadContract(int tier, PersonAPI client, MarketAPI origin, SectorEntityToken anchor, String variantId, int sMods,
                     String shipName, boolean flagship, String escortKey, int escortDeployment, long seed, int fee,
                     float durationDays) {
        super(tier, client, -fee, 0, 0, durationDays);
        this.origin = origin;
        this.anchor = anchor;
        this.variantId = variantId;
        this.sMods = sMods;
        this.shipName = shipName;
        this.flagship = flagship;
        this.escortKey = escortKey;
        this.escortDeployment = escortDeployment;
        this.seed = seed;
        this.fee = fee;
    }

    private ShipHullSpecAPI hull() {
        return Global.getSettings().getVariant(variantId).getHullSpec();
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
        return Tags.CONTACT_UNDERWORLD;
    }

    @Override
    public String cannotSignReason() {
        float credits = Global.getSector().getPlayerFleet().getCargo().getCredits().get();
        if (credits >= fee) return null;
        return "The lead costs " + Misc.getDGSCredits(fee) + ", you have " + Misc.getDGSCredits(Math.max(0f, credits)) + ".";
    }

    @Override
    protected void onAccepted(TextPanelAPI text) {
        Random random = new Random(seed);
        ShipVariantAPI variant = Hulls.withSMods(variantId, sMods);
        if (flagship) {
            spawnFlagship(variant, random);
        } else {
            spawnDerelict(variant, random);
        }
    }

    private void spawnDerelict(ShipVariantAPI variant, Random random) {
        PerShipData ship = new PerShipData(variant, ShipCondition.PRISTINE, shipName, Factions.INDEPENDENT, 0f);
        ship.addDmods = false;
        ship.pruneWeapons = false;
        ship.nameAlwaysKnown = true;
        derelict = BaseThemeGenerator.addSalvageEntity(random, anchor.getContainingLocation(), Entities.WRECK, Factions.NEUTRAL,
                new DerelictShipData(ship, false));
        derelict.setDiscoverable(true);
        derelict.setCircularOrbit(anchor, random.nextFloat() * 360f, anchor.getRadius() + 200f + random.nextFloat() * 200f, 40f);
        ShipRecoverySpecialData recovery = new ShipRecoverySpecialData(null);
        recovery.addShip(ship.clone());
        recovery.storyPointRecovery = false;
        Misc.setSalvageSpecial(derelict, recovery);
        Misc.makeImportant(derelict, IMPORTANT_REASON);
        if (escortKey != null) {
            fleet = EnemyFleets.spawn(escortKey, tier, escortDeployment, derelict, EnemyFleets.ANONYMOUS_NAME,
                    durationDays + 30f, random);
        }
    }

    private void spawnFlagship(ShipVariantAPI variant, Random random) {
        fleet = EnemyFleets.spawn(escortKey, Math.min(tier, EnemyFleets.GROUP_MIN_TIER - 1), escortDeployment, anchor, shipName,
                durationDays + 60f, random);
        if (fleet == null) return;
        variant.addTag(Tags.VARIANT_ALWAYS_RECOVERABLE);
        FleetMemberAPI famous = Hulls.member(variant, shipName);
        FleetMemberAPI oldFlagship = fleet.getFlagship();
        fleet.getFleetData().addFleetMember(famous);
        if (oldFlagship != null && oldFlagship.getCaptain() != null && !oldFlagship.getCaptain().isDefault()) {
            famous.setCaptain(oldFlagship.getCaptain());
            oldFlagship.setCaptain(Global.getFactory().createPerson());
        }
        fleet.getFleetData().setFlagship(famous);
        if (famous.getCaptain() != null && !famous.getCaptain().isDefault()) fleet.setCommander(famous.getCaptain());
        fleet.getFleetData().sort();
        fleet.getFleetData().setSyncNeeded();
        fleet.getFleetData().syncIfNeeded();
        famous.getRepairTracker().setCR(famous.getRepairTracker().getMaxCR());
        famousMemberId = famous.getId();
        fleet.addEventListener(this);
        Misc.makeImportant(fleet, IMPORTANT_REASON);
    }

    @Override
    protected boolean shouldCancel() {
        return flagship && fleet == null;
    }

    @Override
    protected void advanceImpl(float amount) {
        super.advanceImpl(amount);
        if (!isActive() || flagship || derelict == null) return;
        // Recovered or salvaged: the derelict is gone from the map.
        if (!derelict.isAlive() || derelict.getContainingLocation() == null) end(State.COMPLETED, null);
    }

    private boolean famousStillAboard(CampaignFleetAPI f) {
        for (FleetMemberAPI member : f.getFleetData().getMembersListCopy()) {
            if (member.getId().equals(famousMemberId)) return true;
        }
        return false;
    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI f, CampaignFleetAPI primaryWinner, BattleAPI battle) {
        if (f != fleet || !isActive() || !flagship) return;
        if (battle.isPlayerInvolved()) playerFought = true;
        if (playerFought && (f.isEmpty() || !famousStillAboard(f))) end(State.COMPLETED, null);
    }

    @Override
    public void reportFleetDespawnedToListener(CampaignFleetAPI f, FleetDespawnReason reason, Object param) {
        if (f != fleet || !isActive() || !flagship) return;
        end(reason == FleetDespawnReason.DESTROYED_BY_BATTLE && playerFought ? State.COMPLETED : State.CANCELLED, null);
    }

    @Override
    protected float repOnEnd(State result) {
        return 0f;
    }

    @Override
    protected void onEnded(State result) {
        if (derelict != null) {
            Misc.makeUnimportant(derelict, IMPORTANT_REASON);
            // The lead went cold: someone else got to it.
            if (result != State.COMPLETED && derelict.isAlive()) Misc.fadeAndExpire(derelict);
        }
        if (fleet != null) {
            Misc.makeUnimportant(fleet, IMPORTANT_REASON);
            if (result != State.COMPLETED && fleet.getContainingLocation() != null && fleet.getBattle() == null && !fleet.isEmpty()) {
                fleet.despawn(FleetDespawnReason.OTHER, null);
            }
        }
    }

    // ----- offer -----

    @Override
    public void addOfferText(TextPanelAPI text) {
        String where = flagship ? "It leads a fleet near " + place() + "." : "It is drifting near " + place() + ".";
        text.addPara("\"I know where the " + shipName + " is, " + Hulls.aClassName(hull()) + ". " + where + "\"");
        text.addPara("\"The location is yours for " + Misc.getDGSCredits(fee) + ".\"");
    }

    @Override
    public void addOfferTerms(TooltipMakerAPI info) {
        Color h = Misc.getHighlightColor();
        info.addPara("Ship: %s, %s", 0f, h, shipName, Hulls.className(hull()));
        if (sMods > 0) info.addPara("S-mods: %s", 0f, h, "" + sMods);
        info.addPara(flagship ? "Flagship of a hostile fleet" : (escortKey != null ? "Derelict, guarded" : "Derelict"), 0f);
        info.addPara("Location: near %s, %s light years away", 0f, h, place(),
                Misc.getRoundedValueMaxOneAfterDecimal(Misc.getDistanceLY(origin.getPrimaryEntity(), anchor)));
        info.addPara("The lead goes cold in: %s days", 0f, h, "" + Math.round(durationDays));
        addTerms(info, 10f);
    }

    @Override
    public void addTerms(TooltipMakerAPI info, float pad) {
        info.addPara("Price of the lead: %s", pad, Misc.getNegativeHighlightColor(), Misc.getDGSCredits(fee));
    }

    @Override
    public String signText() {
        return "You pay. " + client.getNameString() + " writes down the coordinates.";
    }

    // ----- intel screen -----

    @Override
    protected String getJobName() {
        return "Famous Ship Lead";
    }

    @Override
    protected String successLabel() {
        return "paid";
    }

    @Override
    public String getIcon() {
        return hull().getSpriteName();
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return derelict != null && derelict.isAlive() ? derelict : anchor;
    }

    @Override
    protected void addBullets(TooltipMakerAPI info, ListInfoMode mode) {
        Color tc = getBulletColorForMode(mode);
        float pad = mode == ListInfoMode.IN_DESC ? 10f : 3f;
        bullet(info);
        if (state == State.ACTIVE) {
            addJobBullets(info, tc, pad);
            addDays(info, "left", durationDays - elapsedDays, tc, 0f);
        }
        unindent(info);
    }

    @Override
    protected void addJobBullets(TooltipMakerAPI info, Color tc, float pad) {
        info.addPara("The %s near %s", pad, tc, Misc.getHighlightColor(), shipName, anchor.getName());
    }

    @Override
    protected void addDescriptionTop(TooltipMakerAPI info, float width) {
        info.addImage(hull().getSpriteName(), width, 80f, 10f);
        info.addPara("The " + shipName + ", " + Hulls.aClassName(hull()) + (flagship ? ", leads a fleet near " : ", drifts near ")
                + place() + ".", 10f);
    }

    @Override
    protected String endText() {
        switch (state) {
            case COMPLETED: return "The " + shipName + " is found.";
            case FAILED: return "The lead went cold.";
            case CANCELLED: return "The " + shipName + " is gone.";
            case ABANDONED: return "You gave up on the " + shipName + ".";
            default: return super.endText();
        }
    }
}
