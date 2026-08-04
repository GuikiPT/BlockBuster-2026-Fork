package mchorse.blockbuster.client.textures;

import java.io.IOException;
import java.util.Arrays;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.legacy.LegacyTexturePath;
import mchorse.mclib.utils.MathUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

/**
 * Animated GIF texture
 *
 * Port of Blockbuster 2.7.2's {@code mchorse.blockbuster.client.textures.GifTexture}.
 * A {@code GifTexture} does not own any GL objects of its own: it is a thin proxy
 * registered under the gif's own identifier that, when bound, resolves the current
 * animation frame from tick time and delegates {@link #getGlId()} to that frame's
 * texture (each {@link GifFrameTexture} owns its GL id). {@link #load(ResourceManager)}
 * and are therefore no-ops.
 *
 * <h3>Tick clocks</h3>
 * <ul>
 *   <li>{@link #globalTick} advances once per client tick via {@link #updateTick()}
 *   (driven by the client tick hook, P88).</li>
 *   <li>{@link #entityTick} is a process-global override: when {@code >= 0} it
 *   replaces {@code globalTick} so record-synced actors play GIFs against record
 *   time. It is set by playback (S10) / capture (S18) code around a render pass and
 *   reset to {@code -1} afterwards. One actor setting it affects every gif drawn
 *   that frame — this is exactly how legacy record-sync worked, preserve it.</li>
 * </ul>
 *
 * <h3>Frame timing</h3>
 * GIF delays are in centiseconds; there are 5 centiseconds per client tick
 * (20 tps &times; 5 = 100 cs/s), hence the {@code * 5} in {@link #frameIndex}.
 * A GIF whose delays sum to zero would divide by zero in legacy
 * {@code GifTexture.getFrame} (no duration guard, throws
 * {@code ArithmeticException}). The workspace total-reader rule overrides parity
 * for crashes: {@link #frameIndex} guards {@code duration <= 0} by pinning to
 * frame 0 and logging a one-time warning (parity deviation, recorded in P89 notes).
 */
public class GifTexture extends AbstractTexture
{
    public static int globalTick = 0;
    public static int entityTick = -1;

    /** One-time warning latch for zero-duration GIFs (see {@link #frameIndex}). */
    private static boolean warnedZeroDuration = false;

    public Identifier base;
    public Identifier[] frames;
    public int[] delays;

    public int duration;

    /**
     * Resolve the identifier a gif-suffixed location should actually bind to for
     * the given tick, delegating to the registered {@link GifTexture} if present.
     * Only paths ending in {@code "gif"} get frame-resolved.
     *
     * <p>Every legacy-authored texture path meets
     * {@link LegacyTexturePath#translate(Identifier)} first (P71 sibling shim):
     * a 1.12.2 file — a Snowstorm scheme, an image morph — may
     * point at {@code minecraft:textures/blocks/…}, which the 1.13 flattening
     * moved. A miss there is silent and renders the quad <b>solid black</b>
     * rather than failing loudly, so the translation belongs on the path every
     * such bind takes.</p>
     */
    public static Identifier resolveFrame(Identifier location, int ticks, float partialTicks)
    {
        TextureManager textures = MinecraftClient.getInstance().getTextureManager();

        location = LegacyTexturePath.translate(location);

        if (location.getPath().endsWith("gif"))
        {
            AbstractTexture object = textures.getOrDefault(location, null);

            if (object instanceof GifTexture)
            {
                location = ((GifTexture) object).getFrame(ticks, partialTicks);
            }
        }

        return location;
    }

    public static void updateTick()
    {
        globalTick += 1;
    }

