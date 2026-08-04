package mchorse.blockbuster.client.particles.components.shape;

import mchorse.blockbuster.client.particles.emitter.BedrockEmitter;
import mchorse.blockbuster.client.particles.emitter.BedrockParticle;
import mchorse.blockbuster.client.particles.emitter.RandomProvider;

/**
 * {@code minecraft:emitter_shape_entity_aabb} (roadmap P151).
 *
 * <p>Spawns particles inside the emitter target's bounding box: width/height/width
 * (depth = width) sized from {@code emitter.target}, or zero when there is no
 * target. Unlike the box shape (which uses full half-dimensions), the entity-AABB
 * <b>halves</b> the entity dimensions for both the interior spread and the
 * {@code surface_only} face snap.</p>
 *
 * <p>Yarn: 1.12's public {@code entity.width}/{@code entity.height} fields map to
 * {@link net.minecraft.entity.Entity#getWidth()}/{@link net.minecraft.entity.Entity#getHeight()}.</p>
 */
public class BedrockComponentShapeEntityAABB extends BedrockComponentShapeBase
{
    @Override
    public void apply(BedrockEmitter emitter, BedrockParticle particle)
    {
        float centerX = (float) this.offset[0].get();
        float centerY = (float) this.offset[1].get();
        float centerZ = (float) this.offset[2].get();

        float w = 0;
        float h = 0;
        float d = 0;

        if (emitter.target != null)
        {
            w = emitter.target.getWidth();
            h = emitter.target.getHeight();
            d = emitter.target.getWidth();
        }

        particle.position.x = centerX + ((float) RandomProvider.nextDouble() - 0.5F) * w;
        particle.position.y = centerY + ((float) RandomProvider.nextDouble() - 0.5F) * h;
        particle.position.z = centerZ + ((float) RandomProvider.nextDouble() - 0.5F) * d;

        if (this.surface)
        {
            int roll = (int) (RandomProvider.nextDouble() * 6 * 100) % 6;

            if (roll == 0) particle.position.x = centerX + w / 2F;
            else if (roll == 1) particle.position.x = centerX - w / 2F;
            else if (roll == 2) particle.position.y = centerY + h / 2F;
            else if (roll == 3) particle.position.y = centerY - h / 2F;
            else if (roll == 4) particle.position.z = centerZ + d / 2F;
            else if (roll == 5) particle.position.z = centerZ - d / 2F;
        }

        this.direction.applyDirection(particle, centerX, centerY, centerZ);
    }
}
