package missionsrenewed.bar;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEvent;
import com.fs.starfarer.api.util.Misc;
import missionsrenewed.missions.ContractIntel;
import missionsrenewed.missions.Contracts;
import missionsrenewed.missions.MissionFamily;
import missionsrenewed.tiers.Tier;
import missionsrenewed.tiers.TierTable;

import java.util.Map;
import java.util.Random;

/**
 * Test mode (LunaLib): pick any job type and tier and get that contract at this bar. Never registered
 * with the bar. MissionBoardBarEvent hands a fresh one to the bar as an option, and BarCMD runs any
 * bar event that is an option's data. Ignores the open contract limit so several jobs can be tested at once.
 */
public class TestContractBarEvent extends BaseBarEvent {

    private enum Option { SIGN, BACK, AGAIN, LEAVE }

    private transient MarketAPI market;
    private transient MissionFamily family;
    private transient ContractIntel shown;

    @Override
    public String getBarEventId() {
        return "missions_renewed_test";
    }

    @Override
    public void init(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.init(dialog, memoryMap);
        done = false;
        noContinue = false;
        market = dialog.getInteractionTarget().getMarket();
        showFamilies();
    }

    private void showFamilies() {
        options.clearOptions();
        dialog.getVisualPanel().fadeVisualOut();
        text.addPara("[Test mode] Pick a job type.", Misc.getGrayColor());
        for (MissionFamily f : MissionFamily.playableFamilies()) {
            options.addOption(Misc.ucFirst(f.displayName), f);
            if (!Contracts.canOffer(f, market)) {
                options.setEnabled(f, false);
                options.setTooltip(f, "Not offered by " + market.getFaction().getDisplayNameWithArticle() + ".");
            }
        }
        options.addOption("Leave", Option.LEAVE);
    }

    private void showTiers() {
        options.clearOptions();
        text.addPara("[Test mode] Pick a tier for the " + family.displayName + ".", Misc.getGrayColor());
        for (Tier tier : TierTable.all()) {
            options.addOption("Tier " + tier.level, tier.level);
        }
        options.addOption("Back", Option.BACK);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (optionData instanceof MissionFamily) {
            family = (MissionFamily) optionData;
            showTiers();
        } else if (optionData instanceof Integer) {
            Tier tier = TierTable.get((Integer) optionData);
            shown = tier == null ? null : Contracts.safeBuild(family, market, tier, new Random());
            if (shown == null) {
                text.addPara("[Test mode] No " + family.displayName + " of that tier fits here, for example no market in range."
                                + " If that looks wrong, starsector.log may name an error.",
                        Misc.getNegativeHighlightColor());
                showTiers();
                return;
            }
            options.clearOptions();
            OfferScreen.show(dialog, shown, Option.SIGN, Option.BACK, "Back", true);
        } else if (optionData == Option.SIGN && shown != null) {
            text.addPara(shown.signText());
            shown.accept(text);
            shown = null;
            options.clearOptions();
            options.addOption("Spawn another contract", Option.AGAIN);
            options.addOption("Leave", Option.LEAVE);
        } else if (optionData == Option.BACK || optionData == Option.AGAIN) {
            shown = null;
            showFamilies();
        } else if (optionData == Option.LEAVE) {
            noContinue = true;
            done = true;
        }
    }
}
