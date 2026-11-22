package missionsrenewed.bar;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import missionsrenewed.missions.ContractIntel;

/**
 * Shows an unsigned contract in a bar dialog: the client, their words, the terms, and Sign. A job that takes the
 * player somewhere else shows the small map under the client, as vanilla bar missions do; its "Show intel" button
 * opens the intel screen at the place, where the player can lay in a course. An offer whose picture is a ship
 * keeps the ship and gets a "Look at the map" option that swaps the two.
 */
final class OfferScreen {

    /** The option that swaps a ship offer's picture between the ship and the map. */
    static final Object SWAP_PICTURE = new String("mr_offer_swap_picture");
    private static final String LOOK_AT_MAP = "Look at the map";
    private static final String LOOK_AT_SHIP = "Look at the ship";

    private OfferScreen() {}

    /**
     * Adds the offer and the Sign and back options. Sign is greyed out with the reason when the player
     * cannot sign. ignoreLimit skips the open contract limit (test mode).
     */
    static void show(InteractionDialogAPI dialog, ContractIntel contract, Object signId, Object backId,
                     String backLabel, boolean ignoreLimit) {
        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI options = dialog.getOptionPanel();
        SectorEntityToken target = contract.offerMapTarget(dialog.getInteractionTarget());
        boolean ownPicture = contract.hasOwnOfferPicture();
        if (target != null && !ownPicture) {
            contract.showOfferMap(dialog, target);
        } else {
            contract.showOfferVisual(dialog);
        }
        contract.addOfferText(text);
        TooltipMakerAPI terms = text.beginTooltip();
        contract.addOfferTerms(terms);
        text.addTooltip();

        options.addOption("Sign the contract", signId);
        if (target != null && ownPicture) options.addOption(LOOK_AT_MAP, SWAP_PICTURE);
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

    /**
     * Handles the "Look at the map" / "Look at the ship" option. True when the click was that option; the offer's
     * text and other options stay as they are.
     */
    static boolean handle(InteractionDialogAPI dialog, ContractIntel contract, String optionText, Object optionData) {
        if (optionData != SWAP_PICTURE) return false;
        if (contract == null) return true;
        SectorEntityToken target = contract.offerMapTarget(dialog.getInteractionTarget());
        if (LOOK_AT_MAP.equals(optionText) && target != null) {
            contract.showOfferMap(dialog, target);
            dialog.getOptionPanel().setOptionText(LOOK_AT_SHIP, SWAP_PICTURE);
        } else {
            contract.showOfferVisual(dialog);
            dialog.getOptionPanel().setOptionText(LOOK_AT_MAP, SWAP_PICTURE);
        }
        return true;
    }
}
