package mchorse.blockbuster.client.particles.components.expiration;

import mchorse.blockbuster.client.particles.components.IComponentParticleUpdate;
import mchorse.blockbuster.client.particles.emitter.BedrockEmitter;
import mchorse.blockbuster.client.particles.emitter.BedrockParticle;
import net.minecraft.block.Block;

/**
 * {@code minecraft:particle_expire_if_in_blocks} — roadmap P150.
 *
 * <p>Kills the particle when the block at its global position is one of the
 * listed blocks (identity comparison, no blockstate).</p>
 */
public class BedrockComponentExpireInBlocks extends BedrockComponentExpireBlocks implements IComponentParticleUpdate
{
    @Override
    public void update(BedrockEmitter emitter, BedrockParticle particle)
    {
        if (particle.dead || emitter.world == null)
        {
            return;
        }

        Block current = this.getBlock(emitter, particle);

        for (Block block : this.blocks)
        {
            if (block == current)
            {
                particle.dead = true;

                return;
            }
        }
    }
}
