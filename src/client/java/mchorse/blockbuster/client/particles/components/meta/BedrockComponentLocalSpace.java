package mchorse.blockbuster.client.particles.components.meta;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mchorse.blockbuster.client.particles.components.BedrockComponentBase;
import mchorse.blockbuster.client.particles.components.IComponentParticleInitialize;
import mchorse.blockbuster.client.particles.emitter.BedrockEmitter;
import mchorse.blockbuster.client.particles.emitter.BedrockParticle;
import mchorse.mclib.math.molang.MolangException;
import mchorse.mclib.math.molang.MolangParser;

/**
 * {@code minecraft:emitter_local_space} — roadmap P150.
 *
 * <p>Copies inheritance flags onto the particle's relative-* fields at init.
 * Bedrock keys {@code position}/{@code rotation} (bools) plus the Blockbuster
 * extensions {@code scale}/{@code scale_billboard}/{@code direction}/
 * {@code acceleration}/{@code gravity} (bools) and {@code linear_velocity}/
 * {@code angular_velocity} (floats — inheritance <b>multipliers</b>, not bools).
 * Only true / nonzero keys are serialized. {@link #getSortingIndex()} is
 * {@code 6}.</p>
 *
 * <p>The Blockbuster extension keys (and the float velocity multipliers) are not
 * known to any Bedrock tool but must round-trip.</p>
 */
public class BedrockComponentLocalSpace extends BedrockComponentBase implements IComponentParticleInitialize
{
    public boolean position;
    public boolean rotation;
    public boolean scale;
    public boolean scaleBillboard;
    public boolean direction;
    public boolean acceleration;
    public boolean gravity;
    public float linearVelocity;
    public float angularVelocity;

    public BedrockComponentBase fromJson(JsonElement elem, MolangParser parser) throws MolangException
    {
        if (!elem.isJsonObject()) return super.fromJson(elem, parser);

        JsonObject element = elem.getAsJsonObject();

        if (element.has("position")) this.position = element.get("position").getAsBoolean();
        if (element.has("rotation")) this.rotation = element.get("rotation").getAsBoolean();
        if (element.has("scale")) this.scale = element.get("scale").getAsBoolean();
        if (element.has("scale_billboard")) this.scaleBillboard = element.get("scale_billboard").getAsBoolean();
        if (element.has("direction")) this.direction = element.get("direction").getAsBoolean();
        if (element.has("acceleration")) this.acceleration = element.get("acceleration").getAsBoolean();
        if (element.has("gravity")) this.gravity = element.get("gravity").getAsBoolean();
        if (element.has("linear_velocity")) this.linearVelocity = element.get("linear_velocity").getAsFloat();
        if (element.has("angular_velocity")) this.angularVelocity = element.get("angular_velocity").getAsFloat();

        return super.fromJson(element, parser);
    }

    @Override
    public JsonElement toJson()
    {
        JsonObject object = new JsonObject();

        if (this.position) object.addProperty("position", true);
        if (this.rotation) object.addProperty("rotation", true);
        if (this.scale) object.addProperty("scale", true);
        if (this.scaleBillboard) object.addProperty("scale_billboard", true);
        if (this.direction) object.addProperty("direction", true);
        if (this.acceleration) object.addProperty("acceleration", true);
        if (this.gravity) object.addProperty("gravity", true);
        if (this.linearVelocity != 0) object.addProperty("linear_velocity", this.linearVelocity);
        if (this.angularVelocity != 0) object.addProperty("angular_velocity", this.angularVelocity);

        return object;
    }

    @Override
    public void apply(BedrockEmitter emitter, BedrockParticle particle)
    {
        particle.relativePosition = this.position;
        particle.relativeRotation = this.rotation;
        particle.relativeScale = this.scale;
        particle.relativeScaleBillboard = this.scaleBillboard;
        particle.relativeDirection = this.direction;
        particle.relativeAcceleration = this.acceleration;
        particle.gravity = this.gravity;
        particle.linearVelocity = this.linearVelocity;
        particle.angularVelocity = this.angularVelocity;

        particle.setupMatrix(emitter);
    }

    @Override
    public int getSortingIndex()
    {
        return 6;
    }
}
