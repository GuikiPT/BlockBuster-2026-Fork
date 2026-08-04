package mchorse.blockbuster.client.textures;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.api.formats.obj.OBJMaterial;
import mchorse.blockbuster.client.model.ModelCustom;
import mchorse.mclib.client.render.McLibRenderLayers;
import mchorse.metamorph.client.render.MorphRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.function.Function;

/**
 * S7/P91.1 — model-material texture plumbing.
 *
 * <p>Two jobs, both of them the glue legacy did inline in
 * {@code ModelOBJRenderer}:</p>
 *
 * <ol>
 *   <li><b>Force-replacement</b> ({@link #setup}): an OBJ material texture must
 *       be a {@link MipmapTexture}. Legacy reached into the vanilla texture map
 *       (via {@code ReflectionUtils.getTextures}), deleted whatever regular
 *       texture object was sitting under the material's identifier, loaded a
 *       {@code MipmapTexture} in its place and applied the per-material filter
 *       matrix. The port keeps the "operate on the real map" contract (P87) and
 *       closes the displaced object <b>exactly once</b> — a double
 *       {@code close()} on 1.20.4 is a native crash, unlike 1.12.2's silent
 *       {@code deleteGlTexture}.</li>
 *   <li><b>Per-group consumer selection</b> ({@link #pick}): 1.12.2 bound a
 *       texture per material group right before its display list and restored
 *       the entity skin with {@code RenderCustomModel.bindLastTexture()}
 *       afterwards. On 1.20.4 a texture is not "bound", it is part of the
 *       {@link RenderLayer} the geometry is emitted into — so the per-group
 *       bind becomes a per-group {@link VertexConsumer}, and the restore is
 *       structural (the next group simply picks its own consumer; the model's
 *       own skin stays whatever the caller passed in).</li>
 * </ol>
 *
 * <p>Both halves are pure over their seams ({@code Map} + factory, {@code
 * Consumers} + fallback) so the whole decision tree is headless-testable; the
 * live-client entry points are the thin boundary.</p>
 *
 * Legacy source: blockbuster-1.12/.../client/model/ModelOBJRenderer.java (setupTexture/renderDisplayList)
 */
public final class MaterialTextures
{
    private MaterialTextures()
    {}

    /* --------------------------------------------------------------------- */
    /* Force-replacement (legacy setupTexture)                               */
    /* --------------------------------------------------------------------- */

    /**
     * Legacy {@code ModelOBJRenderer.setupTexture} map surgery.
     *
     * <p>Steps, in legacy order: a present-but-not-mipmapped entry is removed
     * from the map and released; an absent entry is (re)created through
     * {@code factory} and put back. A factory that throws is logged and leaves
     * the slot empty — legacy printed the stack trace and carried on with
     * {@code loaded == false}, which is also the totality rule here.</p>
     *
     * @return legacy's {@code loaded} flag: whether the identifier now holds a
     *         {@link MipmapTexture} (it selects the mipmapped half of the filter
     *         matrix).
     */
    public static boolean setup(Map<Identifier, AbstractTexture> map, Function<Identifier, AbstractTexture> factory, Identifier id)
    {
        if (map == null || id == null)
        {
            return false;
        }

        AbstractTexture texture = map.get(id);

        if (texture != null && !(texture instanceof MipmapTexture))
        {
            /* remove-then-close: the entry is gone before the close, so a
             * second setup() on the same material cannot close it twice. */
            AbstractTexture removed = map.remove(id);

            texture = null;

            if (removed != null)
            {
                try
                {
                    removed.close();
                }
                catch (Exception e)
                {
                    Blockbuster.LOGGER.warn("Failed to release the texture replaced by a mipmapped material texture '" + id + "'", e);
                }
            }
        }

        if (texture == null && factory != null)
        {
            try
            {
                texture = factory.apply(id);

                if (texture != null)
                {
                    map.put(id, texture);
                }
            }
            catch (Exception e)
            {
                /* Legacy: "An error occurred during loading manually a mipmap'd
                 * texture '…'" + printStackTrace. */
                Blockbuster.LOGGER.warn("An error occurred during loading manually a mipmap'd texture '" + id + "'", e);

                texture = null;
            }
        }

        return texture instanceof MipmapTexture;
    }

