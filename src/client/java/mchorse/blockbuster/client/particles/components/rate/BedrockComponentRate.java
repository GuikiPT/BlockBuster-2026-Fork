package mchorse.blockbuster.client.particles.components.rate;

import mchorse.blockbuster.client.particles.components.BedrockComponentBase;
import mchorse.mclib.math.molang.expressions.MolangExpression;

/**
 * Emitter-rate component base (roadmap P150).
 *
 * <p>Ported verbatim from the 1.12.2 legacy engine
 * ({@code mchorse.blockbuster.client.particles.components.rate.BedrockComponentRate}).
 * Carries the shared {@code particles} MoLang count that the two concrete rate
 * modes ({@link BedrockComponentRateInstant} / {@link BedrockComponentRateSteady})
 * interpret differently.</p>
 */
public abstract class BedrockComponentRate extends BedrockComponentBase
{
    public MolangExpression particles;
}
