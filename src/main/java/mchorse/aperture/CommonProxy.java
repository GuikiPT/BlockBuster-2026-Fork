package mchorse.aperture;

import mchorse.aperture.camera.CameraProfile;
import mchorse.aperture.camera.CameraUtils;
import mchorse.aperture.camera.FixtureRegistry;
import mchorse.aperture.camera.ModifierRegistry;
import mchorse.aperture.camera.destination.ServerDestination;
import mchorse.aperture.network.Dispatcher;
import mchorse.aperture.network.common.PacketAperture;
import mchorse.aperture.network.common.PacketCameraProfile;
import mchorse.aperture.network.common.PacketCameraState;
import mchorse.aperture.network.common.PacketLoadCameraProfile;
import mchorse.aperture.network.common.PacketRemoveCameraProfile;
import mchorse.aperture.network.common.PacketRenameCameraProfile;
import mchorse.aperture.camera.fixtures.CircularFixture;
import mchorse.aperture.camera.fixtures.DollyFixture;
import mchorse.aperture.camera.fixtures.IdleFixture;
import mchorse.aperture.camera.fixtures.KeyframeFixture;
import mchorse.aperture.camera.fixtures.ManualFixture;
import mchorse.aperture.camera.fixtures.NullFixture;
import mchorse.aperture.camera.fixtures.PathFixture;
import mchorse.aperture.camera.modifiers.AngleModifier;
import mchorse.aperture.camera.modifiers.DollyZoomModifier;
import mchorse.aperture.camera.modifiers.DragModifier;
import mchorse.aperture.camera.modifiers.FollowModifier;
import mchorse.aperture.camera.modifiers.LookModifier;
import mchorse.aperture.camera.modifiers.MathModifier;
import mchorse.aperture.camera.modifiers.OrbitModifier;
import mchorse.aperture.camera.modifiers.RemapperModifier;
import mchorse.aperture.camera.modifiers.ShakeModifier;
import mchorse.aperture.camera.modifiers.TranslateModifier;
import mchorse.aperture.capabilities.camera.Camera;
import mchorse.aperture.capabilities.camera.ICamera;
import mchorse.mclib.utils.OpHelper;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;

/**
 * Bundled Aperture common proxy (S15).
 *
 * <p>{@link #preLoad()} is the canonical registration order from legacy
 * {@code CommonProxy.preLoad} — <b>IDs derive purely from this order</b>
 * (fixtures: idle=0, dolly=1, circular=2, path=3, keyframe=4, null=5,
 * manual=6; modifiers: angle=0 … dolly_zoom=9). Blockbuster's
 * {@code "tracker"} (byte id 10) registers after these once TrackerModifier
 * lands (deferred, S14-adjacent — see S15 P176). Idempotent so headless
 * tests can call it too.</p>
 *
 * <p>{@link #load()} wires the runtime hooks Forge events used to provide:
 * the world-save camera folder root (legacy
 * {@code DimensionManager.getCurrentSaveRootDirectory()}) and the
 * login-resend from legacy {@code CapabilityHandler.playerLogsIn} (the
 * {@code PacketAperture} handshake send is P182 — the OP'd profile resend
 * bookkeeping runs; the actual packet flows once the sender seam is
 * installed).</p>
 *
 * Legacy sources:
 * .tools/legacy-src/aperture/src/main/java/mchorse/aperture/CommonProxy.java
 * .tools/legacy-src/aperture/src/main/java/mchorse/aperture/capabilities/CapabilityHandler.java
 */
public class CommonProxy
{
    private static boolean registered;
    private static boolean loaded;

    /**
     * Register camera fixtures and modifiers in the legacy byte-id order.
     */
    public static synchronized void preLoad()
    {
        if (registered)
        {
            return;
        }

        registered = true;

        /* Register camera fixtures and modifiers */
        FixtureRegistry.register("idle", IdleFixture.class);
        FixtureRegistry.register("dolly", DollyFixture.class);
        FixtureRegistry.register("circular", CircularFixture.class);
        FixtureRegistry.register("path", PathFixture.class);
        FixtureRegistry.register("keyframe", KeyframeFixture.class);
        FixtureRegistry.register("null", NullFixture.class);
        FixtureRegistry.register("manual", ManualFixture.class);

        ModifierRegistry.register("angle", AngleModifier.class);
        ModifierRegistry.register("translate", TranslateModifier.class);
        ModifierRegistry.register("shake", ShakeModifier.class);
        ModifierRegistry.register("drag", DragModifier.class);
        ModifierRegistry.register("look", LookModifier.class);
        ModifierRegistry.register("follow", FollowModifier.class);
        ModifierRegistry.register("orbit", OrbitModifier.class);
        ModifierRegistry.register("math", MathModifier.class);
        ModifierRegistry.register("remapper", RemapperModifier.class);
        ModifierRegistry.register("dolly_zoom", DollyZoomModifier.class);

        /* Startup assertion (dev parity guard, see plan S15 P171) */
        if (FixtureRegistry.NAME_TO_ID.get("manual") != 6)
        {
            throw new IllegalStateException("Aperture fixture registration order is broken: manual != 6");
        }
    }

    /**
     * Register runtime (Fabric event) hooks. Called once from the mod
     * initializer — never from tests.
     */
    public static synchronized void load()
    {
        if (loaded)
        {
            return;
        }

        loaded = true;

        /* P182: register the Aperture camera-networking channel and install
         * the profile-send seams the P169-P181 core left open. */
        Dispatcher.register();

        CameraUtils.sender = new CameraUtils.ProfileSender()
        {
            @Override
            public void sendProfile(String filename, CameraProfile profile, boolean play, ServerPlayerEntity player)
            {
                Dispatcher.sendTo(new PacketCameraProfile(filename, profile, play), player);
            }

            @Override
            public void sendPlayState(String filename, ServerPlayerEntity player)
            {
                Dispatcher.sendTo(new PacketCameraState(filename, true), player);
            }
        };

        ServerDestination.network = new ServerDestination.NetworkProxy()
        {
            @Override
            public void saveProfile(String filename, CameraProfile profile)
            {
                Dispatcher.sendToServer(new PacketCameraProfile(filename, profile));
            }

            @Override
            public void loadProfile(String filename, boolean force)
            {
                Dispatcher.sendToServer(new PacketLoadCameraProfile(filename, force));
            }

            @Override
            public void renameProfile(String from, String to)
            {
                Dispatcher.sendToServer(new PacketRenameCameraProfile(from, to));
            }

            @Override
            public void removeProfile(String filename)
            {
                Dispatcher.sendToServer(new PacketRemoveCameraProfile(filename));
            }
        };

        ServerLifecycleEvents.SERVER_STARTED.register(server ->
        {
            CameraUtils.serverDirectory = server.getSavePath(WorldSavePath.ROOT).toFile();
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server ->
        {
            CameraUtils.serverDirectory = null;
        });

        /* Legacy CapabilityHandler.playerLogsIn */
        ServerPlayConnectionEvents.JOIN.register((handler, packetSender, server) ->
        {
            ServerPlayerEntity player = handler.player;
            ICamera camera = Camera.get(player);

            if (camera != null && camera.hasProfile() && OpHelper.isPlayerOp(player))
            {
                CameraUtils.sendProfileToPlayer(camera.currentProfile(), player, false, true);

                camera.setCurrentProfileTimestamp(System.currentTimeMillis());
            }

            /* P182: server-mode handshake that flips ClientProxy.server so the
             * client's profile manager proxies profiles to this server. */
            Dispatcher.sendTo(new PacketAperture(), player);
        });
    }
}
