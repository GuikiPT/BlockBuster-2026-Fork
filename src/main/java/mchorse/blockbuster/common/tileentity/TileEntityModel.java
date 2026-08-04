package mchorse.blockbuster.common.tileentity;

import io.netty.buffer.ByteBuf;
import java.nio.file.Path;
import javax.vecmath.Vector3d;
import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.client.render.IRenderLast;
import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.blockbuster.legacy.OrphanedModelBlocks;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.PacketModifyModelBlock;
import mchorse.blockbuster.recording.scene.Scene;
import mchorse.mclib.network.IByteBufSerializable;
import mchorse.metamorph.api.Morph;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.MorphUtils;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Model block entity (roadmap P95).
 *
 * <p>Port of 1.12.2 {@code common/tileentity/TileEntityModel.java}. Stores the
 * rendered morph + a {@link TileEntityModelSettings} bundle, ticks a lazily
 * created dummy {@link EntityActor} (never spawned into the world) so the morph
 * animates, and syncs the <b>full</b> NBT on chunk-load / block-update (the
 * "flower pot fix" — {@link #toInitialChunkDataNbt()} / {@link #toUpdatePacket()}
 * are the 1.20.4 equivalents of the legacy {@code getUpdateTag} +
 * {@code onDataPacket} pair).</p>
 *
 * <p>Field names ({@code morph}, {@code entity}, {@code lastModelUpdate},
 * {@code settings}) and NBT keys are kept legacy-exact so old
 * {@code <world>/blockbuster} + {@code BlockEntityTag} data parses 1:1.</p>
 *
 * <p>SEAM(P96): the client BE renderer + infinite render AABB +
 * {@code actorRenderingRange} distance live in the client source set. This class
 * is the data + server-tick core. <b>P80.3</b> landed the {@link IRenderLast}
 * half: legacy's {@code shouldRenderInPass} enqueue lives on
 * {@code TileEntityModelRenderer} (1.20.4 has no render-pass hook on the block
 * entity itself), but the sort position is here, where legacy put it.</p>
 *
 * <h2>Orphaned model blocks — the 1.12.2 world-upgrade rescue (S22 P266)</h2>
 *
 * <p><b>Opening a 1.12.2 world in the port destroys every model block in every
 * chunk that loads, unless {@link #rescue} runs first.</b> A read-only scan of
 * the reference instance on 2026-07-26 found ~85 of them across 30 worlds
 * (S20 open question #9). The chain, re-verified with {@code javap} against the
 * 1.20.4 named jar:</p>
 *
 * <ol>
 *   <li>{@code ChunkSerializer} hands every {@code block_entities} entry to
 *       {@code Chunk.addPendingBlockEntityNbt}, unfiltered — the tile-entity
 *       compound survives the data-fixer verbatim.</li>
 *   <li>The <b>block</b> does not. Vanilla's {@code BlockStateFlattening} table
 *       covers numeric ids 0–255 only and Forge assigned modded blocks above
 *       that, so {@code blockbuster:model} flattens to {@code minecraft:air}
 *       (S22 P252, {@code LegacyBlockStateScopeTest}).</li>
 *   <li>{@code WorldChunk.runPostProcessing} iterates {@code blockEntityNbts} and
 *       calls {@code getBlockEntity(pos)} for every pending entry, which forces
 *       {@code loadBlockEntity} → {@code BlockEntity.createFromNbt}.</li>
 *   <li>{@code BlockEntityType.instantiate(pos, state)} is a bare
 *       {@code factory.create(pos, state)} — it does <b>not</b> consult
 *       {@code supports(state)} — so this class is constructed and
 *       {@link #readNbt} runs even though the block is air.</li>
 *   <li>Then {@code addBlockEntity} → {@code WorldChunk.setBlockEntity}, which
 *       <b>returns immediately</b> unless {@code getBlockState(pos)
 *       .hasBlockEntity()}. The block entity is never stored, never ticked,
 *       never re-serialized, and is gone from disk at the chunk's first save.</li>
 * </ol>
 *
 * <p>Step 4 is the whole window: one {@code readNbt} call, on the load of that
 * chunk, and then the data is unreachable. The director block survives the same
 * sequence only because it spends its one call writing a scene file
 * ({@code TileEntityDirector}, S22 P252). This class had no destination at all,
 * which was the bug. It now has one:
 * {@code <world>/blockbuster/model_blocks/model_block_&lt;x&gt;_&lt;y&gt;_&lt;z&gt;.dat}
 * (see {@link OrphanedModelBlocks} for the format).</p>
 *
 * <p><b>Bytes are moved, never round-tripped.</b> The dump holds a deep copy of
 * the whole tile-entity compound; no reader in this port stands between the
 * user's data and the file. That is not fastidiousness — the real captured
 * payload in {@code fixtures/worlds/model_te.nbt} contains a {@code block} morph
 * whose block is {@code moreblock:sculksensoractive}, a third-party
 * pre-flattening id the P71 {@code LegacyIdMap} cannot translate, and a
 * parse-and-re-serialize rescue would bake the placeholder in permanently.
 * Translation still happens where it always did: when something reads the morph.</p>
 *
 * <p><b>What it does not do.</b> It does not put the block back, and it cannot
 * stop vanilla discarding the block entity — {@code readNbt} runs before the
 * block entity has a world, so there is nothing to write to. It also cannot help
 * a chunk that has already been loaded and saved by a build without this rescue:
 * that compound is already gone from the region file. Restoring a dump into a
 * live world is a separate, deliberately user-driven step (a future command);
 * the format is a complete block-entity compound precisely so that step needs no
 * new parsing.</p>
 *
 * <p><b>Deliberate deviations from 1.12.2</b>, both because 1.12.2 had no
 * equivalent situation to be faithful to: the new
 * {@code <world>/blockbuster/model_blocks} folder, and the logging (legacy's
 * rescue idiom was a silent {@code catch (Exception e) {}}; a silent data-loss
 * rescue is not defensible — same reasoning as S20 open question #6).</p>
 */
public class TileEntityModel extends BlockEntity implements IByteBufSerializable, IRenderLast
{
    private static AbstractMorph DEFAULT_MORPH;

    public Morph morph = new Morph();
    public LivingEntity entity;

    private long lastModelUpdate;
    private TileEntityModelSettings settings = new TileEntityModelSettings();

    static
    {
        NbtCompound tag = new NbtCompound();

        tag.putString("Name", "blockbuster.fred");

        /* SEAM(S5/S6 blockbuster_pack morphs): "blockbuster.fred" resolves once
         * the model-morph factory + packs land; until then MorphManager has no
         * factory for it and returns null (total reader — never crashes). All
         * default-morph comparisons below are null-safe so behavior converges
         * automatically once fred is registered. */
        DEFAULT_MORPH = MorphManager.INSTANCE.morphFromNBT(tag);
    }

    /**
     * Data-carrier constructor (packet decode, pick-stack, tests): a bare model
     * TE not bound to a placed position. Uses the registered block-entity type
     * with a placeholder position — it is never inserted into a world.
     */
    public TileEntityModel()
    {
        this(BlockPos.ORIGIN, Blockbuster.MODEL_BLOCK.getDefaultState());
    }

    /**
     * The yarn {@link BlockEntity} constructor invoked by
     * {@code BlockModel.createBlockEntity}.
     */
    public TileEntityModel(BlockPos pos, BlockState state)
    {
        super(Blockbuster.MODEL_BLOCK_TILE, pos, state);

        this.morph.setDirect(MorphUtils.copy(getDefaultMorph()));

        this.lastModelUpdate = Scene.lastUpdate;
    }

    /**
     * @return reference to this {@link #settings} object.
     */
    public TileEntityModelSettings getSettings()
    {
        return this.settings;
    }

    /**
     * Legacy {@code TileEntityModel.getRenderLastPos} (1.12.2 lines 86-93),
     * arithmetic included:
     *
     * <pre>blockPos.getX() + settings.getX(), blockPos.getY() + settings.getY(),
     * blockPos.getZ() + settings.getZ()</pre>
     *
     * <p><b>Load-bearing asymmetry, preserved deliberately.</b> The block-entity
     * renderer draws the morph at {@code 0.5F + settings.getX()} /
     * {@code settings.getY()} / {@code 0.5F + settings.getZ()} — i.e. centred in
     * the block on X and Z — but this sort position does <b>not</b> add the 0.5.
     * So the depth key is the block's north-west corner plus the offset, half a
     * block off from where the morph actually draws. That skews the sort by at
     * most ~0.7 blocks of horizontal distance, which only matters for two model
     * blocks within about a block of each other, and 1.12.2 shipped it that way
     * for the whole life of the feature. Do not "fix" it: users' scenes were
     * composed against this ordering.</p>
     *
     * <p>{@code partialTicks} is unused — a block does not move. The parameter
     * exists because {@link IRenderLast} is shared with the actor, which does.</p>
     */
    @Override
    public Vector3d getRenderLastPos(float partialTicks)
    {
        BlockPos blockPos = this.getPos();

        return new Vector3d(
            blockPos.getX() + this.settings.getX(),
            blockPos.getY() + this.settings.getY(),
            blockPos.getZ() + this.settings.getZ());
    }

    public static AbstractMorph getDefaultMorph()
    {
        return DEFAULT_MORPH;
    }

    public void setMorph(AbstractMorph morph)
    {
        this.morph.set(morph);
        this.markDirty();
    }

    public void createEntity(World world)
    {
        if (world == null)
        {
            return;
        }

        this.entity = new EntityActor(world);
        this.entity.setOnGround(true);
        this.updateEntity();
    }

    public void updateEntity()
    {
        if (this.entity == null)
        {
            return;
        }

        ItemStack[] slots = this.settings.getSlots();

        for (int i = 0; i < slots.length; i++)
        {
            this.entity.equipStack(EquipmentSlot.values()[i], slots[i]);
        }

        this.entity.setPosition(
            this.pos.getX() + this.settings.getX() + 0.5,
            this.pos.getY() + this.settings.getY(),
            this.pos.getZ() + this.settings.getZ() + 0.5);
    }

    /**
     * Per-tick logic (legacy {@code ITickable.update()}), driven by the block's
     * {@code BlockEntityTicker}. Lazily builds the dummy actor, advances the
     * morph, and — on the server only — re-broadcasts the block to the whole
     * dimension when a scene reset fires (see {@link PacketModifyModelBlock}).
     */
    public void update(World world)
    {
        if (this.entity == null)
        {
            this.createEntity(world);
        }

        if (this.entity != null && this.settings.isEnabled())
        {
            this.entity.age++;
            this.entity.setPosition(
                this.pos.getX() + this.settings.getX() + 0.5,
                this.pos.getY() + this.settings.getY(),
                this.pos.getZ() + this.settings.getZ() + 0.5);

            if (!this.morph.isEmpty())
            {
                this.morph.get().update(this.entity);
            }
        }

        if (this.lastModelUpdate < Scene.lastUpdate && !this.settings.isExcludeResetPlayback())
        {
            if (world instanceof ServerWorld serverWorld)
            {
                PacketModifyModelBlock message = new PacketModifyModelBlock(this.pos, this);

                Dispatcher.sendToDimension(message, serverWorld);
            }

            this.lastModelUpdate = Scene.lastUpdate;
        }
    }

    /*
     * Legacy {@code shouldRefresh} (only refresh the BE when the block itself
     * changes, not on a blockstate/LIGHT change) needs no port: 1.20.4
     * {@code World.setBlockState} already preserves the BlockEntity across a
     * same-block LIGHT change, so the BE survives — matching 1.12.2.
     */
    public void copyData(TileEntityModel model, boolean merge)
    {
        this.settings.copy(model.settings);

        if (merge)
        {
            this.morph.set(model.morph.get());
        }
        else
        {
            this.morph.setDirect(model.morph.get());
        }

        this.updateEntity();
        this.markDirty();
    }

    /* NBT */

    @Override
    public void writeNbt(NbtCompound compound)
    {
        this.settings.toNBT(compound);

        if (!this.morph.isEmpty())
        {
            compound.put("Morph", this.morph.toNBT());
        }
    }

    @Override
    public void readNbt(NbtCompound compound)
    {
        /* Before anything is parsed: this may be the one and only time these
         * bytes are ever seen (see the "orphaned model block" section of the
         * class javadoc). Total and non-throwing, so it can never stop the
         * normal read below. */
        rescue(compound, this.pos, this.getCachedState());

        this.settings.fromNBT(compound);

        if (compound.contains("Morph", NbtElement.COMPOUND_TYPE))
        {
            this.morph.setDirect(MorphManager.INSTANCE.morphFromNBT(compound.getCompound("Morph")));
        }
    }

    /* The orphaned-model-block rescue (S22 P266) — see the class javadoc. */

    /**
     * The vanilla envelope keys of a 1.12.2 model tile-entity compound. A
     * compound carrying nothing but these has no model data to lose, so the
     * rescue writes no file for it ({@link Status#NOTHING_TO_RESCUE}).
     *
     * <p>{@code TileEntityModel extends TileEntity} directly in 1.12.2 (unlike
     * {@code TileEntityDirector}, which inherited {@code TileEntityFlowerPot}'s
     * {@code Item}/{@code Data}), so this is exactly what
     * {@code TileEntity.writeToNBT} wrote and nothing else. Confirmed against the
     * real capture in {@code fixtures/worlds/model_te.nbt}, whose root keys are
     * {@code id}, {@code x}, {@code y}, {@code z}, {@code Morph}.</p>
     */
    public static final String[] ENVELOPE_KEYS = {"id", "x", "y", "z"};

    /**
     * What a rescue attempt did. Every value is a terminal, non-throwing
     * outcome — {@link #rescue} has no failure mode that escapes.
     *
     * <p>Note what is <b>absent</b>: there is no {@code MALFORMED}. The director
     * rescue needs one because it copies a key-scoped subset and therefore has to
     * understand the payload's shape first. This one copies the whole compound,
     * so no property of the payload can make it refuse — which is precisely what
     * guarantees nothing is dropped for being un-modelled.</p>
     */
    public enum Status
    {
        /**
         * The block at this position still has a block entity, so vanilla will
         * store this one normally and nothing is at risk. The overwhelmingly
         * common case — every healthy model block on every chunk load.
         */
        NOT_ORPHANED,

        /**
         * No world save root (a remote client, or no world loaded). Nothing is
         * written; the server-side load of the same chunk is what does the rescue.
         */
        NO_DESTINATION,

        /**
         * The compound carries nothing but {@link #ENVELOPE_KEYS} — an
         * all-defaults model block with no morph. There is no data to lose, so no
         * file is written rather than littering the folder with empty dumps.
         */
        NOTHING_TO_RESCUE,

        /**
         * The derived dump id is not a legal {@code .dat} filename. Cannot happen
         * for real coordinates (digits, {@code -} and {@code _} are all accepted
         * by {@code Patterns.FILENAME}); guarded anyway, because writing outside
         * the dump folder would be worse than losing the rescue.
         */
        INVALID_ID,

        /** The payload was written to {@code model_blocks/model_block_x_y_z.dat}. */
        WRITTEN,

        /**
         * A dump already exists under that id and was left untouched. This is the
         * idempotent re-load outcome, and the same-coordinates-in-two-dimensions
         * outcome.
         */
        KEPT_EXISTING,

        /** The write itself failed (unwritable folder, full disk, …). Logged, never thrown. */
        SAVE_FAILED
    }

    /**
     * The outcome of a rescue attempt.
     *
     * @param status what happened
     * @param id     the dump id the payload maps to ({@code model_block_x_y_z}),
     *               or {@code null} when the rescue never got that far
     * @param dump   the complete dump root compound that was (or would have been)
     *               written; {@code null} unless there was something to write
     */
    public record Rescue(Status status, String id, NbtCompound dump)
    {}

    /**
     * The old-world data rescue. Total, idempotent, non-clobbering, and it never
     * touches the source compound.
     *
     * <p><b>It fires if and only if the block entity is orphaned</b>, i.e. when
     * {@code !state.hasBlockEntity()} — deliberately the exact predicate
     * {@code WorldChunk.setBlockEntity} uses to decide to throw the block entity
     * away (verified against the 1.20.4 named jar). A healthy model block writes
     * nothing, so no chunk load anywhere costs a file.</p>
     *
     * @param compound the tile-entity compound the chunk holds
     * @param pos      the position vanilla loaded it at
     * @param state    the block actually at {@code pos} — for a block entity being
     *                 constructed this is {@code getCachedState()}, which
     *                 {@code BlockEntity.createFromNbt} filled from
     *                 {@code chunk.getBlockState(pos)}
     */
    public static Rescue rescue(NbtCompound compound, BlockPos pos, BlockState state)
    {
        if (compound == null || pos == null || state == null || state.hasBlockEntity())
        {
            return new Rescue(Status.NOT_ORPHANED, null, null);
        }

        Path saveRoot = CommonProxy.saveRoot();

        if (saveRoot == null)
        {
            return new Rescue(Status.NO_DESTINATION, null, null);
        }

        if (!hasModelData(compound))
        {
            return new Rescue(Status.NOTHING_TO_RESCUE, null, null);
        }

        String id = OrphanedModelBlocks.dumpId(pos);

        if (!OrphanedModelBlocks.isValidFilename(id))
        {
            Blockbuster.LOGGER.warn("Orphaned model block at {} maps to dump id '{}', which is not a legal"
                + " filename; not rescued.", pos, id);

            return new Rescue(Status.INVALID_ID, id, null);
        }

        NbtCompound dump = OrphanedModelBlocks.envelope(compound, pos);
        boolean written;

        try
        {
            written = OrphanedModelBlocks.write(saveRoot, id, dump);
        }
        catch (Exception e)
        {
            /* Never thrown: a failed rescue must not break chunk loading. */
            Blockbuster.LOGGER.warn("Failed to rescue the orphaned model block at " + pos
                + " into 'blockbuster/model_blocks/" + id + ".dat'", e);

            return new Rescue(Status.SAVE_FAILED, id, dump);
        }

        if (written)
        {
            Blockbuster.LOGGER.info("Rescued an orphaned model block at {} into"
                + " 'blockbuster/model_blocks/{}.dat'. The block itself did not survive the world upgrade,"
                + " so Minecraft is about to discard this block entity; its morph and settings are now in"
                + " that file instead.", pos, id);
        }
        else
        {
            Blockbuster.LOGGER.warn("Orphaned model block at {} maps to 'blockbuster/model_blocks/{}.dat',"
                + " which already exists — keeping the existing dump. (Re-loading the same chunk is a no-op;"
                + " two model blocks at the same coordinates in different dimensions collide here, because"
                + " the dump id carries no dimension.)", pos, id);
        }

        return new Rescue(written ? Status.WRITTEN : Status.KEPT_EXISTING, id, dump);
    }

    /**
     * Whether the compound holds anything beyond {@link #ENVELOPE_KEYS}. Asks the
     * question the widest possible way — <i>any</i> non-envelope key counts,
     * including keys this port has never heard of — so an unrecognised payload is
     * rescued rather than discarded as uninteresting.
     */
    public static boolean hasModelData(NbtCompound compound)
    {
        for (String key : compound.getKeys())
        {
            boolean envelope = false;

            for (String skip : ENVELOPE_KEYS)
            {
                if (skip.equals(key))
                {
                    envelope = true;

                    break;
                }
            }

            if (!envelope)
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Full NBT sent on chunk load — 1.20.4 equivalent of the legacy
     * {@code getUpdateTag} "flower pot fix" (asie): the client re-reads the
     * complete tag, so the morph + settings arrive with the chunk.
     */
    @Override
    public NbtCompound toInitialChunkDataNbt()
    {
        return this.createNbt();
    }

    /**
     * Block-update sync packet (full NBT via {@link #toInitialChunkDataNbt()}),
     * the equivalent of legacy {@code getUpdatePacket}/{@code onDataPacket}.
     */
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket()
    {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    /* Byte-buf sync (IByteBufSerializable) */

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.settings.fromBytes(buf);

        this.morph.setDirect(MorphUtils.morphFromBuf(new PacketByteBuf(buf)));
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        this.settings.toBytes(buf);

        MorphUtils.morphToBuf(new PacketByteBuf(buf), this.morph.get());
    }
}
