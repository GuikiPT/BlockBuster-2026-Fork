package mchorse.blockbuster.client.particles.components.appearance;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mchorse.blockbuster.client.particles.components.BedrockComponentBase;
import mchorse.blockbuster.client.particles.components.IComponentParticleRender;
import mchorse.blockbuster.client.particles.emitter.BedrockEmitter;
import mchorse.blockbuster.client.particles.emitter.BedrockParticle;
import mchorse.mclib.math.molang.MolangException;
import mchorse.mclib.math.molang.MolangParser;
import mchorse.mclib.math.molang.expressions.MolangExpression;
import net.minecraft.client.render.BufferBuilder;

import java.util.Map;
import java.util.Set;

/**
 * Collision tinting component — {@code blockbuster:particle_collision_tinting}.
 *
 * <p>Ported verbatim from the 1.12.2 legacy engine. It applies its alternate tint
 * only when {@code particle.isCollisionTinting()} (its {@code enabled} MoLang is 1
 * <b>and</b> the particle has intersected). It runs at <b>sortingIndex -5</b> so it
 * overrides normal tinting (-10) by running after it. Its {@code enabled} defaults
 * to <b>ZERO</b> (off, unlike motion collision's ONE). On serialize it merges the
 * super {@link BedrockComponentAppearanceTinting} entries into its own object.</p>
 */
public class BedrockComponentCollisionTinting extends BedrockComponentAppearanceTinting implements IComponentParticleRender
{
    public MolangExpression enabled = MolangParser.ZERO;

    @Override
    public BedrockComponentBase fromJson(JsonElement elem, MolangParser parser) throws MolangException
    {
        if (!elem.isJsonObject()) return super.fromJson(elem, parser);

        JsonObject element = elem.getAsJsonObject();

        if (element.has("enabled")) this.enabled = parser.parseJson(element.get("enabled"));

        return super.fromJson(element, parser);
    }

    @Override
    public JsonElement toJson()
    {
        JsonObject object = new JsonObject();

        object.add("enabled", this.enabled.toJson());

        /* add the default stuff from super */
        JsonObject superJson = (JsonObject) super.toJson();
        Set<Map.Entry<String, JsonElement>> entries = superJson.entrySet();

        for (Map.Entry<String, JsonElement> entry : entries)
        {
            object.add(entry.getKey(), entry.getValue());
        }

        return object;
    }

    @Override
    public void render(BedrockEmitter emitter, BedrockParticle particle, BufferBuilder builder, float partialTicks)
    {
        if (particle.isCollisionTinting(emitter))
        {
            this.renderOnScreen(particle, 0, 0, 0, 0);
        }
    }

    @Override
    public int getSortingIndex()
    {
        return -5;
    }
}
