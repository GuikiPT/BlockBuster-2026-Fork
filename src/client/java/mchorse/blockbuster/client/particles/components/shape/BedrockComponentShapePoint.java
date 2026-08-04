package mchorse.blockbuster.client.particles.components.shape;

import mchorse.blockbuster.client.particles.emitter.BedrockEmitter;
import mchorse.blockbuster.client.particles.emitter.BedrockParticle;

/**
 * {@code minecraft:emitter_shape_point} (roadmap P151).
 *
 * <p>Spawns particles at {@code position = offset}. Deliberately <b>skips</b> the
 * inwards/outwards preset directions (the offset-from-center would be zero-length
 * at a point), but a custom {@link ShapeDirection.Vector} still applies.</p>
 */
public class BedrockComponentShapePoint extends BedrockComponentShapeBase
{
    @Override
    public void apply(BedrockEmitter emitter, BedrockParticle particle)
    {
        particle.position.x = (float) this.offset[0].get();
        particle.position.y = (float) this.offset[1].get();
        particle.position.z = (float) this.offset[2].get();

        if (this.direction instanceof ShapeDirection.Vector)
        {
            this.direction.applyDirection(particle, particle.position.x, particle.position.y, particle.position.z);
        }
    }
}
