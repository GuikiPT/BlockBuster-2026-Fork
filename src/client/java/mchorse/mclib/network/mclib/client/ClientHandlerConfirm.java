package mchorse.mclib.network.mclib.client;

import mchorse.mclib.network.ClientMessageHandler;
import mchorse.mclib.network.mclib.Dispatcher;
import mchorse.mclib.network.mclib.common.PacketConfirm;
import net.minecraft.client.network.ClientPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Full port of McLib 2.4.3's ClientHandlerConfirm (roadmap P26).
 *
 * <p>Renders the GUI based on the enum value of packet. Every GUI confirmation
 * screen dispatches the packet back to the server — note the legacy contract:
 * it is <b>the same packet instance</b>, with only {@code confirm} mutated.</p>
 *
 * <p>Screen opening is a pluggable {@link #screenOpener} seam. The
 * {@link #HEADLESS_OPENER} default records the pending prompt (inspectable by
 * tests / the dev harness) and logs; the real modal is installed at client
 * init by {@link ConfirmScreenWiring} (S22/P228), which is what the running
 * game uses — legacy's
 * {@code displayGuiScreen(new GuiConfirmationScreen(packet.langKey, answer))}.
 * Until P228 nothing installed it, so a server-side confirm prompt never
 * showed and the server callback never fired.</p>
 *
 * <p>The {@code GUI} enum moved to {@link PacketConfirm.GUI} — see the
 * split-source-set note there.</p>
 *
 * <p>Legacy source: .tools/legacy-src/mclib/build/sources/main/java/mchorse/mclib/network/mclib/client/ClientHandlerConfirm.java</p>
 */
public class ClientHandlerConfirm extends ClientMessageHandler<PacketConfirm>
{
    private static final Logger LOGGER = LoggerFactory.getLogger("mclib");

    /** Last unanswered prompt recorded by the headless default opener. */
    private static PacketConfirm pendingPrompt;
    private static Consumer<Boolean> pendingAnswer;

    /**
     * The no-display fallback: record the prompt and wait for
     * {@link #answerPendingPrompt(boolean)}. Used headlessly (and on any client
     * where {@link ConfirmScreenWiring#install()} has not run).
     */
    public static final BiConsumer<PacketConfirm, Consumer<Boolean>> HEADLESS_OPENER = (packet, answer) ->
    {
        LOGGER.info("Confirm prompt (no screen installed): {}", packet.langKey == null ? "<null key>" : packet.langKey.get());

        pendingPrompt = packet;
        pendingAnswer = answer;
    };

    /**
     * Active prompt opener. {@link ConfirmScreenWiring#install()} points this
     * at the real {@code GuiConfirmationScreen} at client init.
     */
    public static BiConsumer<PacketConfirm, Consumer<Boolean>> screenOpener = HEADLESS_OPENER;

    public static PacketConfirm getPendingPrompt()
    {
        return pendingPrompt;
    }

    /** Answers (and clears) the pending headless prompt, if any. */
    public static void answerPendingPrompt(boolean value)
    {
        Consumer<Boolean> answer = pendingAnswer;

        pendingPrompt = null;
        pendingAnswer = null;

        if (answer != null)
        {
            answer.accept(value);
        }
    }

    /**
     * Renders the GUI based on the enum value of packet. Every GUI confirmation screen dispatches the packet back to the server
     * @param packet
     */
    @Override
    public void run(ClientPlayerEntity player, PacketConfirm packet)
    {
        switch (packet.gui)
        {
            case MCSCREEN:
                screenOpener.accept(packet, (value) ->
                {
                    this.dispatchPacket(packet, value);
                });
        }
    }

    private void dispatchPacket(PacketConfirm packet, boolean value)
    {
        packet.confirm = value;

        Dispatcher.sendToServer(packet);
    }
}
