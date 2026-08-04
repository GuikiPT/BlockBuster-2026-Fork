package mchorse.blockbuster.network.client;

import mchorse.blockbuster.network.common.structure.PacketStructureList;
import mchorse.blockbuster_pack.BlockbusterFactory;
import mchorse.blockbuster_pack.BlockbusterSection;
import mchorse.blockbuster_pack.morphs.structure.StructureRenderer;
import mchorse.blockbuster_pack.morphs.structure.StructureRenderers;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Client handler for {@link PacketStructureList} (roadmap P162). Registers an
 * empty placeholder {@link StructureRenderer} for every unseen structure name
 * (so the render-time status machine can lazily request each), then feeds the
 * names into the {@code blockbuster_structures} creative picker category.
 */
public class ClientHandlerStructureList extends ClientMessageHandler<PacketStructureList>
{
    @Override
    public void run(ClientPlayerEntity player, PacketStructureList message)
    {
        for (String str : message.structures)
        {
            StructureRenderer renderer = StructureRenderers.STRUCTURES.get(str);

            if (renderer == null)
            {
                renderer = new StructureRenderer();
                StructureRenderers.STRUCTURES.put(str, renderer);
            }
        }

        /* S22 P237: feed the creative picker's blockbuster_structures category
         * — legacy Blockbuster.proxy.factory.section.addStructures(...). Null
         * only before the morph factory has registered. */
        BlockbusterSection section = BlockbusterFactory.section();

        if (section != null)
        {
            section.addStructures(message.structures);
        }
    }
}
