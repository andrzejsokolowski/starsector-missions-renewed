package missionsrenewed.missions.delivery;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import missionsrenewed.bar.MissionBoardBarEvent;
import missionsrenewed.missions.ContractIntel;

/**
 * Runs when the player docks at a market. Completes delivery contracts whose destination this is, and
 * makes sure the bar looks at the board event (MissionBoardBarEvent.keepOnBarList). Registered as a
 * transient listener on every game load, so it is never written into the save.
 */
public class DeliveryListener extends BaseCampaignEventListener {

    public DeliveryListener() {
        super(false);
    }

    @Override
    public void reportPlayerOpenedMarket(MarketAPI market) {
        MissionBoardBarEvent.keepOnBarList(market);
        InteractionDialogAPI dialog = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
        for (ContractIntel contract : ContractIntel.open()) {
            if (contract instanceof DeliveryContract) ((DeliveryContract) contract).playerDockedAt(market, dialog);
        }
    }
}
