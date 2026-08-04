package mchorse.blockbuster.api;

import java.util.concurrent.Executor;

import mchorse.blockbuster.api.loaders.lazy.IModelLazyLoader;
import mchorse.blockbuster.client.model.ModelCustom;
import mchorse.blockbuster.client.model.parsing.ModelExtrudedLayer;
import net.minecraft.client.MinecraftClient;

/**
 * Client-side model handler (roadmap P69).
 *
 * <p>Differs from its {@link ModelHandler} parent by registering added models as
 * <b>client</b> morphs ({@link #addMorph} passes {@code true}), by compiling a
 * {@link ModelCustom} out of each added {@link Model} into
 * {@link ModelCustom#MODELS}, and by scheduling the removed model's GL cleanup
 * on the client thread.</p>
 *
 * <p>{@link ModelCustom#MODELS} is what every consumer of a compiled model reads
 * — the custom-morph renderer, the morph editors' viewports and the S12 model
 * editor — so this class is the only thing that fills it. The compile body
 * itself is {@link ModelClientLoader} (legacy put it on the loader as
 * {@code loadClientModel}; see that class for why it moved).</p>
 *
 * <p>Legacy source: {@code blockbuster-1.12/.../api/ModelClientHandler.java}.</p>
 */
public class ModelClientHandler extends ModelHandler
{
    /**
     * Render-thread executor seam — legacy {@code Minecraft.addScheduledTask},
     * needed because deleting GL resources off the render thread kicks the
     * player on an integrated server. Runs inline with no client (headless).
     */
    private Executor renderThread = defaultRenderThread();

    public void setRenderThread(Executor executor)
    {
        this.renderThread = executor == null ? defaultRenderThread() : executor;
    }

    @Override
    public void addModel(String key, IModelLazyLoader loader) throws Exception
    {
        super.addModel(key, loader);

        ModelCustom.MODELS.put(key, ModelClientLoader.loadClientModel(loader, key, this.models.get(key)));
    }

    @Override
    protected void addMorph(String key, Model model)
    {
        if (morphSection != null)
        {
            morphSection.add(key, model, true);
        }
    }

    @Override
    public void removeModel(String key)
    {
        super.removeModel(key);

        final ModelCustom model = ModelCustom.MODELS.remove(key);

        if (model == null)
        {
            return;
        }

        this.renderThread.execute(() ->
        {
            model.delete();
            ModelExtrudedLayer.clearByModel(model);
        });
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
