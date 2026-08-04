package mchorse.mclib.config.json;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mchorse.mclib.McLib;
import mchorse.mclib.config.Config;
import mchorse.mclib.config.values.Value;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

/**
 * Port of McLib 2.4.3's {@code config/json/ConfigParser.java} (roadmap P20).
 *
 * <p>Total reader: a missing file triggers an <b>immediate</b> default save and
 * returns false; parse errors are swallowed (defaults stay in memory and the
 * broken file is only overwritten on the next save — never at load); a category
 * the file does not carry keeps its defaults instead of aborting the load (see
 * the note in {@link #fromJson} — the one deliberate divergence from 2.4.3).</p>
 */
public class ConfigParser
{
    public static JsonObject toJson(Config config)
    {
        JsonObject object = new JsonObject();

        for (Map.Entry<String, Value> entry : config.values.entrySet())
        {
            object.add(entry.getKey(), entry.getValue().toJSON());
        }

        return object;
    }

    public static boolean fromJson(Config config, File file)
    {
        if (!file.exists())
        {
            /* Nothing to lose — write the defaults out as the initial file. */
            config.unreadable = false;
            config.save(file);

            return false;
        }

        try
        {
            JsonObject object = (JsonObject) JsonParser.parseString(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));

            for (Map.Entry<String, Value> entry : config.values.entrySet())
            {
                JsonObject category = object.getAsJsonObject(entry.getKey());

                /* S22/P250 — total reader, deliberate divergence from McLib
                 * 2.4.3. Legacy passed this null straight into
                 * Value.fromJSON, which dereferenced it: a category present in
                 * the registry but absent from the file threw out of the loop,
                 * so EVERY category registered after the missing one silently
                 * kept its defaults and fromJson returned false. That is the
                 * exact shape of a config-file migration — a 2.7.2
                 * config/blockbuster/config.json has no "video" object — and it
                 * would have made adding any category a data-loss bug. Skipping
                 * leaves the category at its defaults, which is what the file
                 * is saying. Observably identical for a complete file, which is
                 * the only case 1.12.2 could produce. */
                if (category == null)
                {
                    continue;
                }

                entry.getValue().fromJSON(category);
            }

            config.unreadable = false;

            return true;
        }
        catch (Exception e)
        {
            /* P284: the file exists but we could not read it, so every value is
             * still at its default. Mark the config so Config.save refuses to
             * write those defaults back over it — see Config#unreadable. */
            config.unreadable = true;

            McLib.LOGGER.error("Could not parse config '" + config.id + "' at '" + file
                + "'. Its settings are at defaults in memory and saving is disabled until it is fixed.", e);
        }

        return false;
    }
}
