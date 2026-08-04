package mchorse.metamorph.network.common;

import java.util.HashMap;
import java.util.Map;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.ForgeByteBufUtils;
import mchorse.mclib.network.IMessage;
import mchorse.metamorph.api.MorphSettings;
import net.minecraft.network.PacketByteBuf;

/**
 * The whole active morph-settings map, keyed by morph name, shipped to each
 * client on login (roadmap P55). Each value serializes via
 * {@link MorphSettings#toBytes(PacketByteBuf)} (P49 order).
 *
 * <p>Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/network/common/PacketSettings.java</p>
 */
public class PacketSettings implements IMessage
{
    public Map<String, MorphSettings> settings = new HashMap<String, MorphSettings>();

    public PacketSettings()
    {}

    public PacketSettings(Map<String, MorphSettings> settings)
    {
        this.settings.clear();
        this.settings.putAll(settings);
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        PacketByteBuf pbuf = buf instanceof PacketByteBuf ? (PacketByteBuf) buf : new PacketByteBuf(buf);

        for (int i = 0, c = buf.readInt(); i < c; i++)
        {
            String key = ForgeByteBufUtils.readUTF8String(buf);
            MorphSettings setting = new MorphSettings();

            setting.fromBytes(pbuf);
            this.settings.put(key, setting);
        }
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        PacketByteBuf pbuf = buf instanceof PacketByteBuf ? (PacketByteBuf) buf : new PacketByteBuf(buf);

        buf.writeInt(this.settings.size());

        for (Map.Entry<String, MorphSettings> setting : this.settings.entrySet())
        {
            ForgeByteBufUtils.writeUTF8String(buf, setting.getKey());
            setting.getValue().toBytes(pbuf);
        }
    }
}
