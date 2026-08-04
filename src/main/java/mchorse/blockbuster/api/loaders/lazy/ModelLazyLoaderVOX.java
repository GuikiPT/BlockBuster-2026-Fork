package mchorse.blockbuster.api.loaders.lazy;

import mchorse.blockbuster.api.Model;
import mchorse.blockbuster.api.ModelLimb;
import mchorse.blockbuster.api.ModelPose;
import mchorse.blockbuster.api.ModelTransform;
import mchorse.blockbuster.api.formats.IMeshes;
import mchorse.blockbuster.api.formats.vox.VoxDocument;
import mchorse.blockbuster.api.formats.vox.VoxReader;
import mchorse.blockbuster.api.resource.IResourceEntry;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Lazy loader for MagicaVoxel (.vox) based models.
 *
 * <p>Ported from Blockbuster 2.7.2. The generated model reuses the OBJ pipeline
 * flags: {@code providesObj = true} and {@code providesMtl = true} even though
 * no OBJ files exist — downstream renderer checks {@code providesObj}; this is
 * intentional and must not be "corrected". The {@link #cachedDocument} is shared
 * between {@link #loadModel(String)} and {@code getMeshes}.</p>
 */
public class ModelLazyLoaderVOX extends ModelLazyLoaderJSON
{
    public IResourceEntry vox;

    private VoxDocument cachedDocument;

    public ModelLazyLoaderVOX(IResourceEntry model, IResourceEntry vox)
    {
        super(model);

        this.vox = vox;
    }

    @Override
    public int count()
    {
        return super.count() + (this.vox.exists() ? 2 : 0);
    }

    @Override
    public boolean hasChanged()
    {
        return super.hasChanged() || this.vox.hasChanged();
    }

    @Override
    public Model loadModel(String key) throws Exception
    {
        Model model = null;

        try
        {
            model = super.loadModel(key);
        }
        catch (Exception e) {}

        if (model == null)
        {
            model = this.generateVOXModel(key);
        }

        return model;
    }

    @Override
    public Map<String, IMeshes> getMeshes(String key, Model model) throws Exception
    {
        Map<String, IMeshes> meshes = new HashMap<String, IMeshes>();
        VoxDocument document = this.getVox();

        for (VoxDocument.LimbNode node : document.generate())
        {
            /* SEAM(P78): the legacy body constructs
             * `new MeshesVOX(document, node)` here. MeshesVOX (the IMeshes impl
             * with the GL-lazy VoxBuilder) lands with P78 in Stage S6. Until
             * then this loop resolves the scene graph (exercising VoxDocument)
             * but produces no mesh entries. loadModel/generateVOXModel below are
             * fully functional and headlessly testable. */
            @SuppressWarnings("unused")
            VoxDocument.LimbNode resolved = node;
        }

        return meshes;
    }

    /**
     * Generate custom model based on given VOX
     */
    private Model generateVOXModel(String model) throws Exception
    {
        /* Generate custom model for a VOX model */
        Model data = new Model();
        ModelPose blocky = new ModelPose();

        /* Generate limbs */
        VoxDocument document = this.getVox();

        for (VoxDocument.LimbNode node : document.generate())
        {
            ModelLimb limb = data.addLimb(node.name);
            ModelTransform transform = new ModelTransform();

            limb.origin[0] = 0;
            limb.origin[1] = 0;
            limb.origin[2] = 0;

            transform.translate[0] = -node.translation.x;
            transform.translate[1] = node.translation.z;
            transform.translate[2] = -node.translation.y;

            blocky.limbs.put(limb.name, transform);
        }

        /* General model properties */
        data.providesObj = true;
        data.providesMtl = true;

        blocky.setSize(1, 1, 1);
        data.poses.put("flying", blocky.copy());
        data.poses.put("standing", blocky.copy());
        data.poses.put("sneaking", blocky.copy());
        data.poses.put("sleeping", blocky.copy());
        data.poses.put("riding", blocky.copy());
        data.name = model;

        return data;
    }

    private VoxDocument getVox() throws Exception
    {
        if (this.cachedDocument != null)
        {
            return this.cachedDocument;
        }

        return this.cachedDocument = new VoxReader().read(this.vox.getStream());
    }

    /**
     * SEAM(S6): the legacy {@code loadClientModel} clears {@link #cachedDocument}
     * after building the client model. When S6 adds the client model compilation,
     * it must null out {@link #cachedDocument} at the end. Exposed for that hook.
     */
    public void clearCachedDocument()
    {
        this.cachedDocument = null;
    }

    @Override
    public boolean copyFiles(File folder)
    {
        boolean skins = super.copyFiles(folder);
        boolean vox = this.vox.copyTo(new File(folder, this.vox.getName()));

        return skins || vox;
    }
}
