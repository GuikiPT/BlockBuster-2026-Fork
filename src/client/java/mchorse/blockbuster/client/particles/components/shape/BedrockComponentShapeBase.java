package mchorse.blockbuster.client.particles.components.shape;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mchorse.blockbuster.client.particles.components.BedrockComponentBase;
import mchorse.blockbuster.client.particles.components.IComponentParticleInitialize;
import mchorse.mclib.math.molang.MolangException;
import mchorse.mclib.math.molang.MolangParser;
import mchorse.mclib.math.molang.expressions.MolangExpression;

/**
 * Shared base for the {@code minecraft:emitter_shape_*} particle-initialize
 * components (roadmap P151).
 *
 * <p>Carries the common keys {@code offset} {@code [x,y,z]} (MoLang),
 * {@code direction} (the string {@code "inwards"}/{@code "outwards"} or a
 * 3-element MoLang vector — anything not {@code "inwards"} parses as outwards)
 * and {@code surface_only} (written only when true). Shapes run as
 * {@link IComponentParticleInitialize}s <b>before</b> {@code InitialSpeed}
 * (index 5) and {@code LocalSpace} (index 6), so they seed the spawn position
 * and the (unit) direction that InitialSpeed later scales.</p>
 */
public abstract class BedrockComponentShapeBase extends BedrockComponentBase implements IComponentParticleInitialize
{
    public MolangExpression[] offset = {MolangParser.ZERO, MolangParser.ZERO, MolangParser.ZERO};
    public ShapeDirection direction = ShapeDirection.OUTWARDS;
    public boolean surface = false;

    @Override
    public BedrockComponentBase fromJson(JsonElement elem, MolangParser parser) throws MolangException
    {
        if (!elem.isJsonObject()) return super.fromJson(elem, parser);

        JsonObject element = elem.getAsJsonObject();

        if (element.has("offset"))
        {
            JsonArray array = element.getAsJsonArray("offset");

            if (array.size() >= 3)
            {
                this.offset[0] = parser.parseJson(array.get(0));
                this.offset[1] = parser.parseJson(array.get(1));
                this.offset[2] = parser.parseJson(array.get(2));
            }
        }

        if (element.has("direction"))
        {
            JsonElement direction = element.get("direction");

            if (direction.isJsonPrimitive())
            {
                String name = direction.getAsString();

                if (name.equals("inwards")) this.direction = ShapeDirection.INWARDS;
                else this.direction = ShapeDirection.OUTWARDS;
            }
            else if (direction.isJsonArray())
            {
                JsonArray array = direction.getAsJsonArray();

                if (array.size() >= 3)
                {
                    this.direction = new ShapeDirection.Vector(
                        parser.parseJson(array.get(0)),
                        parser.parseJson(array.get(1)),
                        parser.parseJson(array.get(2))
                    );
                }
            }
        }

        if (element.has("surface_only"))
        {
            this.surface = element.get("surface_only").getAsBoolean();
        }

        return super.fromJson(element, parser);
    }

    @Override
    public JsonElement toJson()
    {
        JsonObject object = new JsonObject();
        JsonArray offset = new JsonArray();

        for (MolangExpression expression : this.offset)
        {
            offset.add(expression.toJson());
        }

        object.add("offset", offset);

        if (this.direction != ShapeDirection.OUTWARDS)
        {
            object.add("direction", this.direction.toJson());
        }

        if (this.surface)
        {
            object.addProperty("surface_only", true);
        }

        return object;
    }
}
