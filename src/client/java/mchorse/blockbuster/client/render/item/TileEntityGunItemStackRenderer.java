package mchorse.blockbuster.client.render.item;

import mchorse.blockbuster.client.KeyboardHandler;
import mchorse.blockbuster.client.RenderingHandler;
import mchorse.blockbuster.client.render.GunItemContextRouter;
import mchorse.blockbuster.client.render.GunPropsRenderer;
import mchorse.blockbuster.common.GunProps;
import mchorse.blockbuster.common.item.GunState;
import mchorse.blockbuster.utils.NBTUtils;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The gun-as-item renderer (P197).
 *
 * <p>Port of the 1.12.2 {@code TileEntityGunItemStackRenderer} onto the Fabric
 * {@link BuiltinItemRendererRegistry.DynamicItemRenderer} seam. It keeps the
 * identity-keyed {@code Map<ItemStack, GunEntry>} render cache, the 5-tick
 * eviction timer, and the transform-context routing (which of hands / zoom
 * overlay / gun / inventory morphs draw) — the routing decision is the pure
 * {@link GunItemContextRouter} and the actual draws go through
 * {@link GunPropsRenderer} (S6 morph pipeline seam).</p>
 *
 * <p>The legacy {@code RenderingHandler.itemTransformType} side-channel is gone:
 * the {@code DynamicItemRenderer.render} callback receives the
 * {@link ModelTransformationMode} directly.</p>
 *
 * <p>{@code models/item/gun.json} sets {@code "gui_light": "front"} for the same
 * reason the model block's does — it is the same upright, viewer-facing
 * {@code builtin/entity} draw, and the side-lit default leaves every visible
 * face on bare ambient in the creative tab. See
 * {@code TileEntityModelItemStackRenderer}'s "GUI shading" note; it affects GUI
 * item rendering only, never the first-person / hands draws.</p>
 *
 * Legacy source of truth:
 * {@code blockbuster-1.12/.../client/render/tileentity/TileEntityGunItemStackRenderer.java}.
 */
public class TileEntityGunItemStackRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer
{
    /**
     * The identity-keyed render cache. Vanilla {@link ItemStack} has no
     * {@code equals}/{@code hashCode} override, so this {@link HashMap} is
     * effectively an identity map — the legacy {@code ClientHandlerGunInfo}
     * state-transplant only works because it fetches the current mainhand
     * instance. Do not switch to an NBT-keyed map.
     */
    public static final Map<ItemStack, GunEntry> models = new HashMap<ItemStack, GunEntry>();

    private static boolean isRendering;

    /* P229: the vestigial `drawer` ItemStackDrawer seam that used to sit here is
     * gone. It was never read — {@link #render} has routed straight through
     * GunItemContextRouter + GunPropsRenderer since P197, and GunPropsRenderer's
     * own GunMorphRenderer.drawer seam is the one the morph pipeline installs
     * (BlockbusterMorphRenderers.register). Keeping a second, unread seam only
     * made the gun renderer look unwired when it is the one item renderer that
     * always was. */

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
     * Dispatch extracted for readability. Mirrors the transform mode into the
     * {@code RenderingHandler} global (legacy compat) and routes the gun render.
     */
    public void render(ItemRenderContext context)
    {
        RenderingHandler.setTSRTTransform(context.mode);

        isRendering = true;

        try
        {
            GunEntry entry = touch(context.stack);

            if (entry == null)
            {
                return;
            }

            GunProps props = entry.props;
            boolean zooming = KeyboardHandler.zoom != null && KeyboardHandler.zoom.isPressed();

            GunItemContextRouter.Decision decision = GunItemContextRouter.route(
                context.mode,
                zooming,
                props.hideHandsOnZoom,
                props.useZoomOverlayMorph,
                props.useInventoryMorph,
                props.inventoryMorph != null
            );

            LivingEntity holder = context.holder instanceof LivingEntity ? (LivingEntity) context.holder : null;
            boolean firstPerson = GunItemContextRouter.isFirstPerson(context.mode);
            float partialTicks = tickDelta();

            if (decision.hands)
            {
                GunPropsRenderer.renderHands(props, holder, context.matrices, context.vertexConsumers, context.light, partialTicks, firstPerson);
            }

            if (decision.zoomOverlay)
            {
                GunPropsRenderer.renderZoomOverlay(props, holder, context.matrices, context.vertexConsumers, context.light, partialTicks);
            }

            if (decision.gun)
            {
                GunPropsRenderer.render(props, holder, context.matrices, context.vertexConsumers, context.light, partialTicks, firstPerson);
            }

            if (decision.inventory)
            {
                GunPropsRenderer.renderInventoryMorph(props, holder, context.matrices, context.vertexConsumers, context.light, partialTicks);
            }
        }
        finally
        {
            isRendering = false;
        }
    }

