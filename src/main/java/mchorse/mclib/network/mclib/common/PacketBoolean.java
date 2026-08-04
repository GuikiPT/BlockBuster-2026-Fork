package mchorse.mclib.network.mclib.common;

import io.netty.buffer.ByteBuf;

/**
 * Full port of McLib 2.4.3's PacketBoolean (roadmap P26): a special handler
 * duo just for booleans, 5 bytes on the wire instead of a Java-serialized
 * object. The legacy "currently packets cannot be transported via inheritance
 * to handlers ... TODO check in port" is hereby resolved: on Fabric each
 * packet has its own Identifier so the SimpleNetworkWrapper restriction is
 * gone, but the separate class + compact wire format are KEPT for
 * diff-ability and byte economy.
 *
 * <p>Legacy source: .tools/legacy-src/mclib/build/sources/main/java/mchorse/mclib/network/mclib/common/PacketBoolean.java</p>
 */
public class PacketBoolean extends PacketAnswer<Boolean>
{
    public PacketBoolean()
    {}

    public PacketBoolean(int callbackID, boolean value)
    {
        super(callbackID, value);
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.callBackID = buf.readInt();
        this.answer = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeInt(this.callBackID);
        buf.writeBoolean(this.getValue());
    }
}
