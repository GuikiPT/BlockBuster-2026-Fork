package mchorse.blockbuster.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The port's stand-in for 1.12.2's {@code EntityRenderer.drawNameplate} (roadmap
 * P54/P80).
 *
 * <p>Legacy called that vanilla static from two places outside an entity
 * renderer — {@code CustomMorph.render}'s missing-model key and
 * {@code TrackerMorph}'s tracked-point label — so it needs one home here as
 * well. 1.20.4 removed the free function; the equivalent body lives inline in
 * {@code EntityRenderer.renderLabelIfPresent}, and this is that body, with
 * legacy's two parameters that vanilla's version does not take (an explicit
 * vertical shift and an explicit sneaking flag) kept as arguments.</p>
 *
 * <p><b>What became implicit.</b> Legacy passed {@code viewerYaw}/{@code
 * viewerPitch} plus a {@code isThirdPersonFrontal} flag and built the billboard
 * rotation from them by hand — or, in {@code TrackerMorph}'s case, skipped them
 * entirely: it read the model-view back, {@code loadIdentity}'d it and drew the
 * label at the extracted eye-space position with a fixed 180° yaw. That is the
 * form this port uses ({@link #billboard}), because it is the one that holds
 * <b>outside</b> a world entity frame too. The depth/blend/lighting toggles
 * around the two draws became the two {@link TextRenderer.TextLayerType}s:
 * {@code SEE_THROUGH} is legacy's {@code disableDepth} pass and {@code NORMAL}
 * its depth-tested one.</p>
 *
 * <p><b>Why not {@code EntityRenderDispatcher.getRotation()}.</b> Vanilla's
 * {@code renderLabelIfPresent} cancels the view rotation with the dispatcher's
 * camera quaternion, which is only the right rotation when the ambient
 * transform <i>is</i> the world camera's. A GUI model preview
 * ({@code GuiModelRenderer}) writes its own orbit camera into
 * {@code RenderSystem}'s model-view stack and hands morph renderers a fresh
 * identity {@code MatrixStack}, so the dispatcher's rotation there is some
 * unrelated camera's — that is the "tracker name doesn't face the camera" in the
 * morph editor. Stripping the rotation off the <i>actual</i> composite model-view
 * is correct in both, and provably agrees with vanilla in the world case:
 * {@code R_view · Q = R_x(pitch) · R_y(yaw + 180) · R_y(-yaw) · R_x(pitch) =
 * R_y(180)}, which is exactly the extra half-turn legacy passed as its
 * {@code viewerYaw}.</p>
 *
 * <p><b>The sneak rule is legacy's, unchanged.</b> A sneaking subject draws only
 * the depth-tested pass, and draws it at the translucent {@code 0x20FFFFFF}
 * rather than opaque white — so the label is hidden behind geometry instead of
 * showing through it.</p>
 */
public class Nameplate
{
    /** Legacy's text scale: mirrored on X/Y, 1/40th of a block per pixel. */
    public static final float SCALE = 0.025F;

    /** Vanilla's fallback text-background opacity when there are no options. */
    public static final float DEFAULT_BACKGROUND_OPACITY = 0.25F;

    /** The translucent colour legacy used for the see-through pass. */
    public static final int SEE_THROUGH_COLOR = 0x20FFFFFF;

    /**
     * Draw {@code text} as a billboarded label at the current matrix origin.
     *
     * @param verticalShift legacy's {@code verticalShift} argument — raises the
     *                      label in world units before the billboard rotation
     * @param sneaking      legacy's {@code isSneaking} — see the class note
     */
    public static void draw(MatrixStack matrices, VertexConsumerProvider consumers, String text, float verticalShift, boolean sneaking, int light)
    {
        draw(matrices, consumers, text, verticalShift, 0, sneaking, light);
    }

    /**
     * As {@link #draw(MatrixStack, VertexConsumerProvider, String, float,
     * boolean, int)}, plus 1.12.2 {@code drawNameplate}'s {@code verticalShift}
     * argument — an offset in <b>text</b> pixels applied after the billboard
     * scale, which is a different axis of the same word and the reason both
     * exist here. Metamorph's morphed-player nameplate is the only caller that
     * ever passes a non-zero one: the {@code "deadmau5"} {@code -10}.
     */
    public static void draw(MatrixStack matrices, VertexConsumerProvider consumers, String text, float verticalShift, int textShift, boolean sneaking, int light)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (matrices == null || consumers == null || mc == null || mc.textRenderer == null
            || text == null || text.isEmpty())
        {
            return;
        }

        TextRenderer font = mc.textRenderer;

        matrices.push();

        try
        {
            /* The shift stays in the caller's space (a block above the entity's
             * head), exactly where every caller means it — only the rotation is
             * taken from the ambient model-view instead of the caller's. */
            matrices.translate(0F, verticalShift, 0F);

            Matrix4f matrix = billboard(matrices.peek().getPositionMatrix());
            float opacity = mc.options == null
                ? DEFAULT_BACKGROUND_OPACITY
                : mc.options.getTextBackgroundOpacity(DEFAULT_BACKGROUND_OPACITY);
            int background = (int) (opacity * 255F) << 24;
            float x = -font.getWidth(text) / 2F;

            float y = textShift;

            if (!sneaking)
            {
                font.draw(text, x, y, SEE_THROUGH_COLOR, false, matrix, consumers, TextRenderer.TextLayerType.SEE_THROUGH, background, light);
            }

            font.draw(text, x, y, sneaking ? SEE_THROUGH_COLOR : 0xFFFFFFFF, false, matrix, consumers, TextRenderer.TextLayerType.NORMAL, 0, light);
        }
        finally
        {
            matrices.pop();
        }
    }

    /**
     * Legacy's {@code readModelView} + {@code loadIdentity} + {@code
     * drawNameplate(…, 180F, 0, …)}, expressed as one matrix: the label's origin
     * in eye space, with the caller's rotation (and scale) dropped and the
     * legacy text basis put back on.
     *
     * <p>{@code model} maps label-local coordinates into the space the caller
     * draws in; the shader then applies {@code RenderSystem}'s model-view on top
     * ({@code view} below), so the label's eye-space origin is {@code view ·
     * model · 0}. The returned matrix is {@code view⁻¹ · T(origin) · diag(s, -s,
     * -s)} — the inverse cancels the ambient transform the shader is about to
     * re-apply, which leaves a pure translation plus legacy's text basis
     * ({@code rotate(-180°, Y)} then {@code scale(-0.025, -0.025, 0.025)}) in eye
     * space. Text {@code +x} therefore goes screen-right and {@code +y}
     * screen-down at any camera angle, in the world and in a GUI preview
     * alike.</p>
     */
    public static Matrix4f billboard(Matrix4f model)
    {
        return billboard(RenderSystem.getModelViewMatrix(), model);
    }

    /**
     * {@link #billboard(Matrix4f)}'s arithmetic with the ambient model-view
     * passed in rather than read off {@code RenderSystem} — pure, so the basis
     * can be pinned headlessly. Neither argument is modified.
     */
    public static Matrix4f billboard(Matrix4f view, Matrix4f model)
    {
        Matrix4f inverse = new Matrix4f(view);
        Vector3f origin = new Matrix4f(view).mul(model).transformPosition(new Vector3f());

        return inverse.invert().translate(origin).scale(SCALE, -SCALE, -SCALE);
    }

    /**
     * The vertical shift vanilla's own nameplate uses — {@code fontHeight / 48 +
     * 0.1} — which is what legacy's tracker label was positioned by.
     */
    public static float defaultShift()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        return mc == null || mc.textRenderer == null ? 0.1F : mc.textRenderer.fontHeight / 48.0F + 0.1F;
    }
}
