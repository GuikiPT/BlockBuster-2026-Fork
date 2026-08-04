package mchorse.blockbuster.network.common.guns;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.IMessage;

/**
 * Zoom on/off command trigger (P196; legacy slot 40 S —
 * {@code blockbuster:zoom_command}).
 *
 * <p>Entity id + a boolean selecting the stored zoom-on/zoom-off command.
 * <b>Not OP-gated</b> (legacy behaviour): the stored command runs verbatim as
 * the player — preserved, flagged in the S19 permissions sweep. 1:1 port of
 * 2.7.2's {@code PacketZoomCommand}.</p>
 */
public class PacketZoomCommand implements IMessage
{
    public int entity;
    public boolean zoomOn;

    public PacketZoomCommand()
    {}

    public PacketZoomCommand(int entity, boolean zoomOn)
    {
        this.entity = entity;
        this.zoomOn = zoomOn;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.entity = buf.readInt();
        this.zoomOn = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeInt(this.entity);
        buf.writeBoolean(this.zoomOn);
    }
}
