package mchorse.blockbuster.legacy;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;

import mchorse.blockbuster.Blockbuster;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * P71 — the central legacy-ID translation shim.
 *
 * <p>Every legacy NBT reader (recordings, scenes, block/structure morphs) that
 * meets a pre-flattening block/item/entity id routes it through here, so the
 * "1.12 {@code (name, meta)} → 1.20.4 registry object" knowledge lives in one
 * place. Vanilla's {@code DataFixerUpper} never fixes modded-container NBT, so
 * this shim is ours by necessity (CROSS_CUTTING 1.6 / technique ledger).</p>
 *
 * <p><b>Total by contract:</b> unknown / garbage / modded / null input never
 * throws — it logs a warning once per distinct key and returns a stable
 * placeholder:</p>
 * <ul>
 *   <li>block → {@code minecraft:stone} default state ({@link #PLACEHOLDER_BLOCK})</li>
 *   <li>item → {@code minecraft:barrier} ({@link #PLACEHOLDER_ITEM})</li>
 *   <li>entity → {@link Optional#empty()} (caller decides whether to skip)</li>
 * </ul>
 * These placeholders are frozen through P211/P212 acceptance — do not change them.
 *
 * <p><b>Not a rewriter:</b> DTOs keep their raw legacy strings for byte-parity;
 * translation happens at playback/apply time. This class is a lookup service.</p>
 *
 * <p>Meta is resolved per-block via explicit pick-lists / colour+wood families
 * ({@link LegacyIdTable}), never a single global formula — 1.12 metadata is
 * overloaded per block (colour, variant, half, rotation). Orientation-only
 * metadata (stairs facing, log axis) is not encoded in the returned id; the
 * flattened base state is returned and the loss is acceptable at this layer
 * (full state hardening is P212).</p>
 *
 * <h2>P212 embedded-ID call-site inventory</h2>
 *
 * <p>Every legacy-NBT deserialization of an embedded block / item / entity id,
 * its id kind, and its routing status through this shim (the P212 Done-when
 * audit — keep in sync when a new legacy reader lands):</p>
 *
 * <pre>
 * BLOCK (name+meta / numeric)
 *   PlaceBlockAction.block/metadata (Block/Meta)   -> blockState(String,int)  ROUTED
 *   BlockMorph.block (Block/Meta)                  -> blockState(String,int)  ROUTED (raw re-emit for parity)
 *   StructurePalette entries (name+meta)           -> blockId(String,int)     ROUTED
 *   BreakBlockAction                               -- no embedded id (pos+drop only)   N/A
 *
 * ITEM (id+Count+Damage)
 *   EquipAction.itemData (Data)                    -> itemStack(NbtCompound)  ROUTED (raw store, translate at apply)
 *   DropAction.itemData (Data)                     -> itemStack(NbtCompound)  ROUTED (raw store)
 *   HotbarChangeAction.newItemStack (ItemStack)    -> itemStack(NbtCompound)  ROUTED (raw store)
 *   ItemMorph.stack (Stack)                        -> itemStack(NbtCompound)  ROUTED (raw re-emit for parity)
 *   BodyPart.slots[] (Items)                       -> itemStack(NbtCompound)  ROUTED (raw re-emit for parity)
 *   ShootGunAction.stack                           -- carries a blockbuster:gun (modded domain); kept on
 *                                                     the raw ItemStack.fromNbt path so the gun's whole
 *                                                     tag (embedded morphs) is never re-derived.
 *                                                     Intentionally bypassed. (Since the registry
 *                                                     fall-through below, routing it would no longer
 *                                                     break playback — but it buys nothing either.)
 *   GunProps.ammoStack / ValueItemSlots.value[]    -- Blockbuster/mclib config item stores; outside the
 *                                                     P212 required families. FOLLOW-UP only if legacy
 *                                                     vanilla meta-subtype ammo/config items surface.
 *   PacketDropItem.stack / GUI action panels       -- live network / client-editor display of the already
 *                                                     -stored raw NBT, not legacy-disk reads.               N/A
 *
 * ENTITY (registry name)
 *   EntityMorph id via MetamorphFactory.aliasEntityId -> entityId(String)     ROUTED
 * </pre>
 *
 * <p><b>Modded-id contract (uniform across every ROUTED item/block site):</b> a
 * non-{@code minecraft:} id gets a <b>registry fall-through</b> — if the id is
 * present in the live block/item registry <i>right now</i> it resolves directly
 * to that object (no flattening table, no metadata: a mod's 1.12 metadata
 * subtypes are its own business and the mod itself no longer exists in this
 * form), reproducing legacy {@code Block.REGISTRY.getObject(new
 * ResourceLocation(id))} / {@code new ItemStack(nbt)}. Only a mod-namespaced id
 * that is <i>absent</i> from the registry — a mod that isn't installed, or one
 * whose ids changed — is unmappable and degrades to the placeholder + one
 * warning. Without the fall-through, replaying a recording of a
 * {@code blockbuster:director} placement put down stone (batch H).</p>
 */
public final class LegacyIdMap
{
    /** Placeholder block state for unmappable legacy block ids. Frozen (P211). */
    public static final BlockState PLACEHOLDER_BLOCK = Blocks.STONE.getDefaultState();

    /** Placeholder item for unmappable legacy item ids. Frozen (P211). */
    public static final Item PLACEHOLDER_ITEM = Items.BARRIER;

    private static final String RESOURCE = "/assets/blockbuster/legacy/flattening.json";

    private static final LegacyIdTable TABLE = load();

    /** Distinct "category:key" strings already warned about (warn-once). */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private LegacyIdMap()
    {}

    private static LegacyIdTable load()
    {
        try (InputStream in = LegacyIdMap.class.getResourceAsStream(RESOURCE))
        {
            if (in == null)
            {
                Blockbuster.LOGGER.error("P71 legacy flattening table {} not found on classpath; all legacy ids will degrade to placeholders", RESOURCE);

                return new LegacyIdTable();
            }

            LegacyIdTable table = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), LegacyIdTable.class);

            return table == null ? new LegacyIdTable() : table;
        }
        catch (Exception e)
        {
            Blockbuster.LOGGER.error("P71 failed to parse legacy flattening table {}; all legacy ids will degrade to placeholders", RESOURCE, e);

            return new LegacyIdTable();
        }
    }

    /* ------------------------------------------------------------------ *
     *  Public API — the four signatures every legacy reader relies on.    *
     * ------------------------------------------------------------------ */

    /**
     * Resolve a 1.12 {@code (block registry name, metadata)} pair to a 1.20.4
     * {@link BlockState} (default state of the flattened block). Unknown →
     * {@link #PLACEHOLDER_BLOCK} + one warning.
     */
    public static BlockState blockState(String name, int meta)
    {
        String id = blockId(name, meta);
        Block block = resolveBlock(id);

        if (block == null)
        {
            warn("block", name + "@" + meta + (id == null ? "" : " (" + id + ")"));

            return PLACEHOLDER_BLOCK;
        }

        return block.getDefaultState();
    }

    /**
     * Resolve a classic 1.12 numeric block id + metadata to a 1.20.4
     * {@link BlockState}. Unknown numeric id → {@link #PLACEHOLDER_BLOCK}.
     */
    public static BlockState blockState(int numericId, int meta)
    {
        String name = TABLE.numericBlocks.get(Integer.toString(numericId));

        if (name == null)
        {
            warn("block#", numericId + "@" + meta);

            return PLACEHOLDER_BLOCK;
        }

        return blockState("minecraft:" + name, meta);
    }

    /**
     * Resolve a 1.12 {@code (item id, damage)} pair to a 1.20.4 {@link Item}.
     * {@code damage} is the metadata subtype for stackables (already consumed
     * here) — for damageable tools it is durability and is ignored by the
     * lookup. Unknown / spawn_egg → {@link #PLACEHOLDER_ITEM} + one warning.
     */
    public static Item item(String name, int damage)
    {
        String id = itemId(name, damage);
        Item item = resolveItem(id);

        if (item == null)
        {
            warn("item", name + "@" + damage + (id == null ? "" : " (" + id + ")"));

            return PLACEHOLDER_ITEM;
        }

        return item;
    }

    /**
     * Resolve a classic 1.12 numeric item id + damage to a 1.20.4 {@link Item}.
     * Numeric block ids (0–255) double as their block-item ids. Unknown →
     * {@link #PLACEHOLDER_ITEM}.
     */
    public static Item item(int numericId, int damage)
    {
        String key = Integer.toString(numericId);
        String name = TABLE.numericItems.get(key);

        if (name == null)
        {
            name = TABLE.numericBlocks.get(key);
        }

        if (name == null)
        {
            warn("item#", numericId + "@" + damage);

            return PLACEHOLDER_ITEM;
        }

        return item("minecraft:" + name, damage);
    }

    /**
     * Build a 1.20.4 {@link ItemStack} from a 1.12 {@code (id, count, damage)}
     * triple. This is where the 1.12 → 1.20.4 {@code Damage} move happens:
     * {@code damage} selects the subtype for stackables and is applied as
     * durability only when the resolved item is damageable (mirrors legacy
     * {@code ItemStack} semantics). Unknown → a placeholder-item stack.
     */
    public static ItemStack itemStack(String id, int count, int damage)
    {
        Item item = item(id, damage);
        ItemStack stack = new ItemStack(item, Math.max(1, count));

        if (item.isDamageable() && damage > 0)
        {
            stack.setDamage(damage);
        }

        return stack;
    }

    /**
     * Build a 1.20.4 {@link ItemStack} from a raw 1.12 (or already-modern) item
     * NBT compound — the entry point every {@code ItemStack}-carrying legacy
     * action reader routes through (Equip/Drop/HotbarChange {@code Data}/{@code
     * ItemStack} tags). 1.12 stored {@code {id:"minecraft:wool", Count:1b,
     * Damage:14s, tag:{…}}}; this reads the {@code id}+{@code Damage} through
     * {@link #itemId(String, int)} so meta subtypes flatten
     * ({@code wool}@14 → {@code red_wool}) and renames apply, preserves the
     * {@code tag} compound verbatim (enchantments, custom name, and the modern
     * {@code tag.Damage} durability slot), and moves a top-level 1.12
     * {@code Damage} into durability only when the resolved item is damageable.
     *
     * <p>Already-modern captures pass straight through: a flattened {@code id}
     * resolves unchanged, modern durability lives in {@code tag.Damage} (no
     * top-level {@code Damage}), so nothing is rewritten. Total: a null/idless
     * compound → {@link ItemStack#EMPTY} (the de-equip / empty-slot signal), an
     * unmappable / modded id → a {@link #PLACEHOLDER_ITEM} stack + one warning,
     * never a crash.</p>
     */
    public static ItemStack itemStack(NbtCompound legacy)
    {
        if (legacy == null || !legacy.contains("id", NbtElement.STRING_TYPE))
        {
            return ItemStack.EMPTY;
        }

        String rawId = legacy.getString("id");

        if (rawId == null || rawId.trim().isEmpty())
        {
            return ItemStack.EMPTY;
        }

        int count = legacy.contains("Count") ? legacy.getByte("Count") : 1;
        int damage = legacy.contains("Damage") ? legacy.getShort("Damage") : 0;

        String flatId = itemId(rawId, damage);
        Item item = resolveItem(flatId);

        if (item == null)
        {
            warn("itemstack", rawId + "@" + damage + (flatId == null ? "" : " (" + flatId + ")"));

            item = PLACEHOLDER_ITEM;
        }

        /*
         * Count is clamped to >=1: a resolved-but-zero-count 1.12 stack becomes a
         * 1-count stack here, not EMPTY (which is how vanilla ItemStack.fromNbt
         * would read Count:0). Harmless — recordings/morphs always capture
         * count>=1 — and it matches the itemStack(String,int,int) overload; the
         * genuine "no item" signal is the null/idless compound handled above.
         */
        ItemStack stack = new ItemStack(item, Math.max(1, count));

        /*
         * Preserve the item tag verbatim — enchantments, display name, and the
         * modern tag.Damage durability slot are outside this shim's id surface.
         */
        if (legacy.contains("tag", NbtElement.COMPOUND_TYPE))
        {
            stack.setNbt(legacy.getCompound("tag").copy());
        }

        /*
         * 1.12 kept durability in the top-level Damage short; 1.20.4 keeps it in
         * tag.Damage. Move it for damageable items; meta-family items already
         * consumed Damage into the flattened id above, so it must be dropped
         * (a resolved non-damageable item — wool, dye — never applies it).
         */
        if (item.isDamageable() && damage > 0)
        {
            stack.setDamage(damage);
        }

        return stack;
    }

    /**
     * Resolve a 1.12 spawn-egg's embedded entity id to its 1.20.4 spawn-egg
     * item ({@code <entity>_spawn_egg}). 1.12 stored the creature in item NBT
     * ({@code EntityTag}/id or {@code EntityId}), not the item damage, so
     * spawn eggs cannot be resolved by {@link #item(String, int)} alone.
     * Unknown → {@link #PLACEHOLDER_ITEM}.
     */
    public static Item spawnEgg(String legacyEntityId)
    {
        String entity = entityId(legacyEntityId);

        if (entity != null)
        {
            Item item = resolveItem(entity + "_spawn_egg");

            if (item != null)
            {
                return item;
            }
        }

        warn("spawn_egg", String.valueOf(legacyEntityId));

        return PLACEHOLDER_ITEM;
    }

    /**
     * Resolve a 1.12 entity registry name to a 1.20.4 {@link EntityType}.
     * Present when resolvable (renames applied), {@link Optional#empty()} for
     * modded / unknown ids (plus one warning) — callers substitute or skip.
     */
    public static Optional<EntityType<?>> entityType(String legacyId)
    {
        String id = entityId(legacyId);
        Identifier rl = id == null ? null : Identifier.tryParse(id);

        if (rl != null)
        {
            Optional<EntityType<?>> type = Registries.ENTITY_TYPE.getOrEmpty(rl);

            if (type.isPresent())
            {
                return type;
            }
        }

        warn("entity", String.valueOf(legacyId) + (id == null ? "" : " (" + id + ")"));

        return Optional.empty();
    }

    /* ------------------------------------------------------------------ *
     *  String-level resolution (for callers that keep the raw legacy id). *
     *  Return a flattened "minecraft:xxx" id, or null when unmappable.    *
     * ------------------------------------------------------------------ */

    /**
     * Flattened block id for a 1.12 {@code (name, meta)} pair, or null.
     *
     * <p>A mod-namespaced id that is registered right now is returned as-is
     * (registry fall-through, see the class doc); an unregistered one is
     * null.</p>
     */
    public static String blockId(String name, int meta)
    {
        String registered = registeredModdedId(name, Registries.BLOCK);

        if (registered != null)
        {
            return registered;
        }

        String path = path(name);

        if (path == null)
        {
            return null;
        }

        String family = colorFamily(path, meta);

        if (family != null)
        {
            return family;
        }

        String wood = woodFamily(path, meta);

        if (wood != null)
        {
            return wood;
        }

        List<String> picks = TABLE.metaBlocks.get(path);

        if (picks != null)
        {
            return "minecraft:" + pick(meta, picks);
        }

        return "minecraft:" + TABLE.blockRenames.getOrDefault(path, path);
    }

    /**
     * Flattened item id for a 1.12 {@code (id, damage)} pair, or null.
     *
     * <p>A mod-namespaced id that is registered right now is returned as-is
     * (registry fall-through, see the class doc); an unregistered one is
     * null.</p>
     */
    public static String itemId(String name, int damage)
    {
        String registered = registeredModdedId(name, Registries.ITEM);

        if (registered != null)
        {
            return registered;
        }

        String path = path(name);

        if (path == null)
        {
            return null;
        }

        String family = colorFamily(path, damage);

        if (family != null)
        {
            return family;
        }

        if (TABLE.nullItems.contains(path))
        {
            return null;
        }

        List<String> picks = TABLE.metaItems.get(path);

        if (picks != null)
        {
            return "minecraft:" + pick(damage, picks);
        }

        for (Map.Entry<String, String> entry : TABLE.itemPrefixes.entrySet())
        {
            if (path.startsWith(entry.getKey()))
            {
                return "minecraft:" + entry.getValue() + path.substring(entry.getKey().length());
            }
        }

        String wood = woodFamily(path, damage);

        if (wood != null)
        {
            return wood;
        }

        String renamed = TABLE.itemRenames.get(path);

        if (renamed == null)
        {
            renamed = TABLE.blockRenames.getOrDefault(path, path);
        }

        return "minecraft:" + renamed;
    }

    /** Flattened entity id for a 1.12 entity registry name, or null. */
    public static String entityId(String legacyId)
    {
        String path = path(legacyId);

        return path == null ? null : "minecraft:" + TABLE.entityRenames.getOrDefault(path, path);
    }

    /* ------------------------------------------------------------------ *
     *  Internals                                                          *
     * ------------------------------------------------------------------ */

    /** Vanilla path of a legacy id; null for modded domains / empty input. */
    private static String path(String id)
    {
        if (id == null)
        {
            return null;
        }

        String trimmed = id.trim();

        if (trimmed.isEmpty())
        {
            return null;
        }

        int colon = trimmed.indexOf(':');

        if (colon < 0)
        {
            return trimmed.toLowerCase(Locale.ROOT);
        }

        return trimmed.startsWith("minecraft:") ? trimmed.substring(colon + 1).toLowerCase(Locale.ROOT) : null;
    }

    /**
     * Registry fall-through: the id itself when it carries an explicit
     * non-{@code minecraft} namespace <b>and</b> is present in {@code registry}
     * right now; null otherwise (so the caller continues into the vanilla
     * flattening table, or degrades to the placeholder).
     *
     * <p>This is the half of legacy {@code Block.REGISTRY.getObject(new
     * ResourceLocation(this.block))} the flattening table can never carry: 1.12
     * resolved modded ids straight out of the registry, and so does the port
     * for any mod (including Blockbuster itself — {@code blockbuster:director},
     * {@code blockbuster:playback}) that is actually loaded. Metadata is
     * deliberately dropped: {@code getStateFromMeta} was the mod's own
     * per-block encoding and has no 1.20.4 counterpart, exactly like the
     * orientation loss the vanilla side already accepts.</p>
     *
     * <p>A bare id ({@code "stone"}, no colon) is <b>not</b> a modded id — 1.12
     * {@code ResourceLocation} defaulted it to the {@code minecraft} domain, so
     * it stays on the table path.</p>
     */
    private static String registeredModdedId(String id, Registry<?> registry)
    {
        if (id == null)
        {
            return null;
        }

        String trimmed = id.trim().toLowerCase(Locale.ROOT);
        int colon = trimmed.indexOf(':');

        if (colon <= 0 || trimmed.startsWith("minecraft:"))
        {
            return null;
        }

        Identifier rl = Identifier.tryParse(trimmed);

        return rl != null && registry.containsId(rl) ? trimmed : null;
    }

    private static String colorFamily(String path, int meta)
    {
        if (TABLE.colors.isEmpty())
        {
            return null;
        }

        String suffix = TABLE.colorFamilies.get(path);

        if (suffix != null)
        {
            return "minecraft:" + TABLE.colors.get(meta & 15) + suffix;
        }

        /*
         * Dye-damage families: 1.12 stores the colour as an EnumDyeColor
         * dye-damage value, which byDyeDamage inverts vs the byMetadata order
         * (white=15 … black=0). Banner is the only such family — ItemBanner
         * .getBaseColor = byDyeDamage(meta & 15), so damage 0 is a BLACK banner,
         * not white. Invert the index to preserve this load-bearing 1.12 quirk
         * (CROSS_CUTTING 1.1) instead of "fixing" it into wool's order.
         */
        String dye = TABLE.dyeDamageFamilies.get(path);

        if (dye != null)
        {
            return "minecraft:" + TABLE.colors.get(15 - (meta & 15)) + dye;
        }

        return null;
    }

    private static String woodFamily(String path, int meta)
    {
        LegacyIdTable.WoodFamily family = TABLE.woodFamilies.get(path);

        if (family == null || TABLE.woods.isEmpty())
        {
            return null;
        }

        int index = family.base + (meta & family.mask);

        index = Math.min(index, family.clamp);
        index = Math.max(0, Math.min(index, TABLE.woods.size() - 1));

        return "minecraft:" + TABLE.woods.get(index) + family.suffix;
    }

    /** meta-indexed pick that clamps to the first entry on out-of-range. */
    private static String pick(int meta, List<String> names)
    {
        return meta >= 0 && meta < names.size() ? names.get(meta) : names.get(0);
    }

    private static Block resolveBlock(String id)
    {
        Identifier rl = id == null ? null : Identifier.tryParse(id);

        return rl != null ? Registries.BLOCK.getOrEmpty(rl).orElse(null) : null;
    }

    private static Item resolveItem(String id)
    {
        Identifier rl = id == null ? null : Identifier.tryParse(id);

        return rl != null ? Registries.ITEM.getOrEmpty(rl).orElse(null) : null;
    }

    private static void warn(String category, String key)
    {
        if (WARNED.add(category + ":" + key))
        {
            Blockbuster.LOGGER.warn("P71 legacy {} \"{}\" has no 1.20.4 mapping — using placeholder", category, key);
        }
    }
}
