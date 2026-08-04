package mchorse.blockbuster.recording.scene.fake;

import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.listener.PacketListener;

/**
 * A dead client connection for scene fake players.
 *
 * <p>1.12.2 kept a fake netty {@code NetworkManager} alive purely so
 * {@code EntityPlayerMP} construction and {@code NetHandlerPlayServer} would not
 * NPE (the five {@code fake/*} netty-stub classes —
 * {@code FakeContext}/{@code FakeChannel}/{@code FakeProtocol}/
 * {@code FakeFMLAttribute}/{@code FakeConfig}). On 1.20.4 that entire hack
 * collapses into a clientbound {@link ClientConnection} whose packet listener is
 * never attached and whose {@code send} is a no-op (see
 * {@link FakePlayerNetworkHandler}).</p>
 *
 * <p>Technique mirrored from BBS {@code SuperFakePlayerNetworkHandler}'s inner
 * {@code FakeClientConnection} (reference only — no BBS class is imported).</p>
 */
public class FakeClientConnection extends ClientConnection
{
    public FakeClientConnection()
    {
        super(NetworkSide.CLIENTBOUND);
    }

    @Override
    public void setPacketListener(PacketListener packetListener)
    {
        /* Never wire a listener — nothing is ever received on this connection. */
    }
}
