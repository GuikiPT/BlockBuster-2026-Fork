package mchorse.blockbuster.api.loaders;

import mchorse.blockbuster.api.loaders.lazy.IModelLazyLoader;

import java.io.File;

/**
 * Model loader interface.
 *
 * <p>Detects whether a special model format can be loaded from a folder. Ported
 * from Blockbuster 2.7.2.</p>
 */
public interface IModelLoader
{
    public IModelLazyLoader load(File folder);
}
