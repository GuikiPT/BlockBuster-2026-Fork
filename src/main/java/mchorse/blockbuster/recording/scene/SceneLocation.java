package mchorse.blockbuster.recording.scene;

import java.util.Objects;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.ForgeByteBufUtils;

/**
 * Scene location data class.
 *
 * <p>This bad boy allows to unify director blocks and scene identifier into one
 * structure.</p>
 *
 * <p>Legacy source:
 * {@code blockbuster-1.12/src/main/java/mchorse/blockbuster/recording/scene/SceneLocation.java}.
 * The boolean-prefixed wire framing is preserved verbatim: {@code toByteBuf}
 * writes {@code (filename != null)} + optional string, then {@code (scene !=
 * null)} + optional full {@code Scene}. {@code fromByteBuf} resets
 * {@code filename = null} first and only instantiates the embedded scene when
 * {@code isScene()} is true at read time.</p>
 */
public class SceneLocation
{
    private Scene scene;
    private String filename;

    public SceneLocation()
    {}

    public SceneLocation(Scene scene)
    {
        this.scene = scene;
        this.filename = scene.getId();
    }

    public SceneLocation(String filename)
    {
        this.filename = filename;
    }

    public Scene getScene()
    {
        return this.scene;
    }

    public String getFilename()
    {
        return this.filename;
    }

    public int getType()
    {
        return this.isEmpty() ? 0 : 1;
    }

    public boolean isEmpty()
    {
        return !this.isScene();
    }

    public boolean isScene()
    {
        return this.filename != null && !this.filename.isEmpty();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof SceneLocation)
        {
            SceneLocation location = (SceneLocation) obj;

            if (this.getType() == location.getType())
            {
                return Objects.equals(this.filename, location.filename);
            }
        }

        return super.equals(obj);
    }

    public SceneLocation copyEmpty()
    {
        if (this.isScene())
        {
            return new SceneLocation(this.getFilename());
        }

        return new SceneLocation();
    }

    public void fromByteBuf(ByteBuf buf)
    {
        this.filename = null;

        if (buf.readBoolean())
        {
            this.filename = ForgeByteBufUtils.readUTF8String(buf);
        }

        if (buf.readBoolean())
        {
            this.scene = this.isScene() ? new Scene() : null;

            if (this.scene != null)
            {
                this.scene.fromBuf(buf);
            }
        }
    }

    public void toByteBuf(ByteBuf buf)
    {
        buf.writeBoolean(this.filename != null);

        if (this.filename != null)
        {
            ForgeByteBufUtils.writeUTF8String(buf, this.filename);
        }

        buf.writeBoolean(this.scene != null);

        if (this.scene != null)
        {
            this.scene.toBuf(buf);
        }
    }
}
