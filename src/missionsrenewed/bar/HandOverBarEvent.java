package missionsrenewed.bar;

import com.fs.starfarer.api.campaign.FleetMemberPickerListener;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEvent;
import missionsrenewed.missions.ships.Hulls;
import missionsrenewed.missions.ships.ShipOrderContract;

import java.util.List;
import java.util.Map;

/**
 * Hands a ship over for an open ship order. Never registered: MissionBoardBarEvent puts a fresh one on an
 * option at the order's market, and BarCMD runs any bar event that is an option's data.
 */
public class HandOverBarEvent extends BaseBarEvent {

    private enum Option { PICK, LEAVE }

    private final transient ShipOrderContract order;

    public HandOverBarEvent(ShipOrderContract order) {
        this.order = order;
    }

    @Override
    public String getBarEventId() {
        return "missions_renewed_hand_over";
    }

    @Override
    public void init(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.init(dialog, memoryMap);
        done = false;
        noContinue = false;
        options.clearOptions();
        dialog.getVisualPanel().showPersonInfo(order.getClientPerson(), true);
        if (order.matchingShips().isEmpty()) {
            text.addPara("You have no " + Hulls.className(order.hull()) + " to hand over, apart from your flagship.");
        } else {
            options.addOption("Hand over a ship", Option.PICK);
        }
        options.addOption("Leave", Option.LEAVE);
    }

    private void pick() {
        List<FleetMemberAPI> ships = order.matchingShips();
        if (ships.isEmpty()) return;
        dialog.showFleetMemberPickerDialog("Pick the ship to hand over", "Hand over", "Cancel", 3, 7, 58f,
                true, false, ships, new FleetMemberPickerListener() {
                    @Override
                    public void pickedFleetMembers(List<FleetMemberAPI> members) {
                        if (members == null || members.isEmpty()) return;
                        FleetMemberAPI ship = members.get(0);
                        text.addPara(order.getClientPerson().getNameString() + " signs for the " + ship.getShipName() + ".");
                        order.handOver(ship, text);
                        options.clearOptions();
                        options.addOption("Leave", Option.LEAVE);
                    }

                    @Override
                    public void cancelledFleetMemberPicking() {}
                });
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (optionData == Option.PICK) {
            pick();
        } else if (optionData == Option.LEAVE) {
            noContinue = true;
            done = true;
        }
    }
}
