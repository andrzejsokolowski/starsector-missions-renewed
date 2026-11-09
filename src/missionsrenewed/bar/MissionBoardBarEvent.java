package missionsrenewed.bar;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEvent;
import missionsrenewed.Settings;
import missionsrenewed.missions.MissionFamily;
import missionsrenewed.tiers.PlayerTier;
import missionsrenewed.tiers.Tier;
import missionsrenewed.tiers.TierTable;

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
 * re-roll a bar.
 *
 * The tier roll leans toward the player's own tier (PlayerTier), by the LunaLib lean setting.
 *
 * Scaffold: the offer only names its tier and type. Accepting it does nothing yet.
 */
public class MissionBoardBarEvent extends BaseBarEvent {

    public static final String EVENT_ID = "missions_renewed_board";
    /** Market memory: "none", or "<tier>|<FAMILY>" for an open offer. */
    public static final String OFFER_KEY = "$missions_renewed_offer";
    private static final float MIN_DAYS = 20f;
    private static final float MAX_DAYS = 40f;

    private enum Option { LEAVE }

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
        return offerAt(market) != null;
    }

    @Override
    public void addPromptAndOption(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        Offer offer = offerAt(dialog.getInteractionTarget().getMarket());
        if (offer == null) return;
        dialog.getTextPanel().addPara("A spacer at a corner table has a datapad full of contracts and is looking for a captain.");
        dialog.getOptionPanel().addOption("Look over the contract (tier " + offer.tier + ", " + offer.family.displayName + ")", this);
    }

    @Override
    public void init(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.init(dialog, memoryMap);
        Offer offer = offerAt(dialog.getInteractionTarget().getMarket());
        // Keep both ternary branches Strings: mixing String and int types the result as
        // java.io.Serializable, which Starsector's class loader refuses.
        String tier = offer == null ? "?" : String.valueOf(offer.tier);
        String family = offer == null ? "unknown" : offer.family.displayName;
        text.addPara("The contract is a tier " + tier + " " + family + " job. The details are still being written.");
        options.clearOptions();
        options.addOption("Leave", Option.LEAVE);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (optionData == Option.LEAVE) {
            noContinue = true;
            done = true;
        }
    }

    /** The open offer at this market, rolling a new one when the last roll has expired. */
    static Offer offerAt(MarketAPI market) {
        if (market == null || market.isHidden() || market.getPrimaryEntity() == null) return null;
        MemoryAPI memory = market.getMemoryWithoutUpdate();
        if (!memory.contains(OFFER_KEY)) {
            Random random = new Random(BarEventManager.getInstance().getSeed(market.getPrimaryEntity(), null, EVENT_ID));
            memory.set(OFFER_KEY, roll(random), MIN_DAYS + (MAX_DAYS - MIN_DAYS) * random.nextFloat());
        }
        return Offer.parse(memory.getString(OFFER_KEY));
    }

    private static String roll(Random random) {
        if (random.nextFloat() >= Settings.barChance()) return "none";
        Tier tier = TierTable.pick(random, PlayerTier.estimate(), Settings.playerLean());
        if (tier == null) return "none";
        MissionFamily[] families = MissionFamily.values();
        return tier.level + "|" + families[random.nextInt(families.length)].name();
    }

    /** Clears every market's roll so bars visited before this mod was added roll on the next visit. */
    public static void resetAllMarkets() {
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            MemoryAPI memory = market.getMemoryWithoutUpdate();
            memory.unset(OFFER_KEY);
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
