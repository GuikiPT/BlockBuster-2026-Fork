package mchorse.blockbuster.network.server;

import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.structure.PacketStructureList;
import mchorse.blockbuster.network.common.structure.PacketStructureListRequest;
import mchorse.mclib.network.ServerMessageHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;

/**
 * Server handler for {@link PacketStructureListRequest} (roadmap P162) — replies
 * with the current {@link PacketStructureList} built from
 * {@link ServerHandlerStructureRequest#getAllStructures()}. 1:1 with 2.7.2.
 */
public class ServerHandlerStructureListRequest extends ServerMessageHandler<PacketStructureListRequest>
{
    @Override
    public void run(ServerPlayerEntity player, PacketStructureListRequest message)
    {
        if (ServerHandlerStructureRequest.saveRoot == null && player.getServer() != null)
        {
            final MinecraftServer server = player.getServer();

            ServerHandlerStructureRequest.saveRoot = () -> server.getSavePath(WorldSavePath.ROOT).toFile();
        }

        Dispatcher.sendTo(new PacketStructureList(ServerHandlerStructureRequest.getAllStructures()), player);
    }
}
