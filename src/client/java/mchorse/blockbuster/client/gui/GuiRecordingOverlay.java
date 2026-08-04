package mchorse.blockbuster.client.gui;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.ClientProxy;
import mchorse.blockbuster.recording.RecordRecorder;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.util.Identifier;

/**
 * Recording GUI overlay (roadmap P124).
 *
 * <p>Full port of 1.12.2's {@code GuiRecordingOverlay}: draws the REC icon
 * (16×16 from {@code textures/gui/recording.png}) at (4, 4) and the caption
 * text at (22, 8) in the top-left corner while a countdown or recording is in
 * progress. The overlay reads its display state from
 * {@link ClientProxy#recordingOverlay} (a {@link RecordingOverlayState}), which
 * {@code PacketCaption} and {@code PacketPlayerRecording} drive (P116).</p>
 *
 * <p>Legacy source:
 * {@code blockbuster-1.12/.../client/gui/GuiRecordingOverlay.java}. The l10n
 * wrapping ({@code blockbuster.recording}) and the live tick suffix that legacy
 * baked into {@code setCaption} are deferred to render time here, so the state
 * holder can stay a plain data object and the tick counter stays live. The
 * legacy {@code GlStateManager.pushAttrib/color/popAttrib} calls are dropped —
 * {@link DrawContext} owns GL state on 1.20.4 (GL1 → core-profile migration).</p>
 */
public class GuiRecordingOverlay
{
    public static final Identifier TEXTURE = new Identifier(Blockbuster.MOD_ID, "textures/gui/recording.png");

    /**
     * Localization seam. Production path is vanilla {@link I18n}; headless
     * tests override it so the composed caption is deterministic without a
     * loaded {@code Language}. Mirrors the {@code LangKey.translator} pattern.
     */
    public interface Translator
    {
        String translate(String key, Object... args);
    }

    public static Translator translator = I18n::translate;

    /**
     * Compose the caption string exactly as legacy did.
     *
     * <p>Plain (non-filename) captions render as-is. In filename mode
     * (legacy's {@code recording} flag) the caption is wrapped in the
     * {@code blockbuster.recording} translation and, when a recorder mirror is
     * present, the live {@code §r (§lN§r)} suffix is appended where
     * {@code N == recorder.tick + recorder.offset} (append-mode recordings show
     * the absolute tick). Section-sign formatting codes are kept literal —
     * {@code drawTextWithShadow} honors them.</p>
     *
     * @param recorder the client-mirror recorder for the local player, or
     *                 {@code null} when none is running (suffix omitted).
     */
    public static String composeCaption(RecordingOverlayState state, RecordRecorder recorder)
    {
        String caption = state.getCaption();

        if (state.isFilename())
        {
            caption = translator.translate("blockbuster.recording", caption);

            if (recorder != null)
            {
                caption += "§r (§l" + (recorder.tick + recorder.offset) + "§r)";
            }
        }

        return caption;
    }

    /**
     * Draw the recording overlay in the top-left corner when visible. Layout
     * constants are preserved from 1.12.2: icon {@code (4, 4, 0, 0, 16, 16)},
     * text at {@code (22, 8)} in {@code 0xffffffff}.
     */
    public void draw(DrawContext context, int width, int height)
    {
        RecordingOverlayState state = ClientProxy.recordingOverlay;

        if (!state.isVisible())
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        RecordRecorder recorder = null;

        if (state.isFilename() && mc != null && mc.player != null)
        {
            recorder = ClientProxy.manager.recorders.get(mc.player);
        }

        String caption = composeCaption(state, recorder);

        context.drawTexture(TEXTURE, 4, 4, 0, 0, 16, 16);

        if (mc != null)
        {
            /* The HUD and the screen share one DrawContext and one depth
             * buffer, so this caption must go through the framework's text
             * path or it both survives unflushed into the screen's frame and
             * leaves a 0.03-forward depth footprint that the dashboard's own
             * quads then fail GL_LEQUAL against — see GuiDraw.flattenDepth. */
            GuiDraw.bindDrawContext(context);
            GuiDraw.drawStringWithShadow(mc.textRenderer, caption, 22, 8, 0xffffffff);
        }
    }
}
