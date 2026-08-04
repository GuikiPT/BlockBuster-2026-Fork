package mchorse.blockbuster.api.loaders;

import mchorse.blockbuster.api.loaders.lazy.IModelLazyLoader;
import mchorse.blockbuster.api.loaders.lazy.ModelLazyLoaderOBJ;
import mchorse.blockbuster.api.resource.FileEntry;
import mchorse.blockbuster.api.resource.IResourceEntry;

import java.io.File;

/**
 * OBJ model detector.
 *
 * <p>Prefers {@code model.obj} (with the {@code model.mtl} sidecar), else the
 * first {@code *.obj} file in the folder with the sidecar {@code .mtl} derived
 * by renaming the extension. Also passes the {@code shapes} subfolder. Ported
 * from Blockbuster 2.7.2.</p>
 */
public class ModelLoaderOBJ implements IModelLoader
{
    @Override
    public IModelLazyLoader load(File folder)
    {
        IResourceEntry json = new FileEntry(new File(folder, "model.json"));
        File obj = new File(folder, "model.obj");
        File shapes = new File(folder, "shapes");

        if (obj.isFile())
        {
            File mtl = new File(folder, "model.mtl");

            return new ModelLazyLoaderOBJ(json, new FileEntry(obj), new FileEntry(mtl), shapes);
        }

        File[] files = folder.listFiles();

        if (files == null)
        {
            return null;
        }

        for (File file : files)
        {
            if (file.isFile() && file.getName().endsWith(".obj"))
            {
                String name = file.getName();
                File mtl = new File(folder, name.substring(0, name.length() - 3) + "mtl");

                return new ModelLazyLoaderOBJ(json, new FileEntry(file), new FileEntry(mtl), shapes);
            }
        }

        return null;
    }
}
