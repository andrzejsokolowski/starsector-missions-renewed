package missionsrenewed.missions.lead;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Drops;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin.DebrisFieldParams;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin.DebrisFieldSource;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import missionsrenewed.missions.ContractIntel;
import missionsrenewed.missions.Places;
import missionsrenewed.missions.combat.EnemyFleets;

import java.awt.Color;
import java.util.Random;

/**
 * Pay for the location of a debris field. Signing puts the field in place, loaded with salvage by tier:
 * common goods worth lootValue, plus rolls on rarer drop groups from tier 1 (colony items, AI cores, weapons,
 * hull mods, blueprints; more rolls at higher tiers). A small scavenger fleet of battered, officerless ships picks
 * at the field, hostile to the player, sized at a quarter of the tier's DP. Reaching the field completes the
 * lead; the loot comes through the game's own salvage screen. The field fades if the lead runs out first.
 */
public class SalvageLeadContract extends ContractIntel {

    private static final String IMPORTANT_REASON = "missions_renewed_salvage_lead";
    /** Extra drop rolls per tier: {drop group, chances}. */
    private static final Object[][][] EXTRA_DROPS = {
            {},
            {{Drops.EXTENDED, 1}},
            {{Drops.EXTENDED, 2}, {"weapons2", 1}, {"rare_tech_low", 1}},
            {{"rare_tech_low", 2}, {"weapons2", 2}, {Drops.ANY_HULLMOD_MEDIUM, 1}, {"ai_cores2", 2}},
            {{"rare_tech", 2}, {"high_weapons1", 1}, {Drops.ANY_HULLMOD_HIGH, 1}, {"ai_cores2", 4}, {"blueprints_low", 1}},
            {{"rare_tech", 3}, {"high_weapons2", 1}, {"blueprints_low", 1}, {"ai_cores3", 5}, {Drops.ANY_HULLMOD_HIGH, 1}},
            {{"rare_tech", 4}, {"high_weapons2", 2}, {"blueprints", 1}, {"ai_cores3", 7}, {Drops.ANY_HULLMOD_HIGH, 2}},
            {{"rare_tech", 5}, {"high_weapons2", 3}, {"blueprints", 2}, {"ai_cores3", 10}, {Drops.ANY_HULLMOD_HIGH, 2}},
    };

    private final MarketAPI origin;
    private final SectorEntityToken anchor;
    private final int lootValue;
    private final float radius;
    private final long seed;
    private final int fee;
    /** DP of the scavengers at the field. 0 for leads signed before 0.8.0: no scavengers. */
    private final int scavengerDeployment;

    private SectorEntityToken debris;
    private CampaignFleetAPI scavengers;

    SalvageLeadContract(int tier, PersonAPI client, MarketAPI origin, SectorEntityToken anchor, int lootValue, float radius,
                        long seed, int fee, int scavengerDeployment, float durationDays) {
        super(tier, client, -fee, 0, 0, durationDays);
        this.scavengerDeployment = scavengerDeployment;
        this.origin = origin;
        this.anchor = anchor;
        this.lootValue = lootValue;
        this.radius = radius;
        this.seed = seed;
        this.fee = fee;
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
        DebrisFieldParams params = new DebrisFieldParams(radius, 1f, durationDays + 30f, 0f);
        params.source = DebrisFieldSource.MIXED;
        params.baseSalvageXP = 500L + 250L * tier;
        debris = Misc.addDebrisField(anchor.getContainingLocation(), params, random);
        debris.getDropValue().clear();
        debris.getDropRandom().clear();
        debris.addDropValue(Drops.BASIC, lootValue);
        Object[][] extras = EXTRA_DROPS[Math.max(0, Math.min(EXTRA_DROPS.length - 1, tier))];
        for (Object[] drop : extras) debris.addDropRandom((String) drop[0], (Integer) drop[1]);
        debris.setCircularOrbit(anchor, random.nextFloat() * 360f, anchor.getRadius() + radius + 300f, 60f);
        Misc.makeImportant(debris, IMPORTANT_REASON);
        if (scavengerDeployment > 0) scavengers = EnemyFleets.scavengers(tier, scavengerDeployment, debris, random);
    }

    @Override
    protected boolean shouldCancel() {
        return false;
    }

    @Override
    protected void advanceImpl(float amount) {
        super.advanceImpl(amount);
        if (!isActive() || debris == null) return;
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || player.getContainingLocation() != debris.getContainingLocation()) return;
        if (Misc.getDistance(player.getLocation(), debris.getLocation()) > radius + 500f) return;
        Global.getSector().getCampaignUI().addMessage("Debris field found near " + anchor.getName() + ".", Misc.getPositiveHighlightColor());
        end(State.COMPLETED, null);
    }

    @Override
    protected float repOnEnd(State result) {
        return 0f;
    }

    @Override
    protected void onEnded(State result) {
        if (debris == null) return;
        Misc.makeUnimportant(debris, IMPORTANT_REASON);
        if (result != State.COMPLETED && debris.isAlive()) Misc.fadeAndExpire(debris);
        // Scavengers left behind by a lead gone cold leave with it; at a found field they stay until beaten.
        if (result != State.COMPLETED && scavengers != null && scavengers.getContainingLocation() != null
                && scavengers.getBattle() == null && !scavengers.isEmpty()) {
            scavengers.despawn(com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason.OTHER, null);
        }
    }

    // ----- offer -----

    @Override
    public void addOfferText(TextPanelAPI text) {
        text.addPara("\"Found a debris field near " + place() + " on my last run. Good salvage in it, better than "
                + "I could carry out. A few scavengers were already sniffing around, nothing a real warship can't scare off.\"");
        text.addPara("\"The coordinates are yours for " + Misc.getDGSCredits(fee) + ".\"");
    }

    @Override
    public void addOfferTerms(TooltipMakerAPI info) {
        Color h = Misc.getHighlightColor();
        info.addPara("Location: near %s, %s light years away", 0f, h, place(),
                Misc.getRoundedValueMaxOneAfterDecimal(Misc.getDistanceLY(origin.getPrimaryEntity(), anchor)));
        info.addPara("The lead goes cold in: %s days", 0f, h, "" + Math.round(durationDays));
        if (scavengerDeployment > 0) info.addPara("Scavengers at the field: about %s DP", 0f, h, "" + Math.max(10, Math.round(scavengerDeployment / 10f) * 10));
        addTerms(info, 10f);
    }

    @Override
    public void addTerms(TooltipMakerAPI info, float pad) {
        info.addPara("Price of the lead: %s", pad, Misc.getNegativeHighlightColor(), Misc.getDGSCredits(fee));
    }

    @Override
    public String signText() {
        return "You pay. " + client.getNameString() + " sends the coordinates to your flagship.";
    }

    // ----- intel screen -----

    @Override
    protected String getJobName() {
        return "Salvage Lead";
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("intel", "salvors_tally_high");
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return debris != null && debris.isAlive() ? debris : anchor;
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
        info.addPara("Debris field near %s", pad, tc, Misc.getHighlightColor(), anchor.getName());
    }

    @Override
    protected void addDescriptionTop(TooltipMakerAPI info, float width) {
        info.addImage(client.getPortraitSprite(), width, 80f, 10f);
        info.addPara("A debris field near " + place() + ".", 10f);
    }

    @Override
    protected String endText() {
        switch (state) {
            case COMPLETED: return "You found the debris field near " + anchor.getName() + ".";
            case FAILED: return "The lead went cold.";
            case ABANDONED: return "You gave up on the lead.";
            default: return super.endText();
        }
    }
}
