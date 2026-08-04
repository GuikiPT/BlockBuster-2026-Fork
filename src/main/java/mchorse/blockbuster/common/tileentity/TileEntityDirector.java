package mchorse.blockbuster.common.tileentity;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.recording.scene.Scene;
import mchorse.blockbuster.recording.scene.SceneManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;

/**
 * Director block entity (roadmap P94 / P129, hardened by P252).
 *
 * <p>Port of 1.12.2 {@code common/tileentity/TileEntityDirector.java}. The
 * director block is deprecated; this block entity exists purely to salvage
 * old-world data: when its NBT still carries an {@code "Actors"} list (the
 * legacy director payload), {@link #rescue} writes it out as
 * {@code <world>/blockbuster/scenes/director_block_x_y_z.dat} so nothing is
 * lost.</p>
 *
 * <h2>The legacy {@code Actors} format</h2>
 *
 * <p>A pre-2.x Blockbuster director block stored its whole cast <b>inline in the
 * tile-entity compound</b> — there is no nested sub-tag. The compound written to
 * a 1.12.2 chunk's {@code TileEntities} list is:</p>
 *
 * <table border="1">
 *   <caption>Legacy director tile-entity compound</caption>
 *   <tr><th>Key</th><th>Type</th><th>Written by</th><th>Meaning</th></tr>
 *   <tr><td>{@code id}</td><td>string</td><td>{@code TileEntity.writeToNBT}</td>
 *       <td>{@code minecraft:blockbuster_director_tile_entity} — Forge wrapped
 *       the colon-less registration string in a {@code ResourceLocation}, whose
 *       domain defaults to {@code minecraft}. See {@code LegacyBlockEntityIds}.</td></tr>
 *   <tr><td>{@code x} / {@code y} / {@code z}</td><td>int</td>
 *       <td>{@code TileEntity.writeToNBT}</td><td>block position</td></tr>
 *   <tr><td>{@code Item} / {@code Data}</td><td>string / int</td>
 *       <td>{@code TileEntityFlowerPot.writeToNBT}</td>
 *       <td><b>Flower-pot residue.</b> {@code TileEntityDirector extends
 *       TileEntityFlowerPot}, so every director TE also carries the pot's item
 *       id and metadata. Not scene data; deliberately dropped by the rescue
 *       (a {@link Scene} has no such field, and legacy dropped it too).</td></tr>
 *   <tr><td>{@code Actors}</td><td>list&lt;compound&gt; (tag type 10)</td>
 *       <td>the director</td>
 *       <td>the cast — one compound per {@code Replay}, keys {@code Id},
 *       {@code Name}, {@code Target}, {@code Morph}, {@code Fake},
 *       {@code Enabled}, {@code Invincible}, {@code Invisible},
 *       {@code EnableBurning}, {@code RenderLast}, {@code Health},
 *       {@code FoodLevel}, {@code TotalExperience},
 *       {@code PlaybackXPFoodLevel}, {@code TP}. Most are written
 *       conditionally, so absent keys are normal.</td></tr>
 *   <tr><td>{@code Loops}</td><td>byte</td><td>the director</td><td>restart at end</td></tr>
 *   <tr><td>{@code Title}</td><td>string</td><td>the director</td><td>display name</td></tr>
 *   <tr><td>{@code StartCommand} / {@code StopCommand}</td><td>string</td>
 *       <td>the director</td><td>commands fired on playback start/stop</td></tr>
 *   <tr><td>{@code Audio}</td><td>string</td><td>{@code AudioHandler}</td>
 *       <td>audio track name, default {@code ""}</td></tr>
 *   <tr><td>{@code AudioShift}</td><td><b>float or int</b></td>
 *       <td>{@code AudioHandler}</td>
 *       <td>dual-typed: a float is seconds ({@code * 20} to ticks), anything
 *       else is int ticks</td></tr>
 * </table>
 *
 * <p>{@link #SCENE_KEYS} is exactly the scene-bearing subset — it is
 * {@code Scene.toNBT}'s key set, which is why the TE compound doubles as a
 * scene compound and why the rescue is a key-scoped copy rather than a parse.</p>
 *
 * <p><b>2.7.2 never wrote {@code Actors} back.</b> The legacy class overrides
 * {@code readFromNBT} only — there is no {@code writeToNBT} — so from 2.x
 * onward the payload was read once and dropped on the chunk's next save. Every
 * {@code Actors} compound in the wild was therefore written by a pre-2.x
 * Blockbuster, and the rescue window closes at the first save of the chunk that
 * holds it. The port keeps that: no {@code writeNbt} override here either.</p>
 *
 * <h2>Why the rescue copies bytes instead of re-serializing a {@link Scene}</h2>
 *
 * <p>Legacy did {@code scene.fromNBT(compound)} then
 * {@code CommonProxy.scenes.save(id, scene)} — parse, then write the parsed
 * object back out. That was safe in 1.12.2 because the same code that wrote the
 * payload read it. In the port the reader is a <i>reimplementation</i>, so a
 * round-trip silently drops anything it does not model (a blacklisted morph, a
 * key a future format adds). This rescue instead copies the {@link #SCENE_KEYS}
 * subtrees <b>verbatim</b>, so it is total by construction: no reader stands
 * between the user's bytes and the scene file. The parse still happens, but only
 * to produce the {@link Scene} the caller sees and to classify the payload — it
 * never decides the bytes.</p>
 *
 * <p>That is also why the rescue does <b>no id translation</b>. Embedded
 * pre-flattening block/item/entity ids inside {@code Actors[].Morph} stay
 * exactly as they were; the P71 shim ({@code mchorse.blockbuster.legacy.LegacyIdMap})
 * already translates them where it always did — on scene <i>load</i>. Baking a
 * translation into the rescued file would make a lossy, one-way decision
 * permanent in the user's data.</p>
 *
 * <h2>Parity notes / deliberate deviations</h2>
 * <ul>
 *   <li>Legacy extended {@code TileEntityFlowerPot} — a vanilla-TE-id reuse
 *       trick so old worlds kept deserializing. On Fabric we extend
 *       {@link BlockEntity} directly and the historical id spellings are handled
 *       by {@code LegacyBlockEntityIds} + {@code BlockEntityMixin} (P93.1).</li>
 *   <li>The rescue scene id is {@code director_block_<x>_<y>_<z>} — underscores,
 *       raw (possibly negative) coordinates. It is a disk contract with the S11
 *       scene naming and is kept exact, including its flaw: the id carries
 *       <b>no dimension</b>, so two director blocks at the same coordinates in
 *       different dimensions collide. Legacy silently let the second overwrite
 *       the first; here the second is kept out and warned about
 *       ({@link Status#KEPT_EXISTING}).</li>
 *   <li><b>Deviation — non-clobbering.</b> Legacy overwrote the scene file on
 *       every load. The port refuses to overwrite an existing scene file. The TE
 *       payload is frozen legacy data that can never change, while the scene file
 *       is live and user-editable, so overwriting can only ever destroy newer
 *       data (a scene the user edited after the first rescue, or a same-named
 *       scene from another dimension). Re-running the rescue is therefore a
 *       no-op, which is what makes it idempotent.</li>
 *   <li><b>Deviation — logging.</b> Legacy's {@code catch (Exception e) {}}
 *       swallowed save failures without a word. A silent failure in a data-loss
 *       rescue is not defensible, so failures and refusals log a warning. Nothing
 *       throws: the block entity must never break chunk loading.</li>
 *   <li>The infinite render AABB + {@code actorRenderingRange}-squared max
 *       render distance are client-render concerns handled by the client BE
 *       renderer (client source set).</li>
 * </ul>
 *
 * <p>Legacy source:
 * {@code blockbuster-1.12/src/main/java/mchorse/blockbuster/common/tileentity/TileEntityDirector.java}.</p>
 */
