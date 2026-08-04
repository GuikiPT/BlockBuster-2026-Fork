package mchorse.blockbuster.network.common;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.ForgeByteBufUtils;
import mchorse.mclib.network.IMessage;
import net.minecraft.text.Text;

/**
 * Recording-overlay caption push (roadmap P116) — 1:1 wire port of the 2.7.2
 * packet: a bool-prefixed nullable {@link Text} serialized as JSON.
 *
 * <p>Yarn 1.20.4 maps Forge's {@code ITextComponent.Serializer.componentToJson}
 * / {@code jsonToComponent} to {@code Text.Serialization.toJsonString(Text)} /
 * {@code fromJson(String)} (verified via javap).</p>
 */
public class PacketCaption implements IMessage
{
    public Text caption;

    public PacketCaption()
    {}

    public PacketCaption(Text caption)
    {
        this.caption = caption;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        if (buf.readBoolean())
        {
            this.caption = Text.Serializer.fromJson(ForgeByteBufUtils.readUTF8String(buf));
        }
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeBoolean(this.caption != null);

        if (this.caption != null)
        {
            ForgeByteBufUtils.writeUTF8String(buf, Text.Serializer.toJson(this.caption));
        }
    }
}