    /**
     * Bind a (possibly gif-suffixed) texture for the current animation frame —
     * the render-side entry point P153's snowstorm renderer calls before drawing
     * particle quads.
     *
     * <p>Legacy 2.7.2 {@code GifTexture.bindTexture(ResourceLocation, int, float)}
     * resolved the frame and called {@code renderEngine.bindTexture}. On 1.20.4
     * texture binding is a shader-sampler assignment, so this resolves the frame
     * via {@link #resolveFrame(Identifier, int, float)} (a no-op for static, i.e.
     * non-{@code gif}, identifiers) and hands it to
     * {@link RenderSystem#setShaderTexture(int, Identifier)} sampler slot 0. The
     * {@code ticks} argument is the emitter age, matching the legacy call
     * {@code GifTexture.bindTexture(texture, this.age, partialTicks)}.</p>
     */
    public static void bindTexture(Identifier texture, int ticks, float partialTicks)
    {
        RenderSystem.setShaderTexture(0, resolveFrame(texture, ticks, partialTicks));
    }

    public GifTexture(Identifier texture, int[] delays, Identifier[] frames)
    {
        this.base = texture;
        this.delays = Arrays.copyOf(delays, delays.length);
        this.frames = frames;
    }

    public void calculateDuration()
    {
        this.duration = 0;

        for (int delay : this.delays)
        {
            this.duration += delay;
        }
    }

    @Override
    public void load(ResourceManager resourceManager) throws IOException
    {}

    @Override
    public int getGlId()
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextureManager textures = mc.getTextureManager();
        Identifier rl = this.getFrame(entityTick > -1 ? entityTick : globalTick, mc.getTickDelta());

        AbstractTexture texture = textures.getTexture(rl);

        /* P217.1: legacy also copied the frame's Optifine `multiTex` handle onto
         * this wrapper here (GifTexture.updateMultiTex), because Optifine kept
         * shader-texture state in a *field* and either object could end up bound.
         * Iris keys off the GL id instead — the id this method returns *is* the
         * frame's — so there is nothing to copy; the animation is followed by
         * GifPbrTexture at bind time. */

        return texture.getGlId();
    }

    /**
     * The {@link GifFrameTexture} this wrapper currently stands for, resolved
     * against the same clock {@link #getGlId()} uses. Returns {@code null} when
     * the frame is missing or is not one of ours (totality — the caller is a
     * shader-compat path, it must degrade rather than throw).
     *
     * <p>Used by {@code GifPbrTexture} (P217.1) so one PBR registration on the
     * wrapper serves every frame of the animation.</p>
     */
    public GifFrameTexture currentFrameTexture()
    {
        try
        {
            MinecraftClient mc = MinecraftClient.getInstance();
            Identifier rl = this.getFrame(entityTick > -1 ? entityTick : globalTick, mc.getTickDelta());
            AbstractTexture texture = mc.getTextureManager().getTexture(rl);

            return texture instanceof GifFrameTexture ? (GifFrameTexture) texture : null;
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    @Override
    public void close()
    {}

    public Identifier getFrame(int ticks, float partialTicks)
    {
        return this.frames[frameIndex(this.delays, this.duration, ticks, partialTicks)];
    }

    /**
     * Pure frame-selection math, extracted so it is unit-testable without a GL
     * context. Mirrors legacy {@code GifTexture.getFrame}: convert tick time to
     * centiseconds ({@code * 5}), wrap by {@code duration}, then walk the
     * cumulative delays. Result is clamped into {@code [0, delays.length - 1]}.
     *
     * <p>Guards {@code duration <= 0} (all-zero / empty delays) by returning
     * frame 0 and logging once — legacy would throw {@code ArithmeticException}
     * on the {@code % 0}; the total-reader rule forbids the crash.</p>
     */
    public static int frameIndex(int[] delays, int duration, int ticks, float partialTicks)
    {
        if (duration <= 0)
        {
            if (!warnedZeroDuration)
            {
                Blockbuster.LOGGER.warn("Encountered a GIF with zero total frame duration; pinning to frame 0 (static image).");
                warnedZeroDuration = true;
            }

            return 0;
        }

        int tick = (int) ((ticks + partialTicks) * 5 % duration);

        int accumulated = 0;
        int index = 0;

        for (int delay : delays)
        {
            accumulated += delay;

            if (tick < accumulated)
            {
                break;
            }

            index++;
        }

        return MathUtils.clamp(index, 0, delays.length - 1);
    }
}
