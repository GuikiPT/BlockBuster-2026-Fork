package mchorse.metamorph.client.model.custom;

import mchorse.blockbuster.api.Model;
import mchorse.blockbuster.client.model.ModelCustom;
import mchorse.blockbuster.client.model.ModelCustomRenderer;
import mchorse.blockbuster.client.model.PoseContext;
import mchorse.blockbuster.client.model.parsing.IModelCustom;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;

/**
 * Ghast model (roadmap P81).
 *
 * <p>Verbatim port of Metamorph's {@code ModelGhast}. Like the squid, this shim
 * does <b>not</b> call the base flag pass: it runs {@code applyLimbPose} over
 * every limb itself, then sways the 9 {@link #tentacles} on {@code rotateAngleX}.
 * The nine tentacle limbs ({@code left_arm}, {@code right_arm}, {@code arm_1..7})
 * are packed in that fixed order by {@code onGenerated}.</p>
 *
 * <p>The legacy {@code render} override pushed the matrix and translated
 * {@code (0, 0.6, 0)} before drawing; ported to a {@link MatrixStack} push in
 * {@link #render}.</p>
 */
public class ModelGhast extends ModelCustom implements IModelCustom
{
    public ModelCustomRenderer left_arm;
    public ModelCustomRenderer right_arm;
    public ModelCustomRenderer arm_1;
    public ModelCustomRenderer arm_2;
    public ModelCustomRenderer arm_3;
    public ModelCustomRenderer arm_4;
    public ModelCustomRenderer arm_5;
    public ModelCustomRenderer arm_6;
    public ModelCustomRenderer arm_7;

    public ModelCustomRenderer[] tentacles;

    public ModelGhast(Model model)
    {
        super(model);
    }

    @Override
    public void onGenerated()
    {
        this.tentacles = new ModelCustomRenderer[] {left_arm, right_arm, arm_1, arm_2, arm_3, arm_4, arm_5, arm_6, arm_7};
    }

    @Override
    public void setRotationAngles(PoseContext context)
    {
        for (ModelCustomRenderer limb : this.limbs)
        {
            this.applyLimbPose(limb);
        }

        for (int i = 0; i < this.tentacles.length; ++i)
        {
            if (this.tentacles[i] == null)
            {
                continue;
            }

            this.tentacles[i].rotateAngleX = 0.2F * MathHelper.sin(context.ageInTicks * 0.3F + (float) i) + 0.4F;
        }
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumer consumer, float r, float g, float b, float a, int light, int overlay)
    {
        matrices.push();
        matrices.translate(0.0F, 0.6F, 0.0F);
        super.render(matrices, consumer, r, g, b, a, light, overlay);
        matrices.pop();
    }
}
