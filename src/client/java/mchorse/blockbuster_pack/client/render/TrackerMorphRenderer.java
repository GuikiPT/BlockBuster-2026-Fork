package mchorse.blockbuster_pack.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.aperture.camera.CameraExporter;
import mchorse.blockbuster.client.render.Nameplate;
import mchorse.blockbuster_pack.morphs.TrackerMorph;
import mchorse.mclib.client.gui.framework.elements.GuiModelRenderer;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.render.RenderingUtilsClient;
import mchorse.mclib.utils.MatrixUtils;
import mchorse.metamorph.client.render.IMorphRenderer;
import mchorse.metamorph.client.render.MorphRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.awt.Color;

/**
 * Client render body for {@link TrackerMorph} (roadmap P54/P166).
 *
 * <p>A tracker morph is an editor tool, not a costume: it draws a two-armed
 * rainbow pointer plus a nameplate so you can see where the tracked point is,
 * and — the part that is not decoration — it calls its tracker's
 * {@code track(...)} on every render so Aperture's camera reads the position at
 * exactly the interpolation the frame was drawn at. That call happens even when
 * the morph is {@link TrackerMorph#hidden}: hiding is visual only, and a hidden
 * tracker that stopped feeding the camera would be a silent behaviour change.
 * It also happens when the HUD is off, which is why it sits outside the
 * visibility gate.</p>
 *
 * <p><b>Pointer geometry.</b> Legacy emitted {@code GL_LINES} at
 * {@code glLineWidth(5)}: {@code (0,0,0)→(0,0,1)} and {@code (0,0,0)→(0,0.5,0)},
 * each fading from black at the origin to a hue-cycling colour at the tip. The
 * two segments share the origin vertex, so on 1.20.4 they become one
 * {@code DEBUG_LINE_STRIP} — {@code (0,0,1) → (0,0,0) → (0,0.5,0)} — which is
 * the only line layer that takes an explicit width, and reproduces both segments
 * and all four vertex colours exactly.</p>
 *
 * <p><b>The origin dot.</b> {@code Draw.point} was {@code GL_POINTS} at size 12
 * (black) then 10 (white) — screen-space squares, constant size at any distance.
 * Core profile has no {@code POINTS} draw mode and no {@code glPointSize}, so
 * the world path draws two screen-facing quads whose size comes from the live
 * projection and viewport via {@link #pixelSize}, which is what keeps the dot
 * the same 12/10 pixels it was on 1.12.2 — under the game's camera and inside a
 * model preview's own viewport equally. The GUI path does not need any of that:
 * the pointer's origin projects to the pointer's own translation, so the dot is
 * two {@code fill} rects at that pixel.</p>
 *
 * Legacy source: blockbuster-1.12/.../blockbuster_pack/morphs/TrackerMorph.java (render/renderOnScreen/renderPointer/renderLabel)
 */
public class TrackerMorphRenderer implements IMorphRenderer<TrackerMorph>
{
    /** Legacy {@code glLineWidth(5)} for the pointer arms. */
    public static final double LINE_WIDTH = 5D;

    /** Legacy {@code glPointSize} pair: 12 px black behind 10 px white. */
    public static final int POINT_OUTER = 12;
    public static final int POINT_INNER = 10;

    /** Legacy's hue cycle: {@code renderTimer % 50}, second arm half a turn out. */
    public static final int HUE_PERIOD = 50;

    /* --------------------------------------------------------------------- */
    /* In-world                                                              */
    /* --------------------------------------------------------------------- */

    @Override
    public void render(TrackerMorph morph, LivingEntity entity, double x, double y, double z, float entityYaw, float partialTicks, MorphRenderContext context)
    {
        MatrixStack matrices = context.matrices;
        VertexConsumerProvider consumers = context.consumers;

        if (matrices == null || consumers == null)
        {
            /* No render target — the tracker still has to feed the camera. */
            this.track(morph, entity, x, y, z, entityYaw, partialTicks, null);

            return;
        }

        matrices.push();

        try
        {
            matrices.translate(x, y, z);

            if (isVisible(morph))
            {
                this.renderPointer(morph, matrices, consumers, context.light);

                /* Legacy: "Don't render labels in gui - it clutters the screen". */
                if (!morph.hidden)
                {
                    this.renderLabel(morph, matrices, consumers, context.light);
                }
            }

            this.track(morph, entity, x, y, z, entityYaw, partialTicks, matrices);
        }
        finally
        {
            matrices.pop();
        }
    }

