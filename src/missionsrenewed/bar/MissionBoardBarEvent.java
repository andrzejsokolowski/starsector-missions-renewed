package missionsrenewed.bar;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEvent;
import com.fs.starfarer.api.util.Misc;
import missionsrenewed.Settings;
import missionsrenewed.missions.ContractIntel;
import missionsrenewed.missions.Contracts;
import missionsrenewed.missions.MissionFamily;
import missionsrenewed.missions.ships.Hulls;
import missionsrenewed.missions.ships.ShipOrderContract;
import missionsrenewed.tiers.PlayerTier;
import missionsrenewed.tiers.Proving;
import missionsrenewed.tiers.Tier;
import missionsrenewed.tiers.TierTable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The one bar event Missions - Renewed adds: the mission board. It lives in PortsideBarData for good and
 * decides per market whether that bar has a board. The board lists several contracts (each with its own
 * tier and job type), the hand-overs for open ship orders at this market and, in test mode, the test
 * spawner. Hand-overs and the test spawner are their own BaseBarEvents, run as a sub-screen of the board.
 *
 * Why always-show: BarCMD shows at most a few random events per visit and sorts isAlwaysShow()
 * events first without counting them, so this is the only way to give every bar the same chance
 * (Settings.barChance) regardless of how many other bar quests the mod list has.
 *
 * The roll is stored in the market's memory for 20 to 40 days, the same window BarCMD keeps its
 * own list of shown events ($BarCMD_shownEvents), so both refresh together. The seed comes from
 * BarEventManager.getSeed, which changes over time and not on reload, so reloading a save does not
 * re-roll a bar. The tier roll leans toward the player's own tier (PlayerTier), by the LunaLib lean
 * setting. Each contract is built on first look and kept in market memory for the rest of the board's
 * time, so looking twice shows the same terms.
 */
public class MissionBoardBarEvent extends BaseBarEvent {

    public static final String EVENT_ID = "missions_renewed_board";
    /**
     * Market memory: "none", or the board's lines joined by ';', each "<tier>|<FAMILY>" or "-" once signed.
     * 0.6.0 and older stored a single line, which reads as a board of one.
     */
    public static final String OFFER_KEY = "$missions_renewed_offer";
    /** Market memory: the unsigned contract behind line 0. Line i > 0 uses this key + i. */
    public static final String CONTRACT_KEY = "$missions_renewed_contract";
    /** Most lines a board holds. */
    public static final int MAX_LINES = 5;
    private static final String TAKEN = "-";
    private static final float MIN_DAYS = 20f;
    private static final float MAX_DAYS = 40f;

    private enum Option { ACCEPT, BACK, TEST, LEAVE }

    /** A contract line on the board, by its index in OFFER_KEY. */
    private static final class Line {
        final int index;

        Line(int index) {
            this.index = index;
        }
    }

