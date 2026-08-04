package mchorse.blockbuster.client.particles.components.motion;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mchorse.blockbuster.client.particles.components.BedrockComponentBase;
import mchorse.blockbuster.client.particles.components.IComponentParticleInitialize;
import mchorse.blockbuster.client.particles.emitter.BedrockEmitter;
import mchorse.blockbuster.client.particles.emitter.BedrockParticle;
import mchorse.mclib.math.molang.MolangException;
import mchorse.mclib.math.molang.MolangParser;
import mchorse.mclib.math.molang.expressions.MolangExpression;

/**
 * {@code minecraft:particle_initial_spin} (roadmap P151).
 *
 * <p>Keys {@code rotation} (initial angle in degrees) and {@code rotation_rate}.
 * The rate is divided by {@code 20} at apply time to convert deg/s to deg/tick
 * (this {@code /20} is at <b>init</b> — distinct from the dynamic component's
 * {@code /20} which happens at <b>accumulate</b>; do not unify). Zero expressions
 * are omitted from JSON.</p>
 */
public class BedrockComponentInitialSpin extends BedrockComponentBase implements IComponentParticleInitialize
{
    public MolangExpression rotation = MolangParser.ZERO;
    public MolangExpression rate = MolangParser.ZERO;

    @Override
    public BedrockComponentBase fromJson(JsonElement elem, MolangParser parser) throws MolangException
    {
        if (!elem.isJsonObject()) return super.fromJson(elem, parser);

        JsonObject element = elem.getAsJsonObject();

        if (element.has("rotation")) this.rotation = parser.parseJson(element.get("rotation"));
        if (element.has("rotation_rate")) this.rate = parser.parseJson(element.get("rotation_rate"));

        return super.fromJson(element, parser);
    }

    @Override
    public JsonElement toJson()
    {
        JsonObject object = new JsonObject();

        if (!MolangExpression.isZero(this.rotation)) object.add("rotation", this.rotation.toJson());
        if (!MolangExpression.isZero(this.rate)) object.add("rotation_rate", this.rate.toJson());

        return object;
    }

    @Override
    public void apply(BedrockEmitter emitter, BedrockParticle particle)
    {
        particle.initialRotation = (float) this.rotation.get();
        particle.rotationVelocity = (float) this.rate.get() / 20;
    }
}
