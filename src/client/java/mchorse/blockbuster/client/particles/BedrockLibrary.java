package mchorse.blockbuster.client.particles;

import mchorse.blockbuster.Blockbuster;
import mchorse.mclib.utils.AtomicWrite;
import mchorse.mclib.utils.JsonUtils;
import mchorse.mclib.utils.PastCopies;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * P154 — Snowstorm particle preset library.
 *
 * <p>Straight port of legacy
 * {@code mchorse.blockbuster.client.particles.BedrockLibrary} (Blockbuster
 * 2.7.2). Manages {@code config/blockbuster/models/particles/*.json}, the four
 * jar-shipped factory presets ({@code default_fire/magic/rain/snow}), and
 * name-shadowing (a user file with the same name shadows the factory preset).</p>
 *
 * <p>Parity notes:</p>
 * <ul>
 * <li>{@link #lastUpdate} is a <b>global static long</b> cache-invalidation
 * signal other subsystems poll; bumped in {@link #save}.</li>
 * <li>The constructor {@code mkdirs()}es the folder and fills only the
 * {@link #factory} map — {@link #presets} stays empty until the first
 * {@link #reload()} (once per world join).</li>
 * <li>{@link #reload()} clears {@code presets}, re-adds the factory presets,
 * then scans the folder — the disk pass runs <b>second</b>, so a user file
 * shadows the same-named factory preset.</li>
 * <li>{@link #load(String)} tries disk first, factory fallback second.</li>
 * <li>Total reader/writer: {@link #loadScheme} returns null on missing/corrupt
 * input; {@link #save} swallows write exceptions — a logged warning at most,
 * never a crash (legacy printed the stack trace / used an empty catch).</li>
 * </ul>
 *
 * <p>The legacy class was constructed in {@code CommonProxy} (line 124,
 * predating split source sets). The port keeps it client-side; the folder path
 * (via {@code BlockbusterPaths.particles()}) is what matters. Kept
 * Apache-commons-free ({@code Files.readString}/{@code writeString} inside),
 * but the method names/signatures are unchanged from legacy.</p>
 */
public class BedrockLibrary
{
    public static long lastUpdate;

    public Map<String, BedrockScheme> presets = new HashMap<String, BedrockScheme>();
    public Map<String, BedrockScheme> factory = new HashMap<String, BedrockScheme>();
    public File folder;

    public BedrockLibrary(File folder)
    {
        this.folder = folder;
        this.folder.mkdirs();

        /* Load factory (default) presets */
        this.storeFactory("default_fire");
        this.storeFactory("default_magic");
        this.storeFactory("default_rain");
        this.storeFactory("default_snow");
    }

    public File file(String name)
    {
        return new File(this.folder, name + ".json");
    }

    public boolean hasEffect(String name)
    {
        return this.file(name).isFile();
    }

    public void reload()
    {
        this.presets.clear();
        this.presets.putAll(this.factory);

        for (File file : this.folder.listFiles())
        {
            if (file.isFile() && file.getName().endsWith(".json"))
            {
                this.storeScheme(file);
            }
        }
    }

    public BedrockScheme load(String name)
    {
        BedrockScheme scheme = this.loadScheme(this.file(name));

        if (scheme != null)
        {
            return scheme;
        }

        return this.loadFactory(name);
    }

    private void storeScheme(File file)
    {
        BedrockScheme scheme = this.loadScheme(file);

        if (scheme != null)
        {
            String name = file.getName();

            this.presets.put(name.substring(0, name.indexOf(".json")), scheme);
        }
    }

    /**
     * Load a scheme from a file
     */
    public BedrockScheme loadScheme(File file)
    {
        if (!file.exists())
        {
            return null;
        }

        try
        {
            return BedrockScheme.parse(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        }
        catch (Exception e)
        {
            Blockbuster.LOGGER.warn("Failed to load particle scheme from {}", file, e);
        }

        return null;
    }

    private void storeFactory(String name)
    {
        BedrockScheme scheme = this.loadFactory(name);

        if (scheme != null)
        {
            this.factory.put(name, scheme);
        }
    }

    /**
     * Load a scheme from Blockbuster's zip
     */
    public BedrockScheme loadFactory(String name)
    {
        try (InputStream in = this.getClass().getClassLoader().getResourceAsStream("assets/blockbuster/particles/" + name + ".json"))
        {
            return BedrockScheme.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)).factory(true);
        }
        catch (Exception e)
        {
            Blockbuster.LOGGER.warn("Failed to load factory particle scheme {}", name, e);
        }

        return null;
    }

    /**
     * Write a particle preset to
     * {@code config/blockbuster/models/particles/<name>.json}.
     *
     * <p><b>P284, two fixes.</b> The write was in place and truncating with no
     * backup, and the {@code catch} was <i>empty</i> — a failed write was
     * indistinguishable from a successful one, and {@code GuiSnowstorm.save}
     * then unconditionally cleared its dirty flag and flipped the icon to
     * {@code SAVED}. The user's edits were gone the moment they switched
     * schemes (which discards without prompting, by legacy design). Now the
     * write is rotated + atomic, and the outcome is <b>returned</b> so the GUI
     * can keep the panel dirty when it did not land.</p>
     *
     * @return whether the preset reached the disk.
     */
    public boolean save(String filename, BedrockScheme scheme)
    {
        String json = JsonUtils.jsonToPretty(BedrockScheme.toJson(scheme));
        File file = this.file(filename);
        boolean saved = false;

        try
        {
            file.getParentFile().mkdirs();

            PastCopies.rotate(file, ".json");
            AtomicWrite.writeString(file, json);

            saved = true;
        }
        catch (Exception e)
        {
            Blockbuster.LOGGER.error("Could not save particle preset '" + filename + "' to " + file, e);
        }

        this.storeScheme(file);

        lastUpdate = System.currentTimeMillis();

        return saved;
    }
}
