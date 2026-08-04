package mchorse.metamorph.bodypart;

import javax.vecmath.Matrix3f;
import javax.vecmath.Matrix4f;
import javax.vecmath.Vector3d;
import javax.vecmath.Vector3f;

import mchorse.blockbuster.client.render.LayerBodyPart;
import mchorse.mclib.client.Draw;
import mchorse.mclib.client.gui.framework.GuiBase;
import mchorse.mclib.client.gui.framework.elements.GuiModelRenderer;
import mchorse.mclib.client.gui.framework.elements.input.GuiTransformations;
import mchorse.mclib.utils.DummyEntity;
import mchorse.mclib.utils.Interpolation;
import mchorse.mclib.utils.MatrixUtils;
import mchorse.metamorph.Metamorph;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.utils.Animation;
import mchorse.metamorph.api.morphs.utils.IAnimationProvider;
import mchorse.metamorph.client.MorphRenderUtils;
import mchorse.metamorph.client.render.MorphRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.RotationAxis;

import java.util.List;

/**
 * Client render surface of {@link BodyPart} (roadmap P54 / P80).
 *
 * <p>Metamorph declared {@code init}, {@code updateEntity}, {@code render},
 * {@code drawAxis} and {@code recordMatrix} on {@code BodyPart} itself behind
 * {@code @SideOnly(Side.CLIENT)}. In the split source set the four that need a
 * {@code MatrixStack}, {@code MinecraftClient} or the morph render dispatcher
 * cannot live on the common class, so they move here — same package, so the
 * tween-source accessors stay package-private.</p>
 *
 * <p><b>What a body part actually is.</b> A sub-morph pinned to one limb of the
 * parent model. {@code LayerBodyPart} walks the parent's limbs, applies the
 * limb's own transform ({@code postRender}) and then calls {@link #render} to
 * draw the sub-morph in that limb's frame.</p>
 *
 * <p><b>1.20.4 translation of the GL body.</b> Legacy's ambient
 * {@code GL_MODELVIEW} becomes the {@link MorphRenderContext} frame's
 * {@link MatrixStack}: {@code glPushMatrix}/{@code glTranslatef}/{@code glRotatef}
 * /{@code glScalef}/{@code glPopMatrix} map one-for-one, and
 * {@code MatrixUtils.readModelView(...)} — which read the live GL matrix —
 * becomes a read of {@code matrices.peek().getPositionMatrix()} converted into
 * the row-major {@code javax.vecmath} space {@link MatrixUtils} works in.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/bodypart/BodyPart.java
 */
public final class BodyPartRenderer
{
    private BodyPartRenderer()
    {
    }

    /**
     * Fill the common-side seam so {@code BodyPartManager.initBodyParts()} does
     * real work. Called once from {@code BlockbusterClient}.
     */
    public static void install()
    {
        BodyPartManager.initializer = BodyPartRenderer::init;
    }

    /* --------------------------------------------------------------------- */
    /* Init                                                                  */
    /* --------------------------------------------------------------------- */

