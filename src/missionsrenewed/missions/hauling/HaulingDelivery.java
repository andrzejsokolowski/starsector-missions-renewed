package missionsrenewed.missions.hauling;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import missionsrenewed.missions.ContractIntel;

/**
 * Completes hauling contracts when the player docks at their destination. Registered as a transient
 * listener on every game load, so it is never written into the save.
 */
public class HaulingDelivery extends BaseCampaignEventListener {

    public HaulingDelivery() {
        super(false);
    }

    @Override
    public void reportPlayerOpenedMarket(MarketAPI market) {
        InteractionDialogAPI dialog = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
        for (ContractIntel contract : ContractIntel.open()) {
            if (contract instanceof HaulingContract) ((HaulingContract) contract).playerDockedAt(market, dialog);
        }
    }
}
