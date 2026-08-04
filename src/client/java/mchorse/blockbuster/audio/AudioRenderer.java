package mchorse.blockbuster.audio;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.ClientProxy;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.utils.ColorUtils;
import mchorse.mclib.utils.wav.Waveform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import org.apache.commons.lang3.StringUtils;

/**
 * Port of Blockbuster 2.7.2's {@code audio/AudioRenderer} (roadmap P190).
 *
 * <p>Draws the stacked, scissored, playhead-marked waveform bars — one per
 * currently-playing {@link AudioFile} in {@link ClientProxy#audio} — with the
 * optional filename / playback-time labels. Wired at two legacy call sites: the
 * in-game HUD hook ({@code RenderingHandler.onHUDRender}, suppressed while the
 * camera editor is open) and the camera-editor overlay drawable
 * (gated on {@link mchorse.blockbuster.aperture.CameraHandler#isCameraEditorOpen}).</p>
 *
 * <p>GL parity notes: legacy {@code GlStateManager.color(r, g, b[, a])} →
 * {@code RenderSystem.setShaderColor}; {@code enableTexture2D}/{@code enableAlpha}
 * are gone in the core profile (shader discard handles alpha); scissor/gradient/
 * text/rect go through the P31 {@link GuiDraw} shim. Colors are pinned to their
 * legacy literals. The data→pixels math ({@link #unplayedWindow},
 * {@link #playedWindow}, {@link #formatTickLabel}) is factored out pure for
 * headless tests.</p>
 */
public class AudioRenderer
{
    /** Legacy played-portion dimming factor. */
    public static final float BRIGHTNESS = 0.45F;

    /** Legacy centered playhead color. */
    public static final int PLAYHEAD_COLOR = 0xff57f52a;

    /** Legacy side-edge line color. */
    public static final int EDGE_COLOR = 0xaaffffff;

    /** Legacy bottom-edge line color. */
    public static final int BOTTOM_EDGE_COLOR = 0xffffffff;

    /** Legacy label text / background colors. */
    public static final int LABEL_COLOR = 0xffffff;
    public static final int LABEL_BACKGROUND = 0x99000000;

    public static void renderAll(int x, int y, int w, int h, int sw, int sh)
    {
        if (!Blockbuster.audioWaveformVisible.get())
        {
            return;
        }

        AudioLibrary library = ClientProxy.audio;

        if (library == null)
        {
            /* SEAM(P188.1/P189): the AudioLibrary instance is created + reset by
             * the client audio lifecycle wiring; guard so the HUD hook never
             * NPEs before that lands. Legacy always had it instantiated. */
            return;
        }

        /* Make the anchor at the bottom */
        y -= h;

        for (AudioFile file : library.files.values())
        {
            if (!file.isEmpty() && !file.player.isStopped())
            {
                renderWaveform(file, x, y, w, h, sw, sh);

                y -= h + 5;
            }
        }
    }

    public static void renderWaveform(AudioFile file, int x, int y, int w, int h, int sw, int sh)
    {
        if (file == null || file.isEmpty())
        {
            return;
        }

        int half = w / 2;

        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer font = mc == null ? null : mc.textRenderer;

        /* Draw background */
        GuiDraw.drawVerticalGradientRect(x + 2, y + 2, x + w - 2, y + h, 0, ColorUtils.HALF_BLACK);
        GuiDraw.drawRect(x + 1, y, x + 2, y + h, EDGE_COLOR);
        GuiDraw.drawRect(x + w - 2, y, x + w - 1, y + h, EDGE_COLOR);
        GuiDraw.drawRect(x, y + h - 1, x + w, y + h, BOTTOM_EDGE_COLOR);

        GuiDraw.scissor(x + 2, y + 2, w - 4, h - 4, sw, sh);

        Waveform wave = file.waveform;

        if (!wave.isCreated())
        {
            wave.render();
        }

        float playback = file.player.getPlaybackPosition();
        int offset = (int) (playback * wave.getPixelsPerSecond());
        int waveW = wave.getWidth();

        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.enableBlend();

        /* Draw the waveform (unplayed portion, from the center rightwards) */
        WaveWindow unplayed = unplayedWindow(x, half, offset, waveW);

        if (unplayed != null)
        {
            wave.draw(unplayed.x, y, unplayed.u, 0, unplayed.w, h, h);
        }

        /* Draw the passed waveform (dimmed) */
        WaveWindow played = playedWindow(x, half, offset);

        if (played != null)
        {
            RenderSystem.setShaderColor(BRIGHTNESS, BRIGHTNESS, BRIGHTNESS, 1);
            wave.draw(played.x, y, played.u, 0, played.w, h, h);
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }

        GuiDraw.unscissor(sw, sh);

        GuiDraw.drawRect(x + half, y + 1, x + half + 1, y + h - 1, PLAYHEAD_COLOR);

        if (Blockbuster.audioWaveformFilename.get())
        {
            GuiDraw.drawTextBackground(font, file.name, x + 8, y + h / 2 - 4, LABEL_COLOR, LABEL_BACKGROUND);
        }

        if (Blockbuster.audioWaveformTime.get())
        {
            String tickLabel = formatTickLabel(playback);

            GuiDraw.drawTextBackground(font, tickLabel, x + w - 8 - GuiDraw.textWidth(font, tickLabel), y + h / 2 - 4, LABEL_COLOR, LABEL_BACKGROUND);
        }
    }

