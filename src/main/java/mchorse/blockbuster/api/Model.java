package mchorse.blockbuster.api;

import com.google.common.base.MoreObjects;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import mchorse.blockbuster.api.formats.obj.OBJMaterial;
import mchorse.blockbuster.api.json.ModelAdapter;
import mchorse.blockbuster.api.json.ModelLimbAdapter;
import mchorse.mclib.utils.resources.RLUtils;
import mchorse.mclib.utils.resources.ResourceLocation;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Model class
 *
 * This class is a domain object that holds all information about the model like
 * its name, texture size, limbs, poses, interests and stuff.
 */
public class Model
{
    /**
     * Poses that are required by custom models
     */
    public static final List<String> REQUIRED_POSES = Arrays.asList("standing");

    /**
     * Scheme version. Would be used in future versions for extracting and
     * exporting purposes.
     */
    @Expose
    public String scheme = "1.3";

    /**
     * Not really sure what to do with this one.
     */
    @Expose
    public String name = "";

    /**
     * Default texture for this model
     */
    @Expose
    public ResourceLocation defaultTexture;

    /**
     * Texture size. First element is width, second is height.
     */
    @Expose
    public int[] texture = new int[] {64, 32};

    /**
     * Extrude max factor (allows to limit how many extruded sub levels you can have)
     */
    @Expose
    public int extrudeMaxFactor = 1;

    /**
     * Extrude inwards factor (allows to extrude inwards more bits)
     */
    @Expose
    public int extrudeInwards = 1;

    /**
     * Scale of the model
     */
    @Expose
    public float[] scale = new float[] {1, 1, 1};

    /**
     * Scale to be displayed in GUI
     */
    @Expose
    public float scaleGui = 1;

    /**
     * Class for the custom model
     */
    @Expose
    public String model = "";

    /**
     * Does this model provides OBJ model
     */
    @Expose
    public boolean providesObj = false;

    /**
     * Does this model provides MTL file
     */
    @Expose
    public boolean providesMtl = false;

    @Expose
    public boolean legacyObj = true;

    /**
     * Skins folder
     */
    @Expose
    public String skins = "";

    @Expose
    public Map<String, ModelLimb> limbs = new HashMap<String, ModelLimb>();

    @Expose
    public Map<String, ModelPose> poses = new HashMap<String, ModelPose>();

    @Expose
    public Map<String, String> presets = new HashMap<String, String>();

    public Map<String, OBJMaterial> materials = new HashMap<String, OBJMaterial>();

    public List<String> shapes = new ArrayList<String>();

    /**
     * Parse model from input stream
     */
    public static Model parse(InputStream stream) throws Exception
    {
        Scanner scanner = new Scanner(stream, "UTF-8");

        Model model = parse(scanner.useDelimiter("\\A").next());
        scanner.close();

        return model;
    }

    /**
     * This method parses an instance of Model class from provided JSON string.
     * This method also checks if model has all required poses for playing.
     */
    public static Model parse(String json) throws Exception
    {
        Gson gson = new GsonBuilder().registerTypeAdapter(Model.class, new ModelAdapter()).registerTypeAdapter(ModelLimb.class, new ModelLimbAdapter()).excludeFieldsWithoutExposeAnnotation().create();
        Model data = gson.fromJson(json, Model.class);

        for (String key : REQUIRED_POSES)
        {
            if (!data.poses.containsKey(key))
            {
                //throw new Exception(I18n.format("blockbuster.parsing.lacks_pose", data.name, key));
                throw new Exception("Error parsing model " + data.name + ": missing pose " + key);
            }
        }

        if (data.limbs.isEmpty())
        {
            //throw new Exception(I18n.format("blockbuster.parsing.lacks_limbs", data.name));
            throw new Exception("Error parsing model " + data.name + ": missing limbs");
        }

        data.fillInMissing();

        return data;
    }

    /**
     * Checks whether this model has textured materials
     */
    public boolean hasTexturedMaterials()
    {
        if (this.materials.isEmpty())
        {
            return false;
        }

        for (OBJMaterial material : this.materials.values())
        {
            if (material.useTexture) return true;
        }

        return false;
    }

    /**
     * Add a limb into a model
     */
    public ModelLimb addLimb(String name)
    {
        return this.addLimb(new ModelLimb(name));
    }

    /**
     * Add a limb into a model
     */
    public ModelLimb addLimb(ModelLimb limb)
    {
        this.limbs.put(limb.name, limb);

        for (ModelPose pose : this.poses.values())
        {
            pose.limbs.put(limb.name, new ModelTransform());
        }

        return limb;
    }

    /**
     * Remove limb from a model
     *
     * If given any limb in the model is child of this limb, then they're
     * also getting removed.
     */
    public void removeLimb(ModelLimb limb)
    {
        this.limbs.remove(limb.name);

        List<ModelLimb> limbsToRemove = new ArrayList<ModelLimb>();

        for (ModelLimb child : this.limbs.values())
        {
            if (child.parent.equals(limb.name))
            {
                limbsToRemove.add(child);
            }
        }

        for (ModelPose pose : this.poses.values())
        {
            pose.limbs.remove(limb.name);
        }

        for (ModelLimb limbToRemove : limbsToRemove)
        {
            this.removeLimb(limbToRemove);
        }
    }

    /**
     * Rename given limb (this limb should already exist in this model)
     * @return
     */
    public boolean renameLimb(ModelLimb limb, String newName)
    {
        if (this.limbs.containsKey(newName) || !this.limbs.containsValue(limb))
        {
            return false;
        }

        /* Rename limb name in poses */
        for (ModelPose pose : this.poses.values())
        {
            ModelTransform transform = pose.limbs.remove(limb.name);

            pose.limbs.put(newName, transform);
        }

        /* Rename all children limbs */
        for (ModelLimb child : this.limbs.values())
        {
            if (child.parent.equals(limb.name))
            {
                child.parent = newName;
            }
        }

        /* And finally remap the limb name to the new name */
        this.limbs.remove(limb.name);
        this.limbs.put(newName, limb);
        limb.name = newName;

        return true;
    }

