package mchorse.blockbuster.utils;

import mchorse.blockbuster.Blockbuster;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Central on-disk layout utility (roadmap P7). Reproduces the exact 1.12.2
 * folder contract:
 *
 * <ul>
 * <li>{@code config/blockbuster/{models,models/particles,audio,skins}}</li>
 * <li>{@code <world>/blockbuster/{records,scenes,models}}</li>
 * <li>{@code <world>/aperture/cameras} (camera profiles — <b>not</b> under
 * {@code blockbuster/}; folder parity for old Aperture worlds)</li>
 * </ul>
 *
 * <p><b>No other class may build these paths by hand</b> — later stages grep
 * for stray {@code "blockbuster/"} concatenations in review. The folder-name
 * literals live here as {@code *_FOLDER} constants.</p>
 *
 * <p>Legacy sources of truth: {@code CommonProxy.configFile}
 * ({@code new File(configDir, "blockbuster")}), {@code recording/Utils}
 * ({@code serverFile}/{@code serverFiles}), and Aperture
 * {@code camera/CameraUtils.cameraFile}
 * ({@code new File(saveRoot, "aperture/cameras")}). The {@code model_folders.path}
 * config contributes an <i>extra</i> lookup folder; it never relocates
 * {@link #models()}.</p>
 */
public final class BlockbusterPaths
{
    /* World-relative folder names (the single source for these string literals). */

    /** {@code <world>/blockbuster/records} — legacy {@code Utils.serverFile("blockbuster/records", …)}. */
    public static final String RECORDS_FOLDER = "blockbuster/records";

    /** {@code <world>/blockbuster/scenes} — legacy {@code Utils.serverFile("blockbuster/scenes", …)}. */
    public static final String SCENES_FOLDER = "blockbuster/scenes";

    /** {@code <world>/blockbuster/models} — the world-local model lookup folder ({@code ModelPack.setupFolders}). */
    public static final String WORLD_MODELS_FOLDER = "blockbuster/models";

    /**
     * {@code <world>/blockbuster/model_blocks} — <b>the one folder 1.12.2 never
     * had</b> (S22 P266). Orphaned model-block tile entities are dumped here when
     * a 1.12.2 world is upgraded and the model block itself does not survive
     * flattening; see
     * {@link mchorse.blockbuster.legacy.OrphanedModelBlocks}.
     *
     * <p>A deliberate deviation from the folder contract, because there is no
     * legacy destination to deviate <i>from</i>: the director block's payload is
     * a scene and lands in {@code scenes/}, but a model block's morph + settings
     * have no legacy file format at all. Kept a sibling of {@code records/} and
     * {@code scenes/} so it is discoverable in the place users already look.</p>
     */
    public static final String MODEL_BLOCKS_FOLDER = "blockbuster/model_blocks";

    /**
     * {@code <world>/aperture/cameras} — Aperture camera-profile storage.
     * Deliberately <b>not</b> under {@code blockbuster/}: 1.12.2 stored camera
     * profiles here (see {@code aperture/camera/CameraUtils.cameraFile}), so old
     * worlds keep parity. Aperture's {@code CameraUtils} is the runtime owner of
     * this dir (it cannot import this class — bundled-dep layering); the
     * accessors here are the central parity mirror for Blockbuster-side code and
     * tests.
     */
    public static final String CAMERAS_FOLDER = "aperture/cameras";

    /**
     * Bound to the real {@code model_folders.path} config value
     * ({@link mchorse.blockbuster.Blockbuster#modelFolderPath}), which legacy
     * {@code ModelPack.setupFolders} read directly. Empty string means "no
     * extra folder", exactly like 1.12.2's default. Kept as a supplier seam so
     * headless tests can drive {@link #extraModelFolder()} without a config
     * tree.
     */
    public static Supplier<String> extraModelFolderPath = () -> Blockbuster.modelFolderPath.get();

    private BlockbusterPaths()
    {}

    /* Config-relative folders (1.12.2: config/blockbuster/...) */

    public static Path configRoot()
    {
        return FabricLoader.getInstance().getConfigDir().resolve("blockbuster");
    }

    public static Path models()
    {
        return configRoot().resolve("models");
    }

    public static Path particles()
    {
        return models().resolve("particles");
    }

    public static Path audio()
    {
        return configRoot().resolve("audio");
    }

    public static Path skins()
    {
        return configRoot().resolve("skins");
    }

    /**
     * The optional additional model lookup folder ({@code model_folders.path}
     * config) — an extra folder, not a relocation of {@link #models()}.
     */
    public static Optional<Path> extraModelFolder()
    {
        String path = extraModelFolderPath.get();

        return path == null || path.isEmpty() ? Optional.empty() : Optional.of(Path.of(path));
    }

    /**
     * The first-run <b>client</b> model/skin folders that legacy
     * {@code ClientProxy.injectResourcePack} + the {@code skinsFolder} setup
     * {@code mkdirs()}'d under {@code config/blockbuster/} (never under the
     * {@code model_folders.path} override). Exposed here as <i>data</i> so the
     * single source for these literals stays in this class; actual creation is
     * client behavior that lands with the model stages (call
     * {@link #createClientFolders()} from there).
     *
     * <p>Order matches legacy {@code ClientProxy} lines 150–151 (skins) then
     * 202–207 (per-model skins): {@code skins}, then
     * {@code models/{steve,alex,fred,image,cape,eyes/3.0}/skins}. The default
     * skin ({@code default.png}) is copied into {@code models/image/skins}
     * (already in this list) — see the model-stage port.</p>
     */
    public static List<Path> firstRunClientFolders()
    {
        Path root = configRoot();
        List<Path> folders = new ArrayList<>();

        folders.add(skins());
        folders.add(root.resolve("models/steve/skins"));
        folders.add(root.resolve("models/alex/skins"));
        folders.add(root.resolve("models/fred/skins"));
        folders.add(root.resolve("models/image/skins"));
        folders.add(root.resolve("models/cape/skins"));
        folders.add(root.resolve("models/eyes/3.0/skins"));

        return folders;
    }

    /**
     * The folder into which legacy copied the default skin
     * ({@code models/image/skins/default.png}).
     */
    public static Path defaultSkinFolder()
    {
        return models().resolve("image/skins");
    }

    /**
     * Classpath location of the mod's GUI icon, which doubles as the image
     * morph's guaranteed default skin (legacy {@code RLUtils.create("blockbuster",
     * "textures/gui/icon.png")}, read from the mod jar).
     */
    public static final String DEFAULT_SKIN_RESOURCE = "/assets/blockbuster/textures/gui/icon.png";

    /**
     * Default-skin bootstrap (P88): when {@code models/image/skins/default.png}
     * is missing, copy the mod's own GUI icon there — the image morph relies on
     * this file always existing. Mirrors legacy {@code ClientProxy} lines
     * 153–156. No-op (and non-throwing) when the file already exists or the
     * bundled icon resource can't be read. The {@code target} overload lets
     * tests point at a temp destination.
     */
    public static void copyDefaultSkin()
    {
        copyDefaultSkin(defaultSkinFolder().resolve("default.png").toFile());
    }

    public static void copyDefaultSkin(File target)
    {
        if (target.exists())
        {
            return;
        }

        target.getParentFile().mkdirs();

        try (InputStream in = BlockbusterPaths.class.getResourceAsStream(DEFAULT_SKIN_RESOURCE))
        {
            if (in != null)
            {
                Files.copy(in, target.toPath());
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Creates the first-run client folders ({@link #firstRunClientFolders()}),
     * each with {@code mkdirs()} semantics like legacy. Intended to be called
     * once from the client model-stage init; harmless (idempotent) if the
     * folders already exist. Returns the list it created for testability.
     */
    public static List<Path> createClientFolders()
    {
        List<Path> folders = firstRunClientFolders();

        for (Path folder : folders)
        {
            folder.toFile().mkdirs();
        }

        return folders;
    }

    /* World-relative folders (1.12.2: <world>/blockbuster/...) */

    public static Path worldRoot(MinecraftServer server)
    {
        return server.getSavePath(WorldSavePath.ROOT).normalize();
    }

    /* Records — <world>/blockbuster/records. */

    public static File records(MinecraftServer server, String filename)
    {
        return records(worldRoot(server), filename);
    }

    public static List<String> records(MinecraftServer server)
    {
        return records(worldRoot(server));
    }

    public static File records(Path saveRoot, String filename)
    {
        return serverFile(saveRoot, RECORDS_FOLDER, filename);
    }

    public static List<String> records(Path saveRoot)
    {
        return serverFiles(saveRoot, RECORDS_FOLDER);
    }

    /**
     * The records folder as a {@link File}, <b>without</b> creating it
     * (matches legacy {@code getReplayIterations}, which listed the raw dir).
     * Callers that only enumerate (never write) use this.
     */
    public static File recordsFolder(Path saveRoot)
    {
        return saveRoot.resolve(RECORDS_FOLDER).toFile();
    }

    /* Scenes — <world>/blockbuster/scenes. */

    public static File scenes(MinecraftServer server, String filename)
    {
        return scenes(worldRoot(server), filename);
    }

    public static List<String> scenes(MinecraftServer server)
    {
        return scenes(worldRoot(server));
    }

    public static File scenes(Path saveRoot, String filename)
    {
        return serverFile(saveRoot, SCENES_FOLDER, filename);
    }

    public static List<String> scenes(Path saveRoot)
    {
        return serverFiles(saveRoot, SCENES_FOLDER);
    }

    /* Orphaned model-block dumps — <world>/blockbuster/model_blocks (S22 P266). */

    public static File modelBlocks(Path saveRoot, String filename)
    {
        return serverFile(saveRoot, MODEL_BLOCKS_FOLDER, filename);
    }

    public static List<String> modelBlocks(Path saveRoot)
    {
        return serverFiles(saveRoot, MODEL_BLOCKS_FOLDER);
    }

    public static File modelBlocks(MinecraftServer server, String filename)
    {
        return modelBlocks(worldRoot(server), filename);
    }

    public static List<String> modelBlocks(MinecraftServer server)
    {
        return modelBlocks(worldRoot(server));
    }

    /* World-local models — <world>/blockbuster/models. */

    public static Path worldModels(MinecraftServer server)
    {
        return worldModels(worldRoot(server));
    }

    public static Path worldModels(Path saveRoot)
    {
        return saveRoot.resolve(WORLD_MODELS_FOLDER);
    }

    /* Camera profiles — <world>/aperture/cameras (parity mirror, see CAMERAS_FOLDER). */

    /**
     * The camera-profiles folder as a {@link Path} ({@code <world>/aperture/cameras}).
     * Central parity reference; Aperture's {@code CameraUtils} owns the runtime
     * reads/writes (it appends {@code .json}, not {@code .dat}).
     */
    public static Path cameras(Path saveRoot)
    {
        return saveRoot.resolve(CAMERAS_FOLDER);
    }

    public static Path cameras(MinecraftServer server)
    {
        return cameras(worldRoot(server));
    }

    /**
     * Resolves {@code <world>/aperture/cameras/<filename>.json}, lazily creating
     * the folder — mirrors Aperture {@code CameraUtils.cameraFile} exactly (note
     * the {@code .json} extension, unlike {@link #serverFile} which uses
     * {@code .dat}). The returned file may not exist.
     */
    public static File cameraFile(Path saveRoot, String filename)
    {
        File file = cameras(saveRoot).toFile();

        if (!file.exists())
        {
            file.mkdirs();
        }

        return new File(file, filename + ".json");
    }

    /**
     * Builtin default records ship on the classpath at
     * {@code /assets/blockbuster/records/<name>.dat} and are read via
     * {@code getResourceAsStream} — this lookup is separate from the world dir.
     * Kept here so the classpath prefix has one home too.
     */
    public static String builtinRecordResource(String filename)
    {
        return "/assets/blockbuster/records/" + filename + ".dat";
    }

    /**
     * Port of legacy {@code recording/Utils.serverFile}: resolves
     * {@code <world>/<folder>/<filename>.dat}, lazily creating the folder.
     * The returned file may not exist. Package-visible root overload keeps
     * the semantics testable without a running server.
     */
    public static File serverFile(Path saveRoot, String folder, String filename)
    {
        File file = saveRoot.resolve(folder).toFile();

        if (!file.exists())
        {
            file.mkdirs();
        }

        return new File(file, filename + ".dat");
    }

    /**
     * Port of legacy {@code recording/Utils.serverFiles}: lists {@code .dat}
     * files in {@code <world>/<folder>}, stripping the extension at the
     * <b>last</b> dot (a file {@code a.b.dat} lists as {@code a.b}).
     * Null-safe: missing folder yields an empty list.
     */
    public static List<String> serverFiles(Path saveRoot, String folder)
    {
        List<String> files = new ArrayList<>();
        File[] children = saveRoot.resolve(folder).toFile().listFiles();

        if (children == null)
        {
            return files;
        }

        for (File file : children)
        {
            String name = file.getName();

            if (file.isFile() && name.endsWith(".dat"))
            {
                files.add(name.substring(0, name.lastIndexOf(".")));
            }
        }

        return files;
    }
}
