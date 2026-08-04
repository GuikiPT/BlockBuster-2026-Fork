package mchorse.blockbuster.network.client;

import mchorse.blockbuster.common.GuiHandler;
import mchorse.blockbuster.network.common.PacketOpenGui;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Client receiver for {@link PacketOpenGui} (roadmap P100) — the modern
 * replacement for Forge dispatching {@code getClientGuiElement} on the client.
 *
 * <p>Hops to the game thread (via {@link ClientMessageHandler}) and routes the
 * GUI id through {@link GuiHandler#route}, which looks up the id's registered
 * screen factory (installed by {@code GuiHandlerClient}). Unknown ids are
 * handled totally there (warn + no-op).</p>
 */
public class ClientHandlerOpenGui extends ClientMessageHandler<PacketOpenGui>
{
    @Override
    public void run(ClientPlayerEntity player, PacketOpenGui message)
    {
        GuiHandler.route(message.id, message.x, message.y, message.z);
    }
}
