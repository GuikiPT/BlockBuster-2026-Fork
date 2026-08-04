package mchorse.metamorph.client.model.custom;

import mchorse.blockbuster.api.Model;
import mchorse.blockbuster.client.model.ModelCustom;
import mchorse.blockbuster.client.model.ModelCustomRenderer;
import mchorse.blockbuster.client.model.PoseContext;
import mchorse.blockbuster.client.model.parsing.IModelCustom;
import mchorse.metamorph.Metamorph;
import net.minecraft.util.math.MathHelper;

/**
 * Silverfish model (roadmap P81).
 *
 * <p>Verbatim port of Metamorph's {@code ModelSilverfish}. {@code onGenerated}
 * builds {@code spikes = {spike_1..3}} and {@code wings = {head, wing_1..5,
 * tail_end}} — note {@code head} is {@code wings[0]}. The body wave over
 * {@code wings[i]} is indexed by {@code |i-2|}; the spikes then copy specific
 * wing values ({@code spikes[0] ← wings[2]}, {@code spikes[1] ← wings[4]},
 * {@code spikes[2] ← wings[1]}), and — kept exactly — {@code spikes[0]} copies
 * only {@code rotateAngleY} while {@code spikes[1]}/{@code spikes[2]} copy both
 * {@code rotateAngleY} and {@code rotationPointX}.</p>
 */
public class ModelSilverfish extends ModelCustom implements IModelCustom
{
    public ModelCustomRenderer head;
    public ModelCustomRenderer wing_1;
    public ModelCustomRenderer wing_2;
    public ModelCustomRenderer wing_3;
    public ModelCustomRenderer wing_4;
    public ModelCustomRenderer wing_5;
    public ModelCustomRenderer tail_end;

    public ModelCustomRenderer spike_1;
    public ModelCustomRenderer spike_2;
    public ModelCustomRenderer spike_3;

    public ModelCustomRenderer[] spikes;
    public ModelCustomRenderer[] wings;

    private boolean warned;

    public ModelSilverfish(Model model)
    {
        super(model);
    }

    @Override
    public void onGenerated()
    {
        this.spikes = new ModelCustomRenderer[] {spike_1, spike_2, spike_3};
        this.wings = new ModelCustomRenderer[] {head, wing_1, wing_2, wing_3, wing_4, wing_5, tail_end};
    }

    @Override
    public void setRotationAngles(PoseContext context)
    {
        super.setRotationAngles(context);

        if (this.hasMissing())
        {
            this.warnMissing();
            return;
        }

        float ageInTicks = context.ageInTicks;

        for (int i = 0; i < this.wings.length; ++i)
        {
            this.wings[i].rotateAngleY = MathHelper.cos(ageInTicks * 0.9F + (float) i * 0.15F * (float) Math.PI) * (float) Math.PI * 0.05F * (float) (1 + Math.abs(i - 2));
            this.wings[i].rotationPointX = MathHelper.sin(ageInTicks * 0.9F + (float) i * 0.15F * (float) Math.PI) * (float) Math.PI * 0.2F * (float) Math.abs(i - 2);
        }

        this.spikes[0].rotateAngleY = this.wings[2].rotateAngleY;
        this.spikes[1].rotateAngleY = this.wings[4].rotateAngleY;
        this.spikes[1].rotationPointX = this.wings[4].rotationPointX;
        this.spikes[2].rotateAngleY = this.wings[1].rotateAngleY;
        this.spikes[2].rotationPointX = this.wings[1].rotationPointX;
    }

    private boolean hasMissing()
    {
        if (this.wings == null || this.spikes == null)
        {
            return true;
        }

        for (ModelCustomRenderer wing : this.wings)
        {
            if (wing == null)
            {
                return true;
            }
        }

        for (ModelCustomRenderer spike : this.spikes)
        {
            if (spike == null)
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
            Metamorph.log("ModelSilverfish '" + this.model.name + "' is missing a wing/spike limb; skipping silverfish animation.");
            this.warned = true;
        }
    }
}
