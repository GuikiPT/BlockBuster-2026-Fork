package mchorse.blockbuster.client.render.tileentity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.client.RenderingHandler;
import mchorse.blockbuster.client.render.item.InertDrawer;
import mchorse.blockbuster.client.render.item.ItemRenderContext;
import mchorse.blockbuster.client.render.item.ItemStackDrawer;
import mchorse.blockbuster.common.tileentity.TileEntityModel;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.DynamicItemRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

/**
 * Model block's inventory renderer (roadmap P96).
 *
 * <p>Port of 1.12.2 {@code client/render/tileentity/TileEntityModelItemStackRenderer}
 * — the {@code TileEntityItemStackRenderer} (TEISR) replaced on Fabric by a
 * {@link DynamicItemRenderer}. Renders a model-block item by driving the shared
 * {@link TileEntityModelRenderer} on a {@link TileEntityModel} built from the
 * stack's {@code BlockEntityTag}.</p>
 *
 * <h2>Load-bearing quirks (kept verbatim)</h2>
 * <ul>
 * <li>The {@code tickDelta} passed in is <b>ignored</b>, replaced by
 * {@code MinecraftClient.getTickDelta()} ("Thank you Mojang, very cool!") so the
 * animation phase matches the world render exactly.</li>
 * <li>The cache is keyed by the <b>entire stack NBT compound</b>, not just
 * {@code BlockEntityTag} — two stacks differing only in display name cache
 * separately (guarantees display correctness).</li>
 * <li>Each render refreshes {@code model.timer = 5}; {@link #tickCache()} (driven
 * once per client tick, legacy {@code RenderingHandler}) decrements the timers
 * and evicts expired entries so the map cannot leak.</li>
 * <li>Empty NBT renders the shared {@link #def} default TE.</li>
 * <li>The static {@link #isRendering()} flag exposes the TEISR context to other
 * code.</li>
 * </ul>
 *
 * <p>The legacy blur/mipmap atlas reset after each render is unnecessary under
 * 1.20.4's render-layer system (it worked around 1.12 texture-state leakage);
 * its absence is deliberate.</p>
 *
 * <h2>GUI shading — why the model declares {@code gui_light: front}</h2>
 * <p>{@code models/block/model.json} (the parent of all 16 light-variant item
 * models) sets {@code "gui_light": "front"}, matching BBS's model-block item
 * model. JSON carries no comments, so the reasoning lives here.</p>
 *
 * <p>{@code DrawContext.drawItem} picks the GUI diffuse preset off
 * {@code BakedModel.isSideLit()}. Side-lit is the block default, and its preset
 * is rotated to suit the 30°/225° {@code gui} display transform an ordinary
 * block item carries. This model has no {@code gui} transform at all — it draws
 * upright and facing the viewer — and {@code drawItem}'s {@code scaling(1,-1,1)}
 * goes through {@code multiplyPositionMatrix}, which leaves the normal matrix
 * alone. So the normals reaching the shader are plain model space, and against
 * the side-lit preset the two faces actually on screen land on bare ambient
 * (up 0.40, toward-viewer 0.44) while the hidden underside and left side burn
 * to 1.0 — the model rendered near-black in the creative tab.</p>
 *
 * <p>{@code front} is the preset meant for exactly this case: up and
 * toward-viewer both light to 1.0. It only affects GUI item rendering — the
 * in-hand, dropped and item-frame paths never consult it — so this is not a
 * lighting change for the block or for the gun's first-person draw.
 * {@code item/model_static.json} is deliberately left alone: it parents to
 * {@code block/model_static}, a real cube that wants the block default.</p>
 *
 * <h2>P229: the single implementation</h2>
 * <p>A second, parallel model-block item renderer used to live in
 * {@code client/render/item/} (the P83 registration/plumbing phase). It was
 * never registered — {@code BlockbusterItemRenderers.registerModelBlockItem} had
 * zero callers — while this class was. The two have been merged into this one:
 * the P83 half contributed the {@link ItemRenderContext} plumbing (holder +
 * transform mode) and the <b>live config switch</b>, which was otherwise
 * missing here entirely, so {@code model_block_disable_item_rendering} was a
 * dead setting.</p>
 */
public class TileEntityModelItemStackRenderer implements DynamicItemRenderer
{
    /**
     * A cache of model TEs, keyed by the full stack NBT (legacy static map).
     */
    public static final Map<NbtCompound, TEModel> models = new HashMap<NbtCompound, TEModel>();

    private static boolean isRendering;

    /**
     * The shared BE renderer instance (legacy {@code ClientProxy.modelRenderer}).
     * Set at registration; falls back to a standalone instance.
     */
    public static TileEntityModelRenderer modelRenderer;

