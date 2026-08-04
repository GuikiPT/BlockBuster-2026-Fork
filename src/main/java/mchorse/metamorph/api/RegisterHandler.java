package mchorse.metamorph.api;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import mchorse.metamorph.Metamorph;
import mchorse.metamorph.api.events.RegisterBlacklistEvent;
import mchorse.metamorph.api.events.RegisterRemapEvent;
import mchorse.metamorph.api.events.RegisterSettingsEvent;
import mchorse.metamorph.api.json.MorphSettingsAdapter;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

/**
 * Register handler (roadmap P49).
 *
 * <p>Loads the bundled {@code assets/metamorph/morphs.json} first, then the
 * user {@code config/metamorph/morphs.json}, merging per-key via
 * {@code copy()}; always blacklists {@code "metamorph:morph"} (the ghost
 * entity id); default remap includes {@code metamorph.Block -> block}.</p>
 *
 * <p>Port note: legacy {@code @SubscribeEvent} handlers become
 * {@link #register()} which wires these into {@link MetamorphEvents}; the user
 * config files resolve under the Fabric config dir
 * ({@code config/metamorph/…}). The bundled Blockbuster factory (S14)
 * subscribes its own handlers the same way.</p>
 *
 * <p>Installed by {@link mchorse.metamorph.MetamorphSettingsWiring} (S22 P222),
 * which is the class that owns the two legacy call sites this handler needs —
 * {@code CommonProxy.load()}'s subscription + config-file generation and
 * {@code Metamorph.serverStarting}'s reload. Until P222 nothing called
 * {@link #register()} and every loader below was dead code.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/api/RegisterHandler.java
 */
public class RegisterHandler
{
    /**
     * GSON instance responsible for deserializing morph settings
     */
    private final static Gson GSON = new GsonBuilder().registerTypeAdapter(MorphSettings.class, new MorphSettingsAdapter()).create();

    /** User config files ({@code config/metamorph/…}); overridable for tests. */
    public File morphs;
    public File blacklist;
    public File remap;

    public RegisterHandler()
    {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("metamorph");

        this.morphs = dir.resolve("morphs.json").toFile();
        this.blacklist = dir.resolve("blacklist.json").toFile();
        this.remap = dir.resolve("remap.json").toFile();
    }

    /**
     * Auto-generate the empty user config files if missing, mirroring the
     * legacy {@code CommonProxy} file locations ({@code {}}, {@code []},
     * {@code {}}).
     */
    public void setupFiles()
    {
        if (!this.morphs.exists()) MorphUtils.generateFile(this.morphs, "{}");
        if (!this.blacklist.exists()) MorphUtils.generateFile(this.blacklist, "[]");
        if (!this.remap.exists()) MorphUtils.generateFile(this.remap, "{}");
    }

    /**
     * Subscribe this handler's callbacks to the reload event bus.
     */
    public void register()
    {
        MetamorphEvents.REGISTER_SETTINGS.register(this::onSettingsReload);
        MetamorphEvents.REGISTER_BLACKLIST.register(this::onRegisterBlacklist);
        MetamorphEvents.REGISTER_REMAP.register(this::onRegisterRemapper);
    }

    /**
     * Register morph settings from default morphs configuration that comes
     * with Metamorph and the user configuration file.
     */
    public void onSettingsReload(RegisterSettingsEvent event)
    {
        this.loadMorphSettings(event.settings, this.getClass().getClassLoader().getResourceAsStream("assets/metamorph/morphs.json"));
        this.loadMorphSettings(event.settings, this.morphs);
    }

    private void loadMorphSettings(Map<String, MorphSettings> settings, File config)
    {
        try
        {
            this.loadMorphSettings(settings, new FileInputStream(config), config.getAbsolutePath());
        }
        catch (Exception e)
        {
            Metamorph.LOGGER.warn("Failed to open morph settings file {} — skipping it", config, e);
        }
    }

    private void loadMorphSettings(Map<String, MorphSettings> settings, InputStream input)
    {
        this.loadMorphSettings(settings, input, "assets/metamorph/morphs.json");
    }

