package mchorse.blockbuster_pack.client.model;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.ElytraEntityModel;
import net.minecraft.client.render.entity.model.EntityModelPartNames;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Actor elytra model (roadmap P76).
 *
 * <p>Port of Blockbuster 2.7.2's {@code blockbuster_pack/client/model/ModelElytra}
 * — a copy of vanilla's elytra model extended so an actor animates its wings like
 * a player. Vanilla's {@link ElytraEntityModel} keeps its wings private and folds
 * the smoothing into {@code AbstractClientPlayer}, so (as in 1.12.2) we bake our
 * own two-wing model and reimplement the {@code rotateElytra*} lerp, reading the
 * smoothing state from {@link IElytraAnimated} (the actor, S8).</p>
 *
 * <p>The pose math ({@link #targetAngles} + the {@code 0.1} lerp) is the
 * behavior-parity core and is golden-tested headlessly.</p>
 */
public class ModelElytra
{
    private final ModelPart leftWing;
    private final ModelPart rightWing;

    public ModelElytra()
    {
        ModelPart root = ElytraEntityModel.getTexturedModelData().createModel();

        this.leftWing = root.getChild(EntityModelPartNames.LEFT_WING);
        this.rightWing = root.getChild(EntityModelPartNames.RIGHT_WING);
    }

    /**
     * Compute the target wing pose {@code {f, f1, f2, f3}} (leftWing.pitch,
     * leftWing.roll, leftWing.pivotY, leftWing.yaw targets) exactly as legacy
     * {@code setRotationAngles}: default hang, elytra-flight pitch envelope, or
     * the sneaking pose.
     */
    public static float[] targetAngles(boolean flying, boolean sneaking, Vec3d velocity)
    {
        float f = 0.2617994F;
        float f1 = -0.2617994F;
        float f2 = 0.0F;
        float f3 = 0.0F;

        if (flying)
        {
            float f4 = 1.0F;

            if (velocity.y < 0.0D)
            {
                Vec3d normalized = velocity.normalize();
                f4 = 1.0F - (float) Math.pow(-normalized.y, 1.5D);
            }

            f = f4 * 0.34906584F + (1.0F - f4) * f;
            f1 = f4 * -((float) Math.PI / 2F) + (1.0F - f4) * f1;
        }
        else if (sneaking)
        {
            f = ((float) Math.PI * 2F / 9F);
            f1 = -((float) Math.PI / 4F);
            f2 = 3.0F;
            f3 = 0.08726646F;
        }

        return new float[] {f, f1, f2, f3};
    }

    /**
     * Set the wings' rotation angles for {@code entity}, smoothing toward the
     * target at {@code 0.1} per frame via the {@link IElytraAnimated} carrier
     * (actor). Right wing mirrors the left.
     */
    public void setAngles(LivingEntity entity, boolean flying, boolean sneaking)
    {
        float[] t = targetAngles(flying, sneaking, entity.getVelocity());
        float f = t[0];
        float f1 = t[1];
        float f2 = t[2];
        float f3 = t[3];

        this.leftWing.pivotX = 5.0F;
        this.leftWing.pivotY = f2;

        if (entity instanceof IElytraAnimated)
        {
            IElytraAnimated actor = (IElytraAnimated) entity;

            actor.setRotateElytraX((float) (actor.getRotateElytraX() + (f - actor.getRotateElytraX()) * 0.1D));
            actor.setRotateElytraY((float) (actor.getRotateElytraY() + (f3 - actor.getRotateElytraY()) * 0.1D));
            actor.setRotateElytraZ((float) (actor.getRotateElytraZ() + (f1 - actor.getRotateElytraZ()) * 0.1D));

            this.leftWing.pitch = actor.getRotateElytraX();
            this.leftWing.yaw = actor.getRotateElytraY();
            this.leftWing.roll = actor.getRotateElytraZ();
        }
        else
        {
            this.leftWing.pitch = f;
            this.leftWing.roll = f1;
            this.leftWing.yaw = f3;
        }

        this.rightWing.pivotX = -this.leftWing.pivotX;
        this.rightWing.yaw = -this.leftWing.yaw;
        this.rightWing.pivotY = this.leftWing.pivotY;
        this.rightWing.pitch = this.leftWing.pitch;
        this.rightWing.roll = -this.leftWing.roll;
    }

    public void render(MatrixStack matrices, VertexConsumer consumer, int light, int overlay, float r, float g, float b, float a)
    {
        this.leftWing.render(matrices, consumer, light, overlay, r, g, b, a);
        this.rightWing.render(matrices, consumer, light, overlay, r, g, b, a);
    }
}