    /**
     * Legacy per-material filter selection:
     * {@code min = linear ? (loaded ? LINEAR_MIPMAP_LINEAR : LINEAR)
     * : (loaded ? NEAREST_MIPMAP_LINEAR : NEAREST)}, {@code mag = linear ?
     * LINEAR : NEAREST} — the same matrix as the P140 toggle
     * ({@link TextureRegistry#filterEnums}) with {@code loaded} standing in for
     * "has mipmaps".
     *
     * @return {@code {minFilter, magFilter}}
     */
    public static int[] filterEnums(OBJMaterial material, boolean loaded)
    {
        return TextureRegistry.filterEnums(material != null && material.linear, loaded);
    }

    /**
     * Live-client half of {@link #setup}: force the material's texture to a
     * mipmapped one and apply its filter matrix. A no-op without a client
     * (headless tests, dedicated server) — the model still bakes, it just never
     * touches GL.
     *
     * @return the {@code loaded} flag, {@code false} when there is no client.
     */
    public static boolean setup(OBJMaterial material)
    {
        if (material == null || !material.useTexture || material.texture == null)
        {
            return false;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null)
        {
            return false;
        }

        try
        {
            Identifier id = material.texture.toIdentifier();
            TextureRegistry registry = TextureRegistry.get();

            boolean loaded = setup(registry.map(), (location) ->
            {
                MipmapTexture texture = new MipmapTexture(location);

                try
                {
                    texture.load(mc.getResourceManager());
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }

                return texture;
            }, id);

            /* Legacy applied the matrix right after the (re)load, with `loaded`
             * standing in for the mipmap half. */
            registry.setFilter(id, material.linear, loaded);

            return loaded;
        }
        catch (Throwable t)
        {
            /* Total: a material whose texture cannot be set up renders with
             * whatever the texture manager resolves, never crashes the model. */
            Blockbuster.LOGGER.warn("Failed to set up the material texture for '" + material.name + "'", t);

            return false;
        }
    }

    /* --------------------------------------------------------------------- */
    /* Per-group consumer selection (legacy renderDisplayList binds)         */
    /* --------------------------------------------------------------------- */

    /**
     * The per-group bind seam: "give me the buffer that draws with this
     * texture". Null means no per-group selection is available and everything
     * goes into the caller's consumer (the model's own skin) — which is exactly
     * what legacy did for groups with no material texture.
     */
    public interface Consumers
    {
        VertexConsumer get(Identifier texture);
    }

    /**
     * The ambient per-group selector for the current morph render frame, or
     * {@code null} outside one. The frame's {@link VertexConsumerProvider} is
     * the same one the caller drew the model's own skin from, so a group that
     * opts out lands in the same batch it always did.
     *
     * <p>The keying blend is read at <b>pick</b> time, not here: legacy's
     * {@code ModelCustom.render} set the blend equation around the limb pass and
     * the per-group texture binds inside it inherited it, so a keyed morph keyed
     * its material groups too. {@link ModelCustom#isKeyingFrame()} is that
     * ambient state.</p>
     */
    public static Consumers ambient()
    {
        MorphRenderContext context = MorphRenderContext.current();
        VertexConsumerProvider consumers = context == null ? null : context.consumers;

        if (consumers == null)
        {
            return null;
        }

        return (texture) -> consumers.getBuffer(McLibRenderLayers.model(texture, ModelCustom.isKeyingFrame()));
    }

    /**
     * Choose the consumer a material group emits into: the group's own texture
     * when there is one and a selector to build it from, the caller's consumer
     * otherwise.
     */
    public static VertexConsumer pick(Consumers consumers, Identifier texture, VertexConsumer fallback)
    {
        if (consumers == null || texture == null)
        {
            return fallback;
        }

        VertexConsumer consumer = consumers.get(texture);

        return consumer == null ? fallback : consumer;
    }
}
