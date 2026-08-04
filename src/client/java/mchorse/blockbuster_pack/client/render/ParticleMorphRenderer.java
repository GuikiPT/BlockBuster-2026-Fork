package mchorse.blockbuster_pack.client.render;

import mchorse.blockbuster_pack.morphs.ParticleMorph;
import mchorse.blockbuster_pack.morphs.ParticleMorph.MorphParticle;
import mchorse.blockbuster_pack.morphs.SnowstormMorph;
import mchorse.mclib.client.gui.framework.elements.GuiModelRenderer;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.render.RenderingUtilsClient;
import mchorse.mclib.utils.Interpolations;
import mchorse.mclib.utils.MatrixUtils;
import mchorse.metamorph.client.MorphRenderUtils;
import mchorse.metamorph.client.render.IMorphRenderer;
import mchorse.metamorph.client.render.MorphRenderContext;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

import javax.vecmath.Matrix4f;
import javax.vecmath.Vector3f;
import javax.vecmath.Vector4f;

import java.util.List;

/**
 * Client render body for {@link ParticleMorph} (roadmap P54/P163).
 *
 * <p>The morph has two modes and only one of them draws. In
 * {@code VANILLA} mode the render call emits nothing — it exists purely to
 * <b>capture where the morph is</b>, because vanilla particles are spawned from
 * {@code update()} at {@link ParticleMorph#lastGlobal} and oriented by
 * {@link ParticleMorph#lastRotation}; a morph that is never drawn keeps emitting
 * at its last known place, exactly as on 1.12.2. In {@code MORPH} mode the same
 * capture runs and then every live {@link MorphParticle} draws its nested morph
 * at the particle's interpolated position, scale envelope and velocity-aligned
 * rotation.</p>
 *
 * <p><b>The capture.</b> Legacy composed {@code MatrixUtils.matrix⁻¹ ·
 * readModelView()} — the captured parent frame (the outermost custom-model
 * render, see {@code RenderCustomModel.captureMatrix}) inverted against the
 * ambient GL model-view at the moment the particle morph draws, which is the
 * particle morph's transform <i>relative to the model it hangs off</i>. Here the
 * model-view is the frame's own position matrix (also camera-space, so the
 * relative result is identical) bridged into {@code javax.vecmath}. With no
 * captured parent — a particle morph worn directly rather than as a body part —
 * legacy fell back to identity rotation and the entity's interpolated position,
 * and so does this.</p>
 *
 * <p><b>The transposed basis is deliberate.</b> Legacy read the parent-relative
 * matrix's <i>rows</i> as the basis axes ({@code m00/m01/m02}, …) where the
 * mathematically correct axes are its columns — {@code SnowstormMorph} extracts
 * the same quantity with {@code MatrixMajor.COLUMN}. So {@code lastRotation}
 * ends up transposed relative to Snowstorm's, and every 2.7.2 particle direction
 * was authored against that. It is reproduced verbatim; see
 * {@code plan/S14-morph-pack.md} P163.</p>
 *
 * <p><b>Zeroed entity rotations.</b> When the particle applies its own
 * yaw/pitch, legacy zeroed all eight of the entity's rotation fields around the
 * inner draw and restored them afterwards, so the nested morph would not rotate
 * itself a second time from the entity it happens to be drawn on. That
 * save/zero/restore moves here with the draw, in a {@code finally} — legacy
 * restored on the straight-line path only, and a throwing inner morph (which
 * {@link MorphRenderUtils} catches and latches rather than propagating) would
 * otherwise leave the entity pinned at zero rotation for the rest of the
 * frame.</p>
 *
 * Legacy source: blockbuster-1.12/.../blockbuster_pack/morphs/ParticleMorph.java (render/renderOnScreen, MorphParticle.render)
 */
public class ParticleMorphRenderer implements IMorphRenderer<ParticleMorph>
{
    /**
     * The eight sprites of the first row of 1.12.2's {@code particles.png},
     * which is what legacy's on-screen preview animated through. 1.13 split that
     * sheet into individual files and this row became
     * {@code textures/particle/generic_0..7.png} — same pixels, one texture each,
     * so the region maths collapses into picking a file.
     */
    public static final Identifier[] GENERIC = generic();

    /** Legacy on-screen size factor: {@code size = (int) (scale * 1.5F)}. */
    public static final float GUI_SCALE = 1.5F;

