package mchorse.mclib.client.render;

import java.util.OptionalDouble;
import java.util.function.Function;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.opengl.GL14;

/**
 * The render layers the ported morph renderers draw their own geometry through
 * (roadmap P54).
 *
 * <p>1.12.2 expressed "how do these vertices get drawn" as a pile of
 * {@code GlStateManager} calls issued right before a {@code Tessellator} draw
 * ({@code mchorse.mclib.client.render.VertexBuilder} chose only the vertex
 * format). On 1.20.4 the same information is a {@link RenderLayer}, which is why
 * this class lives at the legacy package's address.</p>
 *
 * <p><b>The picture layer.</b> Legacy's image quad used the format
 * {@code POSITION_COLOR_TEX_NORMAL} with blending on, culling on and
 * {@code alphaFunc(GREATER, 0)}, its brightness coming from the global lightmap
 * coordinate and its shading from whether {@code RenderHelper}'s standard item
 * lighting was enabled. All of that maps onto vanilla's
 * {@code entity_translucent_cull} layer: per-vertex light replaces the global
 * lightmap coordinate, and the shader's {@code minecraft_mix_light} replaces the
 * fixed-function diffuse term. The one visible difference is the cutoff — the
 * shader discards below {@code alpha < 0.1} where legacy discarded only exactly
 * zero, so texels fainter than 10% alpha vanish instead of blending. That is
 * vanilla's own translucent-entity behaviour and applies to every entity in the
 * game; matching legacy exactly would need a bespoke shader.</p>
 *
 * <p><b>Keying.</b> {@code ImageMorph.keying} swapped the blend equation to
 * {@code GL_FUNC_REVERSE_SUBTRACT} with both factors {@code ZERO}, which writes
 * zeroes wherever the image is opaque — the "cut a hole in what is behind me"
 * effect. There is no vanilla layer with that blend state, so
 * {@link #KEYING_TRANSPARENCY} is a custom {@link RenderPhase.Transparency} that
 * sets it and restores {@code GL_FUNC_ADD} plus the default blend function
 * afterwards, leaving GL exactly as vanilla's own transparency phases do.</p>
 *
 * <p>It extends {@link RenderLayer} for the usual reason every mod that defines
 * a layer does: {@code RenderPhase.Transparency} and its siblings are
 * <b>protected</b> nested types of {@link RenderPhase}, reachable only from
 * inside a subclass. Nothing is ever instantiated from this class — the
 * constructor exists solely to satisfy the compiler.</p>
 */
public final class McLibRenderLayers extends RenderLayer
{
    /**
     * Legacy's keying blend state: {@code glBlendEquation(GL_FUNC_REVERSE_SUBTRACT)}
     * with {@code blendFunc(ZERO, ZERO)}, restored to add/default on the way out.
     */
    public static final RenderPhase.Transparency KEYING_TRANSPARENCY = new RenderPhase.Transparency("blockbuster_keying_transparency", () ->
    {
        RenderSystem.enableBlend();
        RenderSystem.blendEquation(GL14.GL_FUNC_REVERSE_SUBTRACT);
        RenderSystem.blendFunc(GlStateManager.SrcFactor.ZERO, GlStateManager.DstFactor.ZERO);
    }, () ->
    {
        RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    });

    private static final Function<Identifier, RenderLayer> PICTURE = Util.memoize((Identifier texture) ->
        RenderLayer.of("blockbuster_picture", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS, 256, true, true,
            RenderLayer.MultiPhaseParameters.builder()
                .program(RenderPhase.ENTITY_TRANSLUCENT_CULL_PROGRAM)
                .texture(new RenderPhase.Texture(texture, false, false))
                .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
                .cull(RenderPhase.ENABLE_CULLING)
                .lightmap(RenderPhase.ENABLE_LIGHTMAP)
                .overlay(RenderPhase.ENABLE_OVERLAY_COLOR)
                .build(true)));

    private static final Function<Identifier, RenderLayer> PICTURE_KEYING = Util.memoize((Identifier texture) ->
        RenderLayer.of("blockbuster_picture_keying", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS, 256, true, true,
            RenderLayer.MultiPhaseParameters.builder()
                .program(RenderPhase.ENTITY_TRANSLUCENT_CULL_PROGRAM)
                .texture(new RenderPhase.Texture(texture, false, false))
                .transparency(KEYING_TRANSPARENCY)
                .cull(RenderPhase.ENABLE_CULLING)
                .lightmap(RenderPhase.ENABLE_LIGHTMAP)
                .overlay(RenderPhase.ENABLE_OVERLAY_COLOR)
                .build(true)));

