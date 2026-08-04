package mchorse.vanilla_pack.actions;

import org.jetbrains.annotations.Nullable;

import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Jump action (roadmap P49.1, registry id {@code jump}).
 *
 * <p>Makes player jump very high like a <s>horse</s> rabbit. The strength of
 * this jump is about 3 blocks high.</p>
 *
 * <p>Note the {@code isWet()} gate (not {@code isInWater()}) — legacy's, so
 * standing in the rain also blocks the jump.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../vanilla_pack/actions/Jump.java
 */
public class Jump implements IAction
{
    @Override
    public void execute(LivingEntity target, @Nullable AbstractMorph morph)
    {
        if (target.isOnGround() && !target.isWet())
        {
            Vec3d motion = target.getVelocity();

            target.setVelocity(motion.x * 4.0, 0.75, motion.z * 4.0);
            target.velocityModified = true;
        }
    }
}