    private static Identifier[] generic()
    {
        Identifier[] sprites = new Identifier[8];

        for (int i = 0; i < sprites.length; i++)
        {
            sprites[i] = new Identifier("textures/particle/generic_" + i + ".png");
        }

        return sprites;
    }

    /* --------------------------------------------------------------------- */
    /* In-world                                                              */
    /* --------------------------------------------------------------------- */

    @Override
    public void render(ParticleMorph morph, LivingEntity entity, double x, double y, double z, float entityYaw, float partialTicks, MorphRenderContext context)
    {
        /* Legacy skipped both the capture and the draw inside a model-editor
         * preview or a 2D picker cell: those frames sit in an arbitrary place,
         * and letting them capture would teleport the emitter. */
        if (GuiModelRenderer.isRendering() || MorphRenderUtils.isRenderingOnScreen)
        {
            return;
        }

        MatrixStack matrices = context == null ? null : context.matrices;

        captureFrame(morph, entity, matrices, partialTicks);

        List<MorphParticle> particles = morph.getMorphParticles();

        if (particles.isEmpty() || matrices == null)
        {
            return;
        }

        matrices.push();

        try
        {
            matrices.translate(x, y, z);

            for (MorphParticle particle : particles)
            {
                this.renderParticle(morph, particle, entity, matrices, partialTicks);
            }
        }
        finally
        {
            matrices.pop();
        }
    }

    /**
     * One particle: interpolated position (plus the shared vanilla offset), the
     * lifespan/fade scale envelope, the optional velocity-aligned yaw and pitch,
     * then the nested morph through the error trap.
     */
    private void renderParticle(ParticleMorph morph, MorphParticle particle, LivingEntity entity, MatrixStack matrices, float partialTicks)
    {
        double x = Interpolations.lerp(particle.prevX, particle.x, partialTicks) + morph.vanillaX;
        double y = Interpolations.lerp(particle.prevY, particle.y, partialTicks) + morph.vanillaY;
        double z = Interpolations.lerp(particle.prevZ, particle.z, partialTicks) + morph.vanillaZ;
        /* float overload, as legacy resolved it — lifeSpan/fade are ints. */
        double scale = Interpolations.envelope(particle.timer + partialTicks, morph.lifeSpan, morph.fade);

        matrices.push();

        try
        {
            matrices.translate(x, y, z);
            matrices.scale((float) scale, (float) scale, (float) scale);

            if (morph.yaw)
            {
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(Interpolations.lerp(particle.prevYaw, particle.yaw, partialTicks)));
            }

            if (morph.pitch)
            {
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(Interpolations.lerp(particle.prevPitch, particle.pitch, partialTicks)));
            }

