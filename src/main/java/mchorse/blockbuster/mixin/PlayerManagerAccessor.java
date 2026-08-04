package mchorse.blockbuster.mixin;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for {@link PlayerManager}'s two membership collections (roadmap
 * P241).
 *
 * <p>1.12.2 had a public {@code PlayerList.playerLoggedIn(EntityPlayerMP)} that
 * scene fake players were pushed through
 * ({@code RecordPlayer.checkAndSpawn}). 1.20.4 folded that method's body into
 * {@code PlayerManager.onPlayerConnect(ClientConnection, ServerPlayerEntity,
 * ConnectedClientData)}, which is the equivalent of 1.12.2's much heavier
 * {@code initializeConnectionToPlayer} — it builds a real
 * {@code ServerPlayNetworkHandler} over the passed connection and would
 * overwrite the fake player's {@code FakePlayerNetworkHandler}. So the port
 * re-assembles only the {@code playerLoggedIn} half by hand
 * ({@code mchorse.blockbuster.recording.scene.fake.FakePlayerLifecycle}), and
 * that needs the two private collections the vanilla method mutates.</p>
 *
 * <p>The counterpart, {@code playerLoggedOut}, maps 1:1 onto the still-public
 * {@link PlayerManager#remove(ServerPlayerEntity)} and needs no accessor.</p>
 */
@Mixin(PlayerManager.class)
public interface PlayerManagerAccessor
{
    @Accessor("players")
    List<ServerPlayerEntity> blockbuster$getPlayers();

    @Accessor("playerMap")
    Map<UUID, ServerPlayerEntity> blockbuster$getPlayerMap();
}
