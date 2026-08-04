package mchorse.chameleon.client;

import mchorse.chameleon.Chameleon;
import mchorse.chameleon.lib.ChameleonLoader;
import mchorse.chameleon.lib.ChameleonModel;
import mchorse.chameleon.lib.data.animation.Animations;
import mchorse.chameleon.lib.data.model.Model;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The models-folder scanner — legacy {@code ClientProxy.reloadModels} and its
 * two recursive helpers, installed into {@link Chameleon#modelReloader}.
 *
 * <h3>What counts as a model folder</h3>
 *
 * <p>A directory is a model if it directly contains a {@code *.geo.json}. Only
 * the <b>first</b> one found is used, and only the <b>first</b> loose
 * {@code *.animation.json} beside it — but <i>every</i> {@code *.animation.json}
 * inside an {@code animations/} sub-folder is loaded. All three rules are legacy
 * and are what model packs in the wild are laid out for.</p>
 *
 * <p>Directories that are not model folders are recursed into, so
 * {@code models/packs/wolf} registers under the key {@code "packs/wolf"} — which
 * is what {@code ChameleonSection} splits into a category. {@code skins} is
 * never recursed into (it is textures, not models).</p>
 *
 * <h3>Reload semantics</h3>
 *
 * <p>A folder is re-parsed only when its newest file is newer than the loaded
 * model's timestamp, so a rescan of an unchanged folder is free. Models whose
 * files have disappeared are dropped. A model that fails to parse leaves the
 * previously loaded one in place — a broken edit does not blank the morph.</p>
 */
public class ChameleonModelLoader
{
    private final ChameleonLoader loader = new ChameleonLoader();

    /**
     * Install this loader as {@link Chameleon#modelReloader} and run it once.
     */
    public static ChameleonModelLoader install(File modelsFile)
    {
        ChameleonModelLoader loader = new ChameleonModelLoader();

        Chameleon.modelsFile = modelsFile;
        Chameleon.modelReloader = loader::reloadModels;

        loader.reloadModels();

        return loader;
    }

    public void reloadModels()
    {
        File modelsFile = Chameleon.modelsFile;

        if (modelsFile == null)
        {
            return;
        }

        modelsFile.mkdirs();

        if (!modelsFile.isDirectory())
        {
            return;
        }

        List<String> toCheck = new ArrayList<String>(Chameleon.MODELS.keySet());

        this.recursiveReloadModel(modelsFile, "", toCheck);

        /* Check and remove model if it got removed */
        for (String key : toCheck)
        {
            ChameleonModel model = Chameleon.MODELS.get(key);

            if (model != null && !model.isStillPresent())
            {
                Chameleon.MODELS.remove(key);
            }
        }
    }

    private void recursiveReloadModel(File folder, String prefix, List<String> toCheck)
    {
        File[] files = folder.listFiles();

        if (files == null)
        {
            return;
        }

        for (File modelFile : files)
        {
            if (modelFile.isDirectory())
            {
                if (!this.reloadModelFolder(modelFile, prefix, toCheck) && !modelFile.getName().equals("skins"))
                {
                    this.recursiveReloadModel(modelFile, prefix + modelFile.getName() + "/", toCheck);
                }
            }
        }
    }

    private boolean reloadModelFolder(File modelFolder, String prefix, List<String> toCheck)
    {
        File model = null;
        List<File> animations = new ArrayList<File>();
        File[] files = modelFolder.listFiles();
        long lastUpdated = 0;

        if (files == null)
        {
            return false;
        }

        for (File file : files)
        {
            if (model == null && file.getName().endsWith(".geo.json"))
            {
                model = file;
                lastUpdated = Math.max(file.lastModified(), lastUpdated);
            }
            else if (animations.isEmpty() && file.getName().endsWith(".animation.json"))
            {
                animations.add(file);
                lastUpdated = Math.max(file.lastModified(), lastUpdated);
            }
        }

        /* Scan for animation files also in animations folder */
        File animationsFolder = new File(modelFolder, "animations");

        if (animationsFolder.isDirectory())
        {
            File[] animationsInFolder = animationsFolder.listFiles();

            if (animationsInFolder != null)
            {
                for (File animationFile : animationsInFolder)
                {
                    if (animationFile.getName().endsWith(".animation.json"))
                    {
                        animations.add(animationFile);
                        lastUpdated = Math.max(animationFile.lastModified(), lastUpdated);
                    }
                }
            }
        }

        /* Load model and animation */
        String key = prefix + modelFolder.getName();
        ChameleonModel oldModel = Chameleon.MODELS.get(key);

        if (model != null && (oldModel == null || oldModel.lastUpdate < lastUpdated))
        {
            Model theModel = this.loader.loadModel(model);
            Animations theAnimations = this.loadAnimations(animations);

            if (theModel != null)
            {
                List<File> trackingFiles = new ArrayList<File>();

                trackingFiles.add(model);
                Chameleon.MODELS.put(key, new ChameleonModel(theModel, theAnimations, trackingFiles, lastUpdated));
                toCheck.remove(key);
            }
        }

        return Chameleon.MODELS.containsKey(key);
    }

    private Animations loadAnimations(List<File> files)
    {
        Animations animations = new Animations();

        for (File animationFile : files)
        {
            this.loader.loadAllAnimations(Chameleon.PARSER, animationFile, animations);
        }

        return animations;
    }
}
