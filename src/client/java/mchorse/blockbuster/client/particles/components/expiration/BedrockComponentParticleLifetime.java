package mchorse.blockbuster.client.particles.components.expiration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import mchorse.blockbuster.client.particles.components.BedrockComponentBase;
import mchorse.blockbuster.client.particles.components.IComponentParticleInitialize;
import mchorse.blockbuster.client.particles.components.IComponentParticleUpdate;
import mchorse.blockbuster.client.particles.emitter.BedrockEmitter;
import mchorse.blockbuster.client.particles.emitter.BedrockParticle;
import mchorse.mclib.math.molang.MolangException;
import mchorse.mclib.math.molang.MolangParser;
import mchorse.mclib.math.molang.expressions.MolangExpression;

/**
 * {@code minecraft:particle_lifetime_expression} — roadmap P150.
 *
 * <p>Two mutually-exclusive modes: {@code max_lifetime} (seconds, converted to a
 * tick lifetime at init: {@code lifetime = value * 20}) or
 * {@code expiration_expression} ({@code lifetime = -1}, killed when the
 * expression evaluates nonzero each update). Writes back exactly one key chosen
 * by the {@code max} flag.</p>
 *
 * <p><b>Total-reader exception:</b> missing <em>both</em> keys throws
 * {@link JsonParseException} — one of the few hard parse failures, kept for
 * 1.12.2 parity.</p>
 */
public class BedrockComponentParticleLifetime extends BedrockComponentBase implements IComponentParticleInitialize, IComponentParticleUpdate
{
    public MolangExpression expression = MolangParser.ZERO;
    public boolean max;

    @Override
    public BedrockComponentBase fromJson(JsonElement elem, MolangParser parser) throws MolangException
    {
        if (!elem.isJsonObject()) return super.fromJson(elem, parser);

        JsonObject element = elem.getAsJsonObject();
        JsonElement expression = null;

        if (element.has("expiration_expression"))
        {
            expression = element.get("expiration_expression");
            this.max = false;
        }
        else if (element.has("max_lifetime"))
        {
            expression = element.get("max_lifetime");
            this.max = true;
        }
        else
        {
            throw new JsonParseException("No expiration_expression or max_lifetime was found in minecraft:particle_lifetime_expression component");
        }

        this.expression = parser.parseJson(expression);

        return super.fromJson(element, parser);
    }

    @Override
    public JsonElement toJson()
    {
        JsonObject object = new JsonObject();

        object.add(this.max ? "max_lifetime" : "expiration_expression", this.expression.toJson());

        return object;
    }

    @Override
    public void update(BedrockEmitter emitter, BedrockParticle particle)
    {
        if (!this.max && this.expression.get() != 0)
        {
            particle.dead = true;
        }
    }

    @Override
    public void apply(BedrockEmitter emitter, BedrockParticle particle)
    {
        if (this.max)
        {
            particle.lifetime = (int) (this.expression.get() * 20);
        }
        else
        {
            particle.lifetime = -1;
        }
    }
}
