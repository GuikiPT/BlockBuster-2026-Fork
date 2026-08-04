package mchorse.blockbuster.client.commands;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.client.model.parsing.ModelExtrudedLayer;
import mchorse.blockbuster.client.textures.TextureRegistry;
import mchorse.blockbuster.commands.model.ITextureRegistryOps;
import mchorse.mclib.utils.resources.ResourceLocation;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Client-side {@link ITextureRegistryOps} for {@code /model report} and
 * {@code /model clear} (roadmap P73, completed against the S7 runtime texture
 * registry, P87).
 *
 * <ul>
 *   <li>{@link #reportStatus} — legacy compared
 *       {@code Minecraft.getMinecraft().renderEngine.getTexture(location)}
 *       against {@code TextureUtil.MISSING_TEXTURE}: {@code null} &rarr; no
 *       suffix, the sentinel &rarr; {@code ", loaded but missing"}, anything
 *       else &rarr; {@code ", loaded"}. The port reads the live
 *       {@code TextureManager.textures} map through {@link TextureRegistry}
 *       (<em>not</em> 1.20.4's {@code getTexture}, which registers a
 *       {@code ResourceTexture} for unknown ids as a side effect and would make
 *       {@code ABSENT} unreachable) and compares against
 *       {@link TextureRegistry#missingTexture()}.</li>
 *   <li>{@link #clearTextures} — legacy evicted + GL-deleted every texture whose
 *       domain is a Blockbuster dynamic-texture domain
 *       ({@code c.s}/{@code s&b}/{@code b.a}/{@code http}/{@code https}) and
 *       whose path starts with the prefix, calling
 *       {@link ModelExtrudedLayer#clearByTexture} per evicted key when a prefix
 *       was given and {@link ModelExtrudedLayer#clear()} wholesale when it was
 *       not.</li>
 * </ul>
 *
 * <p>Both are client-only, exactly as in 1.12.2: {@code /model} was a
 * {@code ClientCommandHandler} command, so the status query never round-trips to
 * the server — it reports what <em>this</em> client has loaded, and on a
 * dedicated server the command is still handled locally.</p>
 *
 * <p>Legacy sources:
 * {@code blockbuster-1.12/.../commands/model/SubCommandModelReport.java} +
 * {@code .../SubCommandModelClear.java}.</p>
 */
public class ClientTextureRegistryOps implements ITextureRegistryOps
{
    public static final ClientTextureRegistryOps INSTANCE = new ClientTextureRegistryOps();

    @Override
    public Status reportStatus(ResourceLocation location)
    {
        if (location == null)
        {
            return Status.ABSENT;
        }

        try
        {
            return TextureRegistry.get().status(location.toIdentifier(), TextureRegistry.missingTexture());
        }
        catch (Exception e)
        {
            /* Total: a report must never take down the command. */
            Blockbuster.LOGGER.warn("Failed to query the texture status of " + location, e);

            return Status.ABSENT;
        }
    }

    @Override
    public void clearTextures(String prefix)
    {
        String path = prefix == null ? "" : prefix;

        try
        {
            AbstractTexture missing = TextureRegistry.missingTexture();
            List<Identifier> removed = TextureRegistry.get().clearDynamic(path, missing);

            if (!path.isEmpty())
            {
                for (Identifier id : removed)
                {
                    ModelExtrudedLayer.clearByIdentifier(id);
                }
            }
        }
        catch (Exception e)
        {
            /* Total: keep going to the wholesale extruded-layer clear below,
             * which is what the user asked for either way. */
            Blockbuster.LOGGER.warn("Failed to clear Blockbuster's dynamic textures", e);
        }

        if (path.isEmpty())
        {
            ModelExtrudedLayer.clear();
        }
    }
}
