package mchorse.metamorph.client.model.custom;

import java.util.ArrayList;
import java.util.List;

import mchorse.blockbuster.api.Model;
import mchorse.blockbuster.client.model.ModelCustom;
import mchorse.blockbuster.client.model.ModelCustomRenderer;
import mchorse.blockbuster.client.model.PoseContext;
import mchorse.blockbuster.client.model.parsing.IModelCustom;
import net.minecraft.util.math.MathHelper;

/**
 * Extended model (roadmap P81).
 *
 * <p>Verbatim port of Metamorph's {@code ModelExtended}. Unlike the mob shims,
 * this one binds by {@link String#contains}, not by exact same-named field
 * injection: any limb whose name <b>contains</b> {@code "wheel_"} gets wheel
 * animation and any limb whose name contains {@code "wing_"} gets wing
 * animation. A wheel whose name additionally contains {@code "frontal"} steers.</p>
 *
 * <p>Quirk kept as written: the wing sign factor is
 * {@code (invert || mirror) ? -1 : 1}, which differs from the base class's
 * {@code mirror ^ invert}. And {@code ModelExtended}'s own wing pass runs
 * <i>after</i> {@code super.setRotationAngles} (P82's wing branch), so the two
 * do not double-animate — this one wins.</p>
 */
public class ModelExtended extends ModelCustom implements IModelCustom
{
    public ModelCustomRenderer[] wheels;
    public ModelCustomRenderer[] wings;

    public ModelExtended(Model model)
    {
        super(model);
    }

    @Override
    public void onGenerated()
    {
        List<ModelCustomRenderer> wheels = new ArrayList<ModelCustomRenderer>();
        List<ModelCustomRenderer> wings = new ArrayList<ModelCustomRenderer>();

        for (ModelCustomRenderer limb : this.limbs)
        {
            String name = limb.limb.name;

            if (name.contains("wheel_")) wheels.add(limb);
            if (name.contains("wing_")) wings.add(limb);
        }

        this.wheels = wheels.toArray(new ModelCustomRenderer[wheels.size()]);
        this.wings = wings.toArray(new ModelCustomRenderer[wings.size()]);
    }

    @Override
    public void setRotationAngles(PoseContext context)
    {
        super.setRotationAngles(context);

        for (ModelCustomRenderer wheel : this.wheels)
        {
            wheel.rotateAngleX += context.limbSwing;

            if (wheel.limb.name.contains("frontal"))
            {
                wheel.rotateAngleY = context.netHeadYaw / 180 * (float) Math.PI;
            }
        }

        for (ModelCustomRenderer wing : this.wings)
        {
            wing.rotateAngleY = MathHelper.cos(context.ageInTicks * 1.3F) * (float) Math.PI * 0.25F * (0.5F + context.limbSwingAmount) * (wing.limb.invert || wing.limb.mirror ? -1 : 1);
        }
    }
}
