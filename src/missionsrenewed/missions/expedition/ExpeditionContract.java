package missionsrenewed.missions.expedition;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import missionsrenewed.missions.ContractIntel;
import missionsrenewed.missions.Places;
import missionsrenewed.missions.combat.EnemyFleets;

import java.awt.Color;
import java.util.Random;

/**
 * Haul the client's supplies to a camp at a planet in an uncolonised system (design answer 10 on MSR-1).
 * Flying close to the planet with all the cargo delivers it. Each expedition rolls whether an ambush
 * waits there. The client only knows rumors, so the offer says how likely trouble is in words, never a number
 * and never the answer. The ambush fleet appears when the player enters the destination system.
 */
public class ExpeditionContract extends ContractIntel {

    private static final float DELIVERY_RANGE = 300f;

    private final MarketAPI origin;
    private final SectorEntityToken destination;
    private final String commodityId;
    private final int units;
    private final float ambushChance;
    private final boolean ambush;
    private final String enemyKey;
    private final int ambushFleetPoints;
    private final long seed;

    private boolean ambushSpawned;
    private boolean shortWarned;
    private CampaignFleetAPI ambushFleet;

    ExpeditionContract(int tier, PersonAPI client, MarketAPI origin, SectorEntityToken destination, String commodityId, int units,
                       float ambushChance, boolean ambush, String enemyKey, int ambushFleetPoints, long seed,
                       int advance, int onSuccess, int penalty, float durationDays) {
        super(tier, client, advance, onSuccess, penalty, durationDays);
        this.origin = origin;
        this.destination = destination;
        this.commodityId = commodityId;
        this.units = units;
        this.ambushChance = ambushChance;
        this.ambush = ambush;
        this.enemyKey = enemyKey;
        this.ambushFleetPoints = ambushFleetPoints;
        this.seed = seed;
    }

    private CommoditySpecAPI commodity() {
        return Global.getSettings().getCommoditySpec(commodityId);
    }

    private String good() {
        return commodity().getLowerCaseName();
    }

    private String place() {
        return destination.getName() + " in the " + Places.systemName(destination);
    }

    /** The risk in one word, for the terms box and the intel screen. */
    private String ambushWord() {
        if (ambushChance < 0.2f) return "unlikely";
        if (ambushChance < 0.4f) return "possible";
        if (ambushChance < 0.6f) return "likely";
        return "almost certain";
    }

    /** What the client has heard about the place. They know rumors, not odds. */
    private String ambushRumor() {
        if (ambushChance < 0.2f) return "Nobody's reported trouble out there. Should be a quiet trip, but nobody patrols that far, so I can't promise.";
        if (ambushChance < 0.4f) return "There's talk of ships out there that don't answer hails. Probably nothing. Probably.";
        if (ambushChance < 0.6f) return "The last crew we sent out saw drive plumes on their scopes the whole way. Expect company.";
        return "Our last convoy never came back. Whatever got them is still out there. Go in ready for a fight.";
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
    public String cannotSignReason() {
        float room = Global.getSector().getPlayerFleet().getCargo().getSpaceLeft();
        if (room >= units) return null;
        return "Your holds have room for " + Misc.getWithDGS((int) room) + " more units, the job needs " + Misc.getWithDGS(units) + ".";
    }

    @Override
    protected void onAccepted(TextPanelAPI text) {
        Global.getSector().getPlayerFleet().getCargo().addCommodity(commodityId, units);
        if (text != null) AddRemoveCommodity.addCommodityGainText(commodityId, units, text);
    }

    @Override
    protected boolean shouldCancel() {
        return destination == null || destination.getContainingLocation() == null;
    }

    @Override
    protected void advanceImpl(float amount) {
        super.advanceImpl(amount);
        if (!isActive()) return;
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || player.getContainingLocation() != destination.getContainingLocation()) return;

        if (ambush && !ambushSpawned) {
            ambushSpawned = true;
            ambushFleet = EnemyFleets.spawn(enemyKey, tier, ambushFleetPoints, destination,
                    EnemyFleets.ANONYMOUS_NAME, 45f, new Random(seed));
            if (ambushFleet != null) {
                Global.getSector().getCampaignUI().addMessage("Your sensor officer goes quiet, then very loud: something is waiting near "
                        + destination.getName() + ".", Misc.getNegativeHighlightColor());
            }
        }

        float distance = Misc.getDistance(player.getLocation(), destination.getLocation()) - destination.getRadius();
        if (distance > DELIVERY_RANGE * 2f) {
            shortWarned = false;
            return;
        }
        if (distance > DELIVERY_RANGE) return;
        CargoAPI cargo = player.getCargo();
        int have = (int) cargo.getCommodityQuantity(commodityId);
        if (have < units) {
            if (!shortWarned) {
                shortWarned = true;
                Global.getSector().getCampaignUI().addMessage("The camp at " + destination.getName() + " counts " + Misc.getWithDGS(have)
                        + " of " + Misc.getWithDGS(units) + " units of " + good() + " and waves you off.", Misc.getNegativeHighlightColor());
            }
            return;
        }
        cargo.removeCommodity(commodityId, units);
        Global.getSector().getCampaignUI().addMessage("Shuttles ferry the " + good() + " down to the camp at " + destination.getName()
                + ". The expedition is supplied.", Misc.getPositiveHighlightColor());
        end(State.COMPLETED, null);
    }

