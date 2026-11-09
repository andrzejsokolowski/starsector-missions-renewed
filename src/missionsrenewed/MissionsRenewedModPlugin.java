package missionsrenewed;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarData;
import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarEvent;
import missionsrenewed.bar.MissionBoardBarEvent;
import missionsrenewed.missions.delivery.DeliveryListener;
import missionsrenewed.tiers.TierTable;

public class MissionsRenewedModPlugin extends BaseModPlugin {

    /** Sector memory flag: this save has seen the mod, so bar rolls were reset once. */
    private static final String INSTALLED_KEY = "$missions_renewed_installed";

    @Override
    public void onApplicationLoad() throws Exception {
        TierTable.load();
    }

    @Override
    public void onGameLoad(boolean newGame) {
        // Transient: a fresh listener every load, nothing of it in the save.
        Global.getSector().addTransientListener(new DeliveryListener());

        PortsideBarData bar = PortsideBarData.getInstance();
        if (bar == null) return;

        // Exactly one board event per save. Removing duplicates also tidies a save that gained a
        // second one through some earlier bug.
        boolean found = false;
        for (PortsideBarEvent event : bar.getEvents().toArray(new PortsideBarEvent[0])) {
            if (!(event instanceof MissionBoardBarEvent)) continue;
            if (found) bar.removeEvent(event);
            found = true;
        }
        if (!found) bar.addEvent(new MissionBoardBarEvent());

        MemoryAPI sector = Global.getSector().getMemoryWithoutUpdate();
        if (!sector.getBoolean(INSTALLED_KEY)) {
            if (!newGame) MissionBoardBarEvent.resetAllMarkets();
            sector.set(INSTALLED_KEY, true);
        }
    }
}
