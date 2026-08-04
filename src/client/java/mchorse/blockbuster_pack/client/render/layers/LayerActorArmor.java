package mchorse.blockbuster_pack.client.render.layers;

import mchorse.blockbuster.api.ModelLimb;
import mchorse.blockbuster.api.ModelLimb.ArmorSlot;
import mchorse.blockbuster.client.model.ModelCustom;
import mchorse.blockbuster.client.model.ModelCustomRenderer;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.DyeableItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/**
 * Actor's armor feature layer (roadmap P76).
 *
 * <p>Port of Blockbuster 2.7.2's
 * {@code blockbuster_pack/client/render/layers/LayerActorArmor} (legacy
 * {@code extends LayerArmorBase<ModelBiped>}). It draws vanilla armor pieces onto
 * the actor's {@code slot: …} limbs by wrapping a vanilla {@link BipedEntityModel}
 * part, scaling/translating it to the limb, and rendering it with the armor
 * texture.</p>
 *
 * <p><b>The per-slot constant sets</b> ({@link #placeArmorSlot}) are exact-number
 * parity with legacy {@code setModelSlotVisible} — a golden table test guards
 * them. Two biped models exist: {@code inner} (leggings, inflation 0.5) and
 * {@code outer} (everything else, inflation 1.0), matching legacy
 * {@code modelLeggings}/{@code modelArmor}.</p>
 *
 * <p><b>Forge hook drop:</b> legacy called {@code ForgeHooksClient.getArmorModel}/
 * {@code getArmorResource}; Fabric has no equivalent. Modded armor models fall
 * back to the vanilla biped and the vanilla texture path
 * ({@code textures/models/armor/%s_layer_%d[_overlay].png}) — documented "needed
 * functionality" scope, not a regression against the 1.12.2 behavior bar for
 * vanilla armor.</p>
 */
public class LayerActorArmor
{
    /** Inflation-1.0 biped for head/chest/boots (legacy {@code modelArmor}). */
    private final BipedEntityModel<LivingEntity> outer;
    /** Inflation-0.5 biped for leggings (legacy {@code modelLeggings}). */
    private final BipedEntityModel<LivingEntity> inner;

    public LayerActorArmor(BipedEntityModel<LivingEntity> outer, BipedEntityModel<LivingEntity> inner)
    {
        this.outer = outer;
        this.inner = inner;
    }

    /**
     * Draw armor on every {@code slot: …} limb whose equipped stack's slot
     * matches. Mirrors legacy {@code doRenderLayer}: iterates {@code model.armor},
     * pulls {@code entity.getEquippedStack(limb.slot.slot)}, and only renders when
     * the stack is an {@link ArmorItem} whose {@code getSlotType()} equals the
     * limb's slot (no chestplates on head limbs).
     */
    public void doRenderLayer(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, LivingEntity entity, ModelCustom model, float scale)
    {
        if (model.armor == null)
        {
            return;
        }

        for (ModelCustomRenderer limb : model.armor)
        {
            EquipmentSlot slot = limb.limb.slot.slot;

            if (slot == null)
            {
                continue;
            }

            ItemStack stack = entity.getEquippedStack(slot);

            if (stack.getItem() instanceof ArmorItem)
            {
                ArmorItem item = (ArmorItem) stack.getItem();

                if (item.getSlotType() == slot)
                {
                    this.renderArmorSlot(matrices, vertexConsumers, light, entity, stack, item, limb, scale);
                }
            }
        }
    }

    private void renderArmorSlot(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, LivingEntity entity, ItemStack stack, ArmorItem item, ModelCustomRenderer limb, float scale)
    {
        BipedEntityModel<LivingEntity> model = this.getModelForSlot(item.getSlotType());

        matrices.push();

        limb.postRender(matrices, scale);

        ModelPart part = this.applyArmorLimb(matrices, model, limb.limb, limb.limb.slot);

        if (part != null)
        {
            boolean leather = item.getMaterial().getName().equals("leather") && stack.getItem() instanceof DyeableItem;

            Identifier texture = this.getArmorTexture(item, limb.limb.slot.slot, null);
            VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getArmorCutoutNoCull(texture));

            if (leather)
            {
                int color = ((DyeableItem) stack.getItem()).getColor(stack);
                float r = (color >> 16 & 255) / 255F;
                float g = (color >> 8 & 255) / 255F;
                float b = (color & 255) / 255F;

                part.render(matrices, consumer, light, OverlayTexture.DEFAULT_UV, r, g, b, 1F);

                Identifier overlay = this.getArmorTexture(item, limb.limb.slot.slot, "overlay");
                VertexConsumer overlayConsumer = vertexConsumers.getBuffer(RenderLayer.getArmorCutoutNoCull(overlay));

                part.render(matrices, overlayConsumer, light, OverlayTexture.DEFAULT_UV, 1F, 1F, 1F, 1F);
            }
            else
            {
                part.render(matrices, consumer, light, OverlayTexture.DEFAULT_UV, 1F, 1F, 1F, 1F);
            }

            if (stack.hasGlint())
            {
                VertexConsumer glint = vertexConsumers.getBuffer(RenderLayer.getArmorEntityGlint());

                part.render(matrices, glint, light, OverlayTexture.DEFAULT_UV, 1F, 1F, 1F, 1F);
            }
        }

