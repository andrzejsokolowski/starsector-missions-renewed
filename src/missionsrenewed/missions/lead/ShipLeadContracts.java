package missionsrenewed.missions.lead;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import missionsrenewed.Settings;
import missionsrenewed.missions.Places;
import missionsrenewed.missions.combat.EnemyFleets;
import missionsrenewed.missions.ships.Hulls;
import missionsrenewed.tiers.Tier;

import java.util.Random;

/** Builds famous ship leads: a ship from the tier's eighth of all warships by price, as a derelict or a fleet flagship. */
public final class ShipLeadContracts {

    /** Share of leads where the ship leads a fleet instead of drifting as a derelict (tier 1 and up). */
    private static final float FLAGSHIP_CHANCE = 0.4f;

    /**
     * How strongly a derelict lead favors dangerous systems (Andrzej, 2026-11-15: "something killed that ship"):
     * tier 3 and 4 twice as likely per system, tier 5 and up eight times, so most high tier derelicts lie in one.
     */
    private static float dangerWeight(int tier) {
        if (tier >= 5) return 8f;
        if (tier >= 3) return 2f;
        return 1f;
    }
    /** The lead costs this share of the ship's value, plus 5 percent per S-mod, whatever the tier (Andrzej, 2026-11-15). */
    private static final float FEE_SHARE = 0.15f;
    /** Derelicts: share left unguarded, then share watched by scavengers; the rest have a guard fleet. */
    private static final float UNGUARDED = 0.3f, SCAVENGED = 0.3f;

    private ShipLeadContracts() {}

    public static ShipLeadContract build(MarketAPI origin, Tier tier, Random random) {
        String variantId = Hulls.pickFamous(tier.level, random);
        if (variantId == null) return null;
        ShipHullSpecAPI hull = Global.getSettings().getVariant(variantId).getHullSpec();
        boolean flagship = tier.level >= 1 && random.nextFloat() < FLAGSHIP_CHANCE;
        StarSystemAPI system = Places.pickSystem(origin, 4f, 10f + 4f * tier.level, false,
                flagship ? 1f : dangerWeight(tier.level), random);
        if (system == null) return null;
        SectorEntityToken anchor = Places.pickPlanet(system, random);
        if (anchor == null) return null;
        // Guards vary (Andrzej, 2026-11-15): a flagship's escort is 60 to 130 percent of the tier's DP; a derelict is
        // unguarded, watched by scavengers (a quarter of the tier's DP) or guarded by a fleet of 30 to 100 percent.
        String escort = null;
        int escortDeployment = 0;
        int guards;
        float strength = tier.rollDeployment(random) * Settings.strengthMult();
        if (flagship) {
            // Escorts are crews of people, never the secret enemies: pirates or Pathers at every tier.
            escort = EnemyFleets.pickEnemy(EnemyFleets.GROUP_MIN_TIER - 1, origin.getFaction(), random);
            if (escort == null) return null;
            escortDeployment = Math.max(6, Math.round(strength * (0.6f + 0.7f * random.nextFloat()) - Hulls.dp(hull)));
            guards = ShipLeadContract.GUARDS_FLEET;
        } else {
            float roll = random.nextFloat();
            if (roll < UNGUARDED) {
                guards = ShipLeadContract.GUARDS_NONE;
            } else {
                escort = roll < UNGUARDED + SCAVENGED ? null : EnemyFleets.pickEnemy(tier.level, origin.getFaction(), random);
                if (escort == null) {
                    guards = ShipLeadContract.GUARDS_SCAVENGERS;
                    escortDeployment = Math.max(6, Math.round(strength * 0.25f));
                } else {
                    guards = ShipLeadContract.GUARDS_FLEET;
                    escortDeployment = Math.max(6, Math.round(strength * (0.3f + 0.7f * random.nextFloat())));
                }
            }
        }

        int sMods = Hulls.famousSMods(tier.level);
        int fee = Places.roundTo(Math.max(2000f, hull.getBaseValue() * (FEE_SHARE + 0.05f * sMods)), 500);
        float days = (float) Math.ceil(Places.tripDays(origin.getPrimaryEntity().getLocationInHyperspace(),
                system.getLocation()) * 2f + 60f);

        PersonAPI client = origin.getFaction().createRandomPerson(random);
        client.setRankId(Ranks.CITIZEN);
        client.setPostId(Ranks.POST_SPACER);
        return new ShipLeadContract(tier.level, client, origin, anchor, variantId, sMods, Hulls.shipName(random), flagship,
                escort, escortDeployment, guards, random.nextLong(), fee, Math.max(60f, days));
    }
}
