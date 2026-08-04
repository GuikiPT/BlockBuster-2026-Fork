package mchorse.vanilla_pack.morphs;

import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Iron golem morph (roadmap P53.1).
 *
 * <p>Movement damping to make the (very powerful) iron golem disguise slower:
 * horizontal x/z ×0.5, rising y ×0.9, falling y ×1.3. Dispatched for the legacy
 * {@code minecraft:villager_golem} id and the modern {@code minecraft:iron_golem}
 * id.</p>
 *
 * <p>Legacy math preserved exactly (note: the horizontal ×0.5 is applied once
 * in the branch <i>and</i> once unconditionally after — a compound ×0.25 on the
 * falling branch, ×0.5 on the rising branch, matching legacy line-for-line).</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/vanilla_pack/morphs/IronGolemMorph.java
 */
public class IronGolemMorph extends EntityMorph
{
    @Override
    public void update(LivingEntity target)
    {
        target.setVelocity(dampVelocity(target.getVelocity()));

        super.update(target);
    }

    /**
     * Pure damping math (extracted for headless verification): horizontal x/z
     * ×0.5, rising y ×0.9, falling y ×1.3, with a second unconditional
     * horizontal ×0.5 (compound ×0.25 while falling, matching legacy).
     */
    public static Vec3d dampVelocity(Vec3d v)
    {
        double mx = v.x;
        double my = v.y;
        double mz = v.z;

        if (my > 0)
        {
            my *= 0.9;
        }
        else
        {
            mx *= 0.5;
            mz *= 0.5;
            my *= 1.3;
        }

        mx *= 0.5;
        mz *= 0.5;

        return new Vec3d(mx, my, mz);
    }

    @Override
    public AbstractMorph create()
    {
        return new IronGolemMorph();
    }
}
