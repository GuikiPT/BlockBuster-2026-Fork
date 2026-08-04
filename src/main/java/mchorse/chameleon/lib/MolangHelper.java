package mchorse.chameleon.lib;

import mchorse.mclib.math.Variable;
import mchorse.mclib.math.molang.MolangParser;
import mchorse.mclib.math.molang.expressions.MolangExpression;
import mchorse.mclib.utils.Interpolations;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * The Molang query surface Chameleon animations can read: the variable set
 * ({@link #registerVars}), the per-frame variable fill
 * ({@link #setMolangVariables}), and the keyframe value normalisation
 * ({@link #getValue}).
 *
 * <h3>Port note — why this class is in the common source set</h3>
 *
 * <p>Legacy declared the whole class {@code @SideOnly(CLIENT)} because
 * {@code setMolangVariables} read three things off {@code Minecraft}: the render
 * partial tick, the loaded-entity count, and the camera position. Everything else
 * it touches — the entity's own state, the world's time and moon phase — is
 * common API on 1.20.4.</p>
 *
 * <p>Rather than push {@link #getValue}/{@link Component} (which
 * {@link mchorse.chameleon.lib.data.animation.AnimationInterpolation} needs, and
 * which are pure math) into the client source set, the three client-only reads
 * move behind {@link IClientContext}, installed by {@code ChameleonClient} at
 * client init. With no context — dedicated server, headless test — those three
 * queries evaluate to {@code 0} and the partial tick is {@code 0}; every other
 * query still resolves, so an animation can be evaluated without a game.</p>
 *
 * Legacy source: chameleon/src/main/java/mchorse/chameleon/lib/MolangHelper.java
 */
public class MolangHelper
{
    /**
     * The client-only half of {@link #setMolangVariables} (port seam).
     *
     * <p>Installed by {@code ChameleonClient}; {@code null} everywhere else.</p>
     */
    public interface IClientContext
    {
        /** {@code Minecraft.getRenderPartialTicks()}. */
        float partialTick();

        /**
         * {@code mc.world.loadedEntityList.size()} — feeds
         * {@code query.actor_count}.
         */
        int actorCount(World world);

        /**
         * The camera's <b>world</b> position.
         *
         * <p>Legacy built this as "interpolated render-view-entity position plus
         * {@code ActiveRenderInfo.getCameraPosition()}", where that static was the
         * camera's offset <i>from</i> the view entity. 1.20.4's
         * {@code GameRenderer.getCamera().getPos()} is that same point, already
         * summed.</p>
         */
        Vec3d cameraPosition();
    }

    /** Installed at client init; {@code null} on a server / in tests. */
    public static IClientContext context;

    public static void registerVars(MolangParser parser)
    {
        parser.register(new Variable("query.anim_time", 0));
        parser.register(new Variable("query.life_time", 0));
        parser.register(new Variable("query.actor_count", 0));
        parser.register(new Variable("query.time_of_day", 0));
        parser.register(new Variable("query.moon_phase", 0));
        parser.register(new Variable("query.distance_from_camera", 0));
        parser.register(new Variable("query.is_on_ground", 0));
        parser.register(new Variable("query.is_in_water", 0));
        parser.register(new Variable("query.is_in_water_or_rain", 0));
        parser.register(new Variable("query.health", 0));
        parser.register(new Variable("query.max_health", 0));
        parser.register(new Variable("query.is_on_fire", 0));
        parser.register(new Variable("query.ground_speed", 0));
        parser.register(new Variable("query.yaw_speed", 0));
    }

    public static void setMolangVariables(MolangParser parser, LivingEntity target, float frame)
    {
        parser.setValue("query.anim_time", frame / 20);
        parser.setValue("query.life_time", frame / 20);

        IClientContext client = context;
        World world = target.getWorld();
        float partialTick = client == null ? 0F : client.partialTick();

        parser.setValue("query.actor_count", client == null ? 0 : client.actorCount(world));
        /* Legacy read getTotalWorldTime(), not the time of day, despite the
         * variable's name — kept verbatim, an animation may depend on the
         * monotonically increasing value. */
        parser.setValue("query.time_of_day", normalizeTime(world.getTime()));
        parser.setValue("query.moon_phase", world.getMoonPhase());

        Vec3d entityPosition = new Vec3d(
            MathHelper.lerp(partialTick, target.prevX, target.getX()),
            MathHelper.lerp(partialTick, target.prevY, target.getY()),
            MathHelper.lerp(partialTick, target.prevZ, target.getZ())
        );
        Vec3d camera = client == null ? entityPosition : client.cameraPosition();
        double distance = camera.distanceTo(entityPosition);

        parser.setValue("query.distance_from_camera", distance);
        parser.setValue("query.is_on_ground", booleanToDouble(target.isOnGround()));
        parser.setValue("query.is_in_water", booleanToDouble(target.isTouchingWater()));
        parser.setValue("query.is_in_water_or_rain", booleanToDouble(target.isWet()));

        parser.setValue("query.health", target.getHealth());
        parser.setValue("query.max_health", target.getMaxHealth());
        parser.setValue("query.is_on_fire", booleanToDouble(target.isOnFire()));

        Vec3d motion = target.getVelocity();
        double dx = motion.x;
        double dz = motion.z;
        float groundSpeed = MathHelper.sqrt((float) ((dx * dx) + (dz * dz)));
        parser.setValue("query.ground_speed", groundSpeed);

        parser.setValue("query.yaw_speed", target.headYaw - target.prevHeadYaw);

        /* Chameleon specific queries */
        float yaw = Interpolations.lerp(target.prevHeadYaw, target.headYaw, partialTick);
        float bodyYaw = Interpolations.lerp(target.prevBodyYaw, target.bodyYaw, partialTick);

        parser.setValue("query.head_yaw", yaw - bodyYaw);
        parser.setValue("query.head_pitch", Interpolations.lerp(target.prevPitch, target.getPitch(), partialTick));

        double velocity = Math.sqrt(motion.x * motion.x + motion.y * motion.y + motion.z * motion.z);
        /* 1.20.4's LimbAnimator computes exactly the two expressions legacy
         * spelled out by hand: getSpeed(t) is lerp(prevLimbSwingAmount,
         * limbSwingAmount, t) and getPos(t) is limbSwing - limbSwingAmount *
         * (1 - t). */
        float limbSwingAmount = target.limbAnimator.getSpeed(partialTick);
        float limbSwing = target.limbAnimator.getPos(partialTick);

        /* There is still a tiny bit of vertical velocity (gravity) when an
         * entity stands still, so set it to zero in that case */
        if (target.isOnGround() && motion.y < 0 && (Math.abs(motion.x) < 0.001 || Math.abs(motion.z) < 0.001))
        {
            velocity = 0;
        }

        if (limbSwingAmount > 1.0F)
        {
            limbSwingAmount = 1.0F;
        }

        parser.setValue("query.velocity", velocity);
        parser.setValue("query.limb_swing", limbSwing);
        parser.setValue("query.limb_swing_amount", limbSwingAmount);
        parser.setValue("query.age", target.age + partialTick);
    }

    private static double normalizeTime(long totalWorldTime)
    {
        return totalWorldTime / 24000D;
    }

    private static double booleanToDouble(boolean bool)
    {
        return bool ? 1D : 0D;
    }

    /**
     * Get value from given value of a keyframe (end or start)
     *
     * This method is responsible for processing keyframe value, because
     * for some reason constant values are exported in radians, while molang
     * expressions are in degrees
     *
     * Plus X and Y axis of rotation are inverted for some reason ...
     */
    public static double getValue(MolangExpression value, Component component, Direction.Axis axis)
    {
        double out = value.get();

        if (component == Component.ROTATION)
        {
            if (axis == Direction.Axis.X || axis == Direction.Axis.Y)
            {
                out *= -1;
            }
        }
        else if (component == Component.SCALE)
        {
            out = out - 1;
        }

        return out;
    }

    /**
     * Component enum determines which part of the animation is being
     * calculated
     */
    public static enum Component
    {
        POSITION, ROTATION, SCALE
    }
}