    /* Data → pixels math (extracted pure for headless parity tests, P190) */

    /**
     * Unplayed-portion draw window: cropped waveform region starting at the
     * playhead ({@code u = offset}), drawn from the horizontal center
     * ({@code x + half}), spanning at most {@code half} pixels. Returns
     * {@code null} when the playhead has reached/passed the waveform end
     * (nothing left to draw).
     */
    public static WaveWindow unplayedWindow(int x, int half, int offset, int waveW)
    {
        int runningOffset = waveW - offset;

        if (runningOffset <= 0)
        {
            return null;
        }

        return new WaveWindow(x + half, offset, Math.min(runningOffset, half));
    }

    /**
     * Played-portion draw window: the scrolling window left of the playhead.
     * Below {@code half} pixels of playback it anchors so the playhead sits at
     * {@code x + offset}; past {@code half} it scrolls, always {@code half}
     * wide, its left edge tracking {@code offset - half}. Returns {@code null}
     * before any playback ({@code offset <= 0}).
     */
    public static WaveWindow playedWindow(int x, int half, int offset)
    {
        if (offset <= 0)
        {
            return null;
        }

        int xx = offset > half ? x : x + half - offset;
        int oo = offset > half ? offset - half : 0;
        int ww = offset > half ? half : offset;

        return new WaveWindow(xx, oo, ww);
    }

    /**
     * Playback-time label: {@code "<tick>t (<seconds>.<ms>s)"} where
     * {@code tick = floor(playback * 20)}, ms {@code = tick % 20 * 5} (0 on
     * whole seconds), left-padded to 2 digits. E.g. tick 27 → {@code "27t
     * (1.35s)"}, tick 20 → {@code "20t (1.00s)"}. Legacy quirk preserved: the
     * {@code * 5D} then {@code (int)} truncation and the {@code == 0 ? 0}
     * branch.
     */
    public static String formatTickLabel(float playback)
    {
        return formatTickLabel((int) Math.floor(playback * 20));
    }

    public static String formatTickLabel(int tick)
    {
        int seconds = tick / 20;
        int milliseconds = (int) (tick % 20 == 0 ? 0 : tick % 20 * 5D);

        return tick + "t (" + seconds + "." + StringUtils.leftPad(String.valueOf(milliseconds), 2, "0") + "s)";
    }

    /**
     * A single cropped waveform draw window: destination x ({@link #x}), source
     * u-offset into the sprite strip ({@link #u}), and width ({@link #w}). The
     * {@code v}/{@code h}/{@code height} draw arguments are constant in the
     * legacy call sites so they are not carried here.
     */
    public static class WaveWindow
    {
        public final int x;
        public final int u;
        public final int w;

        public WaveWindow(int x, int u, int w)
        {
            this.x = x;
            this.u = u;
            this.w = w;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (!(obj instanceof WaveWindow))
            {
                return false;
            }

            WaveWindow other = (WaveWindow) obj;

            return this.x == other.x && this.u == other.u && this.w == other.w;
        }

        @Override
        public int hashCode()
        {
            return (this.x * 31 + this.u) * 31 + this.w;
        }

        @Override
        public String toString()
        {
            return "WaveWindow{x=" + this.x + ", u=" + this.u + ", w=" + this.w + "}";
        }
    }
}