    /**
     * Live path (config off): render the stack's configured morph through the
     * shared {@link TileEntityModelRenderer}. Installed by
     * {@link ModelBlockRenderWiring} with {@link #drawLive(ItemRenderContext)};
     * inert (draws nothing, never throws) until then.
     */
    public static ItemStackDrawer liveDrawer = InertDrawer.INSTANCE;

    /**
     * Static path (config on): render the baked {@code model_static} cube
     * instead of the live morph — legacy's {@code ItemMeshDefinition} swap.
     * Installed by {@link ModelBlockRenderWiring} with
     * {@link ModelBlockStaticItemModel#draw(ItemRenderContext)}.
     */
    public static ItemStackDrawer staticDrawer = InertDrawer.INSTANCE;

    /**
     * Default tile entity model (rendered for stacks with no NBT).
     *
     * <p>Static because there is exactly one renderer instance in the first
     * place — legacy shared a single {@code TileEntityModelItemStackRenderer}
     * across all 16 light variants, as {@code BlockbusterItemRenderers.MODEL}
     * does now — and because the live draw is reached through a static drawer
     * seam.</p>
     */
    public static TileEntityModel def;

    public static boolean isRendering()
    {
        return isRendering;
    }

    @Override
    public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay)
    {
        this.render(ItemRenderContext.resolve(stack, mode, matrices, vertexConsumers, light, overlay));
    }

    /**
     * Config-switched dispatch, extracted so the branch is headless-testable
     * with stub drawers (no GL). Mirrors the transform mode into the
     * {@code RenderingHandler} global for parity with morph renderers that query
     * it, then routes to the static-vs-live path by a <b>live</b> config read —
     * legacy's mesh definition was consulted per stack, per frame, so the switch
     * must never be cached at registration.
     */
    public void render(ItemRenderContext context)
    {
        RenderingHandler.setTSRTTransform(context.mode);

        if (Blockbuster.modelBlockDisableItemRendering.get())
        {
            staticDrawer.draw(context);
        }
        else
        {
            liveDrawer.draw(context);
        }
    }

    /**
     * The live path: legacy {@code renderByItem} verbatim — build (or refresh) a
     * cached {@link TileEntityModel} from the stack's {@code BlockEntityTag} and
     * drive the shared block-entity renderer on it.
     */
    public static void drawLive(ItemRenderContext context)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null)
        {
            return;
        }

        /* Thank you Mojang, very cool! */
        float partialTicks = mc.getTickDelta();

        if (def == null)
        {
            def = new TileEntityModel();
        }

        NbtCompound tag = context.stack == null ? null : context.stack.getNbt();

        if (tag != null)
        {
            TEModel model = models.get(tag);

            if (model == null)
            {
                TileEntityModel te = new TileEntityModel();
                te.readNbt(tag.getCompound("BlockEntityTag"));

                model = new TEModel(te);
                models.put(tag, model);
            }

            /*
             * timer in ticks when to remove items that are not rendered anymore
             * 5 should be enough to ensure that even with very low fps the model
             * doesn't get removed unnecessarily
             */
            model.timer = 5;
            renderModel(model.model, context.matrices, context.vertexConsumers, context.light, context.overlay, partialTicks);

            return;
        }

        renderModel(def, context.matrices, context.vertexConsumers, context.light, context.overlay, partialTicks);
    }

    public static void renderModel(TileEntityModel model, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay, float partialTicks)
    {
        isRendering = true;

        try
        {
            TileEntityModelRenderer renderer = modelRenderer;

            if (renderer == null)
            {
                renderer = modelRenderer = new TileEntityModelRenderer(null);
            }

            renderer.render(model, partialTicks, matrices, vertexConsumers, light, overlay);
        }
        finally
        {
            isRendering = false;
        }
    }

    /**
     * Tick-based eviction (legacy {@code RenderingHandler} per-client-tick
     * sweep): decrement every cached model's timer and drop the expired ones.
     * Extracted as pure map logic so it is headlessly testable.
     */
    public static void tickCache()
    {
        Iterator<Map.Entry<NbtCompound, TEModel>> it = models.entrySet().iterator();

        while (it.hasNext())
        {
            TEModel model = it.next().getValue();

            model.timer--;

            if (model.timer <= 0)
            {
                it.remove();
            }
        }
    }

    /**
     * {@link TileEntityModel} wrapper holding an unload timer (so cached models
     * don't get stuck in the map forever, which could leak memory).
     */
    public static class TEModel
    {
        public int timer = 20;
        public TileEntityModel model;

        public TEModel(TileEntityModel model)
        {
            this.model = model;
        }
    }
}
