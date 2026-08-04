package mchorse.blockbuster.aperture.network.common;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.IMessage;

/**
 * Blockbuster aperture-bridge packet (P182): empty client→server request for
 * the server's camera-profile names. The server handler replies with
 * Aperture's {@code PacketCameraProfileList(CameraAPI.getServerProfiles())} on
 * <b>Blockbuster's</b> dispatcher channel ({@code blockbuster:request_profiles}
 * / {@code blockbuster:camera_profile_list}, ledger slots 57/58) so the scene
 * playback GUI ({@code GuiPlayback}) can pick a server profile without opening
 * the full camera editor.
 *
 * <p><b>Registration is deferred:</b> the Blockbuster network dispatcher
 * ({@code mchorse.blockbuster.network.Dispatcher}) and its scene packets land
 * with S10/S11; the bridge handlers register on it there (legacy
 * {@code CameraHandler.registerApertureMessages}). This class ports the wire
 * format now so it is byte-locked ahead of that wiring.</p>
 *
 * <p>Legacy source: blockbuster-1.12/src/main/java/mchorse/blockbuster/aperture/network/common/PacketRequestProfiles.java</p>
 */
public class PacketRequestProfiles implements IMessage
{
    public PacketRequestProfiles()
    {}

    @Override
    public void fromBytes(ByteBuf buf)
    {}

    @Override
    public void toBytes(ByteBuf buf)
    {}
}
