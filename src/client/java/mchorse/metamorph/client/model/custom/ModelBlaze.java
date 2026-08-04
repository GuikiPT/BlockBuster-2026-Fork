package mchorse.metamorph.client.model.custom;

import mchorse.blockbuster.api.Model;
import mchorse.blockbuster.client.model.ModelCustom;
import mchorse.blockbuster.client.model.ModelCustomRenderer;
import mchorse.blockbuster.client.model.PoseContext;
import mchorse.blockbuster.client.model.parsing.IModelCustom;
import mchorse.metamorph.Metamorph;
import net.minecraft.util.math.MathHelper;

/**
 * Blaze model (roadmap P81).
 *
 * <p>Verbatim port of Metamorph's {@code ModelBlaze} — a full copy of vanilla
 * blaze's floating-stick animation. {@code onGenerated} packs the 12
 * reflection-injected stick limbs into {@link #blaze_sticks} <b>in a fixed
 * order</b> ({@code top_right, top_front, top_back, top_left, …}); three orbit
 * rings (radii 9 / 7 / 5, angular speeds {@code -0.1π} / {@code +0.03π} /
 * {@code -0.05π} per tick, per-stick Y bob via {@code cos}) drive the sticks'
 * {@code rotationPoint*} — this shim animates <i>positions</i>, not angles.</p>
 */
public class ModelBlaze extends ModelCustom implements IModelCustom
{
    /* These fields are going to be injected via reflection */
    public ModelCustomRenderer top_front;
    public ModelCustomRenderer top_back;
    public ModelCustomRenderer top_left;
    public ModelCustomRenderer top_right;

    public ModelCustomRenderer middle_front_left;
    public ModelCustomRenderer middle_front_right;
    public ModelCustomRenderer middle_back_left;
    public ModelCustomRenderer middle_back_right;

    public ModelCustomRenderer bottom_front_left;
    public ModelCustomRenderer bottom_front_right;
    public ModelCustomRenderer bottom_back_left;
    public ModelCustomRenderer bottom_back_right;

    /* This field is going to be constructed in onGenerated */
    public ModelCustomRenderer[] blaze_sticks;

    private boolean warned;

    public ModelBlaze(Model model)
    {
        super(model);
    }

    @Override
    public void onGenerated()
    {
        this.blaze_sticks = new ModelCustomRenderer[] {top_right, top_front, top_back, top_left, middle_front_left, middle_front_right, middle_back_left, middle_back_right, bottom_front_left, bottom_front_right, bottom_back_left, bottom_back_right};
    }

    @Override
    public void setRotationAngles(PoseContext context)
    {
        super.setRotationAngles(context);

        if (this.hasMissingStick())
        {
            this.warnMissing();
            return;
        }

        float ageInTicks = context.ageInTicks;
        float f = ageInTicks * (float) Math.PI * -0.1F;

        for (int i = 0; i < 4; ++i)
        {
            this.blaze_sticks[i].rotationPointY = -2.0F + MathHelper.cos(((float) (i * 2) + ageInTicks) * 0.25F);
            this.blaze_sticks[i].rotationPointX = MathHelper.cos(f) * 9.0F;
            this.blaze_sticks[i].rotationPointZ = MathHelper.sin(f) * 9.0F;
            ++f;
        }

        f = ((float) Math.PI / 4F) + ageInTicks * (float) Math.PI * 0.03F;

        for (int j = 4; j < 8; ++j)
        {
            this.blaze_sticks[j].rotationPointY = 2.0F + MathHelper.cos(((float) (j * 2) + ageInTicks) * 0.25F);
            this.blaze_sticks[j].rotationPointX = MathHelper.cos(f) * 7.0F;
            this.blaze_sticks[j].rotationPointZ = MathHelper.sin(f) * 7.0F;
            ++f;
        }

        f = 0.47123894F + ageInTicks * (float) Math.PI * -0.05F;

        for (int k = 8; k < 12; ++k)
        {
            this.blaze_sticks[k].rotationPointY = 11.0F + MathHelper.cos(((float) k * 1.5F + ageInTicks) * 0.5F);
            this.blaze_sticks[k].rotationPointX = MathHelper.cos(f) * 5.0F;
            this.blaze_sticks[k].rotationPointZ = MathHelper.sin(f) * 5.0F;
            ++f;
        }
    }

    private boolean hasMissingStick()
    {
        if (this.blaze_sticks == null || this.blaze_sticks.length != 12)
        {
            return true;
        }

        for (ModelCustomRenderer stick : this.blaze_sticks)
        {
            if (stick == null)
            {
                return true;
            }
        }

        return false;
    }

    private void warnMissing()
    {
        if (!this.warned)
        {
            Metamorph.log("ModelBlaze '" + this.model.name + "' is missing a blaze-stick limb; skipping stick animation.");
            this.warned = true;
        }
    }
}
