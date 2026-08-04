package mchorse.blockbuster_pack.client.render;

import mchorse.blockbuster.client.render.ModelTransforms;
import mchorse.blockbuster.client.textures.GifTexture;
import mchorse.blockbuster.client.textures.TextureSizes;
import mchorse.blockbuster_pack.morphs.ImageMorph;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.render.McLibRenderLayers;
import mchorse.mclib.client.render.RenderingUtilsClient;
import mchorse.mclib.utils.Color;
import mchorse.mclib.utils.Interpolations;
import mchorse.metamorph.client.render.IMorphRenderer;
import mchorse.metamorph.client.render.MorphRenderContext;
import mchorse.vanilla_pack.render.CachedExtrusion;
import mchorse.vanilla_pack.render.ItemExtruder;
import mchorse.vanilla_pack.render.VanillaPackMorphRenderers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

import javax.vecmath.Vector3f;
import javax.vecmath.Vector4d;

/**
 * Client render body for {@link ImageMorph} (roadmap P54/P159) — the billboard
 * image morph, "basically replacement for Imaginary".
 *
 * <p>The quad geometry itself was ported with the morph
 * ({@link ImageMorph#computeGeometry} and {@link ImageMorph#transformUV} are
 * pure and already test-covered); this class is the part that could not go into
 * the common source set — the transform chain, the texture binding and the
 * vertex emission.</p>
 *
 * <p><b>Shading.</b> Legacy fed the quad through fixed-function lighting: the
 * {@code shaded} flag toggled {@code RenderHelper}'s standard item lighting, and
 * the {@code lighting} flag pinned the lightmap coordinate to {@code (240, 240)}
 * when <b>false</b> (an inverted "fullbright" toggle, the same one
 * {@link VanillaPackMorphRenderers#lightOf} reads for the vanilla-pack morphs).
 * On 1.20.4 the light is a per-vertex argument and the diffuse term is
 * {@code minecraft_mix_light} in the shader, computed from the vertex normal
 * alone — so {@code shaded} becomes {@link #normalOf}: the true face normal when
 * shaded, and a straight-up {@code (0, 1, 0)} when not, which both vanilla light
 * setups resolve to a diffuse factor of exactly {@code 1}. That normal is
 * emitted <b>untransformed</b> (the no-matrix {@code normal} overload), because
 * a rotated frame would otherwise tilt the "unshaded" normal back into shading.
 * The shader ignores the normal for anything else, so this changes nothing but
 * the diffuse factor.</p>
 *
 * <p><b>Facing.</b> {@code billboard} routes through
 * {@link RenderingUtilsClient#applyFacingRotation}, which needs the morph's world
 * position for the look-at modes — legacy read it off the GL model-view via
 * {@code MatrixUtils.getTransformation()}, here it is
 * {@link RenderingUtilsClient#worldPosition}. Note {@code lookat_direction} has
 * no direction to look along in an image morph and legacy threw
 * {@code IllegalArgumentException} for it (the editor only offers the other
 * four); that throw is kept — {@code MorphRenderUtils} latches
 * {@code errorRendering} on it exactly as 2.7.2 did.</p>
 *
 * <p><b>Thickness.</b> The extruded-3D variant goes through Metamorph's
 * {@link ItemExtruder} — see {@link #renderExtrusion} for the three legacy
 * behaviours it inherits (no crop/scale, no colour tint, the 180° roll). A
 * texture that will not read draws <i>nothing</i>, which is precisely what
 * legacy did when {@code extrude} returned null. The Optifine shadow-pass guard
 * behind {@code shadow} is inert here (no Optifine; S21).</p>
 *
 * Legacy source: blockbuster-1.12/.../blockbuster_pack/morphs/ImageMorph.java (render/renderOnScreen/renderPicture/updateAnimation)
 */
public class ImageMorphRenderer implements IMorphRenderer<ImageMorph>
{
    /** Legacy on-screen scale, mirrored on X: {@code glScalef(-1.5, 1.5, 1.5)}. */
    public static final float GUI_SCALE = 1.5F;

    /* --------------------------------------------------------------------- */
    /* In-world                                                              */
    /* --------------------------------------------------------------------- */

