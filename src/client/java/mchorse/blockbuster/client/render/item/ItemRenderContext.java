package mchorse.blockbuster.client.render.item;

import mchorse.blockbuster.client.RenderingHandler;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;

/**
 * P83: the per-render context threaded from a Blockbuster
 * {@link net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.DynamicItemRenderer}
 * into the morph render seam.
 *
 * <p>This is the modern replacement for the two 1.12.2 globals the item TEISRs
 * read off {@code RenderingHandler}: the <b>holder</b> entity
 * ({@code getLastItemHolder()}) and the <b>camera transform</b>
 * ({@code itemTransformType}). On 1.20.4 the transform arrives as the
 * {@code mode} parameter of {@code DynamicItemRenderer.render}, and the holder
 * is resolved once here (preferring {@link ItemStack#getHolder()} — non-null
 * for item frames / dropped {@code ItemEntity}s — and falling back to the
 * {@code RenderingHandler} holder seam populated by our own hand/GUI render
 * sites). The concrete {@code render} bodies (the actual morph draw) are S8 /
 * S17 deliverables; this phase carries the context they consume unchanged so
 * the parity of "who holds it" and "which transform applies" is preserved.</p>
 */
public final class ItemRenderContext
{
    public final ItemStack stack;
    public final ModelTransformationMode mode;
    public final Entity holder;
    public final MatrixStack matrices;
    public final VertexConsumerProvider vertexConsumers;
    public final int light;
    public final int overlay;

    public ItemRenderContext(ItemStack stack, ModelTransformationMode mode, Entity holder, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay)
    {
        this.stack = stack;
        this.mode = mode;
        this.holder = holder;
        this.matrices = matrices;
        this.vertexConsumers = vertexConsumers;
        this.light = light;
        this.overlay = overlay;
    }

    /**
     * Build a context for a stack + transform mode, resolving the holder the
     * legacy way: {@link ItemStack#getHolder()} first (the item-frame /
     * item-entity case, which 1.20.4 supplies for free), then the
     * {@link RenderingHandler#getLastItemHolder()} seam our own render sites
     * populate for held-in-hand / GUI renders.
     */
    public static ItemRenderContext resolve(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay)
    {
        return new ItemRenderContext(stack, mode, resolveHolder(stack), matrices, vertexConsumers, light, overlay);
    }

    /**
     * Prefer the stack's own holder (item frame / dropped item entity); fall
     * back to the {@code RenderingHandler} holder seam. Null when neither is
     * available (e.g. a headless render or a stack rendered outside any holder
     * context).
     */
    public static Entity resolveHolder(ItemStack stack)
    {
        Entity holder = stack == null ? null : stack.getHolder();

        return holder != null ? holder : RenderingHandler.getLastItemHolder();
    }

    /** True for the two first-person hand transforms (gun hand-render gate). */
    public boolean isFirstPerson()
    {
        return this.mode == ModelTransformationMode.FIRST_PERSON_LEFT_HAND
            || this.mode == ModelTransformationMode.FIRST_PERSON_RIGHT_HAND;
    }

    /** True for the inventory (GUI) transform. */
    public boolean isGui()
    {
        return this.mode == ModelTransformationMode.GUI;
    }

    /** True for the dropped-item / item-frame ground transform. */
    public boolean isGround()
    {
        return this.mode == ModelTransformationMode.GROUND;
    }
}
