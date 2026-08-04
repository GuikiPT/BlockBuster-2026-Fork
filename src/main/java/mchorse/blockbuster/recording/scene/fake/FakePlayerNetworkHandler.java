package mchorse.blockbuster.recording.scene.fake;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * No-op play network handler for scene fake players.
 *
 * <p>Replaces the legacy {@code NetHandlerPlayServer} that Blockbuster 1.12.2
 * hand-built over a {@code FakeContext}/{@code NetworkManager}. Every outbound
 * packet is dropped, so a fake player never tries to talk to a client that does
 * not exist.</p>
 *
 * <p>Technique mirrored from BBS {@code SuperFakePlayerNetworkHandler}
 * (reference only — no BBS class is imported).</p>
 */
public class FakePlayerNetworkHandler extends ServerPlayNetworkHandler
{
    public FakePlayerNetworkHandler(ServerPlayerEntity player)
    {
        super(player.getServer(), new FakeClientConnection(), player, ConnectedClientData.createDefault(player.getGameProfile()));
    }

    @Override
    public void send(Packet<?> packet, @Nullable PacketCallbacks callbacks)
    {
        /* Drop every packet — there is no client behind this handler. */
    }
}
