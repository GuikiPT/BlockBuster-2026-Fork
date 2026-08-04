package mchorse.aperture.client;

import mchorse.aperture.Aperture;
import mchorse.aperture.ClientProxy;
import mchorse.aperture.camera.CameraUtils;
import mchorse.aperture.client.gui.GuiCameraEditor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * P178.1 — Aperture's HUD-side rendering behaviors.
 *
 * <p>Port of {@code mchorse.aperture.client.RenderingHandler}. Legacy hung four
 * Forge overlay events off this class; the port keeps the class (and its
 * package/name, for diff-ability) and re-expresses each hook as a pure
 * predicate plus a draw call, so every gate is headlessly testable:</p>
 *
 * <table>
 * <tr><th>Legacy event</th><th>Port</th></tr>
 * <tr><td>{@code RenderOverlayEvent.Pre} (letterbox)</td>
 *     <td>{@link #drawLetterbox(DrawContext)} off the P44.1 overlay seam
 *     ({@code HudRenderCallback}, wired in {@link ApertureClient})</td></tr>
 * <tr><td>{@code RenderGameOverlayEvent.Chat} cancel</td>
 *     <td>{@link #shouldHideChat()} read by the {@code ChatHud.render} mixin</td></tr>
 * <tr><td>{@code RenderGameOverlayEvent.Text} cancel</td>
 *     <td>{@link #shouldHideDebugHud()} read by the {@code DebugHud.render} mixin</td></tr>
 * <tr><td>{@code RenderGameOverlayEvent.Text} left-list append</td>
 *     <td>{@link #debugTickLine()} appended by the {@code DebugHud.getLeftText} mixin</td></tr>
 * <tr><td>{@code FOVModifier} pin in editor</td>
 *     <td>P178's {@code GameRenderer#getFov} mixin (already landed)</td></tr>
 * <tr><td>{@code RenderGameOverlayEvent.Post} (manual recording HUD)</td>
 *     <td>{@code GuiManualFixturePanel.drawHUD} (P184, already wired)</td></tr>
 * </table>
 *
 * <p><b>Layering deviation (documented).</b> Legacy drew the letterbox from the
 * <em>Pre</em> overlay hook, i.e. underneath the vanilla HUD; the tree's P44.1
 * seam is {@code HudRenderCallback}, which fires after the vanilla HUD and
 * before the current {@code Screen}. The bars therefore sit above the hotbar
 * and below the camera editor's own chrome. Everything else (which frames get
 * bars, and their rects) is bit-identical.</p>
 *
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/client/RenderingHandler.java
 */
public class RenderingHandler
{
    /** Legacy default when {@code aspect_ratio} fails to parse */
    public static final float DEFAULT_ASPECT = 16F / 9F;

    /** Legacy {@code Gui.drawRect(..., 0xff000000)} */
    public static final int LETTERBOX_COLOR = 0xff000000;

    /** Legacy {@code list.add("Camera ticks " + ClientProxy.runner.ticks)} */
    public static final String DEBUG_TICKS_PREFIX = "Camera ticks ";

    /* ---------------------------------------------------------------- gates */

    /**
     * Whether the camera editor is the current screen (the condition every
     * legacy "in editor" gate used).
     */
    public static boolean isCameraEditorOpen()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        return mc != null && mc.currentScreen instanceof GuiCameraEditor;
    }

    /**
     * Legacy {@code onChatDraw}: {@code editorHideChat && currentScreen
     * instanceof GuiCameraEditor}.
     */
    public static boolean shouldHideChat()
    {
        return shouldHideChat(Aperture.editorHideChat.get(), isCameraEditorOpen());
    }

    public static boolean shouldHideChat(boolean hideChat, boolean editorOpen)
    {
        return hideChat && editorOpen;
    }

    /**
     * Legacy {@code onHUDRender(RenderGameOverlayEvent.Text)}: the whole F3
     * text block is cancelled while the camera editor is open — note this is
     * <b>not</b> gated on {@code editorHideChat}.
     */
    public static boolean shouldHideDebugHud()
    {
        return isCameraEditorOpen();
    }

    /**
     * Legacy {@code "Camera ticks " + runner.ticks}, added to the F3 left
     * column while a profile is playing. Returns null when nothing should be
     * added.
     */
    public static String debugTickLine()
    {
        return debugTickLine(ClientProxy.runner.isRunning(), ClientProxy.runner.ticks);
    }

    public static String debugTickLine(boolean running, long ticks)
    {
        return running ? DEBUG_TICKS_PREFIX + ticks : null;
    }

    /**
     * Legacy {@code onPreRenderOverlay} head: the letterbox needs
     * {@code editorLetterbox} on <b>and</b> either the editor open or the
     * runner running — the out-of-editor case keys off runner state, not
     * screen state.
     */
    public static boolean shouldDrawLetterbox(boolean letterbox, boolean editorOpen, boolean running)
    {
        return letterbox && (editorOpen || running);
    }

    /**
     * Legacy aspect resolution: the editor's own live aspect when it is open,
     * otherwise {@code editorLetterboxAspect} parsed with the 16:9 fallback.
     */
    public static float letterboxAspect(boolean editorOpen, float editorAspect, String configured)
    {
        return editorOpen ? editorAspect : CameraUtils.parseAspectRation(configured, DEFAULT_ASPECT);
    }

    /* ------------------------------------------------------------ rect math */

    /**
     * Legacy {@code onPreRenderOverlay} bar math, verbatim (including the
     * {@code (int) (screenH - …) / 2} cast placement — the cast binds to the
     * subtraction, the integer division happens after).
     *
     * @return the black bars as {@code {x1, y1, x2, y2}} rects; empty when the
     *         aspect ratio is non-positive or already matches the window.
     */
    public static List<int[]> letterboxRects(int screenW, int screenH, float aspectRatio)
    {
        List<int[]> rects = new ArrayList<int[]>();

        if (aspectRatio > 0)
        {
            int width = (int) (aspectRatio * screenH);

            if (width != screenW)
            {
                if (width < screenW)
                {
                    /* Horizontal bars */
                    int w = (screenW - width) / 2;

                    rects.add(new int[] {0, 0, w, screenH});
                    rects.add(new int[] {screenW - w, 0, screenW, screenH});
                }
                else
                {
                    /* Vertical bars */
                    int h = (int) (screenH - (1F / aspectRatio * screenW)) / 2;

                    rects.add(new int[] {0, 0, screenW, h});
                    rects.add(new int[] {0, screenH - h, screenW, screenH});
                }
            }
        }

        return rects;
    }

    /* ---------------------------------------------------------------- draws */

    /**
     * Letterbox draw entry point — registered on the P44.1 overlay seam.
     */
    public static void drawLetterbox(DrawContext context)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null || mc.getWindow() == null)
        {
            return;
        }

        boolean editorOpen = isCameraEditorOpen();

        if (!shouldDrawLetterbox(Aperture.editorLetterbox.get(), editorOpen, ClientProxy.runner.isRunning()))
        {
            return;
        }

        float aspect = letterboxAspect(editorOpen, editorOpen ? ((GuiCameraEditor) mc.currentScreen).aspectRatio : DEFAULT_ASPECT, Aperture.editorLetterboxAspect.get());

        for (int[] rect : letterboxRects(mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(), aspect))
        {
            context.fill(rect[0], rect[1], rect[2], rect[3], LETTERBOX_COLOR);
        }
    }
}