    @Override
    public void render(ImageMorph morph, LivingEntity entity, double x, double y, double z, float entityYaw, float partialTicks, MorphRenderContext context)
    {
        if (morph.texture == null || context.matrices == null || context.consumers == null)
        {
            return;
        }

        updateAnimation(morph, partialTicks);

        MatrixStack matrices = context.matrices;
        boolean defaultPose = morph.image.pose.isDefault();
        int light = VanillaPackMorphRenderers.lightOf(morph.lighting, context.light);

        matrices.push();

        try
        {
            matrices.translate(x, y, z);

            if (!defaultPose)
            {
                ModelTransforms.applyTranslate(matrices, morph.image.pose);
            }

            /* Read before the reverting/billboard rotations, exactly where
             * legacy called MatrixUtils.getTransformation(). */
            Vector3f position = RenderingUtilsClient.worldPosition(matrices);

            if (morph.removeParentScaleRotation)
            {
                RenderingUtilsClient.applyRevertRotationScale(matrices);
            }

            if (morph.billboard)
            {
                RenderingUtilsClient.applyFacingRotation(matrices, morph.facing, position, null);
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0F));
            }

            if (!defaultPose)
            {
                ModelTransforms.applyRotate(matrices, morph.image.pose);
            }

            if (!morph.billboard)
            {
                float entityPitch = Interpolations.lerp(entity.prevPitch, entity.getPitch(), partialTicks);

                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - entityYaw));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180.0F - entityPitch));
            }

            if (!defaultPose)
            {
                ModelTransforms.applyScale(matrices, morph.image.pose);
            }

            this.renderPicture(morph, matrices, context.consumers, light, context.overlay, 1F, entity.age, partialTicks);
        }
        finally
        {
            matrices.pop();
        }
    }

    /* --------------------------------------------------------------------- */
    /* On screen (GUI)                                                       */
    /* --------------------------------------------------------------------- */

    /**
     * Legacy: {@code translate(x, y - scale / 2, 0)} then
     * {@code scale(-1.5, 1.5, 1.5)}. The mirrored X goes through
     * {@code multiplyPositionMatrix} rather than {@link MatrixStack#scale} — a
     * negative non-uniform scale mangles the stack's normal matrix (the same
     * reason {@code CustomMorphRenderer.drawModel} does it).
     */
    @Override
    public void renderOnScreen(ImageMorph morph, PlayerEntity player, int x, int y, float scale, float alpha)
    {
        DrawContext dc = GuiDraw.getDrawContext();
        MinecraftClient mc = MinecraftClient.getInstance();

        if (dc == null || mc == null || morph.texture == null)
        {
            return;
        }

        float partialTicks = mc.getTickDelta();

        updateAnimation(morph, partialTicks);

        MatrixStack matrices = dc.getMatrices();
        VertexConsumerProvider.Immediate consumers = dc.getVertexConsumers();

        matrices.push();

        try
        {
            matrices.translate(x, y - scale / 2, 0);
            matrices.multiplyPositionMatrix(new Matrix4f().scaling(-GUI_SCALE, GUI_SCALE, GUI_SCALE));

            this.renderPicture(morph, matrices, consumers, MorphRenderContext.FULL_BRIGHT, OverlayTexture.DEFAULT_UV,
                scale, player == null ? 0 : player.age, partialTicks);

            consumers.draw();
        }
        finally
        {
            matrices.pop();
        }
    }

    /* --------------------------------------------------------------------- */
    /* Shared body                                                           */
    /* --------------------------------------------------------------------- */

    /**
     * Legacy private {@code renderPicture}: resolve the (possibly animated)
     * texture, compute the quad from the crop and the texture's pixel size, and
     * emit the front and back faces.
     *
     * <p>The texture size is queried on the <b>resolved frame</b>, not on the
     * morph's own location, because legacy queried whatever
     * {@code GifTexture.bindTexture} had just bound — for a GIF that is the
     * current frame. A zero-sized (missing / not-yet-loaded) texture draws
     * nothing rather than dividing by zero into a NaN quad.</p>
     */
    protected void renderPicture(ImageMorph morph, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay, float scale, int ticks, float partialTicks)
    {
        Identifier texture = GifTexture.resolveFrame(morph.texture.toIdentifier(), ticks, partialTicks);

        float w = TextureSizes.width(texture);
        float h = TextureSizes.height(texture);

        if (morph.thickness)
        {
            /* Ahead of the size guard: the extruded branch never touched
             * uv/pos, and its own geometry comes from the file's pixels rather
             * than from the loaded texture — so a texture the resource pack has
             * but the texture manager has not bound yet still extrudes. */
            this.renderExtrusion(morph, matrices, consumers, light, overlay, w, h);

            return;
        }

        if (w <= 0 || h <= 0)
        {
            return;
        }

        ImageMorph.Geometry geometry = ImageMorph.computeGeometry(
            morph.image.crop.x, morph.image.crop.y, morph.image.crop.z, morph.image.crop.w,
            w, h, morph.resizeCrop, scale);

        Vector4d uv = geometry.uv;
        Vector4d pos = geometry.pos;
        Color color = morph.image.color;
        float[] normal = normalOf(morph.shaded, false);
        float[] backNormal = normalOf(morph.shaded, true);

        VertexConsumer buffer = consumers.getBuffer(McLibRenderLayers.picture(texture, morph.keying));
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        /* By default the pos is (0.5, -0.5, -0.5, 0.5) */

        /* Frontface */
        this.vertex(buffer, matrix, morph, pos.x, pos.z, uv.x, uv.z, w, h, color, light, overlay, normal);
        this.vertex(buffer, matrix, morph, pos.x, pos.w, uv.x, uv.w, w, h, color, light, overlay, normal);
        this.vertex(buffer, matrix, morph, pos.y, pos.w, uv.y, uv.w, w, h, color, light, overlay, normal);
        this.vertex(buffer, matrix, morph, pos.y, pos.z, uv.y, uv.z, w, h, color, light, overlay, normal);

        /* Backface */
        this.vertex(buffer, matrix, morph, pos.x, pos.z, uv.x, uv.z, w, h, color, light, overlay, backNormal);
        this.vertex(buffer, matrix, morph, pos.y, pos.z, uv.y, uv.z, w, h, color, light, overlay, backNormal);
        this.vertex(buffer, matrix, morph, pos.y, pos.w, uv.y, uv.w, w, h, color, light, overlay, backNormal);
        this.vertex(buffer, matrix, morph, pos.x, pos.w, uv.x, uv.w, w, h, color, light, overlay, backNormal);
    }

    /**
     * The {@code thickness} branch: the extruded 3D variant of the same image,
     * built by Metamorph's {@link ItemExtruder} (the flat quad's cousin, and the
     * same mesh {@code ItemMorph}'s {@code Texture} field draws).
     *
     * <p>Three things are inherited from 1.12.2 rather than chosen:</p>
     *
     * <ul>
     *   <li><b>It ignores the crop, the resize and the scale.</b> Legacy still
     *       computed {@code uv}/{@code pos} above this branch and then threw
     *       them away — the extrusion is always the whole texture at unit size.
     *       In the world that reads as "a 1×1 slab"; in the picker it means the
     *       cell scale never reaches the mesh, so an extruded image morph
     *       previews as a ~1.5 px speck. Both are 2.7.2 behaviour.</li>
     *   <li><b>It draws white.</b> The extrusion carries no vertex colour, so it
     *       took the ambient {@code glColor4f(1, 1, 1, 1)} legacy set just above
     *       the branch — {@code image.color} tints the flat quad only.</li>
     *   <li><b>The 180° roll on Z</b> is legacy's, and it is what squares the
     *       extruder's own Y-down layout with the quad's Y-up one.</li>
     * </ul>
     *
     * <p>The texture is the morph's own location, <b>not</b> the resolved GIF
     * frame the flat quad uses: legacy passed {@code this.texture} straight to
     * {@code ItemExtruder.extrude}, so an animated image extrudes its first
     * frame and stays there. The UV offset/rotation does reach it, though —
     * legacy pushed that as a {@code GL_TEXTURE} matrix, which applied to
     * whatever was drawn next.</p>
     */
    protected void renderExtrusion(ImageMorph morph, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay, float ow, float oh)
    {
        CachedExtrusion extrusion = ItemExtruder.extrude(morph.texture.toIdentifier());

        if (extrusion == null)
        {
            return;
        }

        matrices.push();

        try
        {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0F));

            CachedExtrusion.IUVTransform uv = null;

            /* ow/oh are the texture-matrix divisors. Legacy read them off GL
             * and would have produced NaN UVs for an unbound texture; skipping
             * the transform there is the only difference, and it only shows up
             * on a frame that would otherwise have drawn nothing readable. */
            if (hasTextureMatrix(morph) && ow > 0 && oh > 0)
            {
                uv = (u, v, out) ->
                {
                    double[] transformed = ImageMorph.transformUV(u, v, morph.image.x, morph.image.y, ow, oh, morph.image.rotation);

                    out[0] = (float) transformed[0];
                    out[1] = (float) transformed[1];
                };
            }

            extrusion.emit(consumers.getBuffer(McLibRenderLayers.extrusion(extrusion.texture, morph.keying)),
                matrices.peek(), light, overlay, 1F, 1F, 1F, 1F, uv);
        }
        finally
        {
            matrices.pop();
        }
    }

    /**
     * One quad vertex. The UV offset/rotation legacy expressed as a
     * {@code GL_TEXTURE} matrix is applied per-vertex on the CPU here (the core
     * profile has no texture-matrix stack) — see {@link ImageMorph#transformUV},
     * gated on the same "any of offset/rotation is non-zero" condition legacy
     * used for pushing the texture matrix at all.
     */
    private void vertex(VertexConsumer buffer, Matrix4f matrix, ImageMorph morph, double x, double y, double u, double v, float ow, float oh, Color color, int light, int overlay, float[] normal)
    {
        if (hasTextureMatrix(morph))
        {
            double[] transformed = ImageMorph.transformUV(u, v, morph.image.x, morph.image.y, ow, oh, morph.image.rotation);

            u = transformed[0];
            v = transformed[1];
        }

        buffer.vertex(matrix, (float) x, (float) y, 0.0F)
            .color(color.r, color.g, color.b, color.a)
            .texture((float) u, (float) v)
            .overlay(overlay)
            .light(light)
            .normal(normal[0], normal[1], normal[2])
            .next();
    }

    /* --------------------------------------------------------------------- */
    /* Pure decisions (headless-testable)                                    */
    /* --------------------------------------------------------------------- */

    /**
     * The normal a quad face is emitted with. Shaded faces keep their true
     * normal ({@code ±Z}), which is what the diffuse term is computed from;
     * unshaded faces get {@code (0, 1, 0)}, whose dot product against both of
     * vanilla's light directions saturates {@code minecraft_mix_light} at
     * {@code 1} — the modern spelling of
     * {@code RenderHelper.disableStandardItemLighting()}.
     */
    public static float[] normalOf(boolean shaded, boolean back)
    {
        if (!shaded)
        {
            return new float[] {0.0F, 1.0F, 0.0F};
        }

        return new float[] {0.0F, 0.0F, back ? -1.0F : 1.0F};
    }

    /**
     * Legacy's {@code textureMatrix} gate: the UV transform is only applied when
     * one of the three inputs is non-zero (so an untouched image keeps its exact
     * source UVs, rather than the rounding of an identity transform).
     */
    public static boolean hasTextureMatrix(ImageMorph morph)
    {
        return morph.image.x != 0 || morph.image.y != 0 || morph.image.rotation != 0;
    }

    /**
     * Legacy private {@code updateAnimation}: the {@code image} properties track
     * the morph's own fields, with the animation applied on top while one is in
     * progress. Both branches copy from the morph first — that is what makes an
     * edited field visible mid-animation.
     */
    public static void updateAnimation(ImageMorph morph, float partialTicks)
    {
        morph.image.from(morph);

        if (morph.animation.isInProgress())
        {
            morph.animation.apply(morph.image, partialTicks);
        }
    }
}