    /**
     * The picture layer with the depth test switched off — legacy's
     * {@code GlStateManager.disableDepth()} around a marker billboard that is
     * meant to be visible through whatever it is buried in.
     */
    private static final Function<Identifier, RenderLayer> PICTURE_NO_DEPTH = Util.memoize((Identifier texture) ->
        RenderLayer.of("blockbuster_picture_no_depth", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS, 256, true, true,
            RenderLayer.MultiPhaseParameters.builder()
                .program(RenderPhase.ENTITY_TRANSLUCENT_CULL_PROGRAM)
                .texture(new RenderPhase.Texture(texture, false, false))
                .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
                .depthTest(RenderPhase.ALWAYS_DEPTH_TEST)
                .cull(RenderPhase.ENABLE_CULLING)
                .lightmap(RenderPhase.ENABLE_LIGHTMAP)
                .overlay(RenderPhase.ENABLE_OVERLAY_COLOR)
                .build(true)));

    /**
     * The picture layer with culling <b>off</b> — legacy's
     * {@code CachedExtrusion.render()} opened with
     * {@code GlStateManager.disableCull()} and closed with
     * {@code enableCull()}, because an extruded sprite is a solid whose back
     * faces are wound the way its front faces are and would otherwise vanish
     * from behind.
     */
    private static final Function<Identifier, RenderLayer> EXTRUSION = Util.memoize((Identifier texture) ->
        RenderLayer.of("blockbuster_extrusion", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS, 256, true, true,
            RenderLayer.MultiPhaseParameters.builder()
                .program(RenderPhase.ENTITY_TRANSLUCENT_CULL_PROGRAM)
                .texture(new RenderPhase.Texture(texture, false, false))
                .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
                .cull(RenderPhase.DISABLE_CULLING)
                .lightmap(RenderPhase.ENABLE_LIGHTMAP)
                .overlay(RenderPhase.ENABLE_OVERLAY_COLOR)
                .build(true)));

    /** {@link #EXTRUSION} under {@code ImageMorph}'s subtractive keying blend. */
    private static final Function<Identifier, RenderLayer> EXTRUSION_KEYING = Util.memoize((Identifier texture) ->
        RenderLayer.of("blockbuster_extrusion_keying", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS, 256, true, true,
            RenderLayer.MultiPhaseParameters.builder()
                .program(RenderPhase.ENTITY_TRANSLUCENT_CULL_PROGRAM)
                .texture(new RenderPhase.Texture(texture, false, false))
                .transparency(KEYING_TRANSPARENCY)
                .cull(RenderPhase.DISABLE_CULLING)
                .lightmap(RenderPhase.ENABLE_LIGHTMAP)
                .overlay(RenderPhase.ENABLE_OVERLAY_COLOR)
                .build(true)));

    /**
     * {@code RenderLayer.getEntityTranslucent(texture)} under the subtractive
     * keying blend — the layer a keyed {@code CustomMorph}'s limb geometry draws
     * through.
     *
     * <p>Legacy {@code ModelCustom.render} (1.12.2
     * {@code client/model/ModelCustom.java:114-126,146-152}) wrapped the whole
     * limb loop in the very same {@code GL_FUNC_REVERSE_SUBTRACT} /
     * {@code blendFunc(ZERO, ZERO)} state that {@code ImageMorph} used, gated on
     * {@code current.keying}, and put {@code GL_FUNC_ADD} +
     * {@code SRC_ALPHA/ONE_MINUS_SRC_ALPHA} back afterwards. Everything else
     * about the draw was unchanged, so this mirrors vanilla's
     * {@code entity_translucent} parameters verbatim — {@code
     * ENTITY_TRANSLUCENT_PROGRAM}, {@code DISABLE_CULLING} (not the {@code
     * _cull} variant), lightmap, overlay, {@code affectsOutline = true}, buffer
     * size 1536 — with only the transparency phase swapped. Verified against the
     * loom-cache named jar with {@code javap}.</p>
     */
    private static final Function<Identifier, RenderLayer> MODEL_KEYING = Util.memoize((Identifier texture) ->
        RenderLayer.of("blockbuster_model_keying", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS, 1536, true, true,
            RenderLayer.MultiPhaseParameters.builder()
                .program(RenderPhase.ENTITY_TRANSLUCENT_PROGRAM)
                .texture(new RenderPhase.Texture(texture, false, false))
                .transparency(KEYING_TRANSPARENCY)
                .cull(RenderPhase.DISABLE_CULLING)
                .lightmap(RenderPhase.ENABLE_LIGHTMAP)
                .overlay(RenderPhase.ENABLE_OVERLAY_COLOR)
                .build(true)));

    /**
     * The key a line layer is memoized under: legacy chose its line width and
     * its depth state per draw ({@code glLineWidth(n)} +
     * {@code enable/disableDepth}), so both have to be part of the layer
     * identity rather than baked into a constant.
     */
    private record LineKey(double width, boolean depth)
    {}

