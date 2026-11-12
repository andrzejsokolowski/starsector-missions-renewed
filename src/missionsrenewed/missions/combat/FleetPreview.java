package missionsrenewed.missions.combat;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * A glimpse of an enemy fleet in an offer, the way the bounty board shows one: the flagship and a few of the
 * biggest ships, then a rough count of the rest and the fleet's size in DP.
 */
public final class FleetPreview {

    private static final int SHOWN = 7;

    private FleetPreview() {}

    public static void add(TooltipMakerAPI info, CampaignFleetAPI fleet, Color color, float pad) {
        if (fleet == null || fleet.isEmpty()) return;
        List<FleetMemberAPI> members = fleet.getFleetData().getMembersListCopy();
        members.sort((a, b) -> Float.compare(b.getHullSpec().getSuppliesToRecover(), a.getHullSpec().getSuppliesToRecover()));
        FleetMemberAPI flagship = fleet.getFlagship();
        if (flagship != null && members.remove(flagship)) members.add(0, flagship);

        List<FleetMemberAPI> shown = new ArrayList<>();
        for (FleetMemberAPI member : members) {
            if (shown.size() >= SHOWN) break;
            if (member.isFighterWing()) continue;
            FleetMemberAPI copy = Global.getFactory().createFleetMember(FleetMemberType.SHIP, member.getVariant());
            copy.setShipName(member.getShipName());
            shown.add(copy);
        }
        int rest = members.size() - shown.size();
        int dp = Math.round(EnemyFleets.deployment(fleet) / 10f) * 10;

        info.addPara("Ships sighted:", pad);
        info.addShipList(SHOWN, 1, 50f, color, shown, 3f);
        String size = "about " + Math.max(10, dp) + " DP";
        if (rest <= 0) {
            info.addPara("Estimated strength: %s.", 3f, Misc.getHighlightColor(), size);
        } else if (rest <= 3) {
            info.addPara("A few more ships besides. Estimated strength: %s.", 3f, Misc.getHighlightColor(), size);
        } else {
            String more = "around " + Math.max(5, Math.round(rest / 5f) * 5);
            info.addPara("And %s more ships. Estimated strength: %s.", 3f, Misc.getHighlightColor(), more, size);
        }
    }
}
