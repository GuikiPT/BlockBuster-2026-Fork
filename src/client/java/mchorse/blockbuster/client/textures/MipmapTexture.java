package mchorse.blockbuster.client.textures;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.blockbuster.utils.TextureUtils;
import net.minecraft.client.texture.ResourceTexture;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Mipmapped texture (roadmap P91) — port of
 * {@code mchorse.blockbuster.client.textures.MipmapTexture}.
 *
 * <p>Replaces a regular texture with a force-mipmapped version, used for OBJ
 * material textures and the P140 mipmap toggle. Legacy (fixed-function GL2) set
 * {@code GL14.GL_GENERATE_MIPMAP = GL_TRUE} so mipmaps were generated on upload.
 * That enum does not exist on the 1.20.4 core profile, so the port uploads
 * level 0 and then calls {@link GL30#glGenerateMipmap} explicitly — same
 * observable result.</p>
 *
 * <p><b>Legacy quirk preserved (deliberately):</b> the original set <em>both</em>
 * min and mag filter to {@code GL_NEAREST_MIPMAP_LINEAR}. {@code *_MIPMAP_*}
 * enums are invalid for the mag filter and drivers silently ignore them,
 * leaving nearest magnification. The port sets mag to {@code GL_NEAREST}
 * directly — the actual behavioral outcome — rather than reproducing the
 * invalid enum (which 1.20.4's stricter GL state validation could reject).</p>
 */
public class MipmapTexture extends ResourceTexture
{
    /**
     * Reproduce the legacy reorder as a direct {@link ByteBuffer} for GL upload
     * (the pure repack lives in {@link TextureUtils#bytesFromBuffer}).
     */
    public static ByteBuffer bytesFromBuffer(BufferedImage image)
    {
        byte[] bytes = TextureUtils.bytesFromBuffer(image);
        ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length);

        buffer.put(bytes);
        buffer.flip();

        return buffer;
    }

    public MipmapTexture(Identifier location)
    {
        super(location);
    }

    @Override
    public void load(ResourceManager resourceManager) throws IOException
    {
        super.load(resourceManager);

        InputStream stream = null;

        try
        {
            stream = resourceManager.open(this.location);

            BufferedImage image = ImageIO.read(stream);

            int w = image.getWidth();
            int h = image.getHeight();

            RenderSystem.bindTexture(this.getGlId());

            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MIN_LOD, 0);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LOD, 3);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 3);
            GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_LOD_BIAS, 0.0F);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST_MIPMAP_LINEAR);
            /* Legacy set this to GL_NEAREST_MIPMAP_LINEAR (invalid mag enum,
             * silently ignored → nearest). Set the real outcome. */
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, bytesFromBuffer(image));

            /* Core-profile replacement for GL_GENERATE_MIPMAP */
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        }
        finally
        {
            if (stream != null)
            {
                try
                {
                    stream.close();
                }
                catch (IOException e)
                {}
            }
        }
    }
}
