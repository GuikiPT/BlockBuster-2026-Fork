package mchorse.blockbuster.client.render.tileentity;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.client.render.item.ItemRenderContext;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * The model block item's "rendering disabled" sprite (roadmap P229).
 *
 * <p>1.12.2 implemented {@code model_block_disable_item_rendering} in
 * {@code ClientProxy.preLoad} with an {@code ItemMeshDefinition} that was
 * consulted <b>per stack, every frame</b>: config on → the plain baked cube
 * {@code blockbuster:model_static#inventory}; config off → the TEISR-backed
 * {@code blockbuster:model#inventory}. 1.20.4 has no mesh definition — a
 * {@code DynamicItemRenderer} owns the whole draw once the item's model is
 * {@code builtin/entity} — so the switch moved inside
 * {@link TileEntityModelItemStackRenderer#render(ItemRenderContext)} and this
 * class is the "config on" arm.</p>
 *
 * <h2>Why the model has to be registered for baking</h2>
 * Nothing references {@code models/item/model_static.json} any more (the 16
 * light-variant items all point at the builtin model), so the model loader
 * would never bake it. {@link ModelLoadingPlugin} {@code addModels} is the
 * supported way to ask for an extra bake, and {@link FabricBakedModelManager}
 * is the matching read side.
 *
 * <h2>The half-transform</h2>
 * By the time a {@code DynamicItemRenderer} is called, {@code ItemRenderer} has
 * already applied the <b>builtin</b> model's display transform for this mode and
 * the {@code (-0.5, -0.5, -0.5)} centring translate. Undoing only the centring
 * and re-entering {@code ItemRenderer.renderItem} with the static model lets
 * vanilla apply that model's own transform + centring, which is exactly what the
 * legacy mesh swap produced. The one residue is the builtin model's own
 * {@code head} display entry (the only one {@code item/model.json} declares),
 * which is applied on top for armour-stand / player heads — a config-gated
 * cosmetic deviation, recorded rather than papered over.
 *
 * Legacy source of truth: {@code blockbuster-1.12/.../ClientProxy.java:118-131}.
 */
public final class ModelBlockStaticItemModel
{
    /** The extra model to bake: {@code assets/blockbuster/models/item/model_static.json}. */
    public static final Identifier MODEL = new Identifier(Blockbuster.MOD_ID, "item/model_static");

    private static boolean registered;

    private ModelBlockStaticItemModel()
    {}

    /**
     * Ask the model loader to bake {@link #MODEL}. Idempotent — a second call
     * (client init plus a headless test in the same JVM) does nothing.
     */
    public static void register()
    {
        if (registered)
        {
            return;
        }

        registered = true;

        try
        {
            ModelLoadingPlugin.register(context -> context.addModels(MODEL));
        }
        catch (Throwable t)
        {
            /* Totality: without the extra bake the static arm falls back to
             * drawing nothing, which is what it did before this phase. */
            Blockbuster.LOGGER.warn("Could not register the model block's static item model for baking", t);
        }
    }

    /** True once {@link #register()} has run (regression pin for the wiring test). */
    public static boolean isRegistered()
    {
        return registered;
    }

    /**
     * The baked static cube, or null when it is missing (no client, no bake, or
     * a resource pack removed it) — in which case the item simply draws nothing
     * rather than drawing the "missing model" checkerboard.
     */
    public static BakedModel model()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null)
        {
            return null;
        }

        BakedModelManager manager = mc.getBakedModelManager();

        if (manager == null)
        {
            return null;
        }

        BakedModel model = ((FabricBakedModelManager) manager).getModel(MODEL);

        return model == null || model == manager.getMissingModel() ? null : model;
    }

    /**
     * The {@code staticDrawer} implementation installed by
     * {@link ModelBlockRenderWiring}.
     */
    public static void draw(ItemRenderContext context)
    {
        if (context == null || context.matrices == null || context.vertexConsumers == null || context.stack == null || context.stack.isEmpty())
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        BakedModel model = model();

        if (mc == null || model == null)
        {
            return;
        }

        MatrixStack matrices = context.matrices;

        matrices.push();

        try
        {
            /* Undo ItemRenderer's centring translate so renderItem can apply the
             * static model's own transform + centring (see the class note). */
            matrices.translate(0.5F, 0.5F, 0.5F);

            mc.getItemRenderer().renderItem(context.stack, context.mode, false, matrices,
                context.vertexConsumers, context.light, context.overlay, model);
        }
        finally
        {
            matrices.pop();
        }
    }
}
