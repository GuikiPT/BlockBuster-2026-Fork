package mchorse.blockbuster.client.particles.components.lifetime;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mchorse.blockbuster.client.particles.components.BedrockComponentBase;
import mchorse.blockbuster.client.particles.components.IComponentEmitterUpdate;
import mchorse.mclib.math.Constant;
import mchorse.mclib.math.molang.MolangException;
import mchorse.mclib.math.molang.MolangParser;
import mchorse.mclib.math.molang.expressions.MolangExpression;
import mchorse.mclib.math.molang.expressions.MolangValue;

/**
 * Emitter-lifetime component base (roadmap P150).
 *
 * <p>Ported verbatim from the 1.12.2 legacy engine. Holds the shared
 * {@code active_time} MoLang value (default constant {@code 10}, omitted from
 * JSON when still {@code 10}) and drives the emitter lifetime in ticks
 * ({@code active * 20}). {@link #getSortingIndex()} is {@code -10} so lifetime
 * evaluation runs before every other emitter-update component (wire contract).</p>
 */
public abstract class BedrockComponentLifetime extends BedrockComponentBase implements IComponentEmitterUpdate
{
    public static final MolangExpression DEFAULT_ACTIVE = new MolangValue(null, new Constant(10));

    public MolangExpression activeTime = DEFAULT_ACTIVE;

    public BedrockComponentBase fromJson(JsonElement elem, MolangParser parser) throws MolangException
    {
        if (!elem.isJsonObject())
        {
            return super.fromJson(elem, parser);
        }

        JsonObject element = elem.getAsJsonObject();

        if (element.has(this.getPropertyName()))
        {
            this.activeTime = parser.parseJson(element.get(this.getPropertyName()));
        }

        return super.fromJson(element, parser);
    }

    @Override
    public JsonElement toJson()
    {
        JsonObject object = new JsonObject();

        if (!MolangExpression.isConstant(this.activeTime, 10))
        {
            object.add(this.getPropertyName(), this.activeTime.toJson());
        }

        return object;
    }

    protected String getPropertyName()
    {
        return "active_time";
    }

    @Override
    public int getSortingIndex()
    {
        return -10;
    }
}
