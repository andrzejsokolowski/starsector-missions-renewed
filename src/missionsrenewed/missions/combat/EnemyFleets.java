package missionsrenewed.missions.combat;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.AICoreOfficerPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithTriggers.OfficerQuality;
import com.fs.starfarer.api.impl.campaign.rulecmd.DwellerCMD;
import com.fs.starfarer.api.impl.combat.threat.ThreatFIDConfig;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.util.List;
import java.util.Random;

/**
 * Picks and builds the enemy fleets of combat contracts and expedition ambushes.
 *
 * Tiers 0 to 4: pirate or Pather fleets built by the game, picked among those hostile to the client.
 * Tiers 5 to 7: a group from enemy_fleets.json (Omega, Threat, Shrouded Dwellers, modded groups), never
 * pirates or Pathers (design answer 5 on MSR-1). An enemy is named by a key: "faction:pirates" or "group:omega".
 */
public final class EnemyFleets {

    /** First tier that uses the hand-built groups. */
    public static final int GROUP_MIN_TIER = 5;
    /** Most ships a hand-built fleet gets, whatever its fleet points. */
    private static final int MAX_SHIPS = 30;
    private static final String[] LOW_TIER_FACTIONS = {Factions.PIRATES, Factions.LUDDIC_PATH};

    private EnemyFleets() {}

    /** An enemy for this tier, or null when none fits (low tiers need a faction hostile to the client). */
    public static String pickEnemy(int tier, FactionAPI clientFaction, Random random) {
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(random);
        if (tier >= GROUP_MIN_TIER) {
            for (EnemyGroups.Group group : EnemyGroups.forTier(tier)) picker.add("group:" + group.id, group.weight);
        } else {
            for (String factionId : LOW_TIER_FACTIONS) {
                FactionAPI faction = Global.getSector().getFaction(factionId);
                if (faction == null || faction == clientFaction) continue;
                if (clientFaction == null || faction.isHostileTo(clientFaction)) picker.add("faction:" + factionId, 1f);
            }
        }
        return picker.pick();
    }

    /** "pirate", "Pather", "Omega": as in "a pirate fleet". */
    public static String enemyName(String key) {
        if (key == null) return "hostile";
        if (key.equals("faction:" + Factions.PIRATES)) return "pirate";
        if (key.equals("faction:" + Factions.LUDDIC_PATH)) return "Pather";
        if (key.startsWith("group:")) {
            EnemyGroups.Group group = EnemyGroups.get(key.substring(6));
            if (group != null) return group.name;
        }
        if (key.startsWith("faction:")) {
            FactionAPI faction = Global.getSector().getFaction(key.substring(8));
            if (faction != null) return faction.getDisplayName();
        }
        return "hostile";
    }

    /**
     * Builds the fleet, puts it in orbit around anchor and makes it hostile and aggressive. Null when the
     * enemy cannot be built (its mod was removed, for example).
     */
    public static CampaignFleetAPI spawn(String key, int tier, float fleetPoints, SectorEntityToken anchor, String name,
                                         float orbitDays, Random random) {
        CampaignFleetAPI fleet = null;
        try {
            if (key != null && key.startsWith("faction:")) {
                fleet = generated(key.substring(8), tier, fleetPoints, anchor, false, random);
            } else if (key != null && key.startsWith("group:")) {
                EnemyGroups.Group group = EnemyGroups.get(key.substring(6));
                if (group != null && group.available()) {
                    fleet = group.generated ? generated(group.factionId, tier, fleetPoints, anchor, group.aiCores, random)
                            : handBuilt(group, tier, fleetPoints, random);
                }
            }
        } catch (RuntimeException e) {
            // A broken modded variant or faction: no fleet, and the contract voids itself.
            Global.getLogger(EnemyFleets.class).error("Missions - Renewed: could not build the " + key + " fleet", e);
            fleet = null;
        }
        if (fleet == null || fleet.isEmpty()) return null;

        fleet.setName(name);
        fleet.setNoFactionInName(true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_JUMP, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_FIGHT_TO_THE_LAST, true);
        // Fighting a contract target never costs standing, whoever it flies for.
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_REP_IMPACT, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_LOW_REP_IMPACT, true);

