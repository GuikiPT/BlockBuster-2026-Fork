package mchorse.blockbuster.network.common.scene;

import io.netty.buffer.ByteBuf;
import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.recording.scene.Scene;
import mchorse.blockbuster.recording.scene.SceneLocation;
import mchorse.mclib.network.IMessage;
import net.minecraft.world.World;

/**
 * Abstract base of the scene packet family (roadmap P131). 1:1 wire port of
 * 1.12.2 {@code network/common/scene/PacketScene.java}.
 *
 * <p>Wire quirk (load-bearing): {@link #location} is initialized non-null
 * ({@code new SceneLocation()}), so the write-side {@code writeBoolean(location
 * != null)} is effectively always true — but the read-side boolean is still
 * honored for byte parity, and {@link SceneLocation#toByteBuf} writes two
 * <em>more</em> presence booleans (filename, embedded scene). Reading must
 * consume all three (see {@code SceneLocation}).</p>
 *
 * <p>{@link #get(World)} resolves the scene via
 * {@code CommonProxy.scenes.get(filename, world)} when {@code isScene()} — this
 * may lazily load the scene from disk server-side (matching legacy).</p>
 */
public abstract class PacketScene implements IMessage
{
    public SceneLocation location = new SceneLocation();

    public PacketScene()
    {}

    public PacketScene(SceneLocation location)
    {
        this.location = location;
    }

    public Scene get(World world)
    {
        if (this.location.isScene())
        {
            return CommonProxy.scenes.get(this.location.getFilename(), world);
        }

        return null;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        if (buf.readBoolean())
        {
            this.location.fromByteBuf(buf);
        }
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeBoolean(this.location != null);

        if (this.location != null)
        {
            this.location.toByteBuf(buf);
        }
    }
}
