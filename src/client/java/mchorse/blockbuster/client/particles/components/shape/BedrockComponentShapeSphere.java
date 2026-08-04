package mchorse.blockbuster.client.particles.components.shape;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mchorse.blockbuster.client.particles.components.BedrockComponentBase;
import mchorse.blockbuster.client.particles.emitter.BedrockEmitter;
import mchorse.blockbuster.client.particles.emitter.BedrockParticle;
import mchorse.blockbuster.client.particles.emitter.RandomProvider;
import mchorse.mclib.math.molang.MolangException;
import mchorse.mclib.math.molang.MolangParser;
import mchorse.mclib.math.molang.expressions.MolangExpression;

import javax.vecmath.Vector3f;

/**
 * {@code minecraft:emitter_shape_sphere} (roadmap P151).
 *
 * <p>Spawns particles on/in a sphere of {@code radius} centered on {@code offset}
 * along a random normalized direction. The direction is scaled by the radius,
 * additionally times a random factor unless {@code surface_only} (which pins the
 * particle to the shell).</p>
 */
public class BedrockComponentShapeSphere extends BedrockComponentShapeBase
{
    public MolangExpression radius = MolangParser.ZERO;

    @Override
    public BedrockComponentBase fromJson(JsonElement elem, MolangParser parser) throws MolangException
    {
        if (!elem.isJsonObject()) return super.fromJson(elem, parser);

        JsonObject element = elem.getAsJsonObject();

        if (element.has("radius")) this.radius = parser.parseJson(element.get("radius"));

        return super.fromJson(element, parser);
    }

    @Override
    public JsonElement toJson()
    {
        JsonObject object = (JsonObject) super.toJson();

        if (!MolangExpression.isZero(this.radius)) object.add("radius", this.radius.toJson());

        return object;
    }

    @Override
    public void apply(BedrockEmitter emitter, BedrockParticle particle)
    {
        float centerX = (float) this.offset[0].get();
        float centerY = (float) this.offset[1].get();
        float centerZ = (float) this.offset[2].get();
        float radius = (float) this.radius.get();

        Vector3f direction = new Vector3f((float) RandomProvider.nextDouble() * 2 - 1, (float) RandomProvider.nextDouble() * 2 - 1, (float) RandomProvider.nextDouble() * 2 - 1);
        direction.normalize();

        if (!this.surface)
        {
            radius *= RandomProvider.nextDouble();
        }

        direction.scale(radius);

        particle.position.x = centerX + direction.x;
        particle.position.y = centerY + direction.y;
        particle.position.z = centerZ + direction.z;

        this.direction.applyDirection(particle, centerX, centerY, centerZ);
    }
}