    /** The contract on the table during this conversation. */
    private transient ContractIntel shown;
    private transient int shownLine;
    private transient MarketAPI market;
    /** A hand-over or the test spawner, while it runs inside the board. */
    private transient BaseBarEvent sub;

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
        if (Settings.testMode() && market != null && market.getPrimaryEntity() != null) return true;
        return !liveLines(market).isEmpty() || !ordersAt(market).isEmpty();
    }

    /** Open ship orders whose client sits in this market's bar. */
    private static List<ShipOrderContract> ordersAt(MarketAPI market) {
        List<ShipOrderContract> result = new ArrayList<>();
        if (market == null) return result;
        for (ContractIntel contract : ContractIntel.open()) {
            if (contract instanceof ShipOrderContract && ((ShipOrderContract) contract).getMarket() == market) {
                result.add((ShipOrderContract) contract);
            }
        }
        return result;
    }

    @Override
    public void addPromptAndOption(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        MarketAPI here = dialog.getInteractionTarget().getMarket();
        int count = liveLines(here).size();
        if (count > 0) {
            dialog.getTextPanel().addPara("The mission board has " + count + (count == 1 ? " contract" : " contracts") + " posted.");
        }
        dialog.getOptionPanel().addOption("Open the mission board", this);
    }

    @Override
    public void init(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.init(dialog, memoryMap);
        // One instance serves every bar for the whole session, and BaseBarEvent never clears these. Left
        // set, the next conversation would close the moment it opens (BarEventDialogPlugin checks right
        // after init).
        done = false;
        noContinue = false;
        shown = null;
        sub = null;
        market = dialog.getInteractionTarget().getMarket();
        showBoard();
    }

    /** The list: one option per contract line, then hand-overs, the test spawner and Leave. */
    private void showBoard() {
        options.clearOptions();
        dialog.getVisualPanel().fadeVisualOut();
        List<Integer> lines = liveLines(market);
        List<ShipOrderContract> orders = ordersAt(market);
        if (lines.isEmpty() && orders.isEmpty()) {
            text.addPara("There are no contracts on the board.");
        }
        int unlocked = Proving.highestUnlocked();
        if (unlocked < 7) {
            int left = Proving.stillNeeded();
            int next = Proving.nextUnlocked();
            String opens = next == unlocked + 1 ? "tier " + next
                    : next == unlocked + 2 ? "tiers " + (unlocked + 1) + " and " + next
                    : "tiers " + (unlocked + 1) + " to " + next;
            text.addPara("Clients trust you with contracts up to tier " + unlocked + ". Finish " + left
                    + (left == 1 ? " more contract" : " more contracts") + " of tier " + Proving.provingFrom()
                    + " or higher to be offered " + opens + ".", Misc.getGrayColor());
        }
        List<Offer> offers = offersAt(market);
        for (int i : lines) {
            Offer offer = offers.get(i);
            options.addOption("Tier " + offer.tier + " " + offer.family.displayName, new Line(i));
        }
        for (ShipOrderContract order : orders) {
            options.addOption("Hand over " + Hulls.aClassName(order.hull()) + " to "
                    + order.getClientPerson().getNameString(), order);
        }
        if (Settings.testMode()) {
            options.addOption("[Test mode] Spawn a contract", Option.TEST);
            dialog.setOptionColor(Option.TEST, Misc.getGrayColor());
        }
        options.addOption("Leave", Option.LEAVE);
    }

    /** Runs a hand-over or the test spawner inside the board. Its Leave returns to the board. */
    private void runSub(BaseBarEvent event) {
        sub = event;
        sub.init(dialog, memoryMap);
        if (sub.isDialogFinished()) {
            sub = null;
            showBoard();
        }
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (sub != null) {
            sub.optionSelected(optionText, optionData);
            if (sub.isDialogFinished()) {
                sub = null;
                showBoard();
            }
            return;
        }
        if (optionData instanceof Line) {
            shownLine = ((Line) optionData).index;
            shown = contractAt(market, shownLine);
            options.clearOptions();
            if (shown == null) {
                // The roll fell through, for example no market in range takes the cargo.
                closeLine(market, shownLine);
                text.addPara("That contract is no longer on offer.");
                showBoard();
                return;
            }
            OfferScreen.show(dialog, shown, Option.ACCEPT, Option.BACK, "Back to the board", false);
        } else if (optionData == Option.ACCEPT && shown != null) {
            closeLine(market, shownLine);
            text.addPara(shown.signText());
            shown.accept(text);
            shown = null;
            showBoard();
        } else if (optionData == Option.BACK) {
            shown = null;
            showBoard();
        } else if (optionData instanceof ShipOrderContract) {
            runSub(new HandOverBarEvent((ShipOrderContract) optionData));
        } else if (optionData == Option.TEST) {
            // The test spawner, built fresh each time.
            runSub(new TestContractBarEvent());
        } else if (optionData == Option.LEAVE) {
            shown = null;
            noContinue = true;
            done = true;
        }
    }

    // ----- the board's lines in market memory -----

    /**
     * The indices of this market's lines that are still on offer: not signed, the type can be offered
     * here, and the LunaLib job type and tier settings still allow it. A line rolled before a setting
     * changed is hidden, not re-rolled.
     */
    private static List<Integer> liveLines(MarketAPI market) {
        List<Integer> live = new ArrayList<>();
        List<Offer> offers = offersAt(market);
        for (int i = 0; i < offers.size(); i++) {
            Offer offer = offers.get(i);
            if (offer == null || !Contracts.canOffer(offer.family, market, offer.tier) || !Settings.jobEnabled(offer.family)) continue;
            if (offer.tier < Settings.minTier() || offer.tier > Settings.maxTier() || offer.tier > Proving.highestUnlocked()) continue;
            live.add(i);
        }
        return live;
    }

    /** This market's lines (null where signed or unreadable), rolling a new board when the last has expired. */
    static List<Offer> offersAt(MarketAPI market) {
        List<Offer> offers = new ArrayList<>();
        if (market == null || market.isHidden() || market.getPrimaryEntity() == null) return offers;
        MemoryAPI memory = market.getMemoryWithoutUpdate();
        if (!memory.contains(OFFER_KEY)) {
            clearContracts(memory);
            Random random = new Random(BarEventManager.getInstance().getSeed(market.getPrimaryEntity(), null, EVENT_ID));
            memory.set(OFFER_KEY, roll(random, market), MIN_DAYS + (MAX_DAYS - MIN_DAYS) * random.nextFloat());
        }
        String value = memory.getString(OFFER_KEY);
        if (value == null || value.equals("none")) return offers;
        for (String part : value.split(";")) offers.add(Offer.parse(part));
        return offers;
    }

    private static String contractKey(int line) {
        return line == 0 ? CONTRACT_KEY : CONTRACT_KEY + line;
    }

    private static void clearContracts(MemoryAPI memory) {
        for (int i = 0; i < MAX_LINES; i++) memory.unset(contractKey(i));
    }

    /** The unsigned contract behind one line, built on first look. Null when it cannot be built. */
    private static ContractIntel contractAt(MarketAPI market, int line) {
        List<Offer> offers = offersAt(market);
        if (line < 0 || line >= offers.size() || offers.get(line) == null) return null;
        Offer offer = offers.get(line);
        MemoryAPI memory = market.getMemoryWithoutUpdate();
        Object stored = memory.get(contractKey(line));
        if (stored instanceof ContractIntel) return (ContractIntel) stored;

        Tier tier = TierTable.get(offer.tier);
        if (tier == null) return null;
        String salt = EVENT_ID + "_contract" + (line == 0 ? "" : "_" + line);
        Random random = new Random(BarEventManager.getInstance().getSeed(market.getPrimaryEntity(), null, salt));
        ContractIntel contract = Contracts.safeBuild(offer.family, market, tier, random);
        if (contract != null) memory.set(contractKey(line), contract, memory.getExpire(OFFER_KEY));
        return contract;
    }

    /** Marks one line as signed for the rest of the board's time. */
    private static void closeLine(MarketAPI market, int line) {
        if (market == null) return;
        MemoryAPI memory = market.getMemoryWithoutUpdate();
        String value = memory.getString(OFFER_KEY);
        float expire = memory.contains(OFFER_KEY) ? memory.getExpire(OFFER_KEY) : MIN_DAYS;
        memory.unset(contractKey(line));
        if (value == null || value.equals("none")) return;
        String[] parts = value.split(";");
        if (line < 0 || line >= parts.length) return;
        parts[line] = TAKEN;
        memory.set(OFFER_KEY, String.join(";", parts), expire > 0f ? expire : MIN_DAYS);
    }

    /** "Rarely" in Andrzej's line counts. */
    private static final float RARE = 0.25f;

    /**
     * Lines on a board by market size (Andrzej's table, MSR-24): size 3 rarely 1 (else no board), 4: 1, rarely 2,
     * 5: 1 or 2, mostly 2, 6: 2, rarely 3, 7: 3, rarely 4, 8: 4, rarely 5, 9 and up: 5. Size 2 and below: none.
     */
    private static int lineCount(MarketAPI market, Random random) {
        int size = market.getSize();
        if (size <= 2) return 0;
        float r = random.nextFloat();
        switch (size) {
            case 3: return r < RARE ? 1 : 0;
            case 4: return r < RARE ? 2 : 1;
            case 5: return r < 0.7f ? 2 : 1;
            case 6: return r < RARE ? 3 : 2;
            case 7: return r < RARE ? 4 : 3;
            case 8: return r < RARE ? 5 : 4;
            default: return MAX_LINES;
        }
    }

    /** Rolls a board: the bar chance, then each line's own tier and job type, never the same pair twice. */
    private static String roll(Random random, MarketAPI market) {
        if (random.nextFloat() >= Settings.barChance()) return "none";
        int count = lineCount(market, random);
        if (count <= 0) return "none";
        int maxTier = Math.min(Settings.maxTier(), Proving.highestUnlocked());
        int minTier = Math.min(Settings.minTier(), maxTier);
        List<String> lines = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int attempt = 0; attempt < count * 3 && lines.size() < count; attempt++) {
            Tier tier = TierTable.pick(random, PlayerTier.estimate(), Settings.playerLean(), minTier, maxTier);
            if (tier == null) break;
            List<MissionFamily> families = Contracts.offeredAt(market, tier.level);
            families.removeIf(family -> !Settings.jobEnabled(family));
            if (families.isEmpty()) continue;
            String line = tier.level + "|" + families.get(random.nextInt(families.size())).name();
            if (seen.add(line)) lines.add(line);
        }
        return lines.isEmpty() ? "none" : String.join(";", lines);
    }

    /**
     * Puts this event on the market's $BarCMD_shownEvents list when the list exists. BarCMD only looks at
     * events on that list until it expires (up to 40 days), and a bar that had no offer when the list was
     * made would leave this event off it. Called when the player docks, before the bar opens.
     */
    @SuppressWarnings("unchecked")
    public static void keepOnBarList(MarketAPI market) {
        if (market == null) return;
        try {
            Object list = market.getMemoryWithoutUpdate().get("$BarCMD_shownEvents");
            if (list instanceof List && !((List<String>) list).contains(EVENT_ID)) ((List<String>) list).add(EVENT_ID);
        } catch (RuntimeException ignored) {
            // An unmodifiable list from another mod: the event shows once the list expires.
        }
    }

    /** Clears every market's roll so bars visited before this mod was added roll on the next visit. */
    public static void resetAllMarkets() {
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            MemoryAPI memory = market.getMemoryWithoutUpdate();
            memory.unset(OFFER_KEY);
            clearContracts(memory);
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
            if (value == null || value.equals("none") || value.equals(TAKEN)) return null;
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
