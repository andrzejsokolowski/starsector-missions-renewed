package missionsrenewed.missions.lead;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import missionsrenewed.Settings;
import missionsrenewed.missions.MissionFamily;
import missionsrenewed.missions.Places;
import missionsrenewed.tiers.Tier;

import java.util.Random;

/** Builds salvage leads: a debris field in a far system, preferably uncolonised, loot by tier. */
public final class SalvageLeadContracts {

    /** Common goods in the field are worth this share of the tier's reward (times the LunaLib loot setting). */
    private static final float LOOT_SHARE = 0.5f;
    /** The lead costs this share of the tier's reward. */
    private static final float FEE_SHARE = 0.2f;

    private SalvageLeadContracts() {}

    public static SalvageLeadContract build(MarketAPI origin, Tier tier, Random random) {
        StarSystemAPI system = Places.pickSystem(origin, 6f, 12f + 4f * tier.level, true, random);
        if (system == null) system = Places.pickSystem(origin, 6f, 12f + 4f * tier.level, false, random);
        if (system == null) return null;
        SectorEntityToken anchor = Places.pickPlanet(system, random);
        if (anchor == null) return null;

        float reward = tier.rollReward(random) * Settings.rewardMult();
        int loot = Math.round(reward * LOOT_SHARE * Settings.payMult(MissionFamily.SALVAGE_LEAD));
        int fee = Places.roundTo(reward * FEE_SHARE, 500);
        float radius = 250f + 50f * tier.level;
        float days = (float) Math.ceil(Places.tripDays(origin.getPrimaryEntity().getLocationInHyperspace(),
                system.getLocation()) * 2f + 45f);

        PersonAPI client = origin.getFaction().createRandomPerson(random);
        client.setRankId(Ranks.CITIZEN);
        client.setPostId(Ranks.POST_SPACER);
        return new SalvageLeadContract(tier.level, client, origin, anchor, loot, radius, random.nextLong(), fee,
                Math.max(45f, days));
    }
}
