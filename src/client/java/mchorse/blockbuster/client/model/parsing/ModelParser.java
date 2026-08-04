package mchorse.blockbuster.client.model.parsing;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mchorse.blockbuster.api.Model;
import mchorse.blockbuster.api.ModelLimb;
import mchorse.blockbuster.api.ModelLimb.ArmorSlot;
import mchorse.blockbuster.api.ModelLimb.Holding;
import mchorse.blockbuster.api.ModelPose;
import mchorse.blockbuster.api.ModelTransform;
import mchorse.blockbuster.api.formats.IMeshes;
import mchorse.blockbuster.client.model.ModelCustom;
import mchorse.blockbuster.client.model.ModelCustomRenderer;
import mchorse.metamorph.client.model.custom.CustomModelRegistry;

/**
 * Model parser (roadmap P75).
 *
 * <p>Port of Blockbuster 2.7.2's {@code client/model/parsing/ModelParser}. It
 * converts a parsed {@link Model} DTO into a renderable {@link ModelCustom}:
 * builds each limb renderer, wires the parent/child graph, collects
 * holding/armor arrays, injects same-named renderers into {@code IModelCustom}
 * fields via reflection, and is <b>total</b> — any exception logs and returns
 * {@code null} rather than crashing.</p>
 *
 * <p>The per-limb {@link IMeshes} dispatch that legacy did via
 * {@code meshes.createRenderer(...)} is delegated to
 * {@link MeshRendererFactory} (S5/S6 source-set split).</p>
 */
public class ModelParser
{
    public String key;
    public Map<String, IMeshes> meshes;

    public static ModelCustom parse(String key, Model data)
    {
        return parse(key, data, ModelCustom.class, null);
    }

    public static ModelCustom parse(String key, Model data, Map<String, IMeshes> meshes)
    {
        return parse(key, data, ModelCustom.class, meshes);
    }

    public static ModelCustom parse(String key, Model data, Class<? extends ModelCustom> clazz, Map<String, IMeshes> meshes)
    {
        try
        {
            return new ModelParser(key, meshes).parseModel(data, clazz);
        }
        catch (Exception e)
        {
            System.out.println("Model for key '" + key + "' couldn't converted to ModelCustom!");
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Resolve a legacy {@code model.model} animator class-name string to a class
     * and parse with it. Blockbuster 2.7.2 did this with
     * {@code Class.forName(model.model)} in {@code ModelLazyLoaderJSON}; the port
     * routes the string through {@link CustomModelRegistry}'s explicit allow-list
     * so an unknown / dropped animator name logs a warning and falls back to a
     * plain {@link ModelCustom} instead of loading an arbitrary class or crashing
     * (total-reader rule).
     */
    public static ModelCustom parse(String key, Model data, String modelClass, Map<String, IMeshes> meshes)
    {
        Class<? extends ModelCustom> clazz = CustomModelRegistry.resolve(modelClass);

        return parse(key, data, clazz == null ? ModelCustom.class : clazz, meshes);
    }

    public ModelParser(String key, Map<String, IMeshes> meshes)
    {
        if (meshes == null)
        {
            meshes = new HashMap<String, IMeshes>();
        }

        this.key = key;
        this.meshes = meshes;
    }

    public ModelCustom parseModel(Model data, Class<? extends ModelCustom> clazz) throws Exception
    {
        ModelCustom model = clazz.getConstructor(Model.class).newInstance(data);
        this.generateLimbs(data, model);

        if (model instanceof IModelCustom)
        {
            ((IModelCustom) model).onGenerated();
        }

        return model;
    }

    protected void generateLimbs(Model data, ModelCustom model)
    {
        Map<String, ModelCustomRenderer> limbs = new HashMap<String, ModelCustomRenderer>();
        List<ModelCustomRenderer> renderable = new ArrayList<ModelCustomRenderer>();

        List<ModelCustomRenderer> left = new ArrayList<ModelCustomRenderer>();
        List<ModelCustomRenderer> right = new ArrayList<ModelCustomRenderer>();
        List<ModelCustomRenderer> armor = new ArrayList<ModelCustomRenderer>();

        ModelPose standing = data.poses.get("standing");

        /* First, iterate to create every limb */
        for (Map.Entry<String, ModelLimb> entry : data.limbs.entrySet())
        {
            ModelLimb limb = entry.getValue();
            ModelTransform transform = standing.limbs.get(entry.getKey());

            ModelCustomRenderer renderer = this.createRenderer(model, data, limb, transform);

            if (limb.holding == Holding.LEFT) left.add(renderer);
            if (limb.holding == Holding.RIGHT) right.add(renderer);
            if (limb.slot != ArmorSlot.NONE) armor.add(renderer);

            limbs.put(entry.getKey(), renderer);
        }

        /* Then, iterate to attach child to their parents */
        for (Map.Entry<String, ModelCustomRenderer> entry : limbs.entrySet())
        {
            ModelLimb limb = data.limbs.get(entry.getKey());

            if (!limb.parent.isEmpty())
            {
                limbs.get(limb.parent).addChild(entry.getValue());
            }
            else
            {
                renderable.add(entry.getValue());
            }

            /* Inject ModelCustomRenderers into the model's fields */
            if (model instanceof IModelCustom)
            {
                try
                {
                    Field field = model.getClass().getField(entry.getKey());

                    if (field != null)
                    {
                        field.set(model, entry.getValue());
                    }
                }
                catch (Exception e)
                {
                    System.err.println("Field '" + entry.getKey() + "' was not found or is not accessible for " + model.getClass().getSimpleName());
                }
            }
        }

        model.left = left.toArray(new ModelCustomRenderer[left.size()]);
        model.right = right.toArray(new ModelCustomRenderer[right.size()]);
        model.armor = armor.toArray(new ModelCustomRenderer[armor.size()]);

        model.limbs = limbs.values().toArray(new ModelCustomRenderer[limbs.size()]);
        model.renderable = renderable.toArray(new ModelCustomRenderer[renderable.size()]);
    }

    protected ModelCustomRenderer createRenderer(ModelCustom model, Model data, ModelLimb limb, ModelTransform transform)
    {
        ModelCustomRenderer renderer = null;

        float w = limb.size[0];
        float h = limb.size[1];
        float d = limb.size[2];

        /* X anchor is inverted; Y/Z use the raw anchor. */
        float ax = 1 - limb.anchor[0];
        float ay = limb.anchor[1];
        float az = limb.anchor[2];

        IMeshes meshes = this.meshes.get(limb.name);

        if (meshes != null)
        {
            renderer = MeshRendererFactory.create(meshes, data, model, limb, transform);
        }

        if (renderer == null)
        {
            renderer = new ModelCustomRenderer(model, limb, transform);
            renderer.mirror = limb.mirror;
            renderer.addBox(-ax * w, -ay * h, -az * d, (int) w, (int) h, (int) d, limb.sizeOffset);
        }

        renderer.applyTransform(transform);

        return renderer;
    }
}
