package mchorse.blockbuster.api;

import java.util.Map;
import java.util.concurrent.Executor;

import mchorse.blockbuster.api.formats.IMeshes;
import mchorse.blockbuster.api.loaders.lazy.IModelLazyLoader;
import mchorse.blockbuster.api.loaders.lazy.ModelLazyLoaderVOX;
import mchorse.blockbuster.client.model.ModelCustom;
import mchorse.blockbuster.client.model.parsing.ModelExtrudedLayer;
import mchorse.blockbuster.client.model.parsing.ModelParser;
import net.minecraft.client.MinecraftClient;

/**
 * The client half of the lazy model loaders (roadmap P67/P69 &times; S6) —
 * legacy {@code IModelLazyLoader.loadClientModel(String, Model)}.
 *
 * <p>Legacy declared this {@code @SideOnly(Side.CLIENT)} on the loader
 * interface, with the body on {@code ModelLazyLoaderJSON} and a one-line
 * override on {@code ModelLazyLoaderVOX}. Here the loaders live in the main
 * source set and cannot name {@link ModelCustom}, so the body moves to this
 * client-set utility and takes the loader as its first argument — the split the
 * S05 plan prescribes ("a client {@code ModelClientLoader} utility performs
 * {@code ModelCustom} compilation in S6"). Everything the body does is legacy
 * verbatim:</p>
 *
 * <ol>
 * <li><b>GC the outgoing model.</b> The previous {@link ModelCustom} for this
 * key has its extruded-layer geometry dropped on the render thread — legacy
 * scheduled it through {@code Minecraft.addScheduledTask} because freeing GL
 * resources off the render thread kicks the player on an integrated server.
 * Note legacy read the <b>old</b> model out of {@code MODELS} <i>before</i>
 * compiling the new one and captured it in the task, so the cleanup targets the
 * model being replaced; kept.</li>
 * <li><b>Mesh-derived limbs.</b> Every mesh name the loader reports that the
 * blueprint has no limb for is added to the blueprint ({@code model.addLimb}),
 * which is how an OBJ/VOX file's groups become limbs of a model.json that never
 * mentioned them.</li>
 * <li><b>The animator-class hook.</b> A non-empty {@code model.model} selects a
 * {@code ModelCustom} subclass. Legacy did {@code Class.forName} and fell back
 * to a plain parse on {@code ClassNotFoundException}; the port routes the string
 * through {@code CustomModelRegistry}'s allow-list inside
 * {@link ModelParser#parse(String, Model, String, Map)}, which has the same
 * fall-back shape without loading an arbitrary class.</li>
 * <li><b>The VOX document cache</b> is cleared afterwards
 * ({@link ModelLazyLoaderVOX#clearCachedDocument()}) — the document is shared
 * between {@code loadModel} and {@code getMeshes} and must not outlive the
 * compile.</li>
 * </ol>
 */
public final class ModelClientLoader
{
    /**
     * Render-thread executor seam, mirroring
     * {@code LimbGeometryCache.renderThread}: GL cleanup is scheduled onto the
     * client thread in game and runs inline when there is no client (headless
     * tests).
     */
    private static Executor renderThread = defaultRenderThread();

    private ModelClientLoader()
    {}

    public static void setRenderThread(Executor executor)
    {
        renderThread = executor == null ? defaultRenderThread() : executor;
    }

    /**
     * Compile a {@link ModelCustom} for {@code key} out of {@code model}, using
     * whatever mesh data {@code loader} provides. Returns {@code null} when the
     * parse fails ({@link ModelParser} is total and logs).
     */
    public static ModelCustom loadClientModel(IModelLazyLoader loader, String key, Model model) throws Exception
    {
        /* GC the old model */
        final ModelCustom previous = ModelCustom.MODELS.get(key);

        renderThread.execute(() -> ModelExtrudedLayer.clearByModel(previous));

        Map<String, IMeshes> meshes = loader == null ? null : loader.getMeshes(key, model);

        if (meshes != null)
        {
            for (String limb : meshes.keySet())
            {
                if (!model.limbs.containsKey(limb))
                {
                    model.addLimb(limb);
                }
            }
        }

        try
        {
            if (!model.model.isEmpty())
            {
                return ModelParser.parse(key, model, model.model, meshes);
            }

            return ModelParser.parse(key, model, ModelCustom.class, meshes);
        }
        finally
        {
            if (loader instanceof ModelLazyLoaderVOX)
            {
                ((ModelLazyLoaderVOX) loader).clearCachedDocument();
            }
        }
    }

    private static Executor defaultRenderThread()
    {
        return task ->
        {
            MinecraftClient mc = MinecraftClient.getInstance();

            if (mc != null)
            {
                mc.execute(task);
            }
            else
            {
                task.run();
            }
        };
    }
}
