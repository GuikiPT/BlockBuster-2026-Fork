package mchorse.blockbuster.api;

import mchorse.blockbuster.api.loaders.lazy.IModelLazyLoader;

import java.util.HashMap;
import java.util.Map;

/**
 * Model handler (roadmap P69) — server + common load/reload orchestration.
 *
 * <p>Stores the domain custom models (as data) and keeps the custom-morph
 * factory section in sync via {@link #morphSection}. Legacy source:
 * {@code blockbuster-1.12/.../api/ModelHandler.java}.</p>
 *
 * <p>Ported quirks:</p>
 * <ul>
 * <li>{@link #lastUpdate} is stamped {@code System.currentTimeMillis()} after
 * every {@link #loadModels(ModelPack, boolean)} — GUI dashboards read it to
 * invalidate model lists; keep it static.</li>
 * <li>A non-forced reload iterates {@code pack.changed} (does <b>not</b>
 * recompile unchanged models); a forced reload iterates {@code pack.models} and
 * {@link #removeModel(String)}s each key before re-adding.</li>
 * <li>Per-model exceptions are caught and logged (the totality guarantee) — one
 * corrupt {@code model.json} must not stop the rest of the reload.</li>
 * <li>The model registry doubles as the morph-list source: add/remove must stay
 * in sync with the custom morph factory section ({@link #morphSection}, S4).</li>
 * </ul>
 *
 * <p>The client-connect reload hook ({@code loadModels(false)} + particle-library
 * reload on join) lives in the client init ({@code BlockbusterClient}); the
 * client compile subclass is {@code ModelClientHandler} (client source set).</p>
 */
public class ModelHandler
{
    public static long lastUpdate;

    /**
     * SEAM(S4): the custom-morph factory section (legacy
     * {@code Blockbuster.proxy.factory.section}). Null until the morph stage
     * wires it in — {@link #addMorph}/{@link #removeModel} are morph-side no-ops
     * while null. Headless tests set a recording double.
     */
    public static IModelMorphSection morphSection;

    /**
     * Cached models, loaded from the pack's lazy loaders.
     */
    public Map<String, Model> models = new HashMap<String, Model>();

    /**
     * Actors pack from which ModelHandler loads its models.
     */
    public ModelPack pack;

    /**
     * Load user and default provided models into the model map.
     */
    public void loadModels(ModelPack pack, boolean force)
    {
        pack.reload();

        /* Load user provided models */
        for (Map.Entry<String, IModelLazyLoader> entry : (force ? pack.models : pack.changed).entrySet())
        {
            IModelLazyLoader loader = entry.getValue();

            try
            {
                if (force)
                {
                    this.removeModel(entry.getKey());
                }

                this.addModel(entry.getKey(), loader);
            }
            catch (Exception e)
            {
                e.printStackTrace();
                System.out.println("Error happened with " + entry.getKey());
            }
        }

        /* Remove unloaded models */
        for (String key : pack.removed)
        {
            this.removeModel(key);
        }

        lastUpdate = System.currentTimeMillis();
    }

    /**
     * Add model to the model handler
     */
    public void addModel(String key, IModelLazyLoader loader) throws Exception
    {
        Model model = loader.loadModel(key);

        this.models.put(key, model);
        this.addMorph(key, model);
    }

    protected void addMorph(String key, Model model)
    {
        if (morphSection != null)
        {
            morphSection.add(key, model, false);
        }
    }

    /**
     * Remove model from the model handler
     */
    public void removeModel(String key)
    {
        Model model = this.models.remove(key);

        if (model != null && morphSection != null)
        {
            morphSection.remove(key);
        }
    }
}
