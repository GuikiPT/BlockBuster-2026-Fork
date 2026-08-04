package mchorse.chameleon.lib;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mchorse.chameleon.Chameleon;
import mchorse.chameleon.lib.data.animation.Animations;
import mchorse.chameleon.lib.data.model.Model;
import mchorse.chameleon.lib.parsing.AnimationParser;
import mchorse.chameleon.lib.parsing.ModelParser;
import mchorse.mclib.math.molang.MolangParser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads {@code .geo.json} / {@code .animation.json} files off disk into
 * {@link Model} / {@link Animations}.
 *
 * <p><b>Total reader</b>, like every legacy-format reader in this port: a
 * malformed or unreadable file is logged and skipped, never thrown. A model file
 * that fails yields {@code null} (the folder is then simply not registered); an
 * animation file that fails leaves the animations already parsed from earlier
 * files intact, and a single bad animation inside a good file skips only that
 * animation.</p>
 *
 * <p>Port notes: legacy's {@code JsonUtils.fromJson} + commons-io
 * {@code IOUtils.toString} become plain Gson + {@link Files#readAllBytes}
 * (Gson's reader is lenient, so a file with a trailing comma that 1.12.2
 * rejected now loads — strictly more permissive, never less), and the
 * {@code System.err.println} + {@code printStackTrace} pair becomes a logger
 * warning.</p>
 *
 * Legacy source: chameleon/.../lib/ChameleonLoader.java
 */
public class ChameleonLoader
{
    public void loadAllAnimations(MolangParser parser, File file, Animations animations)
    {
        JsonObject json = this.loadFile(file);

        if (json != null)
        {
            for (Map.Entry<String, JsonElement> entry : this.getAnimations(json).entrySet())
            {
                String key = entry.getKey();

                try
                {
                    animations.add(AnimationParser.parse(parser, key, entry.getValue().getAsJsonObject()));
                }
                catch (Exception e)
                {
                    Chameleon.LOGGER.warn("An error happened when parsing animation file: " + file.getAbsolutePath(), e);
                }
            }
        }
    }

    private Map<String, JsonElement> getAnimations(JsonObject json)
    {
        Map<String, JsonElement> map = new HashMap<String, JsonElement>();

        if (json.has("animations") && json.get("animations").isJsonObject())
        {
            for (Map.Entry<String, JsonElement> entry : json.get("animations").getAsJsonObject().entrySet())
            {
                map.put(entry.getKey(), entry.getValue());
            }
        }

        return map;
    }

    public Model loadModel(File file)
    {
        try
        {
            return ModelParser.parse(this.loadFile(file));
        }
        catch (Exception e)
        {
            Chameleon.LOGGER.warn("An error happened when parsing model file: " + file.getAbsolutePath(), e);
        }

        return null;
    }

    private JsonObject loadFile(File file)
    {
        try
        {
            return new Gson().fromJson(new StringReader(this.loadStringFile(file)), JsonObject.class);
        }
        catch (Exception e)
        {
            Chameleon.LOGGER.warn("An error happened when reading file: " + file.getAbsolutePath(), e);
        }

        return null;
    }

    private String loadStringFile(File file) throws IOException
    {
        try (InputStream stream = Files.newInputStream(file.toPath()))
        {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