    /**
     * Legacy gate: {@code (Minecraft.isGuiEnabled() && !hidden) ||
     * GuiModelRenderer.isRendering()} — a hidden tracker still shows inside a
     * model-renderer preview (that is where you place it), and F1 hides it in
     * the world along with the rest of the HUD.
     */
    public static boolean isVisible(TrackerMorph morph)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean hud = mc == null || mc.options == null || !mc.options.hudHidden;

        return (hud && !morph.hidden) || GuiModelRenderer.isRendering();
    }

    /**
     * Feed the tracker, publishing the current model-view transformation for
     * the P202.1 {@code CameraExporter} first.
     *
     * <p>Legacy's {@code MatrixUtils.getTransformation()} scraped
     * {@code GL_MODELVIEW_MATRIX} plus the ASM-captured camera matrix from GL
     * state at exactly this point. On core profile the same value is
     * {@code extractTransformations(capturedCamera, matrixStackTop)} — the
     * caller has it, so it is handed over rather than scraped. A tracker
     * rendered without a matrix stack (no render target) publishes
     * {@code null}, and the exporter skips the morph frame.</p>
     */
    private void track(TrackerMorph morph, LivingEntity entity, double x, double y, double z, float entityYaw, float partialTicks, MatrixStack matrices)
    {
        if (morph.tracker == null)
        {
            return;
        }

        CameraExporter.currentTransformation = transformation(matrices);
        currentMatrices = matrices;

        try
        {
            morph.tracker.track(entity, x, y, z, entityYaw, partialTicks);
        }
        finally
        {
            CameraExporter.currentTransformation = null;
            currentMatrices = null;
        }
    }

    /**
     * The matrix stack of the tracker render currently in progress, or
     * {@code null} outside one (S22 P240).
     *
     * <p>{@code ApertureCamera.track} needs the ambient model-view at exactly
     * this point — legacy read {@code GL_MODELVIEW}, which is not a thing on
     * core profile — so the P240 capture hook
     * ({@code TrackerModifierClientWiring}) reads it from here. Published
     * alongside {@link CameraExporter#currentTransformation} and cleared in the
     * same {@code finally}, so nothing can observe a stale stack.</p>
     */
    private static MatrixStack currentMatrices;

    public static MatrixStack currentMatrices()
    {
        return currentMatrices;
    }

    /**
     * The decomposed model-view for the current render, or {@code null} when
     * there is no matrix stack to read.
     */
    static MatrixUtils.Transformation transformation(MatrixStack matrices)
    {
        if (matrices == null)
        {
            return null;
        }

        return MatrixUtils.extractTransformations(MatrixUtils.matrix, RenderingUtilsClient.toVecmath(matrices.peek().getPositionMatrix()));
    }

    /* --------------------------------------------------------------------- */
    /* On screen (GUI)                                                       */
    /* --------------------------------------------------------------------- */

    /**
     * Legacy drew the preview pointer with depth off, 15 px above the cell
     * anchor and 5 px to the left, through four rotations that turn the world
     * pointer into an isometric-looking gizmo, then the tracker's name centred
     * 5 px below the anchor.
     */
    @Override
    public void renderOnScreen(TrackerMorph morph, PlayerEntity player, int x, int y, float scale, float alpha)
    {
        DrawContext dc = GuiDraw.getDrawContext();
        MinecraftClient mc = MinecraftClient.getInstance();

        if (dc == null || mc == null || mc.textRenderer == null)
        {
            return;
        }

        morph.renderTimer++;

        MatrixStack matrices = dc.getMatrices();
        VertexConsumerProvider.Immediate consumers = dc.getVertexConsumers();

        matrices.push();

        try
        {
            matrices.translate(x, y - 15, 0);

            matrices.push();

            try
            {
                matrices.translate(-5, 0, 0);
                matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(45.0F));
                matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees(45.0F));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0F));
                matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees(90.0F));
                matrices.scale(scale * 2, scale * 2, scale * 2);

                this.renderPointerArms(morph, matrices, consumers);
            }
            finally
            {
                matrices.pop();
            }

            consumers.draw();

            /* The pointer's origin is its own translation, so the screen-space
             * dot goes straight there rather than through a billboard. */
            dc.fill(-5 - POINT_OUTER / 2, -POINT_OUTER / 2, -5 + POINT_OUTER / 2, POINT_OUTER / 2, 0xff000000);
            dc.fill(-5 - POINT_INNER / 2, -POINT_INNER / 2, -5 + POINT_INNER / 2, POINT_INNER / 2, 0xffffffff);

            TextRenderer font = mc.textRenderer;
            String name = trackerName(morph);

            dc.drawText(font, name, -font.getWidth(name) / 2, 5, 0xffffffff, false);

            /* Same reason as the pointer's own flush above: the next cell in the
             * morph list draws its background over unflushed text */
            GuiDraw.flush();
        }
        finally
        {
            matrices.pop();
        }
    }

    /**
     * Legacy fell back to the localized {@code tracker_morph.name} when the
     * tracker is absent or unnamed.
     */
    public static String trackerName(TrackerMorph morph)
    {
        if (morph.tracker != null && morph.tracker.name != null && !morph.tracker.name.isEmpty())
        {
            return morph.tracker.name;
        }

        return I18n.translate("blockbuster.gui.tracker_morph.name");
    }

    /* --------------------------------------------------------------------- */
    /* Pointer + label                                                       */
    /* --------------------------------------------------------------------- */

    private void renderPointer(TrackerMorph morph, MatrixStack matrices, VertexConsumerProvider consumers, int light)
    {
        this.renderPointerArms(morph, matrices, consumers);
        this.renderPoint(matrices, consumers);
    }

    /**
     * The two arms as one strip through the shared origin vertex. The colours
     * come from {@link #hue}, which also performs legacy's
     * {@code renderTimer %= 50} — the wrap is a side effect of drawing, so a
     * tracker that is not drawn keeps counting up until it is.
     */
    private void renderPointerArms(TrackerMorph morph, MatrixStack matrices, VertexConsumerProvider consumers)
    {
        morph.renderTimer %= HUE_PERIOD;

        int rgb = hue(morph.renderTimer, 0F);
        int rgb2 = hue(morph.renderTimer, 0.5F);

        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getDebugLineStrip(LINE_WIDTH));
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        buffer.vertex(matrix, 0F, 0F, 1F).color(rgb >> 16 & 0xff, rgb >> 8 & 0xff, rgb & 0xff, 255).next();
        buffer.vertex(matrix, 0F, 0F, 0F).color(0, 0, 0, 255).next();
        buffer.vertex(matrix, 0F, 0.5F, 0F).color(rgb2 >> 16 & 0xff, rgb2 >> 8 & 0xff, rgb2 & 0xff, 255).next();
    }

    /** Legacy's hue cycle: {@code Color.HSBtoRGB(timer / 50F + shift, 1, 1)}. */
    public static int hue(int timer, float shift)
    {
        return Color.HSBtoRGB(timer / (float) HUE_PERIOD + shift, 1.0F, 1.0F);
    }

    /**
     * The origin dot as two screen-facing quads, sized so they cover the same
     * pixel count {@code glPointSize} used to.
     *
     * <p>Both the facing and the size are derived from the transform that is
     * actually in effect: the quads are placed at the origin's eye-space
     * position with the ambient rotation cancelled (the same construction
     * {@link Nameplate#billboard} uses, without its text basis) and scaled by
     * {@link #pixelSize}, so the dot is 12/10 px in the world and in a GUI model
     * preview alike. The earlier version took the camera from
     * {@code EntityRenderDispatcher} and the field of view and height from the
     * game window, which are the wrong three things inside a preview: the
     * matrix there is not camera-relative, so the distance came out ~0 and the
     * dot vanished (and, under a projection the window's numbers do not
     * describe, it could blow up to cover the screen instead).</p>
     */
    private void renderPoint(MatrixStack matrices, VertexConsumerProvider consumers)
    {
        Matrix4f view = viewMatrix();

        if (view == null)
        {
            return;
        }

        Matrix4f model = matrices.peek().getPositionMatrix();
        Vector3f origin = new Matrix4f(view).mul(model).transformPosition(new Vector3f());
        Matrix4f matrix = new Matrix4f(view).invert().translate(origin);
        float unit = pixelSize(RenderSystem.getProjectionMatrix(), origin.z, viewportHeight());

        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getDebugQuads());

        this.quad(buffer, matrix, POINT_OUTER * unit, 0, 0, 0);
        this.quad(buffer, matrix, POINT_INNER * unit, 255, 255, 255);
    }

    /** The ambient model-view, or null outside a GL context (headless tests). */
    private static Matrix4f viewMatrix()
    {
        try
        {
            return RenderSystem.getModelViewMatrix();
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    /**
     * The eye-space size of one screen pixel at eye-space depth {@code eyeZ},
     * for the projection {@code projection} on a viewport {@code height} pixels
     * tall.
     *
     * <p>A pixel is {@code 2 / (m11 · height)} of clip space, and clip space is
     * eye space divided by {@code w} — which is {@code -eyeZ} under a
     * perspective projection ({@code m23 = -1}) and {@code 1} under an
     * orthographic one. That covers both the world (perspective, window-sized
     * viewport) and a model preview (perspective, element-sized viewport), and
     * degrades to 0 rather than to infinity when there is no viewport or no
     * projection to speak of.</p>
     */
    public static float pixelSize(Matrix4f projection, float eyeZ, int height)
    {
        if (projection == null || height <= 0 || projection.m11() == 0F)
        {
            return 0F;
        }

        /* |m11|: the GUI's orthographic projection is built top-down, so its
         * m11 is negative — a pixel is still a pixel */
        float pixel = 2F / (Math.abs(projection.m11()) * height);

        return projection.m23() == 0F ? pixel : pixel * Math.abs(eyeZ);
    }

    /**
     * The height of the GL viewport currently being drawn into. Not the window's
     * — {@code GuiModelRenderer} narrows the viewport to its own element rect,
     * and the dot has to be 12 px of <i>that</i>.
     */
    private static int viewportHeight()
    {
        try
        {
            int[] viewport = new int[4];

            org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_VIEWPORT, viewport);

            return viewport[3];
        }
        catch (Throwable t)
        {
            /* No GL context (headless) — the dot simply doesn't render. */
            return 0;
        }
    }

    private void quad(VertexConsumer buffer, Matrix4f matrix, float size, int r, int g, int b)
    {
        float h = size / 2F;

        buffer.vertex(matrix, -h, -h, 0F).color(r, g, b, 255).next();
        buffer.vertex(matrix, -h, h, 0F).color(r, g, b, 255).next();
        buffer.vertex(matrix, h, h, 0F).color(r, g, b, 255).next();
        buffer.vertex(matrix, h, -h, 0F).color(r, g, b, 255).next();
    }

    /**
     * Legacy read the model-view back, loaded identity and drew a nameplate at
     * the extracted camera-space position with a fixed 180° yaw — matrix surgery
     * whose only purpose was to cancel the camera rotation. 1.20.4 states that
     * directly with {@code EntityRenderDispatcher.getRotation()}, which is also
     * what {@code EntityRenderer.renderLabelIfPresent} uses, so this ends up
     * being vanilla's own nameplate at legacy's vertical offset
     * ({@code fontHeight / 48 + 0.1}).
     */
    private void renderLabel(TrackerMorph morph, MatrixStack matrices, VertexConsumerProvider consumers, int light)
    {
        if (morph.tracker == null)
        {
            return;
        }

        /* Never sneaking: legacy passed the tracked point's own label straight
         * to drawNameplate with no sneak argument to pass. */
        Nameplate.draw(matrices, consumers, morph.tracker.name, Nameplate.defaultShift(), false, light);
    }
}
