package mchorse.mclib.utils;

import mchorse.blockbuster.mixin.client.TextureManagerAccessor;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;

import java.util.Map;

/**
 * Facade kept for diff-ability against {@code .tools/legacy-src/mclib}
 * (roadmap P87).
 *
 * <p>Legacy {@code ReflectionUtils} located the private
 * {@code Map<ResourceLocation, ITextureObject>} inside {@code TextureManager}
 * heuristically at runtime (first non-static {@code Map} field whose first key
 * is a {@code ResourceLocation}) and exposed it via
 * {@code getTextures(TextureManager)}. On 1.20.4 the reflection is replaced by
 * the compile-time {@link TextureManagerAccessor} accessor mixin — no runtime
 * field scanning — but the method name/shape are preserved so bundled call
 * sites read like the originals.</p>
 */
public class ReflectionUtils
{
    /**
     * The live vanilla {@code TextureManager.textures} map
     * ({@code Map<Identifier, AbstractTexture>}). Mutating it (put / remove /
     * alias) is exactly what the legacy texture tricks did.
     */
    public static Map<Identifier, AbstractTexture> getTextures(TextureManager manager)
    {
        return ((TextureManagerAccessor) (Object) manager).blockbuster$getTextures();
    }
}
