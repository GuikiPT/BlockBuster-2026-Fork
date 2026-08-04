package mchorse.blockbuster.client.gui;

import mchorse.mclib.client.gui.framework.elements.GuiModelRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Orbit-camera math of the immersive editor (roadmap P143).
 *
 * <p>Per-frame the legacy {@code GuiImmersiveMorphMenu.onRenderTick}
 * (Phase.START branch, lines ~199-227) derived the world camera position from
 * the in-GUI model-renderer orbit and teleported the client player there. The
 * math is verbatim here; the legacy math used {@code javax.vecmath.Matrix4f}
 * and each {@code rotX}/{@code rotY} call RESET the matrix to a pure rotation
 * (it did not compose), so the transforms are replicated as standalone
 * rotations rather than a matrix product.</p>
 *
 * <p><b>Reconciliation note (P143 surface fix).</b> The first landing exposed
 * only the 12-scalar {@link #compute} and silently dropped two mutations the
 * legacy block performed: it wrote the clamped pitch back onto
 * {@code renderer.pitch} (later read by {@code refreshImmersive} and the GUI
 * render), and it applied the result to the client player through <em>both</em>
 * {@code setPositionAndRotation} and {@code setLocationAndAngles} plus a motion
 * zero. Reconstructing that in the screen would duplicate load-bearing parity
 * details, so {@link #compute(GuiModelRenderer, LivingEntity, float)},
 * {@link Result#applyTo(PlayerEntity)} and the one-call {@link #place} now own
 * them. The pure 12-scalar overload stays as the golden-test surface.</p>
 *
 * <p>Parity notes preserved verbatim:</p>
 * <ul>
 *   <li>pitch is clamped to {@code [-90, 90]} <em>before</em> use (and the
 *       clamped value is written back onto the renderer + used as the camera
 *       pitch);</li>
 *   <li>the orbit vector's Z is {@code (flight ? 0 : -scale) - 0.05F} — the
 *       {@code -scale} pull-back is skipped in flight mode, and the extra
 *       {@code -0.05} nudge is always applied;</li>
 *   <li>the first Y rotation is by {@code (180 - yaw)}, the second (into world
 *       space) by {@code -targetYaw};</li>
 *   <li>camera yaw is {@code rendererYaw + targetYaw + 180} (NOT wrapped —
 *       legacy passed the raw sum);</li>
 *   <li>camera Y is floored to {@code -64} (legacy void guard) after
 *       subtracting the eye height.</li>
 * </ul>
 */
public final class ImmersiveOrbitCamera
{
    /** Legacy float PI constant ({@code 3.1415927F}) — kept for bit-parity. */
    private static final float PI = 3.1415927F;

    /** Legacy void-guard floor for the camera Y (see class javadoc). */
    public static final double Y_FLOOR = -64.0;

    /** Extra pull-back nudge always added to the orbit Z. */
    public static final float Z_NUDGE = -0.05F;

    private ImmersiveOrbitCamera()
    {}

    /**
     * Computed world-camera placement for one immersive frame.
     */
    public static final class Result
    {
        public final double x;
        public final double y;
        public final double z;
        public final float yaw;
        public final float pitch;

        public Result(double x, double y, double z, float yaw, float pitch)
        {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        /**
         * Legacy camera teleport, verbatim: {@code setPositionAndRotation}
         * (yarn {@code updatePositionAndAngles} — clamps/fixes rotation) THEN
         * {@code setLocationAndAngles} (yarn {@code refreshPositionAndAngles} —
         * refreshes prev/last-tick position so the jump is seamless), then
         * {@code motionX = motionY = motionZ = 0}. Both calls are load-bearing;
         * dropping either produces camera jitter. Legacy did NOT touch head yaw
         * here — do not add it.
         */
        public void applyTo(PlayerEntity camera)
        {
            if (camera == null)
            {
                return;
            }

            camera.updatePositionAndAngles(this.x, this.y, this.z, this.yaw, this.pitch);
            camera.refreshPositionAndAngles(this.x, this.y, this.z, this.yaw, this.pitch);
            camera.setVelocity(Vec3d.ZERO);
        }
    }

    /**
     * Clamp a pitch value the way the legacy renderer did before deriving the
     * camera (the clamped value is also what legacy stored back onto
     * {@code renderer.pitch}).
     */
    public static float clampPitch(float pitch)
    {
        if (pitch < -90F)
        {
            return -90F;
        }

        if (pitch > 90F)
        {
            return 90F;
        }

        return pitch;
    }

    /**
     * Compute the world camera position/orientation for an immersive frame.
     *
     * @param rendererPosX orbit-center X in the GUI model renderer ({@code renderer.pos.x})
     * @param rendererPosY orbit-center Y
     * @param rendererPosZ orbit-center Z
     * @param yaw          renderer orbit yaw ({@code renderer.yaw})
     * @param pitch        renderer orbit pitch ({@code renderer.pitch}, pre-clamp)
     * @param scale        renderer orbit scale ({@code renderer.scale})
     * @param flight       renderer flight mode ({@code renderer.flight})
     * @param targetPosX   edited entity world X
     * @param targetPosY   edited entity world Y
     * @param targetPosZ   edited entity world Z
     * @param targetYaw    edited entity {@code rotationYaw}
     * @param eyeHeight    camera-player eye height (subtracted from Y)
     */
    public static Result compute(
        float rendererPosX, float rendererPosY, float rendererPosZ,
        float yaw, float pitch, float scale, boolean flight,
        double targetPosX, double targetPosY, double targetPosZ,
        float targetYaw, float eyeHeight)
    {
        float clampedPitch = clampPitch(pitch);

        /* vec = (0, 0, (flight ? 0 : -scale) - 0.05) */
        float vx = 0F;
        float vy = 0F;
        float vz = (flight ? 0F : -scale) + Z_NUDGE;

        /* mat.rotX(pitch); mat.transform(vec) */
        {
            float a = clampedPitch / 180.0F * PI;
            float cos = (float) Math.cos(a);
            float sin = (float) Math.sin(a);
            float ny = cos * vy - sin * vz;
            float nz = sin * vy + cos * vz;
            vy = ny;
            vz = nz;
        }

        /* mat.rotY(180 - yaw); mat.transform(vec) */
        {
            float a = (180.0F - yaw) / 180.0F * PI;
            float cos = (float) Math.cos(a);
            float sin = (float) Math.sin(a);
            float nx = cos * vx + sin * vz;
            float nz = -sin * vx + cos * vz;
            vx = nx;
            vz = nz;
        }

        /* temp = renderer.pos + vec */
        float tx = rendererPosX + vx;
        float ty = rendererPosY + vy;
        float tz = rendererPosZ + vz;

        /* mat.rotY(-targetYaw); mat.transform(temp) */
        {
            float a = -targetYaw / 180.0F * PI;
            float cos = (float) Math.cos(a);
            float sin = (float) Math.sin(a);
            float nx = cos * tx + sin * tz;
            float nz = -sin * tx + cos * tz;
            tx = nx;
            tz = nz;
        }

        /* temp += target.pos */
        double wx = tx + targetPosX;
        double wy = ty + targetPosY;
        double wz = tz + targetPosZ;

        double camY = Math.max(wy - eyeHeight, Y_FLOOR);
        float camYaw = yaw + targetYaw + 180F;

        return new Result(wx, camY, wz, camYaw, clampedPitch);
    }

    /**
     * Legacy {@code renderer.pitch = MathUtils.clamp(renderer.pitch, -90F, 90F)}
     * — the in-place write-back the first landing of this class dropped. The
     * clamped value is what the GUI render and {@code refreshImmersive} read
     * afterwards, so it must land on the renderer, not just in the result.
     *
     * @return the clamped pitch (0 for a null renderer — total rule)
     */
    public static float clampRendererPitch(GuiModelRenderer renderer)
    {
        if (renderer == null)
        {
            return 0F;
        }

        return renderer.pitch = clampPitch(renderer.pitch);
    }

    /**
     * Retained-mode form: reads the orbit off the live GUI model renderer and
     * the target entity. <b>Also writes the clamped pitch back onto
     * {@code renderer.pitch}</b> via {@link #clampRendererPitch}, exactly as
     * legacy.
     *
     * @param eyeHeight the camera player's eye height. Legacy read
     *        {@code camera.getEyeHeight()}, i.e. the <em>pose-dependent</em>
     *        value (1.62 standing / 1.54 sneaking for a 1.12.2 player).
     *        {@link #place} reproduces exactly that pair — see
     *        {@link #SNEAKING_EYE_HEIGHT}; yarn's live
     *        {@code getEyeHeight(getPose())} would give 1.27 while crouching and
     *        make the camera jump ~0.35 instead of legacy's ~0.08.
     * @return {@code null} when the renderer or target is missing (total rule)
     */
    public static Result compute(GuiModelRenderer renderer, LivingEntity target, float eyeHeight)
    {
        if (renderer == null || target == null)
        {
            return null;
        }

        /* Legacy mutated the renderer's pitch in place before using it. */
        clampRendererPitch(renderer);

        return compute(
            renderer.pos.x, renderer.pos.y, renderer.pos.z,
            renderer.yaw, renderer.pitch, renderer.scale, renderer.flight,
            target.getX(), target.getY(), target.getZ(),
            target.getYaw(), eyeHeight);
    }

    /**
     * The whole legacy Phase.START camera block in one call: clamp + write back
     * the renderer pitch, derive the world placement (using the camera's own eye
     * height) and teleport the camera player there with motion zeroed.
     *
     * @return the applied placement, or {@code null} when anything was missing
     */
    /**
     * 1.12.2's sneaking player eye height ({@code 1.62 - 0.08}). Hard-coded so
     * the immersive camera keeps legacy's small sneak nudge — 1.20.4's crouching
     * eye height is 1.27, which would be a ~0.35 drop instead.
     */
    public static final float SNEAKING_EYE_HEIGHT = 1.54F;

    /**
     * Legacy {@code camera.getEyeHeight()}: the pose-dependent 1.62 / 1.54 pair.
     */
    public static float cameraEyeHeight(PlayerEntity camera)
    {
        return camera.isInSneakingPose() ? SNEAKING_EYE_HEIGHT : camera.getStandingEyeHeight();
    }

    public static Result place(GuiModelRenderer renderer, LivingEntity target, PlayerEntity camera)
    {
        if (camera == null)
        {
            return null;
        }

        Result result = compute(renderer, target, cameraEyeHeight(camera));

        if (result != null)
        {
            result.applyTo(camera);
        }

        return result;
    }
}