    /**
     * Total reader: a missing stream, unparseable JSON or a {@code null}
     * document logs a warning and contributes nothing, rather than throwing out
     * of the collector event and taking the whole reload (or, at server start,
     * the world load) with it. Legacy only guarded the <i>file</i> arm — the
     * bundled-asset arm would NPE on a missing resource and the file arm's
     * {@code printStackTrace} was invisible in a server log.
     */
    private void loadMorphSettings(Map<String, MorphSettings> settings, InputStream input, String source)
    {
        Map<String, MorphSettings> data;

        if (input == null)
        {
            Metamorph.LOGGER.warn("Morph settings source {} is missing — no settings loaded from it", source);

            return;
        }

        try (Scanner scanner = new Scanner(input, "UTF-8"))
        {
            Type type = (new TypeToken<Map<String, MorphSettings>>() {}).getType();

            data = GSON.fromJson(scanner.useDelimiter("\\A").next(), type);
        }
        catch (Exception e)
        {
            Metamorph.LOGGER.warn("Failed to parse morph settings from {} — no settings loaded from it", source, e);

            return;
        }

        if (data == null)
        {
            Metamorph.LOGGER.warn("Morph settings source {} is empty or null — no settings loaded from it", source);

            return;
        }

        for (Map.Entry<String, MorphSettings> entry : data.entrySet())
        {
            String key = entry.getKey();
            MorphSettings morphSettings = entry.getValue();

            /* A JSON null value never reaches MorphSettingsAdapter, so it
             * arrives here as a Java null; letting it into the map would NPE
             * later in setActiveSettings/PacketSettings rather than here. */
            if (key == null || morphSettings == null)
            {
                Metamorph.LOGGER.warn("Morph settings entry \"{}\" in {} is null — skipping it", key, source);

                continue;
            }

            if (settings.containsKey(key))
            {
                settings.get(key).copy(morphSettings);
            }
            else
            {
                settings.put(key, morphSettings);
            }
        }
    }

    public void onRegisterBlacklist(RegisterBlacklistEvent event)
    {
        event.blacklist.add("metamorph:morph");

        this.loadBlacklist(event.blacklist, this.blacklist);
    }

    /** Total reader — see {@link #loadMorphSettings(Map, InputStream, String)}. */
    private void loadBlacklist(Set<String> set, File blacklist)
    {
        try (Scanner scanner = new Scanner(new FileInputStream(blacklist), "UTF-8"))
        {
            Type type = (new TypeToken<List<String>>() {}).getType();
            List<String> data = GSON.fromJson(scanner.useDelimiter("\\A").next(), type);

            if (data == null)
            {
                Metamorph.LOGGER.warn("Morph blacklist {} is empty or null — no entries loaded from it", blacklist);

                return;
            }

            for (String entry : data)
            {
                if (entry != null)
                {
                    set.add(entry);
                }
            }
        }
        catch (Exception e)
        {
            Metamorph.LOGGER.warn("Failed to read morph blacklist {} — no entries loaded from it", blacklist, e);
        }
    }

    public void onRegisterRemapper(RegisterRemapEvent event)
    {
        event.map.put("metamorph.Block", "block");

        this.loadMappings(event.map, this.remap);
    }

    /** Total reader — see {@link #loadMorphSettings(Map, InputStream, String)}. */
    private void loadMappings(Map<String, String> map, File remap)
    {
        try (Scanner scanner = new Scanner(new FileInputStream(remap), "UTF-8"))
        {
            Type type = (new TypeToken<Map<String, String>>() {}).getType();
            Map<String, String> data = GSON.fromJson(scanner.useDelimiter("\\A").next(), type);

            if (data == null)
            {
                Metamorph.LOGGER.warn("Morph remapper {} is empty or null — no mappings loaded from it", remap);

                return;
            }

            map.putAll(data);
        }
        catch (Exception e)
        {
            Metamorph.LOGGER.warn("Failed to read morph remapper {} — no mappings loaded from it", remap, e);
        }
    }
}
