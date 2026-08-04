package mchorse.blockbuster.client.textures;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.blockbuster_pack.morphs.ImageMorph;
import mchorse.mclib.utils.resources.ResourceLocation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;

/**
 * Client implementation of {@link ImageMorph.ITextureSizeProvider} (roadmap
 * P54/P159): the pixel dimensions of a loaded texture.
 *
 * <p>Legacy asked OpenGL directly —
 * {@code glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH)} on
 * whatever texture happened to be bound, which is why {@code ImageMorph.getWidth()}
 * had no argument and only made sense immediately after
 * {@code GifTexture.bindTexture}. That query still exists in the core profile, so
 * the same answer is available; the only change is that the texture is named
 * rather than ambient, and it is bound here for the duration of the query.</p>
 *
 * <p>Textures we build ourselves ({@link NativeImageBackedTexture} — the GIF
 * frames, the texture-manager uploads) carry their {@link
 * net.minecraft.client.texture.NativeImage} on the CPU side, so those skip GL
 * entirely. Everything else (resource-pack and skin textures) takes the GL
 * query. Both paths answer {@code 0} when there is no render thread, no texture
 * manager or no such texture — and {@code ImageMorph} treats a zero-sized
 * texture as "draw nothing", the same as an unset one.</p>
 */
public final class TextureSizes implements ImageMorph.ITextureSizeProvider
{
    private static final int WIDTH = GL11.GL_TEXTURE_WIDTH;
    private static final int HEIGHT = GL11.GL_TEXTURE_HEIGHT;

    /** Install as the morph-side seam. Idempotent. */
    public static void install()
    {
        ImageMorph.textureSizeProvider = new TextureSizes();
    }

    @Override
    public int getWidth(ResourceLocation texture)
    {
        return texture == null ? 0 : width(texture.toIdentifier());
    }

    @Override
    public int getHeight(ResourceLocation texture)
    {
        return texture == null ? 0 : height(texture.toIdentifier());
    }

    public static int width(Identifier texture)
    {
        return size(texture, WIDTH);
    }

    public static int height(Identifier texture)
    {
        return size(texture, HEIGHT);
    }

    private static int size(Identifier texture, int parameter)
    {
        if (texture == null || !RenderSystem.isOnRenderThread())
        {
            return 0;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        TextureManager textures = mc == null ? null : mc.getTextureManager();

        if (textures == null)
        {
            return 0;
        }

        AbstractTexture object = textures.getOrDefault(texture, null);

        if (object instanceof NativeImageBackedTexture)
        {
            NativeImageBackedTexture backed = (NativeImageBackedTexture) object;

            if (backed.getImage() != null)
            {
                return parameter == WIDTH ? backed.getImage().getWidth() : backed.getImage().getHeight();
            }
        }

        if (object == null)
        {
            /* Not registered yet: getTexture registers and loads it, which is
             * what the legacy bind did too (bindTexture loaded on demand). */
            object = textures.getTexture(texture);
        }

        int id = object == null ? 0 : object.getGlId();

        if (id <= 0)
        {
            return 0;
        }

        GlStateManager._bindTexture(id);

        return GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, parameter);
    }
}
