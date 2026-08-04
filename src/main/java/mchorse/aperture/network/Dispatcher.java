package mchorse.aperture.network;

import mchorse.aperture.Aperture;
import mchorse.aperture.network.common.PacketAperture;
import mchorse.aperture.network.common.PacketCameraProfile;
import mchorse.aperture.network.common.PacketCameraProfileList;
import mchorse.aperture.network.common.PacketCameraReset;
import mchorse.aperture.network.common.PacketCameraState;
import mchorse.aperture.network.common.PacketLoadCameraProfile;
import mchorse.aperture.network.common.PacketRemoveCameraProfile;
import mchorse.aperture.network.common.PacketRenameCameraProfile;
import mchorse.aperture.network.common.PacketRequestCameraProfiles;
import mchorse.aperture.network.server.ServerHandlerCameraProfile;
import mchorse.aperture.network.server.ServerHandlerCameraReset;
import mchorse.aperture.network.server.ServerHandlerLoadCameraProfile;
import mchorse.aperture.network.server.ServerHandlerRemoveCameraProfile;
import mchorse.aperture.network.server.ServerHandlerRenameCameraProfile;
import mchorse.aperture.network.server.ServerHandlerRequestCameraProfiles;
import mchorse.mclib.network.AbstractDispatcher;
import mchorse.mclib.network.IMessage;
import mchorse.mclib.network.Side;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Full port of Aperture 1.8.2's camera-networking dispatcher (roadmap P182) on
 * the bundled McLib S2 {@link AbstractDispatcher}.
 *
 * <p>Channel {@code "aperture"}; registration order is the frozen discriminator
 * sequence, identical to legacy
 * {@code .tools/legacy-src/aperture/.../network/Dispatcher.register()}. Both-side
 * registrations (profile, rename, remove) collapse to one Identifier with a
 * receiver per side. CLIENT handlers are referenced by <b>name</b> because they
 * live in the split client source set (same convention as
 * {@code mchorse.mclib.network.mclib.Dispatcher}).</p>
 *
 * <p>This is a <b>new channel</b> — its five reserved Blockbuster-side
 * identifiers ({@code blockbuster:request_profiles} etc.) are the separate
 * bridge packets (P182). The {@code aperture:*} identifiers are ledgered in
 * {@link mchorse.mclib.network.ChannelLedger#APERTURE_CHANNEL} and mirrored in
 * {@code plan/network-ledger.md}; {@code ChannelLedgerTest} pins them slot by
 * slot against this {@code register()} order. (Batch U-N: the earlier
 * "reported to the central ledger at merge" note described something that
 * never happened, so the channel's wire ids were unpinned against a rename.)</p>
 *
 * <p>Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/network/Dispatcher.java</p>
 *
 * @author Ernio (Ernest Sadowski)
 */
public class Dispatcher
{
    private static final String CLIENT = "mchorse.aperture.network.client.";

    public static final AbstractDispatcher DISPATCHER = new AbstractDispatcher(Aperture.MOD_ID)
    {
        @Override
        public void register()
        {
            register(PacketAperture.class, CLIENT + "ClientHandlerAperture", Side.CLIENT);

            register(PacketCameraProfile.class, CLIENT + "ClientHandlerCameraProfile", Side.CLIENT);
            register(PacketCameraProfile.class, ServerHandlerCameraProfile.class, Side.SERVER);
            /* S22 P244 decision: KEEP, no sender. PacketCameraReset has no send
             * site in Aperture 1.8.2 either (grep of the whole legacy tree —
             * only the class, this registration and the handler) nor in
             * Blockbuster 2.7.2, which is Aperture's only in-tree consumer. It
             * is a receive-only API surface for third-party mods that want to
             * clear a player's camera capability, and the port keeps that
             * surface: writing a sender would be a new feature, deleting the
             * registration would move an id in the frozen ChannelLedger golden
             * and silently break any mod that still sends it. */
            register(PacketCameraReset.class, ServerHandlerCameraReset.class, Side.SERVER);
            register(PacketCameraState.class, CLIENT + "ClientHandlerCameraState", Side.CLIENT);
            register(PacketLoadCameraProfile.class, ServerHandlerLoadCameraProfile.class, Side.SERVER);
            register(PacketRequestCameraProfiles.class, ServerHandlerRequestCameraProfiles.class, Side.SERVER);
            register(PacketCameraProfileList.class, CLIENT + "ClientHandlerCameraProfileList", Side.CLIENT);

            register(PacketRenameCameraProfile.class, CLIENT + "ClientHandlerRenameCameraProfile", Side.CLIENT);
            register(PacketRenameCameraProfile.class, ServerHandlerRenameCameraProfile.class, Side.SERVER);

            register(PacketRemoveCameraProfile.class, CLIENT + "ClientHandlerRemoveCameraProfile", Side.CLIENT);
            register(PacketRemoveCameraProfile.class, ServerHandlerRemoveCameraProfile.class, Side.SERVER);
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
