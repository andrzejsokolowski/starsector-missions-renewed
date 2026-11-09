package missionsrenewed.missions.marines;

import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyPlayerHostileActListener;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.TempData;
import missionsrenewed.missions.ContractIntel;

/** Passes the game's raid results to open raid contracts. Transient, added on every load. */
public class RaidListener implements ColonyPlayerHostileActListener {

    @Override
    public void reportRaidToDisruptFinished(InteractionDialogAPI dialog, MarketAPI market, TempData actionData, Industry industry) {
        for (ContractIntel contract : ContractIntel.open()) {
            if (contract instanceof RaidContract) ((RaidContract) contract).raided(market, industry, dialog);
        }
    }

    @Override
    public void reportRaidForValuablesFinishedBeforeCargoShown(InteractionDialogAPI dialog, MarketAPI market, TempData actionData, CargoAPI cargo) {}

    @Override
    public void reportTacticalBombardmentFinished(InteractionDialogAPI dialog, MarketAPI market, TempData actionData) {}

    @Override
    public void reportSaturationBombardmentFinished(InteractionDialogAPI dialog, MarketAPI market, TempData actionData) {}
}
