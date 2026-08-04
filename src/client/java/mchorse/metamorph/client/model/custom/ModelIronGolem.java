package mchorse.metamorph.client.model.custom;

import mchorse.blockbuster.api.Model;
import mchorse.blockbuster.client.model.ModelCustom;
import mchorse.blockbuster.client.model.ModelCustomRenderer;
import mchorse.blockbuster.client.model.PoseContext;
import mchorse.blockbuster.client.model.parsing.IModelCustom;
import mchorse.metamorph.Metamorph;

/**
 * Iron golem model (roadmap P81).
 *
 * <p>Verbatim port of Metamorph's {@code ModelIronGolem}: attack-swing on both
 * arms via a local {@link #triangleWave} of {@code swingProgress}
 * ({@code rotateAngleX = -2 + 1.2·wave}), applied only while
 * {@code swingProgress != 0}.</p>
 *
 * <p>Legacy read {@code this.swingProgress} off the biped model; the port routes
 * it through {@link PoseContext#swingProgress} to keep the math entity-free.</p>
 */
public class ModelIronGolem extends ModelCustom implements IModelCustom
{
    public ModelCustomRenderer left_arm;
    public ModelCustomRenderer right_arm;

    private boolean warned;

    public ModelIronGolem(Model model)
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

        float i = context.swingProgress;

        if (i != 0)
        {
            if (this.left_arm == null || this.right_arm == null)
            {
                this.warnMissing();
                return;
            }

            this.right_arm.rotateAngleX = -2.0F + 1.2F * this.triangleWave(i, 1.0F);
            this.left_arm.rotateAngleX = -2.0F + 1.2F * this.triangleWave(i, 1.0F);
        }
    }

    private float triangleWave(float input, float magnitude)
    {
        return (Math.abs(input % magnitude - magnitude * 0.5F) - magnitude * 0.25F) / (magnitude * 0.25F);
    }

    private void warnMissing()
    {
        if (!this.warned)
        {
            Metamorph.log("ModelIronGolem '" + this.model.name + "' is missing left_arm/right_arm; skipping attack swing.");
            this.warned = true;
        }
    }
}
