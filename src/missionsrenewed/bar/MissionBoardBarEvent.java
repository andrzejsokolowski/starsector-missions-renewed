package missionsrenewed.bar;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEvent;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import missionsrenewed.Settings;
import missionsrenewed.missions.ContractIntel;
import missionsrenewed.missions.MissionFamily;
import missionsrenewed.missions.hauling.HaulingContract;
import missionsrenewed.missions.hauling.HaulingContracts;
import missionsrenewed.tiers.PlayerTier;
import missionsrenewed.tiers.Tier;
import missionsrenewed.tiers.TierTable;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The one bar event Missions - Renewed adds. It lives in PortsideBarData for good and decides per
 * market whether that bar has a tiered offer.
 *
 * Why always-show: BarCMD shows at most a few random events per visit and sorts isAlwaysShow()
 * events first without counting them, so this is the only way to give every bar the same chance
 * (Settings.barChance) regardless of how many other bar quests the mod list has.
 *
 * The roll is stored in the market's memory for 20 to 40 days, the same window BarCMD keeps its
 * own list of shown events ($BarCMD_shownEvents), so both refresh together. The seed comes from
 * BarEventManager.getSeed, which changes over time and not on reload, so reloading a save does not
 * re-roll a bar. The tier roll leans toward the player's own tier (PlayerTier), by the LunaLib lean
 * setting. The contract itself is built on first look and kept in market memory for the rest of the
 * offer's time, so looking twice shows the same terms.
 */
public class MissionBoardBarEvent extends BaseBarEvent {

    public static final String EVENT_ID = "missions_renewed_board";
    /** Market memory: "none", or "<tier>|<FAMILY>" for an open offer. */
    public static final String OFFER_KEY = "$missions_renewed_offer";
    /** Market memory: the unsigned contract behind the offer, once the player has looked at it. */
    public static final String CONTRACT_KEY = "$missions_renewed_contract";
    private static final float MIN_DAYS = 20f;
    private static final float MAX_DAYS = 40f;

    private enum Option { ACCEPT, DECLINE, LEAVE }

    /** The contract on the table during this conversation. */
    private transient ContractIntel shown;
    private transient MarketAPI shownAt;

    @Override
    public String getBarEventId() {
        return EVENT_ID;
    }

    @Override
    public boolean isAlwaysShow() {
        return true;
    }

    /** Never pinned to the first market it was shown at: one event serves every bar. */
    @Override
    public void wasShownAtMarket(MarketAPI market) {}

    @Override
    public boolean shouldShowAtMarket(MarketAPI market) {
        Offer offer = offerAt(market);
        return offer != null && offer.family.playable;
    }

    @Override
    public void addPromptAndOption(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        Offer offer = offerAt(dialog.getInteractionTarget().getMarket());
        if (offer == null || !offer.family.playable) return;
        dialog.getTextPanel().addPara("A spacer at a corner table has a datapad full of contracts and is looking for a captain.");
        dialog.getOptionPanel().addOption("Look over the contract (tier " + offer.tier + ", " + offer.family.displayName + ")", this);
    }

    @Override
    public void init(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.init(dialog, memoryMap);
        shownAt = dialog.getInteractionTarget().getMarket();
        shown = contractAt(shownAt);
        options.clearOptions();
        if (shown instanceof HaulingContract) {
            showHauling((HaulingContract) shown);
            return;
        }
        // The roll fell through, for example no market in range takes the cargo.
        closeOffer(shownAt);
        text.addPara("The spacer scrolls through the datapad, frowns and pockets it. Whatever the job was, it has fallen through.");
        options.addOption("Leave", Option.LEAVE);
    }