    private static float tickDelta()
    {
        /* Legacy overrode its partialTicks with getRenderPartialTicks()
         * ("Thank you Mojang, very cool!"); DynamicItemRenderer has no
         * partialTicks parameter, so read the client tick delta. */
        return MinecraftClient.getInstance().getTickDelta();
    }

    /* ------------------------------------------------------------------ *
     * Cache lifecycle (headlessly testable, no GL)                        *
     * ------------------------------------------------------------------ */

    /**
     * Fetch (or lazily create) the cache entry for a stack and refresh its
     * 5-tick eviction timer. Returns null only for a non-gun stack
     * ({@code getGunProps} yields null). Legacy {@code renderByItem} cache head.
     */
    public static GunEntry touch(ItemStack stack)
    {
        GunEntry entry = models.get(stack);

        if (entry == null)
        {
            GunProps props = NBTUtils.getGunProps(stack);

            if (props == null)
            {
                return null;
            }

            entry = new GunEntry(props);
            models.put(stack, entry);
        }

        entry.timer = 5;

        return entry;
    }

    /**
     * Per-client-tick cache pump (legacy {@code PlayerHandler.updateClient} gun
     * half): decrement each entry's timer, evict entries unrendered for 5 ticks,
     * and drive {@code props.update()} on the survivors (the dummy-actor
     * animation clock + firing-morph countdown). Driven from the client tick.
     */
    public static void updateClient()
    {
        Iterator<Map.Entry<ItemStack, GunEntry>> it = models.entrySet().iterator();

        while (it.hasNext())
        {
            GunEntry entry = it.next().getValue();

            entry.timer--;

            if (entry.timer <= 0)
            {
                it.remove();
            }
            else
            {
                entry.props.update();
            }
        }
    }

    /**
     * Legacy {@code ClientHandlerGunShot}: kick the firing morph + shot-delay
     * timer on the cached gun for a stack, if present.
     */
    public static void shot(ItemStack stack)
    {
        GunEntry entry = models.get(stack);

        if (entry != null)
        {
            entry.props.shot();
        }
    }

    /**
     * Legacy {@code ClientHandlerGunInfo}: transplant <b>only</b> the freshly
     * parsed {@code state} into the cached render entry so animation continuity
     * survives Minecraft's itemstack re-parse. Transplanting more would reset
     * morph animations mid-fire; less would strand the reload morph.
     */
    public static void transplantState(ItemStack stack, GunState state)
    {
        GunEntry entry = models.get(stack);

        if (entry != null)
        {
            entry.props.state = state;
        }
    }

    /**
     * Legacy re-equip cache migration
     * ({@code ItemGun.shouldCauseReequipAnimation}): when the old and new stacks
     * share the same models, move the cached entry from the old key to the new
     * one so animation state survives the swap.
     */
    public static void migrate(ItemStack oldStack, ItemStack newStack)
    {
        GunEntry entry = models.remove(oldStack);

        if (entry != null)
        {
            models.put(newStack, entry);
        }
    }

    public static class GunEntry
    {
        public int timer = 20;
        public GunProps props;

        public GunEntry(GunProps props)
        {
            this.props = props;
        }
    }
}
