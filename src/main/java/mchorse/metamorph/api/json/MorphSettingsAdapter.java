package mchorse.metamorph.api.json;

import java.lang.reflect.Type;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.MorphSettings;
import mchorse.metamorph.api.abilities.IAbility;

/**
 * Morph settings JSON adapter (roadmap P49).
 *
 * <p>Deserializes a {@link MorphSettings} entry from {@code morphs.json};
 * every {@code hasX} flag is set from {@code object.has(key) &&
 * isJsonPrimitive()/isJsonArray()}. JSON keys: {@code health}, {@code speed},
 * {@code hostile}, {@code hands}, {@code abilities} (array), {@code action},
 * {@code attack}, {@code updates}, {@code shadow_option}.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/api/json/MorphSettingsAdapter.java
 */
public class MorphSettingsAdapter implements JsonDeserializer<MorphSettings>
{
    @Override
    public MorphSettings deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException
    {
        JsonObject object = json.getAsJsonObject();

        MorphSettings morph = new MorphSettings();
        MorphManager manager = MorphManager.INSTANCE;

        morph.hasHealth = object.has("health") && object.get("health").isJsonPrimitive();

        if (morph.hasHealth)
        {
            morph.health = object.get("health").getAsInt();
        }

        morph.hasSpeed = object.has("speed") && object.get("speed").isJsonPrimitive();

        if (morph.hasSpeed)
        {
            morph.speed = object.get("speed").getAsFloat();
        }

        morph.hasHostile = object.has("hostile") && object.get("hostile").isJsonPrimitive();

        if (morph.hasHostile)
        {
            morph.hostile = object.get("hostile").getAsBoolean();
        }

        morph.hasHands = object.has("hands") && object.get("hands").isJsonPrimitive();

        if (morph.hasHands)
        {
            morph.hands = object.get("hands").getAsBoolean();
        }

        morph.hasAbilities = object.has("abilities") && object.get("abilities").isJsonArray();

        if (morph.hasAbilities)
        {
            morph.abilities.clear();

            for (JsonElement ability : object.get("abilities").getAsJsonArray())
            {
                if (!ability.isJsonPrimitive())
                {
                    continue;
                }

                IAbility iability = manager.abilities.get(ability.getAsString());

                if (iability != null)
                {
                    morph.abilities.add(iability);
                }
            }
        }

        morph.hasAction = object.has("action") && object.get("action").isJsonPrimitive();

        if (morph.hasAction)
        {
            morph.action = manager.actions.get(object.get("action").getAsString());
        }

        morph.hasAttack = object.has("attack") && object.get("attack").isJsonPrimitive();

        if (morph.hasAttack)
        {
            morph.attack = manager.attacks.get(object.get("attack").getAsString());
        }

        morph.hasUpdates = object.has("updates") && object.get("updates").isJsonPrimitive();

        if (morph.hasUpdates)
        {
            morph.updates = object.get("updates").getAsBoolean();
        }

        morph.hasShadowOption = object.has("shadow_option") && object.get("shadow_option").isJsonPrimitive();

        if (morph.hasShadowOption)
        {
            morph.shadowOption = object.get("shadow_option").getAsInt();
        }

        return morph;
    }
}
