package mchorse.blockbuster.network.client;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.network.common.structure.PacketStructure;
import mchorse.blockbuster_pack.morphs.structure.StructureData;
import mchorse.blockbuster_pack.morphs.structure.StructureRenderer;
import mchorse.blockbuster_pack.morphs.structure.StructureRenderers;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Client handler for {@link PacketStructure} (roadmap P162). Runs on the client
 * game thread (the {@link ClientMessageHandler} base already hops off the netty
 * thread, replacing the legacy {@code addScheduledTask}).
 *
 * <p>A {@code null} tag is the server's hot-reload <b>deletion</b> notice —
 * delete the cached renderer so the next render re-requests fresh geometry.
 * Otherwise decode the template into a {@link StructureData} (routing its
 * palette through the P71 shim) and replace the cached renderer, deleting any
 * previous baked geometry. Total by contract — a bad template logs and is
 * dropped, never crashes.</p>
 */
public class ClientHandlerStructure extends ClientMessageHandler<PacketStructure>
{
    @Override
    public void run(ClientPlayerEntity player, PacketStructure message)
    {
        try
        {
            if (message.tag == null)
            {
                StructureRenderer renderer = StructureRenderers.STRUCTURES.get(message.name);

                if (renderer != null)
                {
                    renderer.delete();
                }

                return;
            }

            StructureData data = StructureData.parse(message.tag);
            StructureRenderer renderer = new StructureRenderer(data);
            StructureRenderer old = StructureRenderers.STRUCTURES.remove(message.name);

            if (old != null)
            {
                old.delete();
            }

            StructureRenderers.STRUCTURES.put(message.name, renderer);
        }
        catch (Exception e)
        {
            Blockbuster.LOGGER.warn("Failed to bake structure \"{}\"", message.name, e);
        }
    }
}
