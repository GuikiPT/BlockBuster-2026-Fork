package mchorse.mclib.network;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

/**
 * Minimal DTO replacement for Forge 1.12.2's
 * {@code NetworkRegistry.TargetPoint} (roadmap P23), consumed by
 * {@link AbstractDispatcher#sendToAllAround}. The legacy class carried
 * {@code (int dimension, double x, y, z, double range)}; the pre-flattening
 * integer dimension id becomes a {@code RegistryKey<World>} — ported call
 * sites construct it from the entity's world (a mechanical change recorded in
 * the port notes; there is no stable int↔dimension mapping post-flattening).
 */
public class TargetPoint
{
    public final RegistryKey<World> dimension;
    public final double x;
    public final double y;
    public final double z;
    public final double range;

    public TargetPoint(RegistryKey<World> dimension, double x, double y, double z, double range)
    {
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.range = range;
    }

    /**
     * Convenience constructor for the common "around this entity's position"
     * call-site shape.
     */
    public TargetPoint(ServerWorld world, double x, double y, double z, double range)
    {
        this(world.getRegistryKey(), x, y, z, range);
    }
}
