package mchorse.mclib.utils;

import mchorse.mclib.network.AbstractDispatcher;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Collections;
import java.util.List;

/**
 * Bundled shim of McLib 2.4.3's {@code mchorse.mclib.utils.ForgeUtils}
 * (roadmap S1 utils / S16 P189). Legacy code (notably
 * {@code mchorse.blockbuster.audio.AudioHandler}) called
 * {@code ForgeUtils.getServerPlayers()} to broadcast to every connected player.
 *
 * <p>1.12.2 reached the player list through
 * {@code FMLCommonHandler.instance().getMinecraftServerInstance()
 * .getPlayerList().getPlayers()}. On Fabric the running server is captured by
 * the S2 {@link AbstractDispatcher#setServer(MinecraftServer)} lifecycle hook
 * (mod entrypoint), so this reads {@code AbstractDispatcher.getServer()} to keep
 * mclib decoupled from Blockbuster's {@code CommonProxy}. Returns an empty list
 * when no server is bound (headless tests / logical client), matching the
 * defensive legacy behavior.</p>
 */
public class ForgeUtils
{
    /**
     * @return every player currently on the logical server, or an empty list
     *         when no server is running.
     */
    public static List<ServerPlayerEntity> getServerPlayers()
    {
        MinecraftServer server = AbstractDispatcher.getServer();

        if (server == null)
        {
            return Collections.emptyList();
        }

        return server.getPlayerManager().getPlayerList();
    }
}
