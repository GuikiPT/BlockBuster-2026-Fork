package mchorse.mclib.network.mclib.common;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.config.Config;
import mchorse.mclib.network.ForgeByteBufUtils;
import mchorse.mclib.network.IMessage;

/**
 * Full port of McLib 2.4.3's PacketConfig (roadmap P26) — the wire form of a
 * {@code Config} module. Wire: UTF8 {@code config.id} + {@code config.toBytes}
 * (categories → typed values via the {@code ConfigManager.TYPES} tokens; an
 * unknown Value class serializes an empty type token and is silently dropped
 * on read — total-reader rule) + {@code overwrite} flag.
 *
 * <p>Legacy source: .tools/legacy-src/mclib/build/sources/main/java/mchorse/mclib/network/mclib/common/PacketConfig.java</p>
 */
public class PacketConfig implements IMessage
{
    public Config config;
    public boolean overwrite;

    public PacketConfig()
    {}

    public PacketConfig(Config config)
    {
        this(config, false);
    }

    public PacketConfig(Config config, boolean overwrite)
    {
        this.config = config;
        this.overwrite = overwrite;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.config = new Config(ForgeByteBufUtils.readUTF8String(buf));
        this.config.fromBytes(buf);
        this.overwrite = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        ForgeByteBufUtils.writeUTF8String(buf, this.config.id);

        this.config.toBytes(buf);
        buf.writeBoolean(this.overwrite);
    }
}
