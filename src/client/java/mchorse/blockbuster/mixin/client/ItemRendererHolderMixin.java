package mchorse.blockbuster.mixin.client;

import mchorse.blockbuster.client.RenderingHandler;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * S17/S18 — the "who is holding the item being rendered" seam.
 *
 * <p>Legacy source of truth: the ASM coremod's
 * {@code core/transformers/RenderItemTransformer.processRenderItemMethod},
 * which wrapped <b>one</b> vanilla method —
 * {@code RenderItem.renderItem(ItemStack, EntityLivingBase, TransformType, boolean)}
 * — with {@code RenderingHandler.setLastItemHolder(entity)} inserted at the top
 * and {@code RenderingHandler.resetLastItemHolder(entity)} inserted before its
 * return. The other three methods that transformer patched
 * ({@code renderItem(ItemStack, TransformType)},
 * {@code renderItemModel}, {@code renderItemModelIntoGUI}) only set the
 * transform type, never the holder; the port takes the transform type straight
 * off the {@code DynamicItemRenderer.render} parameter instead, so this is the
 * <i>only</i> half of that transformer still needing a seam.</p>
 *
 * <p>The 1.20.4 equivalent of that single method is
 * {@code ItemRenderer.renderItem(LivingEntity, ItemStack, ModelTransformationMode,
 * boolean, MatrixStack, VertexConsumerProvider, World, int, int, int)} —
 * signature verified with {@code javap} against the loom-cache named jar. It is
 * the overload every held / worn / armour-stand item render funnels through, and
 * it reaches our {@code BuiltinItemRendererRegistry} renderers further down the
 * same call, so the holder is live by the time
 * {@code ItemRenderContext.resolveHolder} reads it.</p>
 *
 * <p><b>Set/restore, not a single assignment.</b> Both legacy halves are
 * guarded: {@code setLastItemHolder} only takes when the slot is empty (a nested
 * item render never clobbers the outer holder) and
 * {@code resetLastItemHolder} only clears when the entity matches (a nested
 * render never clears the outer holder on its way out). Keeping both is what
 * makes a stale holder impossible; installing only the setter would leave the
 * last-rendered entity anchoring every subsequent GUI/dropped-item render.
 * {@code RETURN} (not {@code TAIL}) so the clear happens on every exit path —
 * legacy patched only the <i>first</i> {@code RETURN} it found, which is the
 * same point in a method with a single exit and strictly leakier if it ever
 * gains an early one.</p>
 */
@Mixin(ItemRenderer.class)
public class ItemRendererHolderMixin
{
    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/world/World;III)V", at = @At("HEAD"))
    private void blockbuster$setLastItemHolder(LivingEntity entity, ItemStack stack, ModelTransformationMode renderMode, boolean leftHanded, MatrixStack matrices, VertexConsumerProvider vertexConsumers, World world, int light, int overlay, int seed, CallbackInfo ci)
    {
        RenderingHandler.setLastItemHolder(entity);
    }

    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/world/World;III)V", at = @At("RETURN"))
    private void blockbuster$resetLastItemHolder(LivingEntity entity, ItemStack stack, ModelTransformationMode renderMode, boolean leftHanded, MatrixStack matrices, VertexConsumerProvider vertexConsumers, World world, int light, int overlay, int seed, CallbackInfo ci)
    {
        RenderingHandler.resetLastItemHolder(entity);
    }
}
