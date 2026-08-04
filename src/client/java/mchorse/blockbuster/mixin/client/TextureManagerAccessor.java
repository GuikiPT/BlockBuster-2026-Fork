package mchorse.blockbuster.mixin.client;

import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * S7/P87 — accessor over {@code TextureManager.textures}
 * ({@code Map<Identifier, AbstractTexture>}, verified with javap against the
 * loom-cache named jar).
 *
 * <p>Replaces legacy {@code mchorse.mclib.utils.ReflectionUtils.getTextures},
 * which reached the private vanilla texture map via runtime reflection. Every
 * 1.12.2 texture trick (GIF swap, URL insert, mipmap force, multiskin upload,
 * userTexture substitution) went through that map; the port routes them through
 * {@code mchorse.mclib.utils.ReflectionUtils} which is now backed by this
 * accessor.</p>
 */
@Mixin(TextureManager.class)
public interface TextureManagerAccessor
{
    @Accessor("textures")
    Map<Identifier, AbstractTexture> blockbuster$getTextures();
}
