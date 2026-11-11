package missionsrenewed.missions.ships;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import missionsrenewed.Settings;
import missionsrenewed.missions.ContractIntel;
import missionsrenewed.missions.MissionFamily;
import missionsrenewed.missions.Places;
import missionsrenewed.tiers.Tier;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/** Builds ship deals: half the time a client orders a hull from the player, half the time sells one. */
public final class ShipDealContracts {

    /** An order pays the hull's value times this, plus a little per tier. */
    private static final float ORDER_MARKUP = 1.3f;
    private static final float ORDER_PENALTY_SHARE = 0.1f;
    private static final float ORDER_DAYS = 120f;
    /** A sale asks this share of the hull's value. */
    private static final float SALE_PRICE_SHARE = 0.65f;

    private ShipDealContracts() {}

    public static ContractIntel build(MarketAPI origin, Tier tier, Random random) {
        boolean orderFirst = random.nextBoolean();
        ContractIntel first = orderFirst ? order(origin, tier, random) : sale(origin, tier, random);
        if (first != null) return first;
        return orderFirst ? sale(origin, tier, random) : order(origin, tier, random);
    }

    /** Base hull ids the factions with markets know, so an ordered hull can be bought somewhere. */
    private static Set<String> buyableHulls() {
        Set<String> factions = new HashSet<>();
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!market.isHidden() && market.isInEconomy()) factions.add(market.getFactionId());
        }
        Set<String> hulls = new HashSet<>();
        for (String factionId : factions) {
            FactionAPI faction = Global.getSector().getFaction(factionId);
            if (faction == null) continue;
            for (String hullId : faction.getKnownShips()) {
                try {
                    ShipHullSpecAPI spec = Global.getSettings().getHullSpec(hullId);
                    if (spec != null) hulls.add(spec.getBaseHullId());
                } catch (RuntimeException ignored) {
                    // A hull a removed mod left in a faction's list.
                }
            }
        }
        return hulls;
    }

    static ShipOrderContract order(MarketAPI origin, Tier tier, Random random) {
        String variantId = Hulls.pickVariant(tier.level, buyableHulls(), random);
        if (variantId == null) return null;
        ShipHullSpecAPI hull = Global.getSettings().getVariant(variantId).getHullSpec();
        int pay = Places.roundTo(hull.getBaseValue() * (ORDER_MARKUP + 0.05f * tier.level) * Settings.rewardMult()
                * Settings.payMult(MissionFamily.SHIP_DEAL), 500);
        int penalty = Places.roundTo(pay * ORDER_PENALTY_SHARE, 500);
        return new ShipOrderContract(tier.level, client(origin, Ranks.POST_TRADER, random), origin, hull.getBaseHullId(),
                pay, penalty, ORDER_DAYS);
    }

    static ShipSaleContract sale(MarketAPI origin, Tier tier, Random random) {
        Set<String> own = new HashSet<>();
        for (String hullId : origin.getFaction().getKnownShips()) {
            try {
                ShipHullSpecAPI spec = Global.getSettings().getHullSpec(hullId);
                if (spec != null) own.add(spec.getBaseHullId());
            } catch (RuntimeException ignored) {
                // See buyableHulls.
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
