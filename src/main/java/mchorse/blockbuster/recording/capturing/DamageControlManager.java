package mchorse.blockbuster.recording.capturing;

import java.util.HashMap;
import java.util.Map;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.CommonProxy;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Damage control manager — port of legacy
 * {@code recording.capturing.DamageControlManager} (roadmap P113).
 *
 * <p>This person is responsible for managing damage control. Owner keys are
 * arbitrary {@link Object}s — {@link mchorse.blockbuster.recording.RecordRecorder}
 * instances (P109) and scene/playback objects (S11) share the same map, so
 * {@link #restoreDamageControl(Object, World)} with an unknown key is a silent
 * no-op.</p>
 */
public class DamageControlManager
{
    /**
     * Damage control objects
     */
    public Map<Object, DamageControl> damage = new HashMap<Object, DamageControl>();

    public void reset()
    {
        this.damage.clear();
    }

    /**
     * Start observing damage made to terrain
     */
    public void addDamageControl(Object object, LivingEntity player)
    {
        if (Blockbuster.damageControl.get())
        {
            int dist = Blockbuster.damageControlDistance.get();

            this.damage.put(object, new DamageControl(player, dist));
        }
    }

    /**
     * Restore made damage
     */
    public void restoreDamageControl(Object object, World world)
    {
        DamageControl control = this.damage.remove(object);

        if (control != null)
        {
            control.apply(world);
        }
    }

    /**
     * Add an entity to track
     */
    public void addEntity(Entity entity)
    {
        for (DamageControl damage : this.damage.values())
        {
            damage.entities.add(entity);
        }
    }

    /**
     * Add a block to track
     *
     * <p>Legacy quirk (kept for diff-ability): this iterates the singleton
     * {@code CommonProxy.damage.damage.values()} rather than
     * {@code this.damage.values()} — equivalent since there is only ever the
     * one {@link CommonProxy#damage} instance.</p>
     */
    public void addBlock(BlockPos pos, BlockState oldState, World worldIn)
    {
        for (DamageControl damage : CommonProxy.damage.damage.values())
        {
            damage.addBlock(pos.toImmutable(), oldState, worldIn);
        }
    }
}
