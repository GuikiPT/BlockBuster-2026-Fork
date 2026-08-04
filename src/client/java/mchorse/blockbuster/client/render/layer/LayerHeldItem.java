package mchorse.blockbuster.client.render.layer;

import mchorse.blockbuster.api.ModelPose;
import mchorse.blockbuster.client.model.ModelCustom;
import mchorse.blockbuster.client.model.ModelCustomRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Arm;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;

/**
 * Held item feature layer (roadmap P76).
 *
 * <p>Port of Blockbuster 2.7.2's {@code client/render/layer/LayerHeldItem}. This
 * layer draws the entity's main/off-hand {@link ItemStack} on every custom limb
 * flagged {@code holding: right}/{@code holding: left}. The 1.12.2 class rendered
 * through {@code GlStateManager} matrix ops + {@code ItemRenderer.renderItemSide};
 * on 1.20.4 the matrix math is folded into a {@link MatrixStack} and the draw goes
 * through {@code ItemRenderer.renderItem(entity, stack, mode, leftHanded, ...)}.</p>
 *
 * <p><b>Parity-critical transform</b> ({@link #applyTransform}) is separated from
 * the GL-boundary draw so the P76 golden tests can validate the final item matrix
 * headlessly. The formulas — the {@code (0.5 - anchor)} x offset, the {@code z/y}
 * swap in the translate call, the wide-limb ({@code size[0] > size[1]}) branch
 * with its overridden x and extra {@code -90°} Y rotation — are load-bearing.</p>
 */
public class LayerHeldItem
{
    /**
     * Draw the entity's held items on all matching limbs. Called from the actor
     * renderer (P80). {@code matrices} is the model-space stack (post model
     * transform), {@code light} the packed lightmap coord.
     *
     * <p>Mirrors legacy {@code doRenderLayer}: builds a {@link HeldModel}
     * snapshot of the pose/animation params, renders main hand then off hand,
     * then re-runs the pose apply so the recursive item render doesn't leave the
     * limb angles corrupted.</p>
     */
    public void doRenderLayer(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, LivingEntity entity, ModelCustom model, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, float scale)
    {
        ItemStack mainHand = entity.getMainHandStack();
        ItemStack offHand = entity.getOffHandStack();

        if (mainHand.isEmpty() && offHand.isEmpty())
        {
            return;
        }

        HeldModel held = new HeldModel(model);

        held.limbSwing = limbSwing;
        held.limbSwingAmount = limbSwingAmount;
        held.ageInTicks = ageInTicks;
        held.netHeadYaw = netHeadYaw;
        held.headPitch = headPitch;
        held.scale = scale;

        renderHeldItem(matrices, vertexConsumers, light, entity, mainHand, held, ModelTransformationMode.THIRD_PERSON_RIGHT_HAND, Arm.RIGHT);
        renderHeldItem(matrices, vertexConsumers, light, entity, offHand, held, ModelTransformationMode.THIRD_PERSON_LEFT_HAND, Arm.LEFT);

        /* Legacy re-ran setRotationAngles here because recursive model-block item
         * rendering corrupts the limb angles. With the P82 runtime that means
         * re-applying the current pose to every limb. */
        held.restore();
    }

    /**
     * Render {@code item} on every limb returned by
     * {@link ModelCustom#getRenderForArm(boolean)} for {@code arm} — items can
     * appear on multiple limbs, matching legacy.
     */
    public static void renderHeldItem(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, LivingEntity entity, ItemStack item, HeldModel model, ModelTransformationMode mode, Arm arm)
    {
        if (item.isEmpty())
        {
            return;
        }

        boolean leftHanded = arm == Arm.LEFT;

        for (ModelCustomRenderer limb : model.model.getRenderForArm(arm == Arm.LEFT))
        {
            model.setup();

            matrices.push();
            applyTransform(matrices, limb);

            MinecraftClient mc = MinecraftClient.getInstance();

            mc.getItemRenderer().renderItem(entity, item, mode, leftHanded, matrices, vertexConsumers, entity.getWorld(), light, OverlayTexture.DEFAULT_UV, entity.getId());

            matrices.pop();
        }
    }

    /**
     * Apply the per-limb held-item transform. <b>Ported verbatim from legacy
     * {@code applyTransform}.</b>
     *
     * <ul>
     *   <li>{@code x = size[0] * (0.5 - anchor[0]) * 0.0625}</li>
     *   <li>{@code y = size[1] * (size[1] * (1 - anchor[1]) / size[1]) * -0.0625}
     *       (the redundant {@code size[1]} cancellation is legacy, preserved)</li>
     *   <li>{@code z = size[2] * anchor[2] * 0.0625}</li>
     *   <li>wide limb ({@code size[0] > size[1]}): {@code x = size[0] * (10/12) *
     *       0.0625}</li>
     * </ul>
     *
     * <p>then {@code postRender(0.0625)}, {@code rotate(-90, X)},
     * {@code rotate(180, Y)}, {@code translate(x, z, y)} (note the z/y swap),
     * extra {@code rotate(-90, Y)} for the wide case, and
     * {@code scale(itemScale)}.</p>
     */
    public static void applyTransform(MatrixStack matrices, ModelCustomRenderer arm)
    {
        int[] size = arm.limb.size;
        float[] anchor = arm.limb.anchor;

        float x = (size[0] * (0.5F - anchor[0])) * 0.0625F;
        float y = size[1] * (size[1] * (1 - anchor[1]) / size[1]) * -0.0625F;
        float z = (size[2] * anchor[2]) * 0.0625F;

        boolean wide = size[0] > size[1];

        if (wide)
        {
            x = size[0] * (10.0F / 12.0F) * 0.0625F;
        }

        arm.postRender(matrices, 0.0625F);

        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0F));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
        matrices.translate(x, z, y);

        if (wide)
        {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90.0F));
        }

        matrices.scale(arm.limb.itemScale, arm.limb.itemScale, arm.limb.itemScale);
    }

    /**
     * Held model snapshot.
     *
     * <p>Stores the animation params and the pose so the recursive item render
     * (which mutates limb angles) can be reset between limbs and after the whole
     * layer. Legacy {@code HeldModel.setup}/final {@code setRotationAngles}.</p>
     */
    public static class HeldModel
    {
        public float limbSwing;
        public float limbSwingAmount;
        public float ageInTicks;
        public float netHeadYaw;
        public float headPitch;
        public float scale;

        public ModelCustom model;
        public ModelPose pose;

        public HeldModel(ModelCustom model)
        {
            this.model = model;
            this.pose = model.pose;
        }

        /** Restore the pose before each limb (legacy {@code setup}). */
        public void setup()
        {
            this.model.pose = this.pose;

            /* SEAM(P82): legacy also re-ran the procedural setRotationAngles pass
             * here; the runtime pose apply lands with the P82 blend factor. The
             * pose restore is the load-bearing half for the recursion guard. */
        }

        /** Final restore after the layer (legacy trailing setRotationAngles). */
        public void restore()
        {
            this.model.pose = this.pose;
        }
    }
}