        matrices.pop();
    }

    private BipedEntityModel<LivingEntity> getModelForSlot(EquipmentSlot slot)
    {
        return slot == EquipmentSlot.LEGS ? this.inner : this.outer;
    }

    /**
     * Reset the biped's part pivots, hide all parts, then translate/scale the
     * matrix and configure + return the single visible part for {@code slot}.
     *
     * <p>Faithful port of legacy {@code setModelSlotVisible}: the common pivot
     * pins ({@code leftArm} at {@code -0.1}, {@code rightArm} at {@code 0.1},
     * others zero) precede a common {@code translate(-ww/4 + offsetX, hh/4 -
     * offsetY, dd/4 - offsetZ)}, then the per-slot scale + part pivot.</p>
     */
    public ModelPart applyArmorLimb(MatrixStack matrices, BipedEntityModel<LivingEntity> model, ModelLimb limb, ArmorSlot slot)
    {
        model.body.setPivot(0, 0, 0);
        model.head.setPivot(0, 0, 0);
        model.hat.setPivot(0, 0, 0);
        model.leftArm.setPivot(-0.1F, 0, 0);
        model.rightArm.setPivot(0.1F, 0, 0);
        model.leftLeg.setPivot(0, 0, 0);
        model.rightLeg.setPivot(0, 0, 0);

        setVisible(model, false);

        ArmorPlacement placement = placeArmorSlot(matrices, limb, slot);

        if (placement == null)
        {
            return null;
        }

        ModelPart part = partFor(model, placement.part);

        part.visible = true;
        part.setPivot(placement.pivotX, placement.pivotY, placement.pivotZ);

        return part;
    }

    /**
     * Apply the common translate + per-slot scale for {@code slot} and return the
     * constant-table {@link ArmorPlacement} (scale is already applied to
     * {@code matrices}; the placement also carries the target part name + pivot).
     *
     * <p>Pure math — no biped needed — so the P76 golden table test drives every
     * slot headlessly.</p>
     */
    public static ArmorPlacement placeArmorSlot(MatrixStack matrices, ModelLimb limb, ArmorSlot slot)
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

        ArmorPlacement placement;

        switch (slot)
        {
            case HEAD:
                placement = new ArmorPlacement("head", w / 8F, h / 8F, d / 8F, 0, 4, 0);
                break;
            case CHEST:
                placement = new ArmorPlacement("body", w / 8F, h / 12F, d / 4F, 0, -6, 0);
                break;
            case LEFT_SHOULDER:
                placement = new ArmorPlacement("leftArm", w / 4F, h / 12F, d / 4F, -1, -4, 0);
                break;
            case RIGHT_SHOULDER:
                placement = new ArmorPlacement("rightArm", w / 4F, h / 12F, d / 4F, 1, -4, 0);
                break;
            case LEGGINGS:
                placement = new ArmorPlacement("body", w / 8F, h / 12F, d / 4F, 0, -6, 0);
                break;
            case LEFT_LEG:
            case LEFT_FOOT:
                placement = new ArmorPlacement("leftLeg", w / 4F, h / 12F, d / 4F, 0, -6, 0);
                break;
            case RIGHT_LEG:
            case RIGHT_FOOT:
                placement = new ArmorPlacement("rightLeg", w / 4F, h / 12F, d / 4F, 0, -6, 0);
                break;
            default:
                return null;
        }

        matrices.scale(placement.scaleX, placement.scaleY, placement.scaleZ);

        return placement;
    }

    private static ModelPart partFor(BipedEntityModel<LivingEntity> model, String name)
    {
        switch (name)
        {
            case "head": return model.head;
            case "body": return model.body;
            case "leftArm": return model.leftArm;
            case "rightArm": return model.rightArm;
            case "leftLeg": return model.leftLeg;
            case "rightLeg": return model.rightLeg;
            default: return null;
        }
    }

    private static void setVisible(BipedEntityModel<LivingEntity> model, boolean visible)
    {
        model.head.visible = visible;
        model.hat.visible = visible;
        model.body.visible = visible;
        model.leftArm.visible = visible;
        model.rightArm.visible = visible;
        model.leftLeg.visible = visible;
        model.rightLeg.visible = visible;
    }

    /**
     * Legacy {@code getArmorResource} shape:
     * {@code minecraft:textures/models/armor/%s_layer_%d[_overlay].png} where
     * {@code %s} is the material name and {@code %d} is 2 for leggings else 1.
     */
    public Identifier getArmorTexture(ArmorItem item, EquipmentSlot slot, String type)
    {
        int layer = slot == EquipmentSlot.LEGS ? 2 : 1;
        String material = item.getMaterial().getName();
        String path = "textures/models/armor/" + material + "_layer_" + layer + (type == null ? "" : "_" + type) + ".png";

        return new Identifier(path);
    }

    /**
     * Per-slot armor constant record: the biped part it drives, its scale and its
     * rotation-point pivot (legacy {@code setModelSlotVisible} numbers).
     */
    public static class ArmorPlacement
    {
        public final String part;
        public final float scaleX;
        public final float scaleY;
        public final float scaleZ;
        public final float pivotX;
        public final float pivotY;
        public final float pivotZ;

        public ArmorPlacement(String part, float scaleX, float scaleY, float scaleZ, float pivotX, float pivotY, float pivotZ)
        {
            this.part = part;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.scaleZ = scaleZ;
            this.pivotX = pivotX;
            this.pivotY = pivotY;
            this.pivotZ = pivotZ;
        }
    }
}
