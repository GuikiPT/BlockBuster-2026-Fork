package mchorse.blockbuster.network.client.scene;

import mchorse.blockbuster.network.common.scene.PacketSceneManage;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Client handler for {@link PacketSceneManage} (roadmap P131) — <b>empty
 * no-op</b>, exactly like 1.12.2 {@code ClientHandlerSceneManage.java}. The GUI
 * applies rename/remove/dupe optimistically and locally; the server's echo of
 * this packet is dropped here.
 *
 * <p><b>Parity note:</b> 1.12.2 mis-registered this client handler on the
 * SERVER side (Dispatcher line 167), so the server→client echo was never
 * routable. The port registers it on the CLIENT (recorded in
 * {@code ChannelLedger}); since the handler is a no-op, observable behavior is
 * identical — the echo simply arrives and does nothing.</p>
 */
public class ClientHandlerSceneManage extends ClientMessageHandler<PacketSceneManage>
{
    @Override
    public void run(ClientPlayerEntity player, PacketSceneManage message)
    {
    }
}