    /**
     * Legacy {@code BodyPart.init}: give the part its private dummy host and
     * push the item slots onto it.
     *
     * <p>The two yaw assignments look like no-ops and are not — they copy the
     * <i>prev</i> fields onto the current ones, which collapses the entity's
     * interpolation to a single value so the sub-morph does not spin up from
     * whatever the freshly-constructed entity happened to hold. {@code onGround}
     * is set so any sub-morph with a falling/flying pose starts standing.</p>
     *
     * <p>Legacy read {@code Minecraft.getMinecraft().world} unguarded; a
     * world-less client (main menu, a morph previewed before join) would have
     * produced a broken entity there. Here a null world leaves
     * {@link BodyPart#entity} null and every consumer treats the part as
     * uninitialized — the totality rule.</p>
     */
    public static void init(BodyPart part)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null || mc.world == null)
        {
            return;
        }

        DummyEntity entity = new DummyEntity(EntityType.ARMOR_STAND, mc.world);

        entity.setYaw(entity.prevYaw);
        entity.headYaw = entity.prevHeadYaw;
        entity.setOnGround(true);

        part.entity = entity;
        part.updateEntity();
    }

    /* --------------------------------------------------------------------- */
    /* Matrix recording                                                      */
    /* --------------------------------------------------------------------- */

    /**
     * Legacy {@code BodyPart.recordMatrix}: run one throwaway render of
     * {@code parent} from an identity matrix with {@link BodyPart#recording} on,
     * so every {@link #render} it reaches stamps its limb's model-view matrix
     * into {@link BodyPart#lastMatrix} and draws nothing. That is how the body
     * part editor learns where a limb <i>is</i> without tracking the model.
     *
     * <p>Legacy achieved "from identity" with
     * {@code glPushMatrix/glLoadIdentity/…/glPopMatrix} around the ambient
     * model-view. Here the pass gets its own fresh {@link MatrixStack} (already
     * identity) pushed as a {@link MorphRenderContext} frame, which is both
     * closer to the intent and safe against the caller's stack. Geometry emitted
     * despite the recording flag — a morph type that ignores it — goes into
     * {@link #DISCARD}, never onto the screen.</p>
     */
    public static void recordMatrix(AbstractMorph parent, LivingEntity entity, float partialTicks)
    {
        BodyPart.recording = true;

        MorphRenderContext.push(new MatrixStack(), DISCARD, MorphRenderContext.FULL_BRIGHT, 0, partialTicks);

        try
        {
            MorphRenderUtils.render(parent, entity, 0, 0, 0, 0, partialTicks);
        }
        finally
        {
            MorphRenderContext.pop();
            BodyPart.recording = false;
        }
    }

    /**
     * A {@link VertexConsumerProvider} that accepts and drops everything. Used
     * by {@link #recordMatrix}, whose render pass exists only for its side
     * effect on the matrix stack.
     */
    public static final VertexConsumerProvider DISCARD = new VertexConsumerProvider()
    {
        @Override
        public VertexConsumer getBuffer(RenderLayer layer)
        {
            return DiscardingVertexConsumer.INSTANCE;
        }
    };

    /* --------------------------------------------------------------------- */
    /* Render                                                                */
    /* --------------------------------------------------------------------- */

    /**
     * Legacy {@code BodyPart.render}. Draw this part's sub-morph in the frame
     * the caller has already moved to the parent limb.
     *
     * @param part the body part
     * @param parent the morph that owns the part (the animation source)
     * @param target the entity wearing the parent morph — used only when
     *        {@link BodyPart#useTarget} is set, else the private dummy host
     * @param partialTicks render partial ticks
     */
    public static void render(BodyPart part, AbstractMorph parent, LivingEntity target, float partialTicks)
    {
        MorphRenderContext context = MorphRenderContext.current();

        if (context == null || context.matrices == null)
        {
            return;
        }

        MatrixStack matrices = context.matrices;

        if (BodyPart.recording)
        {
            part.lastMatrix = toVecmath(matrices.peek().getPositionMatrix());

            return;
        }

        LivingEntity entity = part.useTarget ? target : part.entity;

        if (part.morph.get() == null || entity == null || !part.enabled)
        {
            return;
        }

        Animation animation = parent instanceof IAnimationProvider ? ((IAnimationProvider) parent).getAnimation() : null;

        float tx = part.translate.x;
        float ty = part.translate.y;
        float tz = part.translate.z;
        float sx = part.scale.x;
        float sy = part.scale.y;
        float sz = part.scale.z;
        float rx = part.rotate.x;
        float ry = part.rotate.y;
        float rz = part.rotate.z;

        Vector3f lastTranslate = part.getLastTranslate();

        if (animation != null && animation.isInProgress() && lastTranslate != null && part.animate)
        {
            Vector3f lastScale = part.getLastScale();
            Vector3f lastRotate = part.getLastRotate();
            Interpolation inter = animation.interp;
            float factor = animation.getFactor(partialTicks);

            tx = inter.interpolate(lastTranslate.x, tx, factor);
            ty = inter.interpolate(lastTranslate.y, ty, factor);
            tz = inter.interpolate(lastTranslate.z, tz, factor);
            sx = inter.interpolate(lastScale.x, sx, factor);
            sy = inter.interpolate(lastScale.y, sy, factor);
            sz = inter.interpolate(lastScale.z, sz, factor);
            rx = inter.interpolate(lastRotate.x, rx, factor);
            ry = inter.interpolate(lastRotate.y, ry, factor);
            rz = inter.interpolate(lastRotate.z, rz, factor);
        }

        /* Hand the accumulated world-space translation down to the sub-morph,
         * rotated/scaled into the frame the enclosing model established. Only
         * Snowstorm-style morphs read it; everything else ignores it. */
        if (!part.morph.isEmpty())
        {
            MatrixUtils.Transformation modelView = new MatrixUtils.Transformation();

            if (MatrixUtils.matrix != null)
            {
                modelView = MatrixUtils.extractTransformations(MatrixUtils.matrix, toVecmath(matrices.peek().getPositionMatrix()));
            }

            part.morph.get().cachedTranslation.set(BodyPart.cachedTranslation);

            Vector3f translate = new Vector3f(tx, ty, tz);
            Matrix3f transformation = new Matrix3f(modelView.getRotation3f());

            transformation.mul(modelView.getScale3f());
            transformation.transform(translate);

            part.morph.get().cachedTranslation.add(translate);
        }

        /* Zeroed unconditionally — including when the morph was empty and the
         * block above never read it. That is legacy behaviour: the mailbox is
         * consumed by reaching a body part, not by using it. */
        BodyPart.cachedTranslation.set(0, 0, 0);

        matrices.push();

        try
        {
            matrices.translate(tx, ty, tz);

            /* Z, Y, X — legacy's glRotatef order, which is not the XYZ order the
             * transform *fields* are labelled with. Preserved. */
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rz));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(ry));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rx));

            matrices.scale(sx, sy, sz);

            renderRebased(part, entity, matrices, partialTicks);
        }
        finally
        {
            /* Legacy's glPopMatrix was not in a finally either, but an
             * unbalanced GL matrix stack degrades one frame while an unbalanced
             * MatrixStack corrupts every draw after it in this frame — the pop
             * is guarded, the yaw restore below is not (see renderRebased). */
            matrices.pop();
        }
    }

    /**
     * The yaw-rebased sub-morph draw. Rebase (body yaw out of the yaw and
     * head-yaw fields, then zeroed) so the sub-morph is drawn in body-local
     * space, render, draw the editor axis, restore.
     *
     * <p>The rebase arithmetic and the deliberate absence of a {@code finally}
     * around it live in {@link LayerBodyPart#renderWithYawRebase} — see its
     * javadoc for why the missing restore-on-throw is legacy parity rather than
     * a bug. This method only moves the values between the entity and that
     * helper's {@link LayerBodyPart.YawState} struct.</p>
     */
    private static void renderRebased(BodyPart part, LivingEntity entity, MatrixStack matrices, float partialTicks)
    {
        LayerBodyPart.YawState state = new LayerBodyPart.YawState(
            entity.getYaw(), entity.prevYaw,
            entity.bodyYaw, entity.prevBodyYaw,
            entity.headYaw, entity.prevHeadYaw);

        LayerBodyPart.renderWithYawRebase(state, () ->
        {
            applyYaw(entity, state);

            MorphRenderUtils.render(part.morph.get(), entity, 0, 0, 0, 0, partialTicks);

            drawAxis(part, matrices);
        });

        /* renderWithYawRebase has put the originals back into `state`. */
        applyYaw(entity, state);
    }

    /** Write a {@link LayerBodyPart.YawState} back onto the entity. */
    private static void applyYaw(LivingEntity entity, LayerBodyPart.YawState state)
    {
        entity.setYaw(state.yaw);
        entity.prevYaw = state.prevYaw;
        entity.bodyYaw = state.bodyYaw;
        entity.prevBodyYaw = state.prevBodyYaw;
        entity.headYaw = state.headYaw;
        entity.prevHeadYaw = state.prevHeadYaw;
    }

    /* --------------------------------------------------------------------- */
    /* Editor axis gizmo                                                     */
    /* --------------------------------------------------------------------- */

    /**
     * Legacy {@code BodyPart.drawAxis}: the origin dot (always) and the RGB axis
     * (when the {@code render_bodypart_axis} config is on) for the body part
     * currently selected in {@code GuiBodyPartEditor} — and only inside a model
     * viewport, so it never appears in the world.
     *
     * <p>Under {@code GLOBAL} transform orientation the part's own rotation and
     * scale are reverted first, so the gizmo shows world axes rather than the
     * part's. The legacy GL depth/lighting/texture disable-enable dance around
     * the draw is not reproduced: those are fixed-function states that do not
     * exist in the core profile, and the shared line draw
     * ({@link #drawGizmo}) sets what it needs itself.</p>
     */
    static void drawAxis(BodyPart part, MatrixStack matrices)
    {
        if (!GuiModelRenderer.isRendering())
        {
            return;
        }

        List<GuiBodyPartEditor> editors = GuiBase.getCurrentChildren(GuiBodyPartEditor.class);

        if (editors == null || editors.isEmpty() || !editors.get(0).isSelected(part))
        {
            return;
        }

        matrices.push();

        try
        {
            if (GuiTransformations.GuiStaticTransformOrientation.getOrientation() == GuiTransformations.TransformOrientation.GLOBAL)
            {
                revertRotationScale(matrices, part);
            }

            drawGizmo(matrices, Metamorph.renderBodyPartAxis.get());
        }
        finally
        {
            matrices.pop();
        }
    }

    /**
     * Legacy {@code RenderingUtils.glRevertRotationScale(rot, scale, XYZ)}:
     * undo the part's scale, then its rotation in reverse application order
     * (the render applied Z, Y, X; the revert applies -X, -Y, -Z). Zero scale
     * components invert to zero rather than infinity.
     */
    static void revertRotationScale(MatrixStack matrices, BodyPart part)
    {
        Vector3d scale = new Vector3d(part.scale);

        matrices.scale(
            scale.x != 0 ? (float) (1D / scale.x) : 0F,
            scale.y != 0 ? (float) (1D / scale.y) : 0F,
            scale.z != 0 ? (float) (1D / scale.z) : 0F);

        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-part.rotate.x));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-part.rotate.y));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-part.rotate.z));
    }

    /**
     * Legacy drew {@code Draw.point(0, 0, 0)} unconditionally and then
     * {@code Draw.axis(0.2F)} when the config was on. {@link Draw#axis} already
     * ends with the point, so the config-on branch is the axis call alone —
     * drawing both would double the origin marker.
     */
    private static void drawGizmo(MatrixStack matrices, boolean axis)
    {
        if (axis)
        {
            Draw.axis(matrices, 0.2F);
        }
        else
        {
            Draw.point(matrices);
        }
    }

    /* --------------------------------------------------------------------- */
    /* Matrix bridge                                                         */
    /* --------------------------------------------------------------------- */

    /**
     * Bridge the render-thread {@code org.joml.Matrix4f} (column-major
     * {@code m<col><row>}) into the {@code javax.vecmath.Matrix4f} (row-major)
     * that {@link MatrixUtils} operates in — the same mapping
     * {@code RenderCustomModel.toVecmath} uses.
     */
    static Matrix4f toVecmath(org.joml.Matrix4f m)
    {
        return new Matrix4f(
            m.m00(), m.m10(), m.m20(), m.m30(),
            m.m01(), m.m11(), m.m21(), m.m31(),
            m.m02(), m.m12(), m.m22(), m.m32(),
            m.m03(), m.m13(), m.m23(), m.m33());
    }
}
