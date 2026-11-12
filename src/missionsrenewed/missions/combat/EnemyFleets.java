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
 * Tiers 5 to 7: mostly an elite war fleet of a faction hostile to the client (never pirates or Pathers, design
 * answer 5 on MSR-1, unless no other faction is hostile); sometimes a group from enemy_fleets.json (modded
 * groups, and rarely Omega, Threat or Shrouded Dwellers: Andrzej, 2026-11-12). An enemy is named by a key:
 * "faction:pirates" or "group:omega".
 */
public final class EnemyFleets {

    /** First tier that uses the hand-built groups. */
    public static final int GROUP_MIN_TIER = 5;
    /** Most ships a hand-built fleet gets, whatever its fleet points. */
    private static final int MAX_SHIPS = 30;
    private static final String[] LOW_TIER_FACTIONS = {Factions.PIRATES, Factions.LUDDIC_PATH};
    /** Pick weight shared by every faction hostile to the client at tiers 5 to 7; the groups' weights are next to it. */
    private static final float HOSTILE_FACTION_WEIGHT = 60f;
    /** Never picked as a hostile faction: the strange ones come only through their groups. */
    private static final java.util.Set<String> NOT_A_WAR_FLEET = new java.util.HashSet<>(java.util.Arrays.asList(
            Factions.PLAYER, Factions.PIRATES, Factions.LUDDIC_PATH, Factions.OMEGA, Factions.THREAT, Factions.DWELLER,
            Factions.REMNANTS, Factions.DERELICT, Factions.SCAVENGERS, Factions.NEUTRAL, Factions.POOR, Factions.SLEEPER));

    /** Every contract fleet and ambush flies under this name: the client never knows who it is. */
    public static final String ANONYMOUS_NAME = "Unidentified Fleet";

    private EnemyFleets() {}

    /** An enemy for this tier, or null when none fits (low tiers need a faction hostile to the client). */
    public static String pickEnemy(int tier, FactionAPI clientFaction, Random random) {
        return pickEnemy(tier, clientFaction, false, random);
    }

    /**
     * As pickEnemy. With mustBeHostile, groups whose faction is not hostile to the client are left out, and
     * when none is left the pick falls back to pirates or Pathers: an attack on a market needs an enemy the
     * market's station actually fights.
     */
    public static String pickEnemy(int tier, FactionAPI clientFaction, boolean mustBeHostile, Random random) {
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(random);
        if (tier >= GROUP_MIN_TIER) {
            java.util.Set<String> groupFactions = new java.util.HashSet<>();
            for (EnemyGroups.Group group : EnemyGroups.forTier(tier)) {
                groupFactions.add(group.factionId);
                FactionAPI faction = Global.getSector().getFaction(group.factionId);
                if (mustBeHostile && clientFaction != null && (faction == null || !faction.isHostileTo(clientFaction))) continue;
                picker.add("group:" + group.id, group.weight);
            }
            List<String> hostile = warFleetFactions(clientFaction, groupFactions);
            if (hostile.isEmpty()) {
                // Nobody but pirates and Pathers is at war with the client: they send their best instead.
                for (String factionId : LOW_TIER_FACTIONS) {
                    FactionAPI faction = Global.getSector().getFaction(factionId);
                    if (faction != null && faction != clientFaction && (clientFaction == null || faction.isHostileTo(clientFaction))) {
                        hostile.add(factionId);
                    }
                }
            }
            for (String factionId : hostile) picker.add("faction:" + factionId, HOSTILE_FACTION_WEIGHT / hostile.size());
            if (picker.isEmpty() && mustBeHostile) return pickEnemy(GROUP_MIN_TIER - 1, clientFaction, false, random);
        } else {
            for (String factionId : LOW_TIER_FACTIONS) {
                FactionAPI faction = Global.getSector().getFaction(factionId);
                if (faction == null || faction == clientFaction) continue;
                if (clientFaction == null || faction.isHostileTo(clientFaction)) picker.add("faction:" + factionId, 1f);
            }
        }
        return picker.pick();
    }

