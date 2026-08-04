package mchorse.blockbuster.client.particles.components.appearance;

import mchorse.blockbuster.client.particles.components.BedrockComponentBase;
import mchorse.blockbuster.client.particles.components.IComponentEmitterInitialize;
import mchorse.blockbuster.client.particles.emitter.BedrockEmitter;

/**
 * Lighting appearance component — {@code minecraft:particle_appearance_lighting}.
 *
 * <p>Ported verbatim from the 1.12.2 legacy engine. The component has <b>no
 * fields</b>: its mere presence flips {@code emitter.lit = false} so particles use
 * world lighting instead of fullbright. {@code canBeEmpty()} is {@code true} so the
 * empty object survives serialization (the editor toggle adds/removes the whole
 * component rather than a flag).</p>
 */
public class BedrockComponentAppearanceLighting extends BedrockComponentBase implements IComponentEmitterInitialize
{
    @Override
    public void apply(BedrockEmitter emitter)
    {
        emitter.lit = false;
    }

    @Override
    public boolean canBeEmpty()
    {
        return true;
    }
}