    /**
     * Legacy {@code GL_LINES} + {@code glLineWidth(n)}: a raw vertex-pair
     * stream in {@code POSITION_COLOR}, which is what every McLib gizmo
     * ({@code RenderingUtils.renderCircle}) drew through once
     * {@code disableTexture2D}/{@code disableLighting} had stripped the
     * fixed-function pipeline down to flat coloured lines.
     *
     * <p>Vanilla's own {@code RenderLayer.getDebugLineStrip(width)} is exactly
     * this shape and is where the width strategy comes from — a
     * {@link RenderPhase.LineWidth} phase, not a stray
     * {@code RenderSystem.lineWidth} call that the next layer would inherit.</p>
     */
    private static final Function<LineKey, RenderLayer> LINES = Util.memoize((LineKey key) ->
        RenderLayer.of("blockbuster_lines", VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES, 256,
            RenderLayer.MultiPhaseParameters.builder()
                .program(RenderPhase.COLOR_PROGRAM)
                .lineWidth(new RenderPhase.LineWidth(OptionalDouble.of(key.width())))
                .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
                .depthTest(key.depth() ? RenderPhase.LEQUAL_DEPTH_TEST : RenderPhase.ALWAYS_DEPTH_TEST)
                .cull(RenderPhase.DISABLE_CULLING)
                .build(false)));

    /** {@link #LINES} in {@code GL_LINE_STRIP} mode (the cone's edge fan). */
    private static final Function<LineKey, RenderLayer> LINE_STRIP = Util.memoize((LineKey key) ->
        RenderLayer.of("blockbuster_line_strip", VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINE_STRIP, 256,
            RenderLayer.MultiPhaseParameters.builder()
                .program(RenderPhase.COLOR_PROGRAM)
                .lineWidth(new RenderPhase.LineWidth(OptionalDouble.of(key.width())))
                .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
                .depthTest(key.depth() ? RenderPhase.LEQUAL_DEPTH_TEST : RenderPhase.ALWAYS_DEPTH_TEST)
                .cull(RenderPhase.DISABLE_CULLING)
                .build(false)));

    private McLibRenderLayers(String name, VertexFormat format, VertexFormat.DrawMode drawMode, int expectedBufferSize, boolean hasCrumbling, boolean translucent, Runnable startAction, Runnable endAction)
    {
        super(name, format, drawMode, expectedBufferSize, hasCrumbling, translucent, startAction, endAction);
    }

    /**
     * Legacy's {@code GL_LINES} gizmo stream — consecutive vertex pairs, one
     * line each.
     *
     * @param width legacy {@code glLineWidth(width)}
     * @param depth false reproduces {@code disableDepth()} — draw on top of
     *              everything (the morph editor's viewport did this, the
     *              in-world F3 draw did not)
     */
    public static RenderLayer lines(double width, boolean depth)
    {
        return LINES.apply(new LineKey(width, depth));
    }

    /** {@link #lines} in {@code GL_LINE_STRIP} mode. */
    public static RenderLayer lineStrip(double width, boolean depth)
    {
        return LINE_STRIP.apply(new LineKey(width, depth));
    }

    /**
     * The layer a picture quad (image morph, light-bulb icon) draws through.
     *
     * @param keying legacy's subtractive "cut out the background" blend
     */
    public static RenderLayer picture(Identifier texture, boolean keying)
    {
        return keying ? PICTURE_KEYING.apply(texture) : PICTURE.apply(texture);
    }

    /**
     * The same quad layer as {@link #picture}, with or without the depth test.
     *
     * @param depth false reproduces legacy's {@code disableDepth()} — draw on
     *              top of everything
     */
    public static RenderLayer picture(Identifier texture, boolean keying, boolean depth)
    {
        if (depth)
        {
            return picture(texture, keying);
        }

        return PICTURE_NO_DEPTH.apply(texture);
    }

    /**
     * The layer a {@code CachedExtrusion} draws through — the picture layer with
     * culling switched off, which is the only GL state legacy's
     * {@code CachedExtrusion.render()} touched on its own.
     *
     * @param keying {@code ImageMorph}'s subtractive blend, which wraps the
     *               extruded variant exactly as it wraps the flat quad
     */
    public static RenderLayer extrusion(Identifier texture, boolean keying)
    {
        return keying ? EXTRUSION_KEYING.apply(texture) : EXTRUSION.apply(texture);
    }

    /**
     * The layer a custom model's limb geometry draws through: vanilla's
     * {@code entity_translucent} normally, {@link #MODEL_KEYING} when the morph
     * has {@code keying} switched on.
     *
     * <p>The un-keyed branch deliberately returns <b>vanilla's own</b> layer
     * rather than a private copy of it, so a keyed morph is the only thing that
     * gets its own draw batch and every other custom-model draw keeps sharing
     * the batch it always shared.</p>
     *
     * @param keying {@code CustomMorph.keying} — legacy's subtractive "punch a
     *               hole through everything behind me" blend
     */
    public static RenderLayer model(Identifier texture, boolean keying)
    {
        return keying ? MODEL_KEYING.apply(texture) : RenderLayer.getEntityTranslucent(texture);
    }
}
