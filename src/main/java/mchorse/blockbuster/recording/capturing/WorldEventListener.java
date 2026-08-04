package mchorse.blockbuster.recording.capturing;

import java.util.List;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.common.block.BlockDirector;
import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.blockbuster.recording.actions.Action;
import mchorse.blockbuster.recording.actions.BreakBlockAnimation;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Blockbuster's world event listener (roadmap P108 seam + P114 parity).
 *
 * <p>This dude is responsible only for adding breaking block animation during
 * player recording, plus feeding damage control the block/entity changes that
 * happen while a recording is running.</p>
 *
 * <p>Legacy was an {@code IWorldEventListener} attached per server world; that
 * interface no longer exists on 1.20.4, so this is now a plain class invoked
 * from mixins ({@code WorldMixin.setBlockState} HEAD for the static
 * {@link #setBlockState} coremod target, {@code ServerWorldMixin
 * .setBlockBreakingInfo} for {@link #sendBlockBreakProgress},
 * {@code ServerWorldMixin.onBlockChanged} for {@link #notifyBlockUpdate}) and
 * from a Fabric API event ({@code ServerEntityEvents.ENTITY_LOAD} for
 * {@link #onEntityAdded}, registered in {@link ActionHandler#register()}).</p>
 */
public class WorldEventListener
{
    /**
     * Coremod target (legacy {@code WorldTransformer}): caches the pre-change
     * block entity so damage control can restore it. Gated on
     * {@code damage_control}. Called from {@code WorldMixin} at HEAD, so the
     * block entity is still the pre-change one.
     */
    public static void setBlockState(World world, BlockPos pos, BlockState newState, int flags)
    {
        if (world.isClient())
        {
            return;
        }

        if (Blockbuster.damageControl.get())
        {
            ActionHandler.lastTE = world.getBlockEntity(pos);
        }
    }

    /**
     * Used by damage control (legacy {@code IWorldEventListener
     * .notifyBlockUpdate}). Director blocks are skipped entirely (actors must
     * never toggle them) and moving-piston old states are substituted with air
     * so damage control doesn't restore ghost piston-extension blocks.
     *
     * <p>Called after a successful {@code World.setBlockState} with the
     * pre-change ("old") state — on 1.20.4 that seam is
     * {@code ServerWorld.onBlockChanged}, which vanilla invokes exactly once at
     * the success tail of {@code World.setBlockState(pos, state, flags,
     * maxUpdateDepth)} (verified against the loom-named jar). {@code flags} is
     * not available there and is passed as {@code 0}; legacy ignored it too.</p>
     *
     * <p><b>1.20.4 deviation:</b> 1.12.2 routed this through
     * {@code World.markAndNotifyBlock}, which only reached the listener when
     * the {@code 2} (notify-listeners) flag was set. {@code onBlockChanged} has
     * no such gate, so silent (flag-2-less) block sets are now tracked too.
     * Damage control only ever restores <i>more</i> of the pre-recording world
     * because of this, and the first-writer-wins dedup keeps the restored state
     * identical.</p>
     */
    public static void notifyBlockUpdate(World world, BlockPos pos, BlockState oldState, BlockState newState, int flags)
    {
        if (world != null && world.isClient())
        {
            return;
        }

        if (!Blockbuster.damageControl.get())
        {
            return;
        }

        BlockState resolved = resolveDamageOldState(oldState);

        if (resolved == null)
        {
            return;
        }

        CommonProxy.damage.addBlock(pos, resolved, world);
    }

    /**
     * Legacy decision half of {@link #notifyBlockUpdate}: returns the old
     * state to hand to damage control, {@code Blocks.AIR} substituted for a
     * moving piston, or {@code null} when the change must be skipped (director
     * blocks). Extracted so the branching can be exercised headlessly before
     * the P113 sink exists.
     *
     * <p>P241: the {@link BlockDirector} skip is restored (legacy
     * {@code WorldEventListener.notifyBlockUpdate} lines 52-55). It must stay
     * <b>first</b>, before the moving-piston substitution and before the
     * {@code addBlock} call — a director block that changed state during a take
     * is never handed to damage control, so playback cannot resurrect or
     * rewrite it. The guard is on the <i>old</i> state only, exactly like
     * legacy: breaking a director is skipped, but placing one over a plain
     * block still records the plain block as the restore target.</p>
     */
    static BlockState resolveDamageOldState(BlockState oldState)
    {
        if (oldState.getBlock() instanceof BlockDirector)
        {
            return null;
        }

        if (oldState.getBlock() == Blocks.MOVING_PISTON)
        {
            return Blocks.AIR.getDefaultState();
        }

        return oldState;
    }

    /**
     * Adds a breaking-block animation action to the recorder of the player
     * doing the breaking (legacy {@code sendBlockBreakProgress}). Keyed off
     * the breaker's own recorder — other players mining near a recording is
     * not captured. Called from {@code ServerWorldMixin.setBlockBreakingInfo}.
     */
    public static void sendBlockBreakProgress(World world, int breakerId, BlockPos pos, int progress)
    {
        Entity breaker = world.getEntityById(breakerId);

        if (breaker instanceof PlayerEntity player)
        {
            List<Action> events = CommonProxy.manager.getActions(player);

            if (!player.getWorld().isClient() && events != null)
            {
                events.add(new BreakBlockAnimation(pos, progress));
            }
        }
    }

    /**
     * Damage-control entity tracking (legacy {@code onEntityAdded}): actors and
     * players are never tracked; everything else spawned during a recording is
     * handed to damage control so it can be removed on restore. Called from
     * {@code ServerEntityEvents.ENTITY_LOAD}.
     *
     * <p>No {@code damage_control} config gate here, exactly like legacy: the
     * manager's map is empty whenever the feature is off (only
     * {@code addDamageControl} is gated), so {@code addEntity} is a no-op.</p>
     *
     * <p><b>1.20.4 deviation:</b> the legacy feed was
     * {@code World.onEntityAdded}, reached both from {@code spawnEntity} and
     * from {@code World.loadEntities} (chunk load). Fabric's
     * {@code ServerEntityEvents.ENTITY_LOAD} fires on the same two occasions,
     * so the (pre-existing) legacy hazard of a chunk-loaded entity being
     * discarded on restore is preserved verbatim rather than "fixed".</p>
     */
    public static void onEntityAdded(Entity entity)
    {
        if (!shouldTrackEntity(entity))
        {
            return;
        }

        CommonProxy.damage.addEntity(entity);
    }

    /**
     * Legacy filter for {@link #onEntityAdded}: {@code EntityActor} and player
     * entities are excluded. Package-visible for headless parity tests.
     */
    static boolean shouldTrackEntity(Entity entity)
    {
        return !(entity instanceof EntityActor || entity instanceof PlayerEntity);
    }
}