            if (morph.yaw || morph.pitch)
            {
                this.renderUnrotated(particle, entity, partialTicks);
            }
            else
            {
                MorphRenderUtils.render(particle.morph, entity, 0, 0, 0, 0, partialTicks);
            }
        }
        finally
        {
            matrices.pop();
        }
    }

    /**
     * Draw the nested morph with the entity's eight rotation fields temporarily
     * zeroed (legacy's own list: yaw, pitch, head yaw, body yaw and each one's
     * prev). The particle has already applied its own orientation to the frame;
     * without this the inner morph would add the wearer's on top of it.
     */
    private void renderUnrotated(MorphParticle particle, LivingEntity entity, float partialTicks)
    {
        if (entity == null)
        {
            MorphRenderUtils.render(particle.morph, null, 0, 0, 0, 0, partialTicks);

            return;
        }

        float yaw = entity.getYaw();
        float pitch = entity.getPitch();
        float yawHead = entity.headYaw;
        float yawBody = entity.bodyYaw;
        float prevYaw = entity.prevYaw;
        float prevPitch = entity.prevPitch;
        float prevYawHead = entity.prevHeadYaw;
        float prevYawBody = entity.prevBodyYaw;

        entity.setYaw(0);
        entity.prevYaw = 0;
        entity.headYaw = entity.prevHeadYaw = 0;
        entity.setPitch(0);
        entity.prevPitch = 0;
        entity.bodyYaw = entity.prevBodyYaw = 0;

        try
        {
            MorphRenderUtils.render(particle.morph, entity, 0, 0, 0, 0, partialTicks);
        }
        finally
        {
            entity.setYaw(yaw);
            entity.setPitch(pitch);
            entity.headYaw = yawHead;
            entity.bodyYaw = yawBody;
            entity.prevYaw = prevYaw;
            entity.prevPitch = prevPitch;
            entity.prevHeadYaw = prevYawHead;
            entity.prevBodyYaw = prevYawBody;
        }
    }

    /* --------------------------------------------------------------------- */
    /* The emitter anchor capture                                            */
    /* --------------------------------------------------------------------- */

    /**
     * Fill {@link ParticleMorph#lastGlobal} / {@link ParticleMorph#lastRotation}
     * from the current frame — the world anchor and basis that {@code update()}
     * spawns vanilla particles at.
     *
     * <p>Split out and public because it is the whole render body in
     * {@code VANILLA} mode, and because it is the half worth pinning headlessly:
     * a {@link MatrixStack} needs no GL.</p>
     */
    public static void captureFrame(ParticleMorph morph, LivingEntity entity, MatrixStack matrices, float partialTicks)
    {
        Matrix4f captured = MatrixUtils.matrix;

        if (captured != null && matrices != null)
        {
            Matrix4f parent = new Matrix4f(captured);

            /* Legacy did not guard the inversion: a singular parent frame (a
             * limb scaled to zero) throws, MorphRenderUtils catches it and the
             * morph latches out. Kept — a silent fallback would put the emitter
             * somewhere else entirely instead. */
            parent.invert();
            parent.mul(RenderingUtilsClient.toVecmath(matrices.peek().getPositionMatrix()));

            Vector4f zero = SnowstormMorph.calculateGlobal(parent, entity, 0, 0, 0, partialTicks);

            Vector3f ax = new Vector3f(parent.m00, parent.m01, parent.m02);
            Vector3f ay = new Vector3f(parent.m10, parent.m11, parent.m12);
            Vector3f az = new Vector3f(parent.m20, parent.m21, parent.m22);

            ax.normalize();
            ay.normalize();
            az.normalize();

            morph.lastRotation.setRow(0, ax);
            morph.lastRotation.setRow(1, ay);
            morph.lastRotation.setRow(2, az);

            morph.lastGlobal.x = zero.x;
            morph.lastGlobal.y = zero.y;
            morph.lastGlobal.z = zero.z;
        }
        else
        {
            morph.lastRotation.setIdentity();

            if (entity != null)
            {
                morph.lastGlobal.x = Interpolations.lerp(entity.prevX, entity.getX(), partialTicks);
                morph.lastGlobal.y = Interpolations.lerp(entity.prevY, entity.getY(), partialTicks);
                morph.lastGlobal.z = Interpolations.lerp(entity.prevZ, entity.getZ(), partialTicks);
            }
        }
    }

    /* --------------------------------------------------------------------- */
    /* On screen (GUI)                                                       */
    /* --------------------------------------------------------------------- */

    /**
     * The picker icon: an explosion puff cycling through the eight generic
     * sprites, regardless of what the morph actually emits. Legacy drew one 8×8
     * region of {@code particles.png} scaled to {@code size}, at
     * {@code (x - size/2, y - size + size/8)}.
     */
    @Override
    public void renderOnScreen(ParticleMorph morph, PlayerEntity player, int x, int y, float scale, float alpha)
    {
        DrawContext context = GuiDraw.getDrawContext();

        if (context == null)
        {
            return;
        }

        int size = (int) (scale * GUI_SCALE);

        context.drawTexture(GENERIC[spriteIndex(System.currentTimeMillis())],
            x - size / 2, y - size + size / 8, size, size, 0, 0, 8, 8, 8, 8);
    }

    /**
     * Legacy's animation clock: {@code factor = ms % 1000 / 500.0 - 1} ∈ [-1, 1),
     * then {@code floor(|factor²| × 8)} — a puff that expands and contracts once
     * a second.
     *
     * <p>One-millisecond deviation: at {@code ms % 1000 == 0} the expression
     * yields exactly 8, which on the old sheet read the sprite <i>after</i> the
     * explosion row. There is no such sprite once each frame is its own file, so
     * that instant clamps to the last frame instead.</p>
     */
    public static int spriteIndex(long millis)
    {
        double factor = millis % 1000 / 500.0 - 1;
        int index = (int) Math.floor(Math.abs(factor * factor) * 8);

        return index < 0 ? 0 : (index > 7 ? 7 : index);
    }
}
