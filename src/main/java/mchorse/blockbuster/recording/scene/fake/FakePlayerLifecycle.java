package mchorse.blockbuster.recording.scene.fake;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.mixin.PlayerManagerAccessor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * The scene fake player's login/logout dance (roadmap P241).
 *
 * <p>{@link FakePlayerFactory} builds the fake {@link ServerPlayerEntity};
 * this class is the other half — getting it <i>into</i> the running server and
 * back out again. Legacy did that with two one-liners:</p>
 *
 * <ul>
 *   <li>{@code RecordPlayer.checkAndSpawn} —
 *       {@code if (!realPlayer && !actor.isDead) playerList.playerLoggedIn((EntityPlayerMP) actor)};</li>
 *   <li>{@code RecordManager.stop} (kill branch, non-real actor) —
 *       {@code if (actor instanceof EntityPlayer) playerList.playerLoggedOut((EntityPlayerMP) actor)}.</li>
 * </ul>
 *
 * <h2>Why {@code onPlayerConnect} is not the port of {@code playerLoggedIn}</h2>
 *
 * <p>1.12.2's {@code PlayerList} had two entry points: the heavy
 * {@code initializeConnectionToPlayer} (builds the {@code NetHandlerPlayServer},
 * sends the whole join burst) and the light {@code playerLoggedIn} (list + uuid
 * map + tab-list broadcast + {@code worldserver.spawnEntity} + tab-list
 * backfill). Blockbuster called the <b>light</b> one — it had already built its
 * own fake net handler in {@code Scene.collectActors}. 1.20.4 merged both into
 * {@code PlayerManager.onPlayerConnect}, so calling that would construct a real
 * {@link net.minecraft.server.network.ServerPlayNetworkHandler} over the fake
 * connection and throw away {@link FakePlayerNetworkHandler}, after which every
 * join packet would pile up unsent in the dead connection's queue. Hence the
 * light half is reassembled here instead.</p>
 *
 * <h2>Totality</h2>
 *
 * <p>Both effect methods are total: any failure is logged and swallowed, never
 * propagated into a scene start/stop. A fake player that fails to log in simply
 * stays absent, which is what the tree did before P241.</p>
 */
public class FakePlayerLifecycle
{
    /**
     * Legacy guard of {@code RecordPlayer.checkAndSpawn}'s player branch:
     * {@code !realPlayer && !actor.isDead}, on a player actor. Pure, so the
     * decision is testable without a server.
     *
     * @param actor      the record player's actor.
     * @param realPlayer the {@code RecordPlayer.realPlayer} flag.
     */
    public static boolean shouldLogIn(LivingEntity actor, boolean realPlayer)
    {
        return actor instanceof ServerPlayerEntity && !realPlayer && !actor.isRemoved();
    }

    /**
     * Legacy guard of {@code RecordManager.stop}: only the kill branch, only a
     * non-real player actor, and (legacy {@code instanceof EntityPlayer}) only
     * a player entity. Pure.
     *
     * @param actor      the record player's actor.
     * @param realPlayer the {@code RecordPlayer.realPlayer} flag.
     * @param kill       the {@code RecordPlayer.kill} flag.
     */
    public static boolean shouldLogOut(LivingEntity actor, boolean realPlayer, boolean kill)
    {
        return kill && !realPlayer && actor instanceof ServerPlayerEntity;
    }

    /**
     * Port of {@code PlayerList.playerLoggedIn}, minus the parts that only make
     * sense for a client-backed player. Order follows legacy: list, uuid map,
     * tab-list add broadcast, world spawn.
     *
     * @return {@code true} when the player is now a member of the server's
     *         player list (including when it already was).
     */
    public static boolean logIn(ServerPlayerEntity player)
    {
        if (player == null)
        {
            return false;
        }

        try
        {
            MinecraftServer server = player.getServer();

            if (server == null)
            {
                return false;
            }

            PlayerManager manager = server.getPlayerManager();
            List<ServerPlayerEntity> players = players(manager);
            Map<UUID, ServerPlayerEntity> playerMap = playerMap(manager);

            if (players == null || playerMap == null)
            {
                return false;
            }

            boolean added = false;

            if (!players.contains(player))
            {
                players.add(player);
                added = true;
            }

            playerMap.put(player.getUuid(), player);

            /* Legacy broadcast the tab-list ADD_PLAYER entry to everyone —
             * scene fake players are visible in the player list on 1.12.2 and
             * stay visible here. */
            if (added)
            {
                manager.sendToAll(PlayerListS2CPacket.entryFromPlayer(Collections.singletonList(player)));
            }

            /* Legacy `worldserver.spawnEntity(playerIn)`; on 1.20.4 the player
             * spawn/track path is ServerWorld.onPlayerConnected. */
            if (player.getWorld() instanceof ServerWorld world)
            {
                if (world.getEntityById(player.getId()) != player)
                {
                    world.onPlayerConnected(player);
                }
            }

            return true;
        }
        catch (Exception e)
        {
            Blockbuster.LOGGER.warn("Failed to log in the scene fake player '" + name(player) + "'", e);

            return false;
        }
    }

    /**
     * Port of {@code PlayerList.playerLoggedOut} — 1.20.4 keeps this one whole
     * and public as {@link PlayerManager#remove(ServerPlayerEntity)}, so the
     * only thing left to do is call it.
     *
     * @return {@code true} when the removal ran.
     */
    public static boolean logOut(ServerPlayerEntity player)
    {
        if (player == null)
        {
            return false;
        }

        try
        {
            MinecraftServer server = player.getServer();

            if (server == null)
            {
                return false;
            }

            server.getPlayerManager().remove(player);

            return true;
        }
        catch (Exception e)
        {
            Blockbuster.LOGGER.warn("Failed to log out the scene fake player '" + name(player) + "'", e);

            return false;
        }
    }

    /** {@code null} when the accessor mixin did not apply (plain unit tests). */
    private static List<ServerPlayerEntity> players(PlayerManager manager)
    {
        return manager instanceof PlayerManagerAccessor accessor ? accessor.blockbuster$getPlayers() : null;
    }

    /** {@code null} when the accessor mixin did not apply (plain unit tests). */
    private static Map<UUID, ServerPlayerEntity> playerMap(PlayerManager manager)
    {
        return manager instanceof PlayerManagerAccessor accessor ? accessor.blockbuster$getPlayerMap() : null;
    }

    private static String name(Entity player)
    {
        return player instanceof PlayerEntity ? ((PlayerEntity) player).getGameProfile().getName() : String.valueOf(player);
    }
}