public class TileEntityDirector extends BlockEntity
{
    /**
     * The scene-bearing key set of a legacy director tile-entity compound —
     * exactly what {@code Scene.toNBT} writes. Everything else in the compound
     * ({@code id}, {@code x}, {@code y}, {@code z}, and the inherited flower-pot
     * {@code Item}/{@code Data}) is TE envelope, not scene data.
     */
    public static final String[] SCENE_KEYS = {
        "Actors", "Loops", "Title", "StartCommand", "StopCommand", "Audio", "AudioShift"
    };

    /** The key whose presence gates the whole rescue, exactly as in 1.12.2. */
    public static final String PAYLOAD_KEY = "Actors";

    public TileEntityDirector(BlockPos pos, BlockState state)
    {
        super(Blockbuster.DIRECTOR_TILE, pos, state);
    }

    @Override
    public void readNbt(NbtCompound compound)
    {
        super.readNbt(compound);

        rescue(compound, this.pos);
    }

    /**
     * What a rescue attempt did. Every value is a terminal, non-throwing
     * outcome — {@link #rescue} has no failure mode that escapes.
     */
    public enum Status
    {
        /** No {@code Actors} key: an ordinary post-2.x director TE. Nothing to do. */
        NO_PAYLOAD,

        /**
         * {@code Actors} exists but is not a list of compounds. The payload is
         * not understood, so <b>nothing is written</b> — a half-read director
         * must never put a degraded file where a good one could be.
         */
        MALFORMED,

        /**
         * The derived scene id is not a legal scene filename
         * ({@code Patterns.FILENAME}). Cannot happen for real coordinates
         * (digits, {@code -} and {@code _} are all accepted); guarded anyway
         * because writing outside the scenes folder would be worse than losing
         * the rescue.
         */
        INVALID_ID,

        /** The payload was written to {@code scenes/director_block_x_y_z.dat}. */
        WRITTEN,

        /**
         * A scene file already exists under that id and was left untouched.
         * This is the idempotent re-run outcome and the same-coordinates-in-two-
         * dimensions outcome.
         */
        KEPT_EXISTING,

        /** The write itself failed (unwritable folder, full disk, …). Logged, never thrown. */
        SAVE_FAILED
    }

