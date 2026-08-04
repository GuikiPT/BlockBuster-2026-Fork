package mchorse.mclib.network.mclib.client;

import mchorse.mclib.client.gui.framework.elements.GuiConfirmationScreen;
import mchorse.mclib.client.gui.utils.ScreenOpener;

/**
 * S22/P228 — installs the real {@code PacketConfirm} modal.
 *
 * <p>{@link ClientHandlerConfirm} shipped in P26 with a headless
 * {@code screenOpener} default (record the prompt, log, wait for
 * {@code answerPendingPrompt}) because {@link GuiConfirmationScreen} only
 * landed in P36. Nothing ever went back and installed the real one, so a
 * server-side {@code PacketConfirm} prompt — the gate in front of destructive
 * actions — never appeared on screen and never answered itself either: the
 * server's callback simply never fired.</p>
 *
 * <p>This installer restores the legacy body of
 * {@code ClientHandlerConfirm.run}:</p>
 * <pre>
 * Minecraft.getMinecraft().displayGuiScreen(new GuiConfirmationScreen(packet.langKey, (value) -&gt; dispatch(packet, value)));
 * </pre>
 *
 * <p>The screen is displayed through {@link ScreenOpener} so headless tests
 * can capture it; the handler's own {@code screenOpener} field keeps its
 * record-only default, which is what {@code McLibPacketsTest} drives (no test
 * has to know about a modal it cannot render).</p>
 *
 * <p>Legacy source: .tools/legacy-src/mclib/.../network/mclib/client/ClientHandlerConfirm.java</p>
 */
public final class ConfirmScreenWiring
{
    private ConfirmScreenWiring()
    {}

    /**
     * Point {@link ClientHandlerConfirm#screenOpener} at the real modal.
     * Idempotent — assigning the same behaviour twice is harmless.
     */
    public static void install()
    {
        ClientHandlerConfirm.screenOpener = (packet, answer) ->
            ScreenOpener.open(new GuiConfirmationScreen(packet.langKey, answer));
    }

    /** Test hook: drop back to the P26 record-only headless opener. */
    public static void uninstall()
    {
        ClientHandlerConfirm.screenOpener = ClientHandlerConfirm.HEADLESS_OPENER;
    }
}
