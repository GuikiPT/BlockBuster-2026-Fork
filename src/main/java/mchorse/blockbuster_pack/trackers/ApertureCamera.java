package mchorse.blockbuster_pack.trackers;

import javax.vecmath.Matrix4f;
import javax.vecmath.SingularMatrixException;
import javax.vecmath.Vector3f;

import mchorse.mclib.utils.MatrixUtils;
import mchorse.mclib.utils.MatrixUtils.RotationOrder;
import mchorse.mclib.utils.MatrixUtils.Transformation;
import net.minecraft.entity.LivingEntity;

/**
 * Aperture camera tracker (port of Blockbuster 2.7.2's {@code ApertureCamera},
 * roadmap P166). When Aperture's {@code TrackerModifier} (S15 P176) arms a
 * capture ({@link #enable} = true, {@link #tracking} = this tracker's name),
 * the next render of the matching {@link
 * mchorse.blockbuster_pack.morphs.TrackerMorph} reads the model-view matrix,
 * extracts the world position and YXZ Euler rotation, and publishes them into
 * the {@link #pos}/{@link #rot} statics for the modifier to consume.
 *
 * <p><b>Statics are the API</b> the {@code TrackerModifier} reads/writes:
 * {@link #enable} (one-shot arm flag, consumed per capture), {@link #tracking}
 * (target name), {@link #offsetPos}/{@link #offsetRot} (applied before the
 * capture) and the {@link #pos}/{@link #rot} results. Do not change their
 * shape.</p>
 *
 * <p><b>Capture seam (batch-4 integration).</b> Legacy read {@code
 * GL_MODELVIEW} directly inside {@link #track}, after applying the offset via
 * {@code glTranslate}/{@code glRotate}. There is no global model-view matrix on
 * 1.20.4, so the S6 client render layer installs a {@link ICapture} that
 * pre-applies the offset (translate {@code offsetPos}; rotate {@code
 * -offsetRot.y} about Y, {@code offsetRot.x} about X, {@code offsetRot.z} about
 * Z) onto its {@code MatrixStack} and fills the supplied buffer. When unset
 * (headless) the capture no-ops; {@link #extract(Matrix4f)} is nonetheless a
 * pure static so the extraction math is directly testable.</p>
 *
 * <p><b>S22 P285 — the capture is synchronous again.</b> {@link #enable} is now
 * armed <i>only</i> for the duration of the modifier's own throwaway render of
 * the tracked entity ({@code TrackerModifier.modify} → {@link #capturing}),
 * exactly as 1.12.2 bracketed it with {@code glPushMatrix/glLoadIdentity …
 * glPopMatrix}. Between P240 and P285 the flag was instead left up for the
 * <i>normal</i> world render to consume, which cost a frame of latency and
 * additionally let a GUI morph preview — or nothing at all, when the tracked
 * actor was frustum-culled — win the arm.</p>
 *
 * Legacy source: blockbuster-1.12/src/main/java/mchorse/blockbuster_pack/trackers/ApertureCamera.java
 */
public class ApertureCamera extends BaseTracker
{
    private static final Matrix4f BUFFER = new Matrix4f();

    public static boolean enable = false;
    public static String tracking = "";

    /**
     * S22 <b>P285</b>: true while the tracker modifier's synchronous throwaway
     * render of the tracked entity is in progress.
     *
     * <p>This is the port's stand-in for the {@code glPushMatrix/glLoadIdentity
     * … glPopMatrix} bracket 1.12.2 wrapped that render in. It is <b>not</b>
     * {@link #enable}: {@code enable} is a one-shot arm that {@link #track}
     * clears on the first matching tracker, so it is already false for the rest
     * of the pass, whereas this stays up for the whole pass. Render-side
     * bookkeeping that legacy performed in {@code RenderGlobal.renderEntities}
     * — i.e. one level <i>above</i> the renderer legacy re-invoked, and so
     * never reached twice per frame — reads this to opt out (see
     * {@code RenderActor.render}).</p>
     */
    public static boolean capturing = false;

    public static final Vector3f pos = new Vector3f();
    public static final Vector3f rot = new Vector3f();

    public static final Vector3f offsetPos = new Vector3f();
    public static final Vector3f offsetRot = new Vector3f();

    /**
     * Client render-layer seam supplying the offset-applied model-view matrix.
     * Installed by the S6 render pipeline; null in headless.
     */
    public interface ICapture
    {
        void capture(Matrix4f buffer);
    }

    public static ICapture captureHook;

    @Override
    public void track(LivingEntity entity, double x, double y, double z, float entityYaw, float partialTicks)
    {
        if (enable && tracking != null && !tracking.isEmpty() && tracking.equals(this.name))
        {
            if (captureHook == null)
            {
                return;
            }

            captureHook.capture(BUFFER);

            enable = false;

            extract(BUFFER);
        }
    }

    /**
     * Extract {@link #pos} and {@link #rot} from the given (offset pre-applied)
     * model-view matrix. Pure static so the math is testable without GL.
     *
     * <p>Mirrors the legacy sequence: translation → {@link #pos}; invert the
     * rotation ({@link SingularMatrixException} → silent abort, leaving
     * {@link #rot} untouched); YXZ Euler extraction; then the negative-Y-scale
     * branch — {@code scale.y < 0} adds 180° of roll, otherwise pitch and roll
     * are negated.</p>
     */
    public static void extract(Matrix4f modelView)
    {
        Transformation transform = MatrixUtils.extractTransformations(null, modelView);
        pos.set(transform.getTranslation3f());

        try
        {
            transform.rotation.invert();
        }
        catch (SingularMatrixException e)
        {
            return;
        }

        Vector3f rotation = transform.getRotation(RotationOrder.YXZ, 1);

        if (rotation != null)
        {
            rot.set(rotation);

            if (transform.getScale(1).y < 0)
            {
                rot.z += 180;
            }
            else
            {
                rot.x *= -1;
                rot.z *= -1;
            }
        }
    }

    @Override
    public boolean equals(Object obj)
    {
        return obj instanceof ApertureCamera && super.equals(obj);
    }
}