    /**
     * Returns amount of limbs given limb hosts
     */
    public int getLimbCount(ModelLimb parent)
    {
        int count = 1;

        for (ModelLimb child : this.limbs.values())
        {
            if (child.parent.equals(parent.name))
            {
                count += this.getLimbCount(child);
            }
        }

        return count;
    }

    /**
     * Fill in missing transforms and assign name to every limb
     */
    public void fillInMissing()
    {
        /* Total readers (CLAUDE.md): Gson deserializes a hand-written
         * "scale": [1, 2] verbatim into a 2-element float[], and every render
         * path indexes [0..2] — an ArrayIndexOutOfBounds straight out of
         * drawUserModel. Normalise every fixed-length array instead. */
        this.scale = normalize(this.scale, 1F, 1F, 1F);
        this.texture = normalize(this.texture, 64, 32);

        for (Map.Entry<String, ModelLimb> entry : this.limbs.entrySet())
        {
            String key = entry.getKey();
            ModelLimb limb = entry.getValue();

            limb.anchor = normalize(limb.anchor, 0.5F, 0.5F, 0.5F);
            limb.color = normalize(limb.color, 1F, 1F, 1F);
            limb.origin = normalize(limb.origin, 0F, 0F, 0F);
            limb.size = normalize(limb.size, 4, 4, 4);
            limb.texture = normalize(limb.texture, 0, 0);

            for (ModelPose pose : this.poses.values())
            {
                if (!pose.limbs.containsKey(key))
                {
                    pose.limbs.put(key, new ModelTransform());
                }
            }

            entry.getValue().name = key;
        }

        for (ModelPose pose : this.poses.values())
        {
            pose.size = normalize(pose.size, 1F, 1F, 1F);

            for (ModelTransform transform : pose.limbs.values())
            {
                transform.translate = normalize(transform.translate, 0F, 0F, 0F);
                transform.scale = normalize(transform.scale, 1F, 1F, 1F);
                transform.rotate = normalize(transform.rotate, 0F, 0F, 0F);
            }
        }
    }

    /**
     * Pad/truncate a {@code float[]} to the length of {@code defaults}, keeping
     * whatever values the file did supply and filling the rest from the
     * defaults. A {@code null} array becomes a copy of the defaults.
     */
    public static float[] normalize(float[] array, float... defaults)
    {
        if (array != null && array.length == defaults.length)
        {
            return array;
        }

        float[] result = defaults.clone();

        if (array != null)
        {
            System.arraycopy(array, 0, result, 0, Math.min(array.length, result.length));
        }

        return result;
    }

    /** {@code int[]} variant of {@link #normalize(float[], float...)}. */
    public static int[] normalize(int[] array, int... defaults)
    {
        if (array != null && array.length == defaults.length)
        {
            return array;
        }

        int[] result = defaults.clone();

        if (array != null)
        {
            System.arraycopy(array, 0, result, 0, Math.min(array.length, result.length));
        }

        return result;
    }

    /**
     * Get pose, or return default pose (which is the "standing" pose)
     */
    public ModelPose getPose(String key)
    {
        ModelPose pose = this.poses.get(key);

        return pose == null ? this.poses.get("standing") : pose;
    }

    /**
     * Clone a model
     */
    public Model copy()
    {
        Model b = new Model();

        b.texture = new int[] {this.texture[0], this.texture[1]};
        b.scale = new float[] {this.scale[0], this.scale[1], this.scale[2]};
        b.scaleGui = this.scaleGui;

        b.name = this.name;
        b.scheme = this.scheme;
        b.model = this.model;

        b.defaultTexture = this.defaultTexture == null ? null : RLUtils.clone(this.defaultTexture);
        b.providesObj = this.providesObj;
        b.providesMtl = this.providesMtl;
        b.legacyObj = this.legacyObj;
        b.skins = this.skins;

        for (Map.Entry<String, ModelLimb> entry : this.limbs.entrySet())
        {
            b.limbs.put(entry.getKey(), entry.getValue().clone());
        }

        for (Map.Entry<String, ModelPose> entry : this.poses.entrySet())
        {
            b.poses.put(entry.getKey(), entry.getValue().copy());
        }

        b.presets.putAll(this.presets);
        b.shapes.addAll(this.shapes);

        return b;
    }

    public List<String> getChildren(String limb)
    {
        Map<String, List<String>> tree = new HashMap<String, List<String>>();

        for (Map.Entry<String, ModelLimb> entry : this.limbs.entrySet())
        {
            String parent = entry.getValue().parent;

            if (tree.get(parent) == null)
            {
                tree.put(parent, new ArrayList<String>());
            }

            tree.get(parent).add(entry.getKey());
        }

        List<String> children = new ArrayList<String>();

        this.getChildren(tree, limb, children);

        return children;
    }

    private void getChildren(Map<String, List<String>> tree, String limb, List<String> out)
    {
        List<String> children = tree.get(limb);

        if (children != null)
        {
            out.addAll(children);

            for (String child : children)
            {
                getChildren(tree, child, out);
            }
        }
    }

    @Override
    public String toString()
    {
        return MoreObjects.toStringHelper(this).add("scheme", this.scheme).add("name", this.name).add("texture", Arrays.toString(this.texture)).add("limbs", this.limbs).add("poses", this.poses).toString();
    }
}
