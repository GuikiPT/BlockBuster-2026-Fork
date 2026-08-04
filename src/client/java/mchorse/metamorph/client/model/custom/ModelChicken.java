package mchorse.metamorph.client.model.custom;

import mchorse.blockbuster.api.Model;
import mchorse.blockbuster.client.model.ModelCustom;
import mchorse.blockbuster.client.model.ModelCustomRenderer;
import mchorse.blockbuster.client.model.PoseContext;
import mchorse.blockbuster.client.model.parsing.IModelCustom;
import mchorse.metamorph.Metamorph;
import net.minecraft.util.math.MathHelper;

/**
 * Chicken model (roadmap P81).
 *
 * <p>Verbatim port of Metamorph's {@code ModelChicken}: wing (arm) flap on
 * {@code rotateAngleZ} (left negated), gated on
 * {@code !onGround || |motionY| > 0.1} — only flaps while airborne / falling.</p>
 */
public class ModelChicken extends ModelCustom implements IModelCustom
{
    public ModelCustomRenderer left_arm;
    public ModelCustomRenderer right_arm;

    private boolean warned;

    public ModelChicken(Model model)
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

        if (!context.onGround || Math.abs(context.motionY) > 0.1)
        {
            if (this.left_arm == null || this.right_arm == null)
            {
                this.warnMissing();
                return;
            }

            float flap = MathHelper.sin(context.ageInTicks) + 1.0F;

            this.right_arm.rotateAngleZ = flap;
            this.left_arm.rotateAngleZ = -flap;
        }
    }

    private void warnMissing()
    {
        if (!this.warned)
        {
            Metamorph.log("ModelChicken '" + this.model.name + "' is missing left_arm/right_arm; skipping wing flap.");
            this.warned = true;
        }
    }
}
