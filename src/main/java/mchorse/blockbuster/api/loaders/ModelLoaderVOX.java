package mchorse.blockbuster.api.loaders;

import mchorse.blockbuster.api.loaders.lazy.IModelLazyLoader;
import mchorse.blockbuster.api.loaders.lazy.ModelLazyLoaderVOX;
import mchorse.blockbuster.api.resource.FileEntry;
import mchorse.blockbuster.api.resource.IResourceEntry;

import java.io.File;

/**
 * VOX model detector.
 *
 * <p>Prefers {@code model.vox}, else the first {@code *.vox} file in the folder.
 * Ported from Blockbuster 2.7.2.</p>
 */
public class ModelLoaderVOX implements IModelLoader
{
    @Override
    public IModelLazyLoader load(File folder)
    {
        IResourceEntry json = new FileEntry(new File(folder, "model.json"));
        File vox = new File(folder, "model.vox");

        if (vox.isFile())
        {
            return new ModelLazyLoaderVOX(json, new FileEntry(vox));
        }

        File[] files = folder.listFiles();

        if (files == null)
        {
            return null;
        }

        for (File file : files)
        {
            if (file.isFile() && file.getName().endsWith(".vox"))
            {
                return new ModelLazyLoaderVOX(json, new FileEntry(file));
            }
        }

        return null;
    }
}
