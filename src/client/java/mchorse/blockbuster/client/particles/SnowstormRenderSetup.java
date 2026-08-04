package mchorse.blockbuster.client.particles;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.blockbuster.client.textures.GifTexture;
import mchorse.mclib.utils.resources.ResourceLocation;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.Identifier;

/**
 * The one place a Snowstorm billboard pass sets up its shader and its texture
 * sampler — roadmap P275.
 *
 * <p>There are two Snowstorm quad renderers and they are <b>not</b> the same
 * code path:</p>
 *
 * <ul>
 *   <li>{@code BedrockEmitter.render} &rarr; {@code renderParticles}, which draws
 *       the world pass ({@code WorldRendererParticlesMixin}) <i>and</i> the
 *       Snowstorm dashboard viewport ({@code GuiSnowstormRenderer});</li>
 *   <li>{@code BedrockEmitter.renderOnScreen}, the morph-picker thumbnail.</li>
 * </ul>
 *
 * <p>They had each grown their own sampler-0 bind, and they had already drifted:
 * the world/viewport path resolved the texture through
 * {@link GifTexture#bindTexture(Identifier, int, float)} (animation frame +
 * the P252 legacy-path shim), while the thumbnail handed
 * {@code scheme.texture.toIdentifier()} straight to
 * {@link RenderSystem#setShaderTexture(int, Identifier)} — so an animated GIF
 * particle sheet froze on frame 0 in the picker, and a 1.12.2-era vanilla path
 * silently missed. Both now call in here, so a change to how a Snowstorm quad
 * finds its texture cannot land on one surface only.</p>
 *
 * <p>The GL half sits behind {@link Gl} for the same reason the rest of S22's
 * render seams do: {@link RenderSystem} cannot be driven headlessly, but "does
 * production actually call the shared setup" is exactly the question that has to
 * stay answered. It is <b>passed</b>, not installed into a mutable static —
 * the {@code ChromaSky.render(CHROMA_GL, …)} idiom this codebase already uses.
 * That keeps {@link #REAL} the only implementation production can reach (there
 * is no writer for a test to leak across classes) while the pure overloads stay
 * drivable headlessly.</p>
 */
public class SnowstormRenderSetup
{
    /**
     * The vanilla core shader a Snowstorm quad pass draws with. The pairing with
     * the buffer's {@code VertexFormat} is fixed: {@link #PARTICLE} goes with
     * {@code POSITION_TEXTURE_COLOR_LIGHT} (it samples the lightmap through
     * {@code Sampler2}), {@link #POSITION_TEX_COLOR} with
     * {@code POSITION_TEXTURE_COLOR} (the GUI thumbnail, which has no lightmap).
     */
    public enum Shader
    {
        PARTICLE, POSITION_TEX_COLOR
    }

    /** The GL calls this setup makes, behind a seam so they are assertable. */
    public interface Gl
    {
        void shader(Shader shader);

        void texture(Identifier texture, int ticks, float partialTicks);
    }

    /** The real implementation — what production passes. */
    public static final Gl REAL = new Gl()
    {
        @Override
        public void shader(Shader shader)
        {
            if (shader == Shader.PARTICLE)
            {
                RenderSystem.setShader(GameRenderer::getParticleProgram);
            }
            else
            {
                RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
            }
        }

        @Override
        public void texture(Identifier texture, int ticks, float partialTicks)
        {
            GifTexture.bindTexture(texture, ticks, partialTicks);
        }
    };

    private SnowstormRenderSetup()
    {}

    /**
     * Shader + sampler 0 for the billboard quad pass (world and dashboard
     * viewport). Legacy 2.7.2 did the equivalent with
     * {@code GifTexture.bindTexture(texture, this.age, partialTicks)} and the
     * fixed-function texture unit; {@code ticks} is still the emitter age, which
     * is what drives GIF frame selection.
     */
    public static void beginQuadPass(ResourceLocation texture, int ticks, float partialTicks)
    {
        beginQuadPass(REAL, texture, ticks, partialTicks);
    }

    /** As {@link #beginQuadPass(ResourceLocation, int, float)}, against a given GL. */
    public static void beginQuadPass(Gl gl, ResourceLocation texture, int ticks, float partialTicks)
    {
        gl.shader(Shader.PARTICLE);

        bindTexture(gl, texture, ticks, partialTicks);
    }

    /**
     * Sampler 0 only — the GUI thumbnail path, whose shader is chosen by the
     * appearance component that owns the vertex format it begins.
     */
    public static void bindTexture(ResourceLocation texture, int ticks, float partialTicks)
    {
        bindTexture(REAL, texture, ticks, partialTicks);
    }

    /** As {@link #bindTexture(ResourceLocation, int, float)}, against a given GL. */
    public static void bindTexture(Gl gl, ResourceLocation texture, int ticks, float partialTicks)
    {
        gl.texture(resolve(texture), ticks, partialTicks);
    }

    /**
     * The identifier a Snowstorm quad pass binds for {@code texture}. Total: a
     * scheme that carries no texture at all falls back to the shipped default
     * sheet rather than throwing inside a render pass (the workspace
     * total-reader rule). The 1.12.2 &rarr; 1.13 path moves live one level down,
     * in {@code ResourceLocation.toIdentifier()} (P252/P257).
     */
    public static Identifier resolve(ResourceLocation texture)
    {
        return (texture == null ? BedrockScheme.DEFAULT_TEXTURE : texture).toIdentifier();
    }
}
