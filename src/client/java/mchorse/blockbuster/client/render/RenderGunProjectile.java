package mchorse.blockbuster.client.render;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.common.GunProps;
import mchorse.blockbuster.common.entity.EntityGunProjectile;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/**
 * Gun projectile renderer (P197).
 *
 * <p>Port of the 1.12.2 {@code RenderGunProjectile}: renders the projectile's
 * morph against a dummy actor, with a fade envelope over the projectile's
 * lifetime (and a separate vanish-delay envelope), the deliberate anti-Z-fight
 * jitter, and optional yaw/pitch alignment to the projectile's flight. The
 * scale/jitter/angle math is in the pure {@link GunProjectileRenderMath} helper
 * (headlessly tested); the morph draw routes through {@link GunMorphRenderer}
 * (S6 pipeline seam). Frustum culling is bypassed under
 * {@code actor_always_render}.</p>
 *
 * Legacy source of truth:
 * {@code blockbuster-1.12/.../client/render/RenderGunProjectile.java}.
 */
public class RenderGunProjectile extends EntityRenderer<EntityGunProjectile>
{
    public RenderGunProjectile(EntityRendererFactory.Context context)
    {
        super(context);
    }

    @Override
    public Identifier getTexture(EntityGunProjectile entity)
    {
        /* Morph-rendered — no entity texture (legacy returned null). */
        return null;
    }

    /**
     * Legacy {@code shouldRender}: the {@code actor_always_render} config forces
     * the projectile to render even when off-screen (kept animating).
     */
    @Override
    public boolean shouldRender(EntityGunProjectile entity, Frustum frustum, double x, double y, double z)
    {
        return shouldRender(Blockbuster.actorAlwaysRender.get(), super.shouldRender(entity, frustum, x, y, z));
    }

    /** Pure {@code shouldRender} decision (headless-testable). */
    public static boolean shouldRender(boolean actorAlwaysRender, boolean superShouldRender)
    {
        return actorAlwaysRender || superShouldRender;
    }

    @Override
    public void render(EntityGunProjectile entity, float entityYaw, float partialTicks, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light)
    {
        GunProps props = entity.props;
        AbstractMorph morph = entity.morph.get();

        if (props == null || morph == null)
        {
            return;
        }

        float scale = GunProjectileRenderMath.scale(entity.getId(), entity.ticksExisted, partialTicks, props.lifeSpan, props.fadeIn, props.fadeOut, entity.vanish, entity.vanishDelay);

        matrices.push();

        matrices.scale(scale, scale, scale);

        if (props.yaw)
        {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(GunProjectileRenderMath.yawAngle(entity.prevYaw, entity.getYaw(), partialTicks)));
        }

        if (props.pitch)
        {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(GunProjectileRenderMath.pitchAngle(entity.prevPitch, entity.getPitch(), partialTicks)));
        }

        GunPropsRenderer.applyTransform(matrices, props.projectileTransform);

        props.createEntity(entity.getWorld());
        LivingEntity dummy = props.getEntity(entity);

        GunMorphRenderer.draw(morph, dummy, matrices, vertexConsumers, light, partialTicks);

        matrices.pop();
    }
}