        anchor.getContainingLocation().addEntity(fleet);
        float angle = random.nextFloat() * 360f;
        float radius = anchor.getRadius() + 200f + random.nextFloat() * 300f;
        fleet.setLocation(anchor.getLocation().x + (float) Math.cos(Math.toRadians(angle)) * radius,
                anchor.getLocation().y + (float) Math.sin(Math.toRadians(angle)) * radius);
        fleet.addAssignment(FleetAssignment.ORBIT_AGGRESSIVE, anchor, orbitDays, "lying in wait");
        // Without this a fleet that outlives its job would sit there hostile for the rest of the game.
        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, anchor, 30f);
        return fleet;
    }

    /** A fleet the game builds from the faction's own ships, scaled up with the tier. */
    private static CampaignFleetAPI generated(String factionId, int tier, float fleetPoints, SectorEntityToken anchor,
                                              boolean aiCores, Random random) {
        if (Global.getSector().getFaction(factionId) == null) return null;
        FleetParamsV3 params = new FleetParamsV3(null, anchor.getLocationInHyperspace(), factionId,
                Math.min(1f, 0.4f + 0.1f * tier), FleetTypes.PATROL_LARGE, fleetPoints, 0f, 0f, 0f, 0f, 0f, 0f);
        params.officerLevelBonus = tier / 2;
        params.officerNumberMult = 1f + 0.2f * tier;
        if (tier >= 3) params.averageSMods = Math.min(3, tier - 2);
        params.ignoreMarketFleetSizeMult = true;
        // Automated factions: AI core officers, as the game does for Remnants. A mod's own officer plugin still wins.
        if (aiCores) params.aiCores = tier >= 6 ? OfficerQuality.AI_ALPHA : OfficerQuality.AI_MIXED;
        params.random = random;
        try {
            return FleetFactoryV3.createFleet(params);
        } catch (RuntimeException e) {
            Global.getLogger(EnemyFleets.class).warn("Missions - Renewed: could not build a " + factionId + " fleet", e);
            return null;
        }
    }

    /** Fills a fleet with the group's variants until the fleet points are spent. Automated ships get AI cores. */
    private static CampaignFleetAPI handBuilt(EnemyGroups.Group group, int tier, float fleetPoints, Random random) {
        WeightedRandomPicker<String> variants = new WeightedRandomPicker<>(random);
        String cheapest = null;
        for (java.util.Map.Entry<String, Float> e : group.variants.entrySet()) {
            if (!Global.getSettings().doesVariantExist(e.getKey())) continue;
            variants.add(e.getKey(), e.getValue());
            if (cheapest == null || cost(e.getKey()) < cost(cheapest)) cheapest = e.getKey();
        }
        if (variants.isEmpty()) return null;

        CampaignFleetAPI fleet = FleetFactoryV3.createEmptyFleet(group.factionId, FleetTypes.PATROL_LARGE, null);
        // The variants are complete. The game's own Threat and Dweller fleets also run without an inflater,
        // which would otherwise refit the ships from the faction's weapon list on first contact.
        fleet.setInflater(null);
        float left = fleetPoints;
        int ships = 0;
        while (ships < MAX_SHIPS && left > 0f) {
            String chosen = null;
            for (int i = 0; i < 12 && chosen == null; i++) {
                String v = variants.pick();
                if (cost(v) <= left) chosen = v;
            }
            if (chosen == null) {
                if (ships > 0 || cheapest == null) break;
                chosen = cheapest;
            }
            fleet.getFleetData().addFleetMember(chosen);
            left -= cost(chosen);
            ships++;
        }

        fleet.getFleetData().sort();
        List<FleetMemberAPI> members = fleet.getFleetData().getMembersListCopy();
        for (int i = 0; i < members.size(); i++) {
            FleetMemberAPI member = members.get(i);
            if (!group.aiCores || !Misc.isAutomated(member)) continue;
            String core = (i == 0 || tier >= 6) ? Commodities.ALPHA_CORE : Commodities.BETA_CORE;
            AICoreOfficerPlugin plugin = Misc.getAICoreOfficerPlugin(core);
            if (plugin == null) continue;
            PersonAPI captain = plugin.createPerson(core, group.factionId, random);
            member.setCaptain(captain);
        }
        if (!members.isEmpty()) {
            FleetMemberAPI flagship = members.get(0);
            fleet.getFleetData().setFlagship(flagship);
            if (flagship.getCaptain() != null && !flagship.getCaptain().isDefault()) fleet.setCommander(flagship.getCaptain());
        }
        fleet.getFleetData().setSyncNeeded();
        fleet.getFleetData().syncIfNeeded();
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());
        }
        addFactionExtras(fleet, group.factionId);
        return fleet;
    }

    /** What the game itself gives its Threat and Dweller fleets: the Threat battle and loot rules, the Dweller drops. */
    private static void addFactionExtras(CampaignFleetAPI fleet, String factionId) {
        if (Factions.THREAT.equals(factionId)) {
            fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_INTERACTION_DIALOG_CONFIG_OVERRIDE_GEN, new ThreatFIDConfig());
        } else if (Factions.DWELLER.equals(factionId)) {
            for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
                List<String> drops = DwellerCMD.DROP_GROUPS.get(member.getHullSpec().getRestoredToHullId());
                if (drops == null) continue;
                for (String group : drops) fleet.addDropRandom(group, 1);
            }
        }
    }

    private static float cost(String variantId) {
        ShipVariantAPI variant = Global.getSettings().getVariant(variantId);
        return variant == null ? 9999f : Math.max(1f, variant.getHullSpec().getFleetPoints());
    }
}
