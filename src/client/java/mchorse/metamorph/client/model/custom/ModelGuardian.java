package mchorse.metamorph.client.model.custom;

import mchorse.blockbuster.api.Model;
import mchorse.blockbuster.client.model.ModelCustom;
import mchorse.blockbuster.client.model.ModelCustomRenderer;
import mchorse.blockbuster.client.model.PoseContext;
import mchorse.blockbuster.client.model.parsing.IModelCustom;
import mchorse.metamorph.Metamorph;
import net.minecraft.util.math.MathHelper;

/**
 * Guardian model (roadmap P81).
 *
 * <p>Verbatim port of Metamorph's {@code ModelGuardian}: the eye tracks pitch
 * ({@code eye.rotationPointY += headPitch/90}, a {@code +=} onto the base pose)
 * and the tail Y-waves with descending amplitude ({@code 0.1 / 0.075 / 0.05}).
 * The wave uses {@code limbSwingAmount + 0.1} (local bump, so the tail always
 * has a little life). {@code tail_end} is injected but not driven here.</p>
 */
public class ModelGuardian extends ModelCustom implements IModelCustom
{
    public ModelCustomRenderer eye;

    public ModelCustomRenderer tail_1;
    public ModelCustomRenderer tail_2;
    public ModelCustomRenderer tail_3;
    public ModelCustomRenderer tail_end;

    private boolean warned;

    public ModelGuardian(Model model)
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

        if (this.eye == null || this.tail_1 == null || this.tail_2 == null || this.tail_3 == null)
        {
            this.warnMissing();
            return;
        }

        /* Make eye look up and down */
        this.eye.rotationPointY += context.headPitch / 90;

        /* Legacy did `limbSwingAmount += 0.1` (double literal) → widen-then-narrow;
         * kept exactly so the last-bit float result matches 1.12.2. */
        float limbSwingAmount = (float) (context.limbSwingAmount + 0.1);

        /* Make tail look cool */
        this.tail_1.rotateAngleY = limbSwingAmount * MathHelper.sin(context.ageInTicks / 2) * (float) Math.PI * 0.1F;
        this.tail_2.rotateAngleY = limbSwingAmount * MathHelper.sin(context.ageInTicks / 2) * (float) Math.PI * 0.075F;
        this.tail_3.rotateAngleY = limbSwingAmount * MathHelper.sin(context.ageInTicks / 2) * (float) Math.PI * 0.05F;
    }

    private void warnMissing()
    {
        if (!this.warned)
        {
            Metamorph.log("ModelGuardian '" + this.model.name + "' is missing eye/tail_1..3; skipping guardian animation.");
            this.warned = true;
        }
    }
}
