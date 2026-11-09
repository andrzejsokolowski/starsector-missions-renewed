package missionsrenewed.tiers;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** The tier table from data/config/missions_renewed/tiers.json, merged across mods by the game. */
public final class TierTable {

    public static final String PATH = "data/config/missions_renewed/tiers.json";
    private static final Logger LOG = Global.getLogger(TierTable.class);

    private static List<Tier> tiers = Collections.emptyList();

    private TierTable() {}

    /** Reads the table. Called once from the mod plugin's onApplicationLoad, so a broken file stops the game at startup. */
    public static void load() throws Exception {
        JSONObject root = Global.getSettings().getMergedJSON(PATH);
        JSONArray rows = root.getJSONArray("tiers");
        List<Tier> loaded = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            loaded.add(new Tier(
                    row.getInt("tier"),
                    pair(row, "reward"),
                    pair(row, "fleetPoints"),
                    pair(row, "cargo"),
                    pair(row, "fuel"),
                    pair(row, "crew"),
                    pair(row, "stake"),
                    (float) row.optDouble("weight", 10)));
        }
        loaded.sort((a, b) -> Integer.compare(a.level, b.level));
        tiers = Collections.unmodifiableList(loaded);
        LOG.info("Missions - Renewed: loaded " + tiers.size() + " tiers");
    }

    public static List<Tier> all() {
        return tiers;
    }

    public static Tier get(int level) {
        for (Tier tier : tiers) {
            if (tier.level == level) return tier;
        }
        return null;
    }

    /**
     * A tier by weight, leaning toward playerTier (see PlayerTier.leanMult), or null when
     * no tier lies in [minTier, maxTier]. A playerTier of -1 or a lean of 0 gives the plain weights.
     */
    public static Tier pick(Random random, int playerTier, float lean, int minTier, int maxTier) {
        WeightedRandomPicker<Tier> picker = new WeightedRandomPicker<>(random);
        for (Tier tier : tiers) {
            if (tier.level < minTier || tier.level > maxTier) continue;
            picker.add(tier, tier.weight * PlayerTier.leanMult(tier.level, playerTier, lean));
        }
        return picker.isEmpty() ? null : picker.pick();
    }

    private static float[] pair(JSONObject row, String key) throws Exception {
        JSONArray range = row.getJSONArray(key);
        float min = (float) range.getDouble(0);
        float max = (float) range.getDouble(1);
        if (max < min) throw new IllegalArgumentException(PATH + ": tier " + row.optInt("tier") + " has " + key + " max below min");
        return new float[] {min, max};
    }
}
