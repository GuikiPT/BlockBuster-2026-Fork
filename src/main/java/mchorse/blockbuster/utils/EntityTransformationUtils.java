package mchorse.blockbuster.utils;

import net.minecraft.entity.Entity;

/**
 * Utility to read an entity's two-tick position history (roadmap P151.1).
 *
 * <p>In 1.12.2 this class was a set of stub getters ({@code return 0;}) whose
 * bodies were rewritten at load time by the {@code EntityTransformationUtilsTransformer}
 * coremod to read the ASM-injected {@code prevPrevPosX/Y/Z} fields on
 * {@code Entity}. The Fabric port replaces the coremod with a mixin
 * ({@code EntityPrevPrevPosMixin}) that implements {@link IEntityPrevPrevPos};
 * these getters simply forward to it.</p>
 *
 * <p>The {@code instanceof} guard reproduces the un-transformed legacy behavior:
 * when the mixin has not been applied to the object (e.g. a plain unit-test mock),
 * the getters return {@code 0.0} exactly like the stub source did. Keeping the
 * static signatures identical to 1.12.2 means the P151 collision call sites stay
 * byte-for-byte the same.</p>
 */
public class EntityTransformationUtils
{
    public static double getPrevPrevPosX(Entity entity)
    {
        if (entity instanceof IEntityPrevPrevPos history)
        {
            return history.blockbuster$getPrevPrevPosX();
        }

        return 0;
    }

    public static double getPrevPrevPosY(Entity entity)
    {
        if (entity instanceof IEntityPrevPrevPos history)
        {
            return history.blockbuster$getPrevPrevPosY();
        }

        return 0;
    }

    public static double getPrevPrevPosZ(Entity entity)
    {
        if (entity instanceof IEntityPrevPrevPos history)
        {
            return history.blockbuster$getPrevPrevPosZ();
        }

        return 0;
    }
}