    /** Factions at war with the client that field real war fleets: shown on the intel screen, with ships, not a group's. */
    private static List<String> warFleetFactions(FactionAPI clientFaction, java.util.Set<String> groupFactions) {
        List<String> result = new java.util.ArrayList<>();
        if (clientFaction == null) return result;
        for (FactionAPI faction : Global.getSector().getAllFactions()) {
            String id = faction.getId();
            if (faction == clientFaction || NOT_A_WAR_FLEET.contains(id) || groupFactions.contains(id)) continue;
            if (!faction.isShowInIntelTab() || !faction.isHostileTo(clientFaction)) continue;
            if (faction.getKnownShips() == null || faction.getKnownShips().isEmpty()) continue;
            result.add(id);
        }
        return result;
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
    public static CampaignFleetAPI spawn(String key, int tier, float deployment, SectorEntityToken anchor, String name,
                                         float orbitDays, Random random) {
        CampaignFleetAPI fleet = build(key, tier, deployment, anchor, name, random);
        if (fleet == null) return null;
        return placeOrbiting(fleet, anchor, orbitDays, random);
    }

    /** Puts an already built fleet in orbit around anchor, lying in wait for orbitDays, then leaving. */
    public static CampaignFleetAPI placeOrbiting(CampaignFleetAPI fleet, SectorEntityToken anchor, float orbitDays, Random random) {
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

    /**
     * Puts an already built fleet well out from target, waiting there quietly (other fleets leave it alone) until
     * startAttack is called. Used by defense contracts: the attack begins when the player arrives.
     */
    public static CampaignFleetAPI placeGathering(CampaignFleetAPI fleet, SectorEntityToken target, float days, Random random) {
        target.getContainingLocation().addEntity(fleet);
        float angle = random.nextFloat() * 360f;
        float radius = target.getRadius() + 2500f;
        float x = target.getLocation().x + (float) Math.cos(Math.toRadians(angle)) * radius;
        float y = target.getLocation().y + (float) Math.sin(Math.toRadians(angle)) * radius;
        fleet.setLocation(x, y);
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
        SectorEntityToken spot = target.getContainingLocation().createToken(x, y);
        fleet.addAssignment(FleetAssignment.HOLD, spot, days, "gathering for an attack on " + target.getName());
        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, target, 30f);
        return fleet;
    }

    /** Sends a gathering fleet at its target for `days`. */
    public static void startAttack(CampaignFleetAPI fleet, SectorEntityToken target, float days) {
        fleet.getMemoryWithoutUpdate().unset(MemFlags.FLEET_IGNORES_OTHER_FLEETS);
        fleet.getMemoryWithoutUpdate().unset(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS);
        fleet.clearAssignments();
        fleet.addAssignment(FleetAssignment.ATTACK_LOCATION, target, days, "attacking " + target.getName());
        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, target, 30f);
    }

    /**
     * Scavengers picking at a debris field: the game's scavenger ships at the lowest quality (many D-mods), no
     * officers, hostile to the player. Null when they cannot be built.
     */
    public static CampaignFleetAPI scavengers(int tier, float deployment, SectorEntityToken anchor, Random random) {
        if (Global.getSector().getFaction(Factions.SCAVENGERS) == null) return null;
        CampaignFleetAPI fleet;
        try {
            FleetParamsV3 params = new FleetParamsV3(null, anchor.getLocationInHyperspace(), Factions.SCAVENGERS,
                    0f, FleetTypes.SCAVENGER_MEDIUM, deployment, 0f, 0f, 0f, 0f, 0f, 0f);
            params.qualityOverride = 0f;
            params.withOfficers = false;
            params.ignoreMarketFleetSizeMult = true;
            params.random = random;
            fleet = trimToDeployment(FleetFactoryV3.createFleet(params), deployment);
        } catch (RuntimeException e) {
            Global.getLogger(EnemyFleets.class).warn("Missions - Renewed: could not build scavengers", e);
            return null;
        }
        if (fleet == null || fleet.isEmpty()) return null;
        fleet.setName("Scavengers");
        fleet.setNoFactionInName(true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_JUMP, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_REP_IMPACT, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_LOW_REP_IMPACT, true);
        anchor.getContainingLocation().addEntity(fleet);
        fleet.setLocation(anchor.getLocation().x, anchor.getLocation().y);
        fleet.addAssignment(FleetAssignment.ORBIT_AGGRESSIVE, anchor, 1000f, "picking over the debris");
        return fleet;
    }

