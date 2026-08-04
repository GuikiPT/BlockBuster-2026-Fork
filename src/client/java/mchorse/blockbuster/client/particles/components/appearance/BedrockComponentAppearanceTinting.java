package mchorse.blockbuster.client.particles.components.appearance;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mchorse.blockbuster.client.particles.BedrockSchemeJsonAdapter;
import mchorse.blockbuster.client.particles.components.BedrockComponentBase;
import mchorse.blockbuster.client.particles.components.IComponentParticleRender;
import mchorse.blockbuster.client.particles.emitter.BedrockEmitter;
import mchorse.blockbuster.client.particles.emitter.BedrockParticle;
import mchorse.mclib.math.molang.MolangException;
import mchorse.mclib.math.molang.MolangParser;
import net.minecraft.client.render.BufferBuilder;

/**
 * Tinting appearance component — {@code minecraft:particle_appearance_tinting}.
 *
 * <p>Ported verbatim from the 1.12.2 legacy engine. The {@code color} key accepts
 * a hex string, an RGB(A) MoLang array, or a gradient object (see {@link Tint}).
 * The tint is computed into the particle's {@code r/g/b/a} during the render pass
 * at <b>sortingIndex -10</b> (before the billboard) by delegating to
 * {@code renderOnScreen}; a null color resets to opaque white.</p>
 */
public class BedrockComponentAppearanceTinting extends BedrockComponentBase implements IComponentParticleRender
{
    public Tint color = new Tint.Solid();

    @Override
    public BedrockComponentBase fromJson(JsonElement elem, MolangParser parser) throws MolangException
    {
        if (!elem.isJsonObject()) return super.fromJson(elem, parser);

        JsonObject element = elem.getAsJsonObject();

        if (element.has("color"))
        {
            JsonElement color = element.get("color");

            if (color.isJsonArray() || color.isJsonPrimitive())
            {
                this.color = Tint.parseColor(color, parser);
            }
            else if (color.isJsonObject())
            {
                this.color = Tint.parseGradient(color.getAsJsonObject(), parser);
            }
        }

        return super.fromJson(element, parser);
    }

    @Override
    public JsonElement toJson()
    {
        JsonObject object = new JsonObject();
        JsonElement element = this.color.toJson();

        if (!BedrockSchemeJsonAdapter.isEmpty(element))
        {
            object.add("color", element);
        }

        return object;
    }

    /* Interface implementations */

    @Override
    public void preRender(BedrockEmitter emitter, float partialTicks)
    {}

    @Override
    public void render(BedrockEmitter emitter, BedrockParticle particle, BufferBuilder builder, float partialTicks)
    {
        this.renderOnScreen(particle, 0, 0, 0, 0);
    }

    @Override
    public void renderOnScreen(BedrockParticle particle, int x, int y, float scale, float partialTicks)
    {
        if (this.color != null)
        {
            this.color.compute(particle);
        }
        else
        {
            particle.r = particle.g = particle.b = particle.a = 1;
        }
    }

    @Override
    public void postRender(BedrockEmitter emitter, float partialTicks)
    {}

    @Override
    public int getSortingIndex()
    {
        return -10;
    }
}
