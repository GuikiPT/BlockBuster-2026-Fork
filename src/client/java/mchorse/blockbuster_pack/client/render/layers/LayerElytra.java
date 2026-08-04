package mchorse.blockbuster_pack.client.render.layers;

import mchorse.blockbuster.api.ModelLimb;
import mchorse.blockbuster.api.ModelLimb.ArmorSlot;
import mchorse.blockbuster.client.model.ModelCustom;
import mchorse.blockbuster.client.model.ModelCustomRenderer;
import mchorse.blockbuster_pack.client.model.ModelElytra;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

/**
 * Elytra feature layer (roadmap P76).
 *
 * <p>Port of Blockbuster 2.7.2's
 * {@code blockbuster_pack/client/render/layers/LayerElytra}. When the actor's
 * chest stack is an {@link Items#ELYTRA}, draws {@link ModelElytra} on every
 * {@code slot: chest} limb with the exact legacy transform, and adds the vanilla
 * enchant glint pass when the stack is enchanted.</p>
 */
public class LayerElytra
{
    private static final Identifier TEXTURE_ELYTRA = new Identifier("textures/entity/elytra.png");

    private final ModelElytra modelElytra = new ModelElytra();

    public void doRenderLayer(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, LivingEntity entity, ModelCustom model, float scale, boolean flying, boolean sneaking)
    {
        ItemStack stack = entity.getEquippedStack(EquipmentSlot.CHEST);

        if (stack.getItem() != Items.ELYTRA || model.armor == null)
        {
            return;
        }

        for (ModelCustomRenderer renderer : model.armor)
        {
            ModelLimb limb = renderer.limb;

            if (limb.slot != ArmorSlot.CHEST)
            {
                continue;
            }

            matrices.push();

            renderer.postRender(matrices, scale);
            applyElytraTransform(matrices, limb);

            this.modelElytra.setAngles(entity, flying, sneaking);

            VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getArmorCutoutNoCull(TEXTURE_ELYTRA));

            this.modelElytra.render(matrices, consumer, light, OverlayTexture.DEFAULT_UV, 1F, 1F, 1F, 1F);

            if (stack.hasGlint())
            {
                VertexConsumer glint = vertexConsumers.getBuffer(RenderLayer.getArmorEntityGlint());

                this.modelElytra.render(matrices, glint, light, OverlayTexture.DEFAULT_UV, 1F, 1F, 1F, 1F);
            }

            matrices.pop();
        }
    }

    /**
     * Elytra transform (after {@code postRender}): {@code translate(-ww/4 +
     * offsetX, hh/4 - offsetY, dd/4 - offsetZ)}, {@code scale(w/8, h/12, d/4)},
     * {@code translate(0, -0.125 * 2.75, 0.125)} — i.e. {@code (0, -0.34375,
     * +0.125)}.
     */
    public static void applyElytraTransform(MatrixStack matrices, ModelLimb limb)
    {
        int w = limb.size[0];
        int h = limb.size[1];
        int d = limb.size[2];

        float ww = w / 8F;
        float hh = h / 8F;
        float dd = d / 8F;

        float offsetX = limb.anchor[0] * ww / 2;
        float offsetY = limb.anchor[1] * hh / 2;
        float offsetZ = limb.anchor[2] * dd / 2;

        matrices.translate(-ww / 4 + offsetX, hh / 4 - offsetY, dd / 4 - offsetZ);
        matrices.scale(w / 8F, h / 12F, d / 4F);
        matrices.translate(0.0F, -0.125F * 2.75F, 0.125F);
    }
}
