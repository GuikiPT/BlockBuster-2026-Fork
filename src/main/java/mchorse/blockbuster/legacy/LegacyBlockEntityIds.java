package mchorse.blockbuster.legacy;

import java.util.HashMap;
import java.util.Map;

import mchorse.blockbuster.Blockbuster;
import net.minecraft.util.Identifier;

/**
 * P93.1 — the block-entity {@code id} alias table.
 *
 * <p>1.20.4 keys a saved block entity by the registry id of its
 * {@link net.minecraft.block.entity.BlockEntityType}, written into the chunk
 * (and into an item stack's {@code BlockEntityTag}) as the string {@code id}.
 * Three <em>different</em> strings have named Blockbuster's two block entities
 * over the mod's life, and all three must keep loading:</p>
 *
 * <table border="1">
 *   <caption>Block-entity id spellings</caption>
 *   <tr><th>Spelling</th><th>Written by</th></tr>
 *   <tr><td>{@code minecraft:blockbuster_model_tile_entity}</td>
 *       <td><b>1.12.2</b>. {@code GameRegistry.registerTileEntity(clazz,
 *       "blockbuster_model_tile_entity")} passes the raw string to
 *       {@code TileEntity.register}, which wraps it in
 *       {@code new ResourceLocation(id)} — <b>no colon, so the domain defaults
 *       to {@code minecraft}</b> — and {@code TileEntity.writeToNBT} serializes
 *       that {@code ResourceLocation.toString()}. Confirmed empirically against
 *       real captured 2.7.2 data: the {@code BlockEntityTag} compounds inside
 *       {@code fixtures/goldens/records/E_Crafting.golden.json} carry exactly
 *       {@code "id": "minecraft:blockbuster_model_tile_entity"}.</td></tr>
 *   <tr><td>{@code blockbuster:model}</td>
 *       <td><b>This port, 2026-07-21 … 2026-07-26.</b> The model BE type was
 *       registered under the block's own id instead of the legacy TE string
 *       (the director's was always correct). Worlds saved by those builds —
 *       the MultiMC 1.20.4 dev instance among them — hold it.</td></tr>
 *   <tr><td>{@code blockbuster:blockbuster_model_tile_entity}</td>
 *       <td><b>Current.</b> The spec id
 *       ({@code plan/S08-blocks-items-entities.md} step 3): the legacy TE string
 *       kept verbatim as the path. Everything above maps forward to it.</td></tr>
 * </table>
 *
 * <p>Mapping forward is one-way and self-healing: {@code createFromNbt} resolves
 * the alias to the canonical type, and the next save writes the canonical id
 * back (BlockEntity.writeIdToNbt asks the registry for the type's id), so a
 * world visits the alias path at most once per chunk.</p>
 *
 * <p>The unnamespaced spelling is accepted too, for defence in depth — it is
 * what the S08/S20 plan text assumed 1.12.2 wrote, and what a hand-edited or
 * third-party-exported compound is most likely to contain.</p>
 *
 * <p>This class holds only the table + lookup; the seam that consults it is
 * {@code mchorse.blockbuster.mixin.BlockEntityMixin}.
 * It is deliberately separate from {@link LegacyIdMap}, which translates
 * pre-flattening <i>block/item/entity</i> ids and needs the full metadata
 * machinery; block-entity ids never had metadata.</p>
 */
public final class LegacyBlockEntityIds
{
    /** The legacy TE string 1.12.2 registered {@code TileEntityModel} under. */
    public static final String MODEL_LEGACY_KEY = "blockbuster_model_tile_entity";

    /** The legacy TE string 1.12.2 registered {@code TileEntityDirector} under. */
    public static final String DIRECTOR_LEGACY_KEY = "blockbuster_director_tile_entity";

    /** Canonical modern id of the model block entity type. */
    public static final Identifier MODEL = new Identifier(Blockbuster.MOD_ID, MODEL_LEGACY_KEY);

    /** Canonical modern id of the director block entity type. */
    public static final Identifier DIRECTOR = new Identifier(Blockbuster.MOD_ID, DIRECTOR_LEGACY_KEY);

    private static final Map<String, Identifier> ALIASES = aliases();

    private LegacyBlockEntityIds()
    {}

    private static Map<String, Identifier> aliases()
    {
        Map<String, Identifier> map = new HashMap<String, Identifier>();

        /* 1.12.2 on-disk (Forge's default-domain ResourceLocation) */
        map.put("minecraft:" + MODEL_LEGACY_KEY, MODEL);
        map.put("minecraft:" + DIRECTOR_LEGACY_KEY, DIRECTOR);

        /* The raw registration argument, unnamespaced */
        map.put(MODEL_LEGACY_KEY, MODEL);
        map.put(DIRECTOR_LEGACY_KEY, DIRECTOR);

        /* This port's own 2026-07 mis-registration of the model BE type */
        map.put(Blockbuster.MOD_ID + ":model", MODEL);

        return Map.copyOf(map);
    }

    /** The alias table, for the P93.1 contract test and the S20 migrator. */
    public static Map<String, Identifier> aliasTable()
    {
        return ALIASES;
    }

    /**
     * The canonical block-entity id for a raw NBT {@code id} string. Falls back
     * to {@link Identifier#tryParse(String)} — including its {@code null} return
     * for garbage — so non-Blockbuster ids behave exactly as vanilla.
     */
    public static Identifier resolve(String raw)
    {
        Identifier alias = raw == null ? null : ALIASES.get(raw);

        return alias != null ? alias : Identifier.tryParse(raw);
    }
}
