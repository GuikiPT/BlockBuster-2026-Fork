package mchorse.metamorph.client.render;

import java.util.Map;
import java.util.function.Consumer;

import mchorse.mclib.utils.ReflectionUtils;
import mchorse.mclib.utils.resources.ResourceLocation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;

/**
 * {@code EntityMorph.userTexture} substitution (roadmap P54/P80.2).
 *
 * <p>Legacy comment, kept because it is still the honest description: "This is
 * pretty ugly, but it's the only way to replace entity's textures". A vanilla
 * entity renderer picks its own texture from the entity; the only seam that
 * does not need one mixin per renderer is the texture manager's id → texture
 * map. Swap the user texture's object in under the mob's texture id, draw, put
 * the mob's object back.</p>
 *
 * <p><b>The one thing 1.20.4 changes</b> is <i>when</i> the bind happens.
 * 1.12.2 bound inside the draw call, so legacy could restore immediately after
 * {@code renderEntity} returned. Here the entity render only fills a buffer and
 * the bind happens when that buffer is flushed — after the restore, i.e. never
 * with the swap in place. So the in-world path flushes the immediate consumer
 * before restoring ({@link #flush}); the GUI path does not need to, because
 * {@code GuiUtils.drawEntityOnScreen} already ends with {@code DrawContext.draw()}.</p>
 *
 * <p>The map surgery itself is pure over a {@code Map} + a "make sure this id is
 * loaded" callback, so the whole legacy decision tree (no user texture, no mob
 * texture, user == mob, user not loadable, mob not loadable) is headless-testable
 * without a GL context.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/api/morphs/EntityMorph.java (replaceUserTexture/restoreMobTexture)
 */
public class UserTextureSwap
{
    /**
     * The mob texture object displaced by the swap. Non-null exactly while a
     * swap is in effect — legacy's {@code lastTexture} field, and its restore
     * gate.
     */
    private AbstractTexture lastTexture;

    /** The id whose entry was overwritten, so the restore targets the same key. */
    private Identifier swappedId;

    /**
     * Put the user texture's object under the mob texture's id.
     *
     * @return whether a swap is now in effect (and so whether {@link
     *         #restore(Map)} has anything to do).
     */
    public boolean replace(Map<Identifier, AbstractTexture> map, Consumer<Identifier> loader, Identifier userTexture, Identifier mobTexture)
    {
        if (map == null || userTexture == null || mobTexture == null || userTexture.equals(mobTexture))
        {
            return false;
        }

        AbstractTexture object = map.get(userTexture);

        if (object == null && loader != null)
        {
            loader.accept(userTexture);
            object = map.get(userTexture);
        }

        if (object == null)
        {
            return false;
        }

        AbstractTexture mob = map.get(mobTexture);

        if (mob == null && loader != null)
        {
            loader.accept(mobTexture);
            mob = map.get(mobTexture);
        }

        if (mob == null)
        {
            /* Legacy bailed here too: with nothing to restore later, swapping
             * would leak the user texture onto the mob's id for good. */
            return false;
        }

        this.lastTexture = mob;
        this.swappedId = mobTexture;
        map.put(mobTexture, object);

        return true;
    }

    public void restore(Map<Identifier, AbstractTexture> map)
    {
        if (this.lastTexture == null)
        {
            return;
        }

        if (map != null && this.swappedId != null)
        {
            map.put(this.swappedId, this.lastTexture);
        }

        this.lastTexture = null;
        this.swappedId = null;
    }

    public boolean isSwapped()
    {
        return this.lastTexture != null;
    }

    /* --------------------------------------------------------------------- */
    /* Live client bindings                                                  */
    /* --------------------------------------------------------------------- */

    /**
     * Convenience over the live {@code TextureManager.textures} map, with
     * {@code bindTexture} as the "make sure this id is loaded" callback (which
     * is exactly what legacy used, for the same reason: {@code bindTexture}
     * registers a resource texture for an id the manager has never seen).
     */
    public boolean replace(ResourceLocation userTexture, Identifier mobTexture)
    {
        if (userTexture == null)
        {
            return false;
        }

        TextureManager manager = textureManager();

        if (manager == null)
        {
            return false;
        }

        return this.replace(ReflectionUtils.getTextures(manager), manager::bindTexture, userTexture.toIdentifier(), mobTexture);
    }

    public void restore()
    {
        if (this.lastTexture == null)
        {
            return;
        }

        TextureManager manager = textureManager();

        this.restore(manager == null ? null : ReflectionUtils.getTextures(manager));
    }

    /**
     * Force the pending geometry out while the swap is still installed. Only
     * meaningful for an {@link VertexConsumerProvider.Immediate}; anything else
     * (a GUI draw context's provider, a test double) either flushes itself or
     * never batched in the first place.
     */
    public static void flush(VertexConsumerProvider consumers)
    {
        if (consumers instanceof VertexConsumerProvider.Immediate immediate)
        {
            immediate.draw();
        }
    }

    private static TextureManager textureManager()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        return mc == null ? null : mc.getTextureManager();
    }
}
