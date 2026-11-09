package missionsrenewed.missions.combat;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The tier 5 to 7 enemy groups from data/config/missions_renewed/enemy_fleets.json, merged across mods.
 * A group is usable only when its faction is loaded and, for hand-built groups, at least one variant exists.
 */
public final class EnemyGroups {

    public static final String PATH = "data/config/missions_renewed/enemy_fleets.json";
    private static final Logger LOG = Global.getLogger(EnemyGroups.class);

    private static List<Group> groups = Collections.emptyList();

    private EnemyGroups() {}

    public static final class Group {
        public final String id, name, factionId;
        public final int minTier, maxTier;
        public final float weight;
        public final boolean generated;
        /** Variant id to pick weight. Empty for generated groups. */
        public final Map<String, Float> variants;

        Group(String id, String name, String factionId, int minTier, int maxTier, float weight, boolean generated,
              Map<String, Float> variants) {
            this.id = id;
            this.name = name;
            this.factionId = factionId;
            this.minTier = minTier;
            this.maxTier = maxTier;
            this.weight = weight;
            this.generated = generated;
            this.variants = variants;
        }

        /** Whether this group can be built in the running game. */
        public boolean available() {
            if (Global.getSector() == null || Global.getSector().getFaction(factionId) == null) return false;
            if (generated) return true;
            for (String variant : variants.keySet()) {
                if (Global.getSettings().doesVariantExist(variant)) return true;
            }
            return false;
        }
    }

    /** Reads the file. Called from onApplicationLoad, so a broken file stops the game at startup with a clear error. */
    public static void load() throws Exception {
        JSONObject root = Global.getSettings().getMergedJSON(PATH);
        JSONObject rows = root.getJSONObject("groups");
        List<Group> loaded = new ArrayList<>();
        Iterator<?> keys = rows.keys();
        while (keys.hasNext()) {
            String id = (String) keys.next();
            JSONObject row = rows.getJSONObject(id);
            Map<String, Float> variants = new LinkedHashMap<>();
            JSONObject v = row.optJSONObject("variants");
            if (v != null) {
                Iterator<?> vk = v.keys();
                while (vk.hasNext()) {
                    String variant = (String) vk.next();
                    variants.put(variant, (float) v.getDouble(variant));
                }
            }
            loaded.add(new Group(id, row.getString("name"), row.getString("faction"), row.optInt("minTier", 5),
                    row.optInt("maxTier", 7), (float) row.optDouble("weight", 10), row.optBoolean("generated", false), variants));
        }
        groups = Collections.unmodifiableList(loaded);
        LOG.info("Missions - Renewed: loaded " + groups.size() + " enemy groups");
    }

    /** Groups that fit this tier and can be built now. */
    public static List<Group> forTier(int tier) {
        List<Group> result = new ArrayList<>();
        for (Group group : groups) {
            if (tier >= group.minTier && tier <= group.maxTier && group.available()) result.add(group);
        }
        return result;
    }

    public static Group get(String id) {
        for (Group group : groups) {
            if (group.id.equals(id)) return group;
        }
        return null;
    }
}
