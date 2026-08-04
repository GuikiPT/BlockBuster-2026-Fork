package mchorse.blockbuster.network.common.scene;

import io.netty.buffer.ByteBuf;
import mchorse.blockbuster.recording.scene.SceneLocation;
import mchorse.mclib.network.ForgeByteBufUtils;

/**
 * Record a replay into a scene (roadmap P131). Base + {@code String record} +
 * {@code int offset}. When the location is a scene the record is captured into
 * that scene's slot; otherwise it falls back to a standalone action recording.
 * 1:1 wire port of 1.12.2 {@code PacketSceneRecord.java}.
 */
public class PacketSceneRecord extends PacketScene
{
    public String record = "";
    public int offset;

    public PacketSceneRecord()
    {}

    public PacketSceneRecord(SceneLocation location, String record, int offset)
    {
        this(location, record);

        this.offset = offset;
    }

    public PacketSceneRecord(SceneLocation location, String record)
    {
        super(location);

        this.record = record;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        super.fromBytes(buf);

        this.record = ForgeByteBufUtils.readUTF8String(buf);
        this.offset = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        super.toBytes(buf);

        ForgeByteBufUtils.writeUTF8String(buf, this.record);
        buf.writeInt(this.offset);
    }
}