    /**
     * The outcome of a rescue attempt.
     *
     * @param status  what happened
     * @param id      the scene id the payload maps to ({@code director_block_x_y_z}),
     *                or {@code null} for {@link Status#NO_PAYLOAD}
     * @param payload the verbatim scene compound that was (or would have been)
     *                written; {@code null} unless the payload was understood
     * @param scene   the parsed {@link Scene}, for callers and tests; {@code null}
     *                unless the payload was understood. Parsed <i>from</i>
     *                {@code payload} — it never decides what gets written.
     */
    public record Rescue(Status status, String id, NbtCompound payload, Scene scene)
    {}

    /**
     * The legacy scene id for a director block at {@code pos}. Format is a disk
     * contract: {@code director_block_<x>_<y>_<z>}, raw coordinates, negatives
     * included.
     */
    public static String sceneId(BlockPos pos)
    {
        return "director_block_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
    }

    /**
     * The old-world data rescue. Total, idempotent and non-clobbering — see the
     * class javadoc for the format and the two deliberate deviations from
     * 1.12.2 (non-clobbering; warnings instead of a silent swallow).
     *
     * <p>The source compound is never mutated.</p>
     */
    public static Rescue rescue(NbtCompound compound, BlockPos pos)
    {
        /* At least the data wouldn't be lost */
        if (compound == null || !compound.contains(PAYLOAD_KEY))
        {
            return new Rescue(Status.NO_PAYLOAD, null, null, null);
        }

        String id = sceneId(pos);

        if (!isReadablePayload(compound))
        {
            Blockbuster.LOGGER.warn("Director block at {} carries an '{}' tag that is not a list of compounds"
                + " ({}); its data is left in the chunk untouched rather than rescued into a degraded"
                + " scene file.", pos, PAYLOAD_KEY, describe(compound.get(PAYLOAD_KEY)));

            return new Rescue(Status.MALFORMED, id, null, null);
        }

        if (!SceneManager.isValidFilename(id))
        {
            Blockbuster.LOGGER.warn("Director block at {} maps to scene id '{}', which is not a legal scene"
                + " filename; not rescued.", pos, id);

            return new Rescue(Status.INVALID_ID, id, null, null);
        }

        NbtCompound payload = payload(compound);
        Scene scene = new Scene();

        scene.fromNBT(payload);
        scene.setId(id);

        Status status;

        try
        {
            status = CommonProxy.scenes.rescue(id, payload) ? Status.WRITTEN : Status.KEPT_EXISTING;
        }
        catch (Exception e)
        {
            /* Legacy swallowed this silently; we log it. Still never thrown —
             * a failed rescue must not break chunk loading. */
            Blockbuster.LOGGER.warn("Failed to rescue the director block at " + pos + " into scene '" + id + "'", e);

            return new Rescue(Status.SAVE_FAILED, id, payload, scene);
        }

        if (status == Status.WRITTEN)
        {
            Blockbuster.LOGGER.info("Rescued the legacy director block at {} into scene '{}' ({} actor(s)).",
                pos, id, scene.replays.size());
        }
        else
        {
            Blockbuster.LOGGER.warn("Legacy director block at {} maps to scene '{}', which already exists —"
                + " keeping the existing scene file. (Re-loading the same chunk is a no-op; two director"
                + " blocks at the same coordinates in different dimensions collide here, because the legacy"
                + " scene id carries no dimension.)", pos, id);
        }

        return new Rescue(status, id, payload, scene);
    }

    /**
     * Back-compat entry point for callers that only want the migrated scene.
     *
     * @return the {@link Scene} the payload describes, or {@code null} when
     *         there was no readable payload.
     */
    public static Scene migrate(NbtCompound compound, BlockPos pos)
    {
        return rescue(compound, pos).scene();
    }

    /**
     * Whether the {@code Actors} tag is shaped like a legacy cast: a list tag
     * whose elements are compounds. An empty list passes (a director with no
     * actors is legitimate and 1.12.2 wrote one); a list of anything else does
     * not, because {@code NbtCompound.getList} would silently hand back an empty
     * list and the rescue would write an empty cast over real data.
     */
    private static boolean isReadablePayload(NbtCompound compound)
    {
        NbtElement actors = compound.get(PAYLOAD_KEY);

        if (!(actors instanceof NbtList list))
        {
            return false;
        }

        return list.isEmpty() || list.getHeldType() == NbtElement.COMPOUND_TYPE;
    }

    /**
     * The verbatim scene compound: a deep copy of every {@link #SCENE_KEYS}
     * subtree present in the tile-entity compound, and nothing else. Copying
     * means the source compound (which the chunk still owns) is never aliased or
     * mutated.
     */
    public static NbtCompound payload(NbtCompound compound)
    {
        NbtCompound payload = new NbtCompound();

        for (String key : SCENE_KEYS)
        {
            NbtElement value = compound.get(key);

            if (value != null)
            {
                payload.put(key, value.copy());
            }
        }

        return payload;
    }

    private static String describe(NbtElement element)
    {
        return element == null ? "absent" : element.getNbtType().getCrashReportName();
    }
}
