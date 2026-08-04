package mchorse.metamorph.client.model.custom;

import mchorse.blockbuster.api.Model;
import mchorse.blockbuster.client.model.ModelCustom;
import mchorse.blockbuster.client.model.ModelCustomRenderer;
import mchorse.blockbuster.client.model.PoseContext;
import mchorse.blockbuster.client.model.parsing.IModelCustom;
import mchorse.metamorph.Metamorph;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.render.VertexConsumer;

/**
 * Bat model (roadmap P81).
 *
 * <p>Verbatim port of Metamorph's {@code ModelBat}. Wing flap on
 * {@code rotateAngleY} (outer wings at &times;0.5, left negated).</p>
 *
 * <p><b>GL side effect &rarr; render offset:</b> the 1.12.2 class issued a raw
 * {@code GlStateManager.translate(0, -0.4 - cos(age*0.3)*0.1, 0)} <i>inside</i>
 * {@code setRotationAngles} — a hover-bob matrix push. On 1.20.4 (no global GL
 * matrix) that becomes a {@link MatrixStack} translate applied at render entry;
 * this class computes the offset into {@link #bobY} during the pose pass and
 * applies it in {@link #render} (the model's render entry, P80).</p>
 */
public class ModelBat extends ModelCustom implements IModelCustom
{
    public ModelCustomRenderer right_wing;
    public ModelCustomRenderer right_wing_2;

    public ModelCustomRenderer left_wing;
    public ModelCustomRenderer left_wing_2;

    /** Hover-bob Y offset (legacy GL translate), applied at render entry. */
    public float bobY;

    private boolean warned;

    public ModelBat(Model model)
    {
        super(model);
    }

    @Override
    public void onGenerated()
    {}

    @Override
    public void setRotationAngles(PoseContext context)
    {
        /* Legacy issued a GlStateManager.translate here; captured as bobY and
         * applied in render() (the P80 render entry) instead. */
        this.bobY = -0.4F - MathHelper.cos(context.ageInTicks * 0.3F) * 0.1F;

        super.setRotationAngles(context);

        if (this.right_wing == null || this.left_wing == null || this.right_wing_2 == null || this.left_wing_2 == null)
        {
            this.warnMissing();
            return;
        }

        this.right_wing.rotateAngleY = MathHelper.cos(context.ageInTicks * 1.3F) * (float) Math.PI * 0.25F * (0.5F + context.limbSwingAmount);
        this.left_wing.rotateAngleY = -this.right_wing.rotateAngleY;
        this.right_wing_2.rotateAngleY = this.right_wing.rotateAngleY * 0.5F;
        this.left_wing_2.rotateAngleY = -this.right_wing.rotateAngleY * 0.5F;
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumer consumer, float r, float g, float b, float a, int light, int overlay)
    {
        matrices.push();
        matrices.translate(0.0F, this.bobY, 0.0F);
        super.render(matrices, consumer, r, g, b, a, light, overlay);
        matrices.pop();
    }

    private void warnMissing()
    {
        if (!this.warned)
        {
            Metamorph.log("ModelBat '" + this.model.name + "' is missing a wing limb (right_wing/left_wing/right_wing_2/left_wing_2); skipping wing animation.");
            this.warned = true;
        }
    }
}
