package mchorse.metamorph.network;

import mchorse.mclib.network.AbstractDispatcher;
import mchorse.mclib.network.IMessage;
import mchorse.mclib.network.Side;
import mchorse.metamorph.Metamorph;
import mchorse.metamorph.network.common.PacketBlacklist;
import mchorse.metamorph.network.common.PacketMorphSpawnData;
import mchorse.metamorph.network.common.PacketSettings;
import mchorse.metamorph.network.common.creative.PacketAcquireMorph;
import mchorse.metamorph.network.common.creative.PacketClearAcquired;
import mchorse.metamorph.network.common.creative.PacketMorph;
import mchorse.metamorph.network.common.creative.PacketSyncMorph;
import mchorse.metamorph.network.common.survival.PacketAcquiredMorphs;
import mchorse.metamorph.network.common.survival.PacketAction;
import mchorse.metamorph.network.common.survival.PacketFavorite;
import mchorse.metamorph.network.common.survival.PacketKeybind;
import mchorse.metamorph.network.common.survival.PacketMorphPlayer;
import mchorse.metamorph.network.common.survival.PacketMorphState;
import mchorse.metamorph.network.common.survival.PacketRemoveMorph;
import mchorse.metamorph.network.common.survival.PacketSelectMorph;
import mchorse.metamorph.network.server.creative.ServerHandlerAcquireMorph;
import mchorse.metamorph.network.server.creative.ServerHandlerClearAcquired;
import mchorse.metamorph.network.server.creative.ServerHandlerMorph;
import mchorse.metamorph.network.server.creative.ServerHandlerSyncMorph;
import mchorse.metamorph.network.server.survival.ServerHandlerAction;
import mchorse.metamorph.network.server.survival.ServerHandlerFavorite;
import mchorse.metamorph.network.server.survival.ServerHandlerKeybind;
import mchorse.metamorph.network.server.survival.ServerHandlerRemoveMorph;
import mchorse.metamorph.network.server.survival.ServerHandlerSelectMorph;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Bundled Metamorph network dispatcher (roadmap P55) on the McLib S2
 * {@link AbstractDispatcher}.
 *
 * <p>Channel {@code "metamorph"}; the {@code register()} order below is the
 * frozen discriminator sequence, identical to legacy
 * {@code .tools/legacy-src/metamorph/.../network/Dispatcher.register()}. Both-side
 * registrations (morph, acquire, favorite, keybind, remove) collapse to one
 * Identifier with a receiver per side. CLIENT handlers are referenced by
 * <b>name</b> because they live in the split client source set (same convention
 * as {@code mchorse.aperture.network.Dispatcher}).</p>
 *
 * <p>This is a <b>new channel</b>: its {@code metamorph:*} identifiers are new
 * (no 1.12 discriminator-byte cross-version contract). They are ledgered in
 * {@link mchorse.mclib.network.ChannelLedger#METAMORPH_CHANNEL} and mirrored in
 * {@code plan/network-ledger.md}; {@code ChannelLedgerTest} pins them slot by
 * slot against this {@code register()} order (batch U-N — the earlier
 * "reported to the central ledger at merge" note never happened).</p>
 *
 * <p>Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/network/Dispatcher.java</p>
 */
public class Dispatcher
{
    private static final String CLIENT = "mchorse.metamorph.network.client.";

    public static final AbstractDispatcher DISPATCHER = new AbstractDispatcher(Metamorph.MOD_ID)
    {
        @Override
        public void register()
        {
            /* Action */
            register(PacketAction.class, ServerHandlerAction.class, Side.SERVER);

            /* Morphing */
            register(PacketMorph.class, CLIENT + "creative.ClientHandlerMorph", Side.CLIENT);
            register(PacketMorph.class, ServerHandlerMorph.class, Side.SERVER);
            register(PacketMorphPlayer.class, CLIENT + "survival.ClientHandlerMorphPlayer", Side.CLIENT);

            register(PacketAcquireMorph.class, CLIENT + "creative.ClientHandlerAcquireMorph", Side.CLIENT);
            register(PacketAcquireMorph.class, ServerHandlerAcquireMorph.class, Side.SERVER);
            register(PacketAcquiredMorphs.class, CLIENT + "survival.ClientHandlerAcquiredMorphs", Side.CLIENT);
            register(PacketSyncMorph.class, ServerHandlerSyncMorph.class, Side.SERVER);

            register(PacketSelectMorph.class, ServerHandlerSelectMorph.class, Side.SERVER);
            register(PacketClearAcquired.class, ServerHandlerClearAcquired.class, Side.SERVER);

            /* Morph state */
            register(PacketMorphState.class, CLIENT + "survival.ClientHandlerMorphState", Side.CLIENT);

            /* Managing morphs */
            register(PacketFavorite.class, CLIENT + "survival.ClientHandlerFavorite", Side.CLIENT);
            register(PacketFavorite.class, ServerHandlerFavorite.class, Side.SERVER);

            register(PacketKeybind.class, CLIENT + "survival.ClientHandlerKeybind", Side.CLIENT);
            register(PacketKeybind.class, ServerHandlerKeybind.class, Side.SERVER);

            register(PacketRemoveMorph.class, CLIENT + "survival.ClientHandlerRemoveMorph", Side.CLIENT);
            register(PacketRemoveMorph.class, ServerHandlerRemoveMorph.class, Side.SERVER);

            /* Syncing data */
            register(PacketBlacklist.class, CLIENT + "ClientHandlerBlacklist", Side.CLIENT);
            register(PacketSettings.class, CLIENT + "ClientHandlerSettings", Side.CLIENT);

            /* P56.1 port addition (no legacy slot): the morph ghost's spawn
             * payload, which Forge carried on the vanilla spawn packet through
             * IEntityAdditionalSpawnData. Appended after the legacy sequence so
             * the frozen order above is untouched. */
            register(PacketMorphSpawnData.class, CLIENT + "ClientHandlerMorphSpawnData", Side.CLIENT);
        }
    };

    private static boolean registered;

    /**
     * Send message to players who are tracking given entity
     */
    public static void sendToTracked(Entity entity, IMessage message)
    {
        DISPATCHER.sendToTracked(entity, message);
    }

    /**
     * Send message to given player
     */
    public static void sendTo(IMessage message, ServerPlayerEntity player)
    {
        DISPATCHER.sendTo(message, player);
    }

    /**
     * Send message to every connected player (legacy
     * {@code CommandMetamorph.broadcastPacket}, which walked
     * {@code PlayerList.getOnlinePlayerNames()} by hand). A no-op with a
     * warning when no server is running.
     */
    public static void sendToAll(IMessage message)
    {
        DISPATCHER.sendToAll(message);
    }

    /**
     * Send message to the server
     */
    public static void sendToServer(IMessage message)
    {
        DISPATCHER.sendToServer(message);
    }

    /**
     * Register all the networking messages and message handlers (idempotent —
     * mod init and headless tests may both call it).
     */
    public static synchronized void register()
    {
        if (registered)
        {
            return;
        }

        registered = true;

        DISPATCHER.register();
    }
}
