package missionsrenewed.bar;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import missionsrenewed.missions.ContractIntel;

/** Shows an unsigned contract in a bar dialog: the client, their words, the terms, and Sign. */
final class OfferScreen {

    private OfferScreen() {}

    /**
     * Adds the offer and the Sign and back options. Sign is greyed out with the reason when the player
     * cannot sign. ignoreLimit skips the open contract limit (test mode).
     */
    static void show(InteractionDialogAPI dialog, ContractIntel contract, Object signId, Object backId,
                     String backLabel, boolean ignoreLimit) {
        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI options = dialog.getOptionPanel();
        dialog.getVisualPanel().showPersonInfo(contract.getClientPerson(), true);
        contract.addOfferText(text);
        TooltipMakerAPI terms = text.beginTooltip();
        contract.addOfferTerms(terms);
        text.addTooltip();

        options.addOption("Sign the contract", signId);
        options.addOption(backLabel, backId);

        String reason = null;
        if (!ignoreLimit && ContractIntel.open().size() >= ContractIntel.MAX_OPEN) {
            reason = "You already hold " + ContractIntel.MAX_OPEN + " open contracts.";
        }
        if (reason == null) reason = contract.cannotSignReason();
        if (reason != null) {
            options.setEnabled(signId, false);
            options.setTooltip(signId, reason);
        }
    }
}
