package mchorse.blockbuster.network.common;

import io.netty.buffer.ByteBuf;
import mchorse.blockbuster.network.common.scene.PacketScene;
import mchorse.blockbuster.recording.scene.SceneLocation;
import mchorse.mclib.network.ForgeByteBufUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Playback-button config packet (roadmap P129).
 *
 * <p>1:1 wire port of 1.12.2 {@code network/common/PacketPlaybackButton.java}.
 * Server → client: opens the playback-button config GUI seeded with the item's
 * bound {@link SceneLocation}, its camera {@code mode} (0 none / 1 play-current
 * / 2 named profile), the {@code profile} filename, and the full list of
 * available scene filenames. Extends {@link PacketScene} so the location rides
 * the shared boolean-prefixed framing; the extra {@code mode} int, {@code
 * profile} string, and the {@code scenes} list (int count + strings) follow.</p>
 *
 * <p>Channel {@code blockbuster:playback_button}. The wire order —
 * {@code location} body, {@code mode}, {@code profile}, {@code scenes} — is a
 * contract and must match legacy byte-for-byte.</p>
 *
 * <p>Legacy source:
 * {@code blockbuster-1.12/src/main/java/mchorse/blockbuster/network/common/PacketPlaybackButton.java}.</p>
 */
public class PacketPlaybackButton extends PacketScene
{
    public int mode;
    public String profile = "";
    public List<String> scenes = new ArrayList<String>();

    public PacketPlaybackButton()
    {}

    public PacketPlaybackButton(SceneLocation location, int mode, String profile)
    {
        super(location);
        this.mode = mode;
        this.profile = profile;
    }

    public PacketPlaybackButton withScenes(List<String> scenes)
    {
        this.scenes.addAll(scenes);

        return this;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        super.fromBytes(buf);

        this.mode = buf.readInt();
        this.profile = ForgeByteBufUtils.readUTF8String(buf);

        int count = buf.readInt();

        for (int i = 0; i < count; i++)
        {
            this.scenes.add(ForgeByteBufUtils.readUTF8String(buf));
        }
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        super.toBytes(buf);

        buf.writeInt(this.mode);
        ForgeByteBufUtils.writeUTF8String(buf, this.profile);

        buf.writeInt(this.scenes.size());

        for (String scene : this.scenes)
        {
            ForgeByteBufUtils.writeUTF8String(buf, scene);
        }
    }
}
