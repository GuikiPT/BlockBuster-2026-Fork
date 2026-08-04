package mchorse.blockbuster.utils;

/**
 * Two-tick entity position history accessor (roadmap P151.1).
 *
 * <p>The 1.12.2 Blockbuster coremod ({@code EntityTransformer}) injected three
 * {@code public double prevPrevPosX/Y/Z} fields onto {@code net.minecraft.entity.Entity}
 * and assigned them from {@code prevPosX/Y/Z} at the <b>start</b> of
 * {@code Entity#onEntityUpdate}, i.e. one tick behind {@code prevPos*}. The
 * Snowstorm particle collision component (P151) reads that history to compute
 * the inertia / momentum kick that a resting particle receives from the entity
 * it is sitting on.</p>
 *
 * <p>On Fabric this is provided by a mixin on {@code Entity} (see
 * {@code EntityPrevPrevPosMixin}) that implements this interface. Code reaches
 * it exclusively through {@link EntityTransformationUtils}, whose static getters
 * are the only public surface the particle engine touches — exactly like the
 * 1.12.2 utility class.</p>
 */
public interface IEntityPrevPrevPos
{
    double blockbuster$getPrevPrevPosX();

    double blockbuster$getPrevPrevPosY();

    double blockbuster$getPrevPrevPosZ();
}
