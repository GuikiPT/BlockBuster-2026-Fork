package mchorse.blockbuster.legacy;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Plain data holder for {@code assets/blockbuster/legacy/flattening.json},
 * deserialized once by {@link LegacyIdMap}. Field names match the JSON keys
 * verbatim (Gson binds by name). No logic lives here — it is a table.
 *
 * <p>P71: this table is the single offline source of pre-flattening
 * {@code (name, meta)} → 1.20.4 id knowledge. See {@link LegacyIdMap} for the
 * lookup rules that consume it.</p>
 */
public class LegacyIdTable
{
    /** 1.12 metadata colour order (wool, carpet, stained glass, concrete…). */
    public List<String> colors = Collections.emptyList();

    /** 1.12 wood species order shared by planks/sapling/log metadata. */
    public List<String> woods = Collections.emptyList();

    /** Block/item families whose 1.12 metadata picked a colour by {@code byMetadata} (path → suffix). */
    public Map<String, String> colorFamilies = Collections.emptyMap();

    /**
     * Colour families whose 1.12 metadata is a <em>dye-damage</em> value, not a
     * {@code byMetadata} index — {@code EnumDyeColor.byDyeDamage} inverts the
     * colour order (white=15 … black=0). Banner is the sole such family
     * ({@code ItemBanner.getBaseColor = byDyeDamage(meta & 15)}); resolved via
     * {@code colors[15 - (meta & 15)]}. Load-bearing 1.12 quirk (path → suffix).
     */
    public Map<String, String> dyeDamageFamilies = Collections.emptyMap();

    /** Families whose 1.12 metadata picked a wood species. */
    public Map<String, WoodFamily> woodFamilies = Collections.emptyMap();

    /** Blocks whose metadata is an explicit per-value pick-list (path → names). */
    public Map<String, List<String>> metaBlocks = Collections.emptyMap();

    /** Items whose damage is an explicit per-value pick-list (path → names). */
    public Map<String, List<String>> metaItems = Collections.emptyMap();

    /** Item id prefixes remapped wholesale (e.g. {@code record_} → {@code music_disc_}). */
    public Map<String, String> itemPrefixes = Collections.emptyMap();

    /** Item paths that carry their real identity in NBT, not the id (spawn_egg). */
    public List<String> nullItems = Collections.emptyList();

    public Map<String, String> itemRenames = Collections.emptyMap();
    public Map<String, String> blockRenames = Collections.emptyMap();
    public Map<String, String> entityRenames = Collections.emptyMap();

    /** Classic 1.12 numeric block id → legacy string name. */
    public Map<String, String> numericBlocks = Collections.emptyMap();

    /** Classic 1.12 numeric item id → legacy string name. */
    public Map<String, String> numericItems = Collections.emptyMap();

    /**
     * A wood-species family: {@code species = woods[min(base + (meta & mask), clamp)]},
     * result {@code "minecraft:" + species + suffix}.
     */
    public static class WoodFamily
    {
        public String suffix = "";
        public int mask;
        public int base;
        public int clamp;
    }
}
