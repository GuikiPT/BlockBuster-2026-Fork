package mchorse.blockbuster.api;

import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.api.loaders.IModelLoader;
import mchorse.blockbuster.api.loaders.ModelLoaderJSON;
import mchorse.blockbuster.api.loaders.ModelLoaderOBJ;
import mchorse.blockbuster.api.loaders.ModelLoaderVOX;
import mchorse.blockbuster.api.loaders.lazy.IModelLazyLoader;
import mchorse.blockbuster.api.loaders.lazy.ModelLazyLoaderJSON;
import mchorse.blockbuster.api.loaders.lazy.ModelLazyLoaderOBJ;
import mchorse.blockbuster.api.loaders.lazy.ModelLazyLoaderVOX;
import mchorse.blockbuster.api.resource.IResourceEntry;
import mchorse.blockbuster.api.resource.StreamEntry;
import mchorse.blockbuster.utils.BlockbusterPaths;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Model pack (roadmap P68 + P70 + P70.1).
 *
 * <p>Owns the model-folder roots, the recursive discovery rules, the fixed
 * loader precedence (<b>VOX &gt; OBJ &gt; JSON</b>, per-folder), the incremental
 * reload / removal bookkeeping ({@link #models}/{@link #changed}/{@link #removed}/
 * {@link #lastTime}), the jar-bundled default-model registration (P70), and the
 * packed {@code user.json} mechanism (P70.1).</p>
 *
 * <p>Legacy source: {@code blockbuster-1.12/.../api/ModelPack.java}. Field names
 * ({@code models}/{@code changed}/{@code removed}) are a contract with
 * {@code ModelHandler}. Ported quirks:</p>
 * <ul>
 * <li>Skip rules: {@code "__"}-prefixed (hidden) folders, {@code "skins"}, plain
 * files, and {@code "particles"} <b>only at the top level</b> (a nested
 * {@code foo/particles} <i>is</i> scanned).</li>
 * <li>{@code lastTime == -1} sentinel marks jar-bundled models immortal (never
 * removed); a filesystem folder with the same key silently overrides a default
 * model, because the scan unconditionally re-creates loaders for keys whose
 * cached loader is a default.</li>
 * <li>Default registration also adds the loader to {@link #changed} and calls
 * {@code removed.remove(id)} — a default key can never end a reload removed.</li>
 * </ul>
 *
 * <p><b>Fabric folder mapping</b> (vs. legacy {@code CommonProxy.configFile} +
 * {@code DimensionManager.getCurrentSaveRootDirectory()}): the config folder is
 * {@link BlockbusterPaths#models()}, the optional extra folder is
 * {@link BlockbusterPaths#extraModelFolder()} (the {@code model_folders.path}
 * config value seam), and the world folder is
 * {@link BlockbusterPaths#worldModels(Path)} rooted at {@link CommonProxy#saveRoot()}
 * (null when no integrated/dedicated server world is loaded — guarded exactly
 * like the legacy null-check). The legacy {@code ImageFolder} (a {@code File}
 * subclass that double-lists {@code .gif}s as frame pseudo-directories) is
 * substituted with a plain directory here: P88.1 does that wrapping where it is
 * actually observed — at {@code BlockbusterTree} construction in
 * {@code BlockbusterClient.registerFileTrees} — so the GIF pseudo-folders reach
 * the texture picker without also walking into the model folder scan.</p>
 */
public class ModelPack
{
    /**
     * Headless-test seam: when non-null, {@link #setupFolders()} scans exactly
     * these folders instead of the config/extra/world discovery (mirrors the
     * {@code CommonProxy.saveRootOverride} test-seam idiom). Reset to {@code null}
     * in production.
     */
    public static List<File> folderOverride;

    /**
     * Classpath resource path of the packed-model manifest ({@code user.json}).
     * A seam so tests can point {@link #setupPackedModels()} at a fake manifest
     * without repacking the jar. Defaults to the shipped {@code {}} manifest.
     */
    public static String packedManifestPath = "assets/blockbuster/models/user";

    /**
     * List of model loaders. Order is the fixed precedence: {@code VOX}, then
     * {@code OBJ}, then {@code JSON}.
     */
    public List<IModelLoader> loaders = new ArrayList<IModelLoader>();

    /**
     * Cached models (key → lazy loader).
     */
    public Map<String, IModelLazyLoader> models = new HashMap<String, IModelLazyLoader>();

    /**
     * Folders which to check when reloading models.
     */
    public List<File> folders = new ArrayList<File>();

    /**
     * Map for only changed models (consumed by {@code ModelHandler} on a
     * non-forced reload).
     */
    public Map<String, IModelLazyLoader> changed = new HashMap<String, IModelLazyLoader>();

    /**
     * List of removed models.
     */
    public List<String> removed = new ArrayList<String>();

    private long lastTime;

    private Map<String, ModelUserItem> packed = new HashMap<String, ModelUserItem>();

    public ModelPack()
    {
        this.loaders.add(new ModelLoaderVOX());
        this.loaders.add(new ModelLoaderOBJ());
        this.loaders.add(new ModelLoaderJSON());

        this.setupFolders();
        this.setupPackedModels();
    }

    private void setupPackedModels()
    {
        try
        {
            InputStream stream = this.getClass().getClassLoader().getResourceAsStream(packedManifestPath + ".json");
            String json = IOUtils.toString(stream, StandardCharsets.UTF_8);

            this.packed = new Gson().fromJson(json, new TypeToken<Map<String, ModelUserItem>>(){}.getType());
        }
        catch (Exception e)
        {}
    }

    public IModelLazyLoader create(File file)
    {
        IModelLazyLoader lazyLoader = null;

        for (IModelLoader loader : this.loaders)
        {
            lazyLoader = loader.load(file);

            if (lazyLoader != null)
            {
                break;
            }
        }

        return lazyLoader;
    }

    /**
     * Setup folders
     */
    public void setupFolders()
    {
        this.folders.clear();

        if (folderOverride != null)
        {
            for (File folder : folderOverride)
            {
                this.addFolder(folder);
            }

            return;
        }

        this.addFolder(BlockbusterPaths.models().toFile());

        Optional<Path> extra = BlockbusterPaths.extraModelFolder();

        if (extra.isPresent())
        {
            this.addFolder(extra.get().toFile());
        }

        Path saveRoot = CommonProxy.saveRoot();

        if (saveRoot != null)
        {
            this.addFolder(BlockbusterPaths.worldModels(saveRoot).toFile());
        }
    }

    /**
     * Add a folder to the list of folders to where to look up models and skins
     */
    private void addFolder(File folder)
    {
        folder.mkdirs();

        if (folder.isDirectory())
        {
            this.folders.add(folder);
        }
    }

    /**
     * Reload model handler
     */
    public void reload()
    {
        this.setupFolders();

        this.changed.clear();
        this.removed.clear();
        this.lastTime = System.currentTimeMillis();

        for (File folder : this.folders)
        {
            this.reloadModels(folder, "");
        }

        this.removeOld();

        try
        {
            /* Load default provided models */
            this.addDefaultModel("alex");
            this.addDefaultModel("alex_3d");
            this.addDefaultModel("steve");
            this.addDefaultModel("steve_3d");
            this.addDefaultModel("fred");
            this.addDefaultModel("fred_3d");
            this.addDefaultModel("empty");
            this.addDefaultModel("cape");

            /* Eyes related models */
            List<String> shapes = ImmutableList.of(
                "eyebrow_l",
                "eyebrow_r",
                "eyelid_lb",
                "eyelid_lt",
                "eyelid_rb",
                "eyelid_rt"
            );

            this.addDefaultModel("eyes/3.0");
            this.addDefaultModel("eyes/3.0_1px");
            this.addDefaultModelWithShapes("eyes/3.1", shapes);
            this.addDefaultModelWithShapes("eyes/3.1_simple", shapes);
            this.addDefaultModel("eyes/alex");
            this.addDefaultModel("eyes/steve");
            this.addDefaultModel("eyes/fred");
            this.addDefaultModel("eyes/head");
            this.addDefaultModel("eyes/head_3d");

            /* Of course I know him, he's me */
            this.addDefaultModel("mchorse/head");

            if (this.packed != null)
            {
                for (Map.Entry<String, ModelUserItem> entry : this.packed.entrySet())
                {
                    this.addUserModel(entry.getKey(), entry.getValue());
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private void addUserModel(String id, ModelUserItem userItem)
    {
        IModelLazyLoader lazy = this.models.get(id);

        if (lazy == null)
        {
            String path = packedManifestPath + "/" + id;
            ClassLoader loader = this.getClass().getClassLoader();

            StreamEntry json = new StreamEntry(path + "/model.json", 0, loader);

            if (userItem.obj != null)
            {
                String mtlPath = userItem.mtl == null ? null : path + "/" + userItem.mtl;
                StreamEntry obj = new StreamEntry(path + "/" + userItem.obj, 0, loader);
                StreamEntry mtl = new StreamEntry(mtlPath, 0, loader);
                List<IResourceEntry> s = new ArrayList<IResourceEntry>();

                if (userItem.shapes != null)
                {
                    for (String shape : userItem.shapes)
                    {
                        s.add(new StreamEntry(path + "/"  + shape, 0, loader));
                    }
                }

                lazy = new ModelLazyLoaderOBJ(json, obj, mtl, s);
            }
            else if (userItem.vox != null)
            {
                lazy = new ModelLazyLoaderVOX(json, new StreamEntry(path + "/" + userItem.vox, 0, loader));
            }
            else
            {
                lazy = new ModelLazyLoaderJSON(json);
            }

            lazy.setLastTime(-1);

            this.models.put(id, lazy);
            this.changed.put(id, lazy);
            this.removed.remove(id);
        }
    }

    /**
     * Remove old entries
     */
    private void removeOld()
    {
        Iterator<Map.Entry<String, IModelLazyLoader>> it = this.models.entrySet().iterator();

        while (it.hasNext())
        {
            Map.Entry<String, IModelLazyLoader> entry = it.next();
            long lastTime = entry.getValue().getLastTime();

            if (lastTime < this.lastTime && lastTime >= 0)
            {
                it.remove();

                this.removed.add(entry.getKey());
            }
        }
    }

    /**
     * Add a default model bundled with the mod
     */
    private void addDefaultModel(String id) throws Exception
    {
        IModelLazyLoader lazy = this.models.get(id);

        if (lazy == null)
        {
            String path = "assets/blockbuster/models/entity/";
            ClassLoader loader = this.getClass().getClassLoader();

            lazy = new ModelLazyLoaderJSON(new StreamEntry(path + id + ".json", 0, loader));
            lazy.setLastTime(-1);

            this.models.put(id, lazy);
            this.changed.put(id, lazy);
            this.removed.remove(id);
        }
    }

    private void addDefaultModelWithShapes(String id, List<String> shapes) throws Exception
    {
        IModelLazyLoader lazy = this.models.get(id);

        if (lazy == null)
        {
            String path = "assets/blockbuster/models/entity/";
            ClassLoader loader = this.getClass().getClassLoader();

            StreamEntry json = new StreamEntry(path + id + ".json", 0, loader);
            StreamEntry obj = new StreamEntry(path + id + "/base.obj", 0, loader);
            List<IResourceEntry> s = new ArrayList<IResourceEntry>();

            for (String shape : shapes)
            {
                s.add(new StreamEntry(path + id + "/"  + shape + ".obj", 0, loader));
            }

            lazy = new ModelLazyLoaderOBJ(json, obj, new StreamEntry(null, 0, loader), s);
            lazy.setLastTime(-1);

            this.models.put(id, lazy);
            this.changed.put(id, lazy);
            this.removed.remove(id);
        }
    }

    /**
     * Reload models
     *
     * Simply caches files in the map
     */
    protected void reloadModels(File folder, String prefix)
    {
        for (File file : folder.listFiles())
        {
            String name = file.getName();

            if (name.startsWith("__") || name.equals("skins") || file.isFile() || (name.equals("particles") && prefix.isEmpty()))
            {
                continue;
            }

            String path = prefix + name;
            IModelLazyLoader lazyLoader = this.models.get(path);

            if (lazyLoader != null && lazyLoader.getLastTime() >= 0)
            {
                if (lazyLoader.stillExists())
                {
                    lazyLoader.setLastTime(this.lastTime);

                    if (lazyLoader.hasChanged())
                    {
                        this.changed.put(path, lazyLoader);
                    }

                    continue;
                }
            }
            else
            {
                /* Overwriting the default model */
                lazyLoader = null;
            }

            for (IModelLoader loader : this.loaders)
            {
                lazyLoader = loader.load(file);

                if (lazyLoader != null)
                {
                    lazyLoader.setLastTime(this.lastTime);

                    break;
                }
            }

            if (lazyLoader != null)
            {
                this.models.put(path, lazyLoader);
                this.changed.put(path, lazyLoader);
            }
            else
            {
                this.reloadModels(file, path + "/");
            }
        }
    }
}
