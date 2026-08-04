package mchorse.blockbuster.api.loaders.lazy;

import mchorse.blockbuster.api.Model;
import mchorse.blockbuster.api.formats.IMeshes;

import java.io.File;
import java.util.Map;

/**
 * Lazy model loader.
 *
 * <p>This class is responsible for creation of {@link Model} instances (and,
 * client side, {@code ModelCustom} classes). Ported from Blockbuster 2.7.2.</p>
 *
 * <p><b>The client half.</b> The legacy interface also declares
 * {@code @SideOnly(Side.CLIENT) ModelCustom loadClientModel(String, Model)}.
 * {@code ModelCustom} lives in the client source set, which the main set cannot
 * name, so that method is not on this interface: it is
 * {@code ModelClientLoader.loadClientModel(loader, key, model)} instead (the
 * split the S05 plan prescribes). The data-only {@code getMeshes} half legacy
 * kept {@code protected} on {@code ModelLazyLoaderJSON} <b>is</b> declared here,
 * because that is what the client compiler needs from an arbitrary loader and
 * mesh production involves no GL.</p>
 */
public interface IModelLazyLoader
{
    public long getLastTime();

    public void setLastTime(long time);

    public boolean stillExists();

    public boolean hasChanged();

    public boolean copyFiles(File folder);

    public Model loadModel(String key) throws Exception;

    /**
     * The per-limb mesh data this loader contributes (OBJ/VOX loaders; the plain
     * JSON loader has none). Legacy's base implementation returned {@code null}
     * and that is the default here — {@code null} means "no meshes", distinct
     * from an empty map.
     */
    default Map<String, IMeshes> getMeshes(String key, Model model) throws Exception
    {
        return null;
    }
}
