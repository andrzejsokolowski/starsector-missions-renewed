package missionsrenewed.missions.ships;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import missionsrenewed.Settings;
import missionsrenewed.compat.Legends;
import missionsrenewed.missions.ContractIntel;
import missionsrenewed.missions.MissionFamily;
import missionsrenewed.missions.Places;
import missionsrenewed.tiers.Tier;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Builds ship deals: half the time a client wants one ship from the player's fleet, half the time sells one.
 * When the player has no ship that fits the tier, the deal is a sale.
 */
public final class ShipDealContracts {

    /** A wanted ship pays its hull value times this, plus a little per tier, times its Starship Legends fame. */
    private static final float WANTED_MARKUP = 1.3f;
    /** A sale asks this share of the hull's value. */
    private static final float SALE_PRICE_SHARE = 0.65f;

    private ShipDealContracts() {}

    public static ContractIntel build(MarketAPI origin, Tier tier, Random random) {
        boolean wantedFirst = random.nextBoolean();
        ContractIntel first = wantedFirst ? wanted(origin, tier, random) : sale(origin, tier, random);
        if (first != null) return first;
        return wantedFirst ? sale(origin, tier, random) : wanted(origin, tier, random);
    }

    /** A ship from the player's fleet in the tier's DP band, never the flagship. Better known ships are wanted more. */
    static ShipWantedContract wanted(MarketAPI origin, Tier tier, Random random) {
        WeightedRandomPicker<FleetMemberAPI> picker = new WeightedRandomPicker<>(random);
        for (FleetMemberAPI member : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
            if (member.isFlagship() || member.isFighterWing() || member.getHullSpec() == null) continue;
            float dp = Hulls.dp(member.getHullSpec());
            if (dp < Hulls.minDP(tier.level) || dp > Hulls.maxDP(tier.level)) continue;
            picker.add(member, 1f + 2f * Legends.fame(member));
        }
        FleetMemberAPI member = picker.pick();
        if (member == null) return null;
        int fame = Legends.fame(member);
        int pay = Places.roundTo(member.getHullSpec().getBaseValue() * (WANTED_MARKUP + 0.05f * tier.level) * Legends.payMult(fame)
                * Settings.rewardMult() * Settings.payMult(MissionFamily.SHIP_DEAL), 500);
        return new ShipWantedContract(tier.level, client(origin, Ranks.POST_TRADER, random), origin, member, fame, pay);
    }

    static ShipSaleContract sale(MarketAPI origin, Tier tier, Random random) {
        Set<String> own = new HashSet<>();
        for (String hullId : origin.getFaction().getKnownShips()) {
            try {
                ShipHullSpecAPI spec = Global.getSettings().getHullSpec(hullId);
                if (spec != null) own.add(spec.getBaseHullId());
            } catch (RuntimeException ignored) {
                // A hull a removed mod left in a faction's list.
            }
        }
        String variantId = Hulls.pickVariant(tier.level, own, random);
        if (variantId == null) variantId = Hulls.pickVariant(tier.level, null, random);
        if (variantId == null) return null;
        ShipHullSpecAPI hull = Global.getSettings().getVariant(variantId).getHullSpec();
        int price = Places.roundTo(hull.getBaseValue() * SALE_PRICE_SHARE, 500);
        return new ShipSaleContract(tier.level, client(origin, Ranks.POST_ARMS_DEALER, random), origin, variantId,
                Hulls.sModsForTier(tier.level), Hulls.shipName(random), price);
    }

    private static PersonAPI client(MarketAPI origin, String post, Random random) {
        PersonAPI client = origin.getFaction().createRandomPerson(random);
        client.setRankId(Ranks.CITIZEN);
        client.setPostId(post);
        return client;
    }
}
