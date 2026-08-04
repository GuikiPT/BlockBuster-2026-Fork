package mchorse.metamorph.capabilities.render;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import mchorse.mclib.utils.NBTUtils;

import java.lang.reflect.Type;

/**
 * Entity selector JSON adapter (roadmap P54.1).
 *
 * <p>The JSON has flat {@code name}/{@code type}/{@code enabled} fields plus
 * {@code match}/{@code morph} stored as <b>stringified NBT (Mojangson) inside
 * JSON strings</b>. Legacy parsed the strings with {@code
 * JsonToNBT.getTagFromJson} and swallowed parse errors; the modern port routes
 * through {@link NBTUtils#parseSnbtCompound(String)} (total: returns null on a
 * malformed string, which disables the selector's match/morph, never crashes).
 * Serialization uses {@link net.minecraft.nbt.NbtCompound#toString()} to emit
 * the SNBT — the same shape 1.12.2 wrote.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/capabilities/render/EntitySelectorAdapter.java
 */
public class EntitySelectorAdapter implements JsonDeserializer<EntitySelector>, JsonSerializer<EntitySelector>
{
    @Override
    public EntitySelector deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException
    {
        if (!json.isJsonObject())
        {
            return null;
        }

        EntitySelector selector = new EntitySelector();
        JsonObject object = json.getAsJsonObject();

        if (object.has("name"))
        {
            selector.name = object.get("name").getAsString();
        }

        if (object.has("type"))
        {
            selector.type = object.get("type").getAsString();
        }

        if (object.has("enabled"))
        {
            selector.enabled = object.get("enabled").getAsBoolean();
        }

        if (object.has("match"))
        {
            selector.match = NBTUtils.parseSnbtCompound(object.get("match").getAsString());
        }

        if (object.has("morph"))
        {
            selector.morph = NBTUtils.parseSnbtCompound(object.get("morph").getAsString());
        }

        return selector;
    }

    @Override
    public JsonElement serialize(EntitySelector src, Type typeOfSrc, JsonSerializationContext context)
    {
        JsonObject object = new JsonObject();

        object.addProperty("name", src.name);
        object.addProperty("type", src.type);
        object.addProperty("enabled", src.enabled);

        if (src.match != null && !src.match.isEmpty())
        {
            object.addProperty("match", src.match.toString());
        }

        if (src.morph != null)
        {
            object.addProperty("morph", src.morph.toString());
        }

        return object;
    }
}
