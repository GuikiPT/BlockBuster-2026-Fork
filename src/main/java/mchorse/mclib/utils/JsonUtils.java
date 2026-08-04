package mchorse.mclib.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.stream.JsonWriter;

import java.io.StringWriter;

/**
 * Full port of McLib 2.4.3's JsonUtils (roadmap P14).
 *
 * Legacy source: .tools/legacy-src/mclib/src/main/java/mchorse/mclib/utils/JsonUtils.java
 */
public class JsonUtils
{
    public static String jsonToPretty(JsonElement element)
    {
        StringWriter writer = new StringWriter();
        JsonWriter jsonWriter = new JsonWriter(writer);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        jsonWriter.setIndent("    ");
        gson.toJson(element, jsonWriter);

        /* Prettify arrays */
        return writer.toString();
    }
}
