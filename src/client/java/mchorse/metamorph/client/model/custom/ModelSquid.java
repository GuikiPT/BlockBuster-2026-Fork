package mchorse.metamorph.client.model.custom;

import mchorse.blockbuster.api.Model;
import mchorse.blockbuster.client.model.ModelCustom;
import mchorse.blockbuster.client.model.ModelCustomRenderer;
import mchorse.blockbuster.client.model.PoseContext;
import mchorse.blockbuster.client.model.parsing.IModelCustom;
import mchorse.metamorph.Metamorph;

/**
 * Squid model (roadmap P81).
 *
 * <p>Verbatim port of Metamorph's {@code ModelSquid}. Like the ghast, this shim
 * does <b>not</b> call the base flag pass — squid-class models get <i>no</i>
 * look / swing / idle / etc. processing. It runs {@code applyLimbPose} over
 * every limb itself, animates every non-{@code head} limb as a tentacle
 * ({@code π/8 + sin(limbSwing / (inWater ? 4 : 2)) · π/8}), and, only in water,
 * pitches the {@code head} ({@code π/2 + sin(limbSwing / 6) · π/16}).</p>
 *
 * <p>Uses raw {@link Math#sin} (not the {@code MathHelper} sine table), matching
 * legacy exactly.</p>
 */
public class ModelSquid extends ModelCustom implements IModelCustom
{
    /** Injected by the parser (P75). */
    public ModelCustomRenderer head;

    private boolean warned;

    public ModelSquid(Model model)
    {
        super(model);
    }

    @Override
    public void onGenerated()
    {}

    @Override
    public void setRotationAngles(PoseContext context)
    {
        float pi = (float) Math.PI;
        float limbSwing = context.limbSwing;
        boolean inWater = context.inWater;

        if (this.head == null)
        {
            this.warnMissing();
        }
        else
        {
            this.head.rotateAngleX = 0;
        }

        for (ModelCustomRenderer limb : this.limbs)
        {
            this.applyLimbPose(limb);

            if (limb == this.head)
            {
                continue;
            }

            limb.rotateAngleX = pi / 8 + (float) Math.sin(limbSwing / (inWater ? 4 : 2)) * pi / 8;
        }

        if (inWater && this.head != null)
        {
            this.head.rotateAngleX = pi / 2 + (float) Math.sin(limbSwing / 6) * pi / 16;
        }
    }

    private void warnMissing()
    {
        if (!this.warned)
        {
            Metamorph.log("ModelSquid '" + this.model.name + "' is missing the head limb; skipping head pitch.");
            this.warned = true;
        }
    }
}