    /** Builds the fleet with the contract flags (hostile, aggressive, no standing loss), not yet in any location. */
    public static CampaignFleetAPI build(String key, int tier, float deployment, SectorEntityToken anchor, String name, Random random) {
        CampaignFleetAPI fleet = null;
        try {
            if (key != null && key.startsWith("faction:")) {
                fleet = generated(key.substring(8), tier, deployment, anchor, false, random);
            } else if (key != null && key.startsWith("group:")) {
                EnemyGroups.Group group = EnemyGroups.get(key.substring(6));
                if (group != null && group.available()) {
                    float dp = deployment * group.strength;
                    fleet = group.generated ? generated(group.factionId, tier, dp, anchor, group.aiCores, random)
                            : handBuilt(group, tier, dp, random);
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
        return fleet;
    }

    /** A fleet the game builds from the faction's own ships, scaled up with the tier. */
    private static CampaignFleetAPI generated(String factionId, int tier, float deployment, SectorEntityToken anchor,
                                              boolean aiCores, Random random) {
        if (Global.getSector().getFaction(factionId) == null) return null;
        FleetParamsV3 params = new FleetParamsV3(null, anchor.getLocationInHyperspace(), factionId,
                Math.min(1f, 0.4f + 0.1f * tier), FleetTypes.PATROL_LARGE, deployment, 0f, 0f, 0f, 0f, 0f, 0f);
        params.officerLevelBonus = tier / 2;
        params.officerNumberMult = 1f + 0.2f * tier;
        if (tier >= 3) params.averageSMods = Math.min(3, tier - 2);
        params.ignoreMarketFleetSizeMult = true;
        // Automated factions: AI core officers, as the game does for Remnants. A mod's own officer plugin still wins.
        if (aiCores) params.aiCores = tier >= 6 ? OfficerQuality.AI_ALPHA : OfficerQuality.AI_MIXED;
        params.random = random;
        try {
            // The game builds by fleet points, which run below DP for big warships: trim to the DP asked for.
            return trimToDeployment(FleetFactoryV3.createFleet(params), deployment);
        } catch (RuntimeException e) {
            Global.getLogger(EnemyFleets.class).warn("Missions - Renewed: could not build a " + factionId + " fleet", e);
            return null;
        }
    }

    /** Fills a fleet with the group's variants until the fleet points are spent. Automated ships get AI cores. */
    private static CampaignFleetAPI handBuilt(EnemyGroups.Group group, int tier, float deployment, Random random) {
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
        float left = deployment;
        int ships = 0;
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        while (ships < MAX_SHIPS && left > 0f) {
            String chosen = null;
            for (int i = 0; i < 12 && chosen == null; i++) {
                String v = variants.pick();
                if (cost(v) <= left && underLimit(group, v, counts)) chosen = v;
            }
            if (chosen == null) {
                if (ships > 0 || cheapest == null) break;
                chosen = cheapest;
            }
            fleet.getFleetData().addFleetMember(chosen);
            counts.merge(baseHull(chosen), 1, Integer::sum);
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

    private static String baseHull(String variantId) {
        return Global.getSettings().getVariant(variantId).getHullSpec().getBaseHullId();
    }

    /** Whether one more of this variant's hull stays within the group's limits. */
    private static boolean underLimit(EnemyGroups.Group group, String variantId, java.util.Map<String, Integer> counts) {
        Integer max = group.limits.get(baseHull(variantId));
        return max == null || counts.getOrDefault(baseHull(variantId), 0) < max;
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

    /** Deployment points of a variant, as the refit screen shows them. */
    private static float cost(String variantId) {
        ShipVariantAPI variant = Global.getSettings().getVariant(variantId);
        return variant == null ? 9999f : Math.max(1f, variant.getHullSpec().getSuppliesToRecover());
    }

    private static float cost(FleetMemberAPI member) {
        return Math.max(1f, member.getHullSpec().getSuppliesToRecover());
    }

    /** Total deployment points of a fleet. */
    public static float deployment(CampaignFleetAPI fleet) {
        float total = 0f;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) total += cost(member);
        return total;
    }

    /**
     * Drops ships until the fleet is at most 15 percent over the DP asked for: each time the largest ship that
     * fits in the excess, or the smallest one when none does. The flagship always stays.
     */
    private static CampaignFleetAPI trimToDeployment(CampaignFleetAPI fleet, float target) {
        if (fleet == null) return null;
        float total = deployment(fleet);
        while (total > target * 1.15f) {
            FleetMemberAPI flagship = fleet.getFlagship();
            float excess = total - target;
            FleetMemberAPI drop = null;
            for (FleetMemberAPI m : fleet.getFleetData().getMembersListCopy()) {
                if (m == flagship) continue;
                if (cost(m) <= excess && (drop == null || cost(m) > cost(drop))) drop = m;
            }
            if (drop == null) {
                for (FleetMemberAPI m : fleet.getFleetData().getMembersListCopy()) {
                    if (m != flagship && (drop == null || cost(m) < cost(drop))) drop = m;
                }
            }
            if (drop == null) break;
            fleet.getFleetData().removeFleetMember(drop);
            total -= cost(drop);
        }
        fleet.getFleetData().setSyncNeeded();
        fleet.getFleetData().syncIfNeeded();
        return fleet;
    }
}