    private void showHauling(HaulingContract contract) {
        dialog.getVisualPanel().showPersonInfo(contract.getClientPerson(), true);
        Color h = Misc.getHighlightColor();
        String good = contract.getCommodity().getLowerCaseName();
        text.addPara(contract.getClientPerson().getNameString() + " has a hold's worth of " + good
                + " that needs to be somewhere else, and no patience for slow captains.");
        text.addPara("\"" + Misc.getWithDGS(contract.getUnits()) + " units, my crates, your holds. Delivered to "
                + contract.getDestination().getName() + " within " + Math.round(contract.getDurationDays())
                + " days. Short or late, and the breach clause applies.\"");

        TooltipMakerAPI terms = text.beginTooltip();
        terms.addPara("Cargo: %s units of " + good + ", supplied by the client", 0f, h, Misc.getWithDGS(contract.getUnits()));
        terms.addPara("Destination: %s, %s light years away", 0f, h, contract.getDestination().getName(),
                Misc.getRoundedValueMaxOneAfterDecimal(contract.getDistanceLY()));
        terms.addPara("Deadline: %s days", 0f, h, "" + Math.round(contract.getDurationDays()));
        contract.addTerms(terms, 10f);
        text.addTooltip();

        options.addOption("Sign the contract", Option.ACCEPT);
        options.addOption("Decline", Option.DECLINE);

        List<ContractIntel> open = ContractIntel.open();
        float space = Global.getSector().getPlayerFleet().getCargo().getSpaceLeft();
        if (open.size() >= ContractIntel.MAX_OPEN) {
            options.setEnabled(Option.ACCEPT, false);
            options.setTooltip(Option.ACCEPT, "You already hold " + ContractIntel.MAX_OPEN + " open contracts.");
        } else if (space < contract.getUnits()) {
            options.setEnabled(Option.ACCEPT, false);
            options.setTooltip(Option.ACCEPT, "Your holds have room for " + Misc.getWithDGS((int) space) + " more units, "
                    + "the cargo needs " + Misc.getWithDGS(contract.getUnits()) + ".");
        }
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (optionData == Option.ACCEPT && shown != null) {
            closeOffer(shownAt);
            text.addPara("You sign. Dock crews start moving the crates into your holds before the ink is dry.");
            shown.accept(text);
            shown = null;
            done = true;
        } else if (optionData == Option.DECLINE || optionData == Option.LEAVE) {
            shown = null;
            noContinue = true;
            done = true;
        }
    }

    /** The open offer at this market, rolling a new one when the last roll has expired. */
    static Offer offerAt(MarketAPI market) {
        if (market == null || market.isHidden() || market.getPrimaryEntity() == null) return null;
        MemoryAPI memory = market.getMemoryWithoutUpdate();
        if (!memory.contains(OFFER_KEY)) {
            memory.unset(CONTRACT_KEY);
            Random random = new Random(BarEventManager.getInstance().getSeed(market.getPrimaryEntity(), null, EVENT_ID));
            memory.set(OFFER_KEY, roll(random), MIN_DAYS + (MAX_DAYS - MIN_DAYS) * random.nextFloat());
        }
        return Offer.parse(memory.getString(OFFER_KEY));
    }

    /** The unsigned contract behind this market's offer, built on first look. Null when it cannot be built. */
    private static ContractIntel contractAt(MarketAPI market) {
        Offer offer = offerAt(market);
        if (offer == null) return null;
        MemoryAPI memory = market.getMemoryWithoutUpdate();
        Object stored = memory.get(CONTRACT_KEY);
        if (stored instanceof ContractIntel) return (ContractIntel) stored;

        Tier tier = TierTable.get(offer.tier);
        if (tier == null) return null;
        Random random = new Random(BarEventManager.getInstance().getSeed(market.getPrimaryEntity(), null, EVENT_ID + "_contract"));
        ContractIntel contract = null;
        if (offer.family == MissionFamily.HAULING) contract = HaulingContracts.build(market, tier, random);
        if (contract != null) memory.set(CONTRACT_KEY, contract, memory.getExpire(OFFER_KEY));
        return contract;
    }

    /** Marks this market's offer as taken for the rest of its time. */
    private static void closeOffer(MarketAPI market) {
        if (market == null) return;
        MemoryAPI memory = market.getMemoryWithoutUpdate();
        float expire = memory.contains(OFFER_KEY) ? memory.getExpire(OFFER_KEY) : MIN_DAYS;
        memory.set(OFFER_KEY, "none", expire > 0f ? expire : MIN_DAYS);
        memory.unset(CONTRACT_KEY);
    }

    private static String roll(Random random) {
        if (random.nextFloat() >= Settings.barChance()) return "none";
        List<MissionFamily> families = MissionFamily.playableFamilies();
        if (families.isEmpty()) return "none";
        Tier tier = TierTable.pick(random, PlayerTier.estimate(), Settings.playerLean());
        if (tier == null) return "none";
        return tier.level + "|" + families.get(random.nextInt(families.size())).name();
    }

    /** Clears every market's roll so bars visited before this mod was added roll on the next visit. */
    public static void resetAllMarkets() {
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            MemoryAPI memory = market.getMemoryWithoutUpdate();
            memory.unset(OFFER_KEY);
            memory.unset(CONTRACT_KEY);
            // BarCMD only considers events named in this list until it expires, so a bar visited
            // before install would not look at this event for up to 40 days.
            memory.unset("$BarCMD_shownEvents");
        }
    }

    static final class Offer {
        final int tier;
        final MissionFamily family;

        private Offer(int tier, MissionFamily family) {
            this.tier = tier;
            this.family = family;
        }

        static Offer parse(String value) {
            if (value == null || value.equals("none")) return null;
            String[] parts = value.split("\\|");
            if (parts.length != 2) return null;
            try {
                return new Offer(Integer.parseInt(parts[0]), MissionFamily.valueOf(parts[1]));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}
