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

/** Builds famous ship leads: a ship sized by the tier's DP band, as a derelict or a fleet flagship. */
public final class ShipLeadContracts {

    /** Share of leads where the ship leads a fleet instead of drifting as a derelict (tier 1 and up). */
    private static final float FLAGSHIP_CHANCE = 0.4f;
    /** Derelicts get guards from this tier. */
    private static final int GUARDED_FROM_TIER = 3;
    /** The lead costs this share of the ship's value. */
    private static final float FEE_SHARE = 0.15f;

    private ShipLeadContracts() {}

    public static ShipLeadContract build(MarketAPI origin, Tier tier, Random random) {
        String variantId = Hulls.pickVariant(tier.level, null, random);
        if (variantId == null) return null;
        ShipHullSpecAPI hull = Global.getSettings().getVariant(variantId).getHullSpec();
        StarSystemAPI system = Places.pickSystem(origin, 4f, 10f + 4f * tier.level, false, random);
        if (system == null) return null;
        SectorEntityToken anchor = Places.pickPlanet(system, random);
        if (anchor == null) return null;

        boolean flagship = tier.level >= 1 && random.nextFloat() < FLAGSHIP_CHANCE;
        String escort = null;
        int escortDeployment = 0;
        if (flagship) {
            // Escorts are crews of people, never the secret enemies: pirates or Pathers at every tier.
            escort = EnemyFleets.pickEnemy(EnemyFleets.GROUP_MIN_TIER - 1, origin.getFaction(), random);
            if (escort == null) return null;
            escortDeployment = Math.max(6, Math.round(tier.rollDeployment(random) * Settings.strengthMult() - Hulls.dp(hull)));
        } else if (tier.level >= GUARDED_FROM_TIER) {
            escort = EnemyFleets.pickEnemy(tier.level, origin.getFaction(), random);
            escortDeployment = Math.max(6, Math.round(tier.rollDeployment(random) * 0.5f * Settings.strengthMult()));
        }

        int sMods = Hulls.sModsForTier(tier.level);
        int fee = Places.roundTo(Math.max(2000f, hull.getBaseValue() * (FEE_SHARE + 0.05f * sMods)), 500);
        float days = (float) Math.ceil(Places.tripDays(origin.getPrimaryEntity().getLocationInHyperspace(),
                system.getLocation()) * 2f + 60f);

        PersonAPI client = origin.getFaction().createRandomPerson(random);
        client.setRankId(Ranks.CITIZEN);
        client.setPostId(Ranks.POST_SPACER);
        return new ShipLeadContract(tier.level, client, origin, anchor, variantId, sMods, Hulls.shipName(random), flagship,
                escort, escortDeployment, random.nextLong(), fee, Math.max(60f, days));
    }
}
