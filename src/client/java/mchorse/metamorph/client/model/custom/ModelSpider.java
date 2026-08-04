package mchorse.metamorph.client.model.custom;

import mchorse.blockbuster.api.Model;
import mchorse.blockbuster.client.model.ModelCustom;
import mchorse.blockbuster.client.model.ModelCustomRenderer;
import mchorse.blockbuster.client.model.PoseContext;
import mchorse.blockbuster.client.model.parsing.IModelCustom;
import mchorse.metamorph.Metamorph;
import net.minecraft.util.math.MathHelper;

/**
 * Spider model (roadmap P81).
 *
 * <p>Verbatim port of Metamorph's {@code ModelSpider} — a copy of vanilla
 * spider leg animation. The static splay ({@code rotateAngleZ} &plusmn;&pi;/4 /
 * &plusmn;0.58119464, {@code rotateAngleY} &plusmn;&pi;/4 / &plusmn;0.3926991) is
 * assigned with {@code =} (overwriting the base pose + P82 procedural values),
 * then the walk-cycle add-ons are applied with {@code +=} — operators kept
 * exactly.</p>
 *
 * <p>Legacy declared these fields as vanilla {@code ModelRenderer}
 * (ModelCustomRenderer's 1.12.2 superclass); on 1.20.4 they are
 * {@link ModelCustomRenderer} directly, which is what P75's reflection injector
 * assigns.</p>
 */
public class ModelSpider extends ModelCustom implements IModelCustom
{
    public ModelCustomRenderer left_leg_1;
    public ModelCustomRenderer left_leg_2;
    public ModelCustomRenderer left_leg_3;
    public ModelCustomRenderer left_leg_4;

    public ModelCustomRenderer right_leg_1;
    public ModelCustomRenderer right_leg_2;
    public ModelCustomRenderer right_leg_3;
    public ModelCustomRenderer right_leg_4;

    private boolean warned;

    public ModelSpider(Model model)
    {
        super(model);
    }

    @Override
    public void onGenerated()
    {}

    @Override
    public void setRotationAngles(PoseContext context)
    {
        super.setRotationAngles(context);

        if (this.hasMissing())
        {
            this.warnMissing();
            return;
        }

        float limbSwing = context.limbSwing;
        float limbSwingAmount = context.limbSwingAmount;

        this.right_leg_1.rotateAngleZ = -((float) Math.PI / 4F);
        this.left_leg_1.rotateAngleZ = ((float) Math.PI / 4F);
        this.right_leg_2.rotateAngleZ = -0.58119464F;
        this.left_leg_2.rotateAngleZ = 0.58119464F;
        this.right_leg_3.rotateAngleZ = -0.58119464F;
        this.left_leg_3.rotateAngleZ = 0.58119464F;
        this.right_leg_4.rotateAngleZ = -((float) Math.PI / 4F);
        this.left_leg_4.rotateAngleZ = ((float) Math.PI / 4F);

        this.right_leg_1.rotateAngleY = ((float) Math.PI / 4F);
        this.left_leg_1.rotateAngleY = -((float) Math.PI / 4F);
        this.right_leg_2.rotateAngleY = 0.3926991F;
        this.left_leg_2.rotateAngleY = -0.3926991F;
        this.right_leg_3.rotateAngleY = -0.3926991F;
        this.left_leg_3.rotateAngleY = 0.3926991F;
        this.right_leg_4.rotateAngleY = -((float) Math.PI / 4F);
        this.left_leg_4.rotateAngleY = ((float) Math.PI / 4F);

        float f3 = -(MathHelper.cos(limbSwing * 0.6662F * 2.0F + 0.0F) * 0.4F) * limbSwingAmount;
        float f4 = -(MathHelper.cos(limbSwing * 0.6662F * 2.0F + (float) Math.PI) * 0.4F) * limbSwingAmount;
        float f5 = -(MathHelper.cos(limbSwing * 0.6662F * 2.0F + ((float) Math.PI / 2F)) * 0.4F) * limbSwingAmount;
        float f6 = -(MathHelper.cos(limbSwing * 0.6662F * 2.0F + ((float) Math.PI * 3F / 2F)) * 0.4F) * limbSwingAmount;
        float f7 = Math.abs(MathHelper.sin(limbSwing * 0.6662F + 0.0F) * 0.4F) * limbSwingAmount;
        float f8 = Math.abs(MathHelper.sin(limbSwing * 0.6662F + (float) Math.PI) * 0.4F) * limbSwingAmount;
        float f9 = Math.abs(MathHelper.sin(limbSwing * 0.6662F + ((float) Math.PI / 2F)) * 0.4F) * limbSwingAmount;
        float f10 = Math.abs(MathHelper.sin(limbSwing * 0.6662F + ((float) Math.PI * 3F / 2F)) * 0.4F) * limbSwingAmount;

        this.right_leg_1.rotateAngleY += f3;
        this.left_leg_1.rotateAngleY += -f3;
        this.right_leg_2.rotateAngleY += f4;
        this.left_leg_2.rotateAngleY += -f4;
        this.right_leg_3.rotateAngleY += f5;
        this.left_leg_3.rotateAngleY += -f5;
        this.right_leg_4.rotateAngleY += f6;
        this.left_leg_4.rotateAngleY += -f6;
        this.right_leg_1.rotateAngleZ += f7;
        this.left_leg_1.rotateAngleZ += -f7;
        this.right_leg_2.rotateAngleZ += f8;
        this.left_leg_2.rotateAngleZ += -f8;
        this.right_leg_3.rotateAngleZ += f9;
        this.left_leg_3.rotateAngleZ += -f9;
        this.right_leg_4.rotateAngleZ += f10;
        this.left_leg_4.rotateAngleZ += -f10;
    }

    private boolean hasMissing()
    {
        return this.left_leg_1 == null || this.left_leg_2 == null || this.left_leg_3 == null || this.left_leg_4 == null
            || this.right_leg_1 == null || this.right_leg_2 == null || this.right_leg_3 == null || this.right_leg_4 == null;
    }

    private void warnMissing()
    {
        if (!this.warned)
        {
            Metamorph.log("ModelSpider '" + this.model.name + "' is missing a leg limb (left_leg_1..4 / right_leg_1..4); skipping spider animation.");
            this.warned = true;
        }
    }
}
