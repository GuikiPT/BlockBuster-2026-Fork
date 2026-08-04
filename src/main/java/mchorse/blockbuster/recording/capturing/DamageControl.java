package mchorse.blockbuster.recording.capturing;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Damage control — port of legacy
 * {@code recording.capturing.DamageControl} (roadmap P113).
 *
 * <p>This class is responsible for storing damaged blocks and be able to
 * restore them in the world (the world-edit rollback around recording and
 * playback).</p>
 *
 * <p>1.20.4 mappings: {@code EntityLivingBase} → {@link LivingEntity};
 * {@code IBlockState} → {@link BlockState}; {@code TileEntity} →
 * {@link BlockEntity} (snapshot via
 * {@code BlockEntity.createNbtWithIdentifyingData()}, restore via
 * {@code BlockEntity.createFromNbt(pos, state, nbt)} +
 * {@code World.addBlockEntity}); {@code Entity.setDead()} →
 * {@code Entity.discard()}.</p>
 */
public class DamageControl
{
    public List<BlockEntry> blocks = new ArrayList<BlockEntry>();
    public List<Entity> entities = new ArrayList<Entity>();
    public LivingEntity target;

    public int maxDistance;

    public DamageControl(LivingEntity target, int maxDistance)
    {
        this.target = target;
        this.maxDistance = maxDistance;
    }

    /**
     * Add a block to damage control repository
     *
     * <p>This method is responsible for adding only these blocks which are in
     * the radius of allowed {@link #maxDistance} range. Max distance gets set
     * from the config property
     * {@link mchorse.blockbuster.Blockbuster#damageControlDistance}.</p>
     *
     * <p>Quirks (load-bearing): the distance check is <b>per-axis</b>
     * ({@code |dx| > d || |dy| > d || |dz| > d}), not euclidean; and the
     * per-position dedup is first-writer-wins — only the oldest state at a
     * position is kept, so restoring returns the world to its pre-recording
     * state even after multiple edits. The {@code world} argument is accepted
     * for signature parity with the legacy feed but unused here.</p>
     */
    public void addBlock(BlockPos pos, BlockState state, World world)
    {
        double x = Math.abs(this.target.getX() - pos.getX());
        double y = Math.abs(this.target.getY() - pos.getY());
        double z = Math.abs(this.target.getZ() - pos.getZ());

        if (x > this.maxDistance || y > this.maxDistance || z > this.maxDistance)
        {
            return;
        }

        for (BlockEntry entry : this.blocks)
        {
            if (entry.pos.getX() == pos.getX() && entry.pos.getY() == pos.getY() && entry.pos.getZ() == pos.getZ())
            {
                return;
            }
        }

        this.blocks.add(new BlockEntry(pos, state, ActionHandler.lastTE));
    }

    /**
     * Apply recorded damaged blocks back in the world
     */
    public void apply(World world)
    {
        for (BlockEntry entry : this.blocks)
        {
            world.setBlockState(entry.pos, entry.state);

            if (entry.te != null)
            {
                BlockEntity be = BlockEntity.createFromNbt(entry.pos, entry.state, entry.te);

                if (be != null)
                {
                    world.addBlockEntity(be);
                }
            }
        }

        for (Entity entity : this.entities)
        {
            entity.discard();
        }

        this.blocks.clear();
        this.entities.clear();
    }

    /**
     * Block entry in the damage control class
     *
     * <p>This class holds information about a destroyed block, such as its
     * state (and, when present, its block-entity NBT).</p>
     */
    public static class BlockEntry
    {
        public BlockPos pos;
        public BlockState state;
        public NbtCompound te;

        public BlockEntry(BlockPos pos, BlockState state, BlockEntity te)
        {
            this.pos = pos;
            this.state = state;

            if (te != null)
            {
                this.te = te.createNbtWithIdentifyingData();
            }
        }
    }
}