    @Override
    protected void testComplete() {
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        if (cargo.getCommodityQuantity(commodityId) >= units) cargo.removeCommodity(commodityId, units);
        end(State.COMPLETED, null);
    }

    // ----- offer -----

    @Override
    public void addOfferText(TextPanelAPI text) {
        text.addPara("\"We're building a camp at " + place() + ", well past the last patrol route, and it needs "
                + Misc.getWithDGS(units) + " units of " + good() + " within " + Math.round(durationDays) + " days.\"");
        text.addPara("\"I need a captain with the cargo space who can also defend it. " + ambushRumor()
                + " You take this on at your own risk.\"");
    }

    @Override
    public void addOfferTerms(TooltipMakerAPI info) {
        Color h = Misc.getHighlightColor();
        info.addPara("Cargo: %s units of " + good() + ", supplied by " + client.getNameString(), 0f, h, Misc.getWithDGS(units));
        info.addPara("Destination: the camp at %s, %s light years away", 0f, h, place(),
                Misc.getRoundedValueMaxOneAfterDecimal(Misc.getDistanceLY(origin.getPrimaryEntity(), destination)));
        info.addPara("Deadline: %s days", 0f, h, "" + Math.round(durationDays));
        Color odds = ambushChance < 0.4f ? h : Misc.getNegativeHighlightColor();
        info.addPara("Ambush: %s", 0f, odds, ambushWord());
        addTerms(info, 10f);
    }

    @Override
    public String signText() {
        return "You sign. The crates come aboard with a star chart that has more blank space than stars.";
    }

    // ----- intel screen -----

    @Override
    protected String getJobName() {
        return "Expedition";
    }

    @Override
    public String getIcon() {
        return commodity().getIconName();
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return destination;
    }

    @Override
    protected void addJobBullets(TooltipMakerAPI info, Color tc, float pad) {
        Color h = Misc.getHighlightColor();
        info.addPara("%s units of " + good() + " to %s", pad, tc, h, Misc.getWithDGS(units), destination.getName());
    }

    @Override
    protected void addDescriptionTop(TooltipMakerAPI info, float width) {
        info.addImages(width, 80f, 10f, 20f, getIcon(), client.getPortraitSprite());
        info.addPara(client.getNameString() + " of " + origin.getName() + " has trusted you with " + Misc.getWithDGS(units)
                + " units of " + good() + " for the camp at " + place() + ". Fly close to the planet to deliver. Ambush: "
                + ambushWord() + ".", 10f);
    }

    @Override
    protected String endText() {
        if (state == State.COMPLETED) return "The camp at " + destination.getName() + " is supplied.";
        if (state == State.CANCELLED) return "The contract is void: the camp is gone.";
        return super.endText();
    }
}
