package mchorse.blockbuster.aperture;

import mchorse.aperture.camera.CameraAPI;
import mchorse.aperture.camera.minema.MinemaIntegration;
import mchorse.blockbuster.recording.scene.SceneLocation;
import mchorse.mclib.config.ConfigBuilder;
import mchorse.mclib.config.values.ValueBoolean;
import mchorse.mclib.utils.resources.RLUtils;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Camera handler — Blockbuster's bridge to the (formerly soft-dependency)
 * Aperture camera. In 1.12.2 every touchpoint here was double-gated
 * ({@code Loader.isModLoaded(Aperture.MOD_ID)} + {@code @Optional.Method}) with
 * a safe fallback. Aperture is now bundled (S15), so the shim collapses
 * (roadmap P186): {@link #isApertureLoaded()} is always {@code true} and the
 * {@code @Optional.Method} indirections are gone — <b>but the fallback
 * sentinels are load-bearing and stay</b>:
 *
 * <ul>
 *   <li>{@link #getOffset()} returns {@code -1} when the camera editor is
 *       closed (callers distinguish "editor closed" (−1) from "at tick 0"
 *       (0) — never collapse to 0);</li>
 *   <li>{@link #getRoll()} / {@link #getRoll(float)} return {@code 0} with no
 *       control context.</li>
 * </ul>
 *
 * <p>Because this class lives in the <b>main</b> source set (it is read from
 * both server and client sides) while the real implementations touch
 * client-only Aperture GUI/runner/control state, the client bindings are
 * installed as seams from {@code ApertureClient.init()} (the S15 client
 * entrypoint). Headless / dedicated-server defaults keep the sentinels, safe
 * by construction.</p>
 *
 * Legacy source: blockbuster-1.12/src/main/java/mchorse/blockbuster/aperture/CameraHandler.java
 */
public class CameraHandler
{
    /**
     * Tick which is used to set the value of the camera editor scrub back.
     *
     * <p>Load-bearing static (P185.1): it is written when the camera editor
     * screen opens and read back by {@code ClientHandlerSceneLength} when the
     * server answers with the scene length, which is what restores the scrub
     * position across a close/reopen of the editor.</p>
     */
    public static int tick = 0;

    /**
     * Whether the director should be reloaded when entering the camera editor
     * GUI (Blockbuster config category {@code "aperture"}).
     */
    public static ValueBoolean reload;

    /** Whether actions should be played back also. */
    public static ValueBoolean actions;

    /** Whether the scene should be stopped when exiting the camera editor GUI. */
    public static ValueBoolean stopScene;

    /* Client-installed seams (default sentinels preserved). */

    /** Installed by the S15 client entrypoint. */
    public static Supplier<Float> rollGetter = () -> 0F;

    /** Installed by the S15 client entrypoint ((prevRoll, roll) pair). */
    public static RollSetter rollSetter = (prevRoll, roll) -> {};

    /** Roll-with-partial-ticks getter (legacy {@code control.getRoll(pt)}). */
    public static RollPartialGetter rollPartialGetter = (partialTicks) -> 0F;

    /** Reset-roll seam (legacy {@code control.resetRoll()}). */
    public static Runnable rollResetter = () -> {};

    /**
     * Camera-editor scrub offset seam — returns the editor timeline value when
     * the camera editor is open, else the {@code -1} sentinel. Installed by the
     * client entrypoint.
     */
    public static IntSupplier offsetGetter = () -> -1;

    /** Attach the outside camera (legacy {@code runner.attachOutside()}). */
    public static Runnable outsideAttacher = () -> {};

    /** Detach the outside camera (legacy {@code runner.detachOutside()}). */
    public static Runnable outsideDetacher = () -> {};

    /**
     * Close the current screen, running the camera-editor exit logic if the
     * camera editor is the current screen (legacy
     * {@code closeScreenOrCameraEditor}). Installed by the client entrypoint;
     * the headless default is a no-op.
     */
    public static Runnable screenCloser = () -> {};

    /**
     * Re-seed the camera editor's stored player position from the live player
     * (legacy {@code updateCameraPlayerPosition} →
     * {@code ClientProxy.cameraEditor.position.set(player)}). Installed by the
     * client entrypoint; no-op headless.
     */
    public static Runnable playerPositionUpdater = () -> {};

    /**
     * Re-parent the recording editor's timeline / action editor / record list
     * into the camera editor (legacy {@code moveRecordPanelToEditor}). Takes
     * the {@code GuiRecordingEditorPanel}, which lives in the client source
     * set — hence {@link Object} here. Installed by the client entrypoint;
     * no-op headless.
     */
    public static Consumer<Object> recordPanelMover = (panel) -> {};

    /**
     * Open the playback-button screen for a scene location and the list of
     * scene filenames to choose from (legacy {@code CameraHandler.attach}).
     * Installed by the client entrypoint; no-op headless.
     */
    public static BiConsumer<SceneLocation, List<String>> playbackScreenOpener = (location, scenes) -> {};

    /**
     * Open the camera editor screen (legacy {@code openCameraEditor}, which
     * zeroed the player's velocity before displaying it). Installed by the
     * client entrypoint; no-op headless.
     */
    public static Runnable cameraEditorOpener = () -> {};

    public interface RollSetter
    {
        void setRoll(float prevRoll, float roll);
    }

    public interface RollPartialGetter
    {
        float getRoll(float partialTicks);
    }

    /**
     * Whether Aperture is loaded. Aperture is bundled inside Blockbuster now,
     * so this is always {@code true} (kept as a method for diff-ability with
     * the legacy soft-dependency gate and because call sites read it).
     */
    public static boolean isApertureLoaded()
    {
        return true;
    }

    /**
     * Whether the built-in recorder is available (legacy
     * {@code CameraHandler.isMinemaAvailable()} → {@code MinemaIntegration
     * .isAvailable()}). Grep-verified dormant public API in 2.7.2 (only
     * {@link #isApertureAndMinemaLoaded()} read it); kept because third parties
     * may call it. The bundled recorder is always available (a missing
     * {@code ffmpeg} merely switches to the PNG-sequence sink), so this is
     * effectively constant-true (S18 P202).
     */
    public static boolean isMinemaAvailable()
    {
        return MinemaIntegration.isAvailable();
    }

    /**
     * Blockbuster's original recording gate (legacy
     * {@code CameraHandler.isApertureAndMinemaLoaded()} =
     * {@code isApertureLoaded() && isMinemaAvailable()}). In 1.12.2 this gated
     * the recording panel's visibility on the Aperture+Minema soft dependencies;
     * both are now bundled, so it collapses to the recorder-availability check.
     * Dormant public API surface (no in-tree call sites in 2.7.2) — kept for
     * diff-ability and third-party callers.
     */
    public static boolean isApertureAndMinemaLoaded()
    {
        return isApertureLoaded() && isMinemaAvailable();
    }

    /**
     * Register Blockbuster's {@code "aperture"} config category into
     * Blockbuster's own config module (legacy
     * {@code CameraHandler.registerConfig}). Client-side + invisible, exactly
     * as legacy.
     */
    public static void registerConfig(ConfigBuilder builder)
    {
        builder.category("aperture");
        reload = builder.getBoolean("reload", true);
        actions = builder.getBoolean("actions", true);
        stopScene = builder.getBoolean("stop_scene", false);

        builder.getCategory().clientSide().invisible();
    }

    /**
     * Whether the Aperture camera editor screen is currently open. Legacy
     * {@code CameraHandler.isCameraEditorOpen()} gated two things the port
     * keeps: (1) the in-game waveform HUD overlay is suppressed while the
     * editor is open so its own overlay drawable does not double-draw (P190),
     * and (2) various camera-editor-only render hooks.
     *
     * <p>The real predicate ({@code MinecraftClient.currentScreen instanceof
     * GuiCameraEditor}) lives in {@code mchorse.aperture.client
     * .RenderingHandler#isCameraEditorOpen} and is installed here by
     * {@code mchorse.aperture.client.CameraProfileWiring#install} (P239) — this
     * source set is common and cannot see a screen. The {@code () -> false}
     * initialiser is the dedicated-server / headless answer, not a hole.</p>
     */
    public static BooleanSupplier cameraEditorOpen = () -> false;

    public static boolean isCameraEditorOpen()
    {
        return cameraEditorOpen.getAsBoolean();
    }

    /**
     * Scene location the camera editor is bound to (legacy
     * {@code CameraHandler.location}). Set when a scene is attached; cleared on
     * detach.
     */
    public static SceneLocation location;

    /**
     * Legacy {@link #get()} first read the player's main-hand
     * {@code ItemPlayback} ({@code Scene} string tag) and only then fell back to
     * {@link #location}. The held-item probe needs the client player, which is
     * not reachable from this (common) source set, so it is installed as a
     * supplier by the client entrypoint
     * ({@code mchorse.aperture.client.ApertureClient#init}). The {@code null}
     * default is the dedicated-server / headless answer — "no playback button
     * in hand" — which makes {@link #get()} behave exactly like legacy there;
     * it is not an unported seam.
     */
    public static Supplier<SceneLocation> heldPlaybackLocation = () -> null;

    /**
     * Scene location from the playback button, falling back to
     * {@link #location} (legacy {@code CameraHandler.get()}).
     */
    public static SceneLocation get()
    {
        SceneLocation held = heldPlaybackLocation.get();

        return held == null ? location : held;
    }

    /** Legacy {@code CameraHandler.canSync()}. */
    public static boolean canSync()
    {
        return get() != null;
    }

    public static float getRoll()
    {
        return rollGetter.get();
    }

    public static float getRoll(float partialTicks)
    {
        return rollPartialGetter.getRoll(partialTicks);
    }

    public static void setRoll(float prevRoll, float roll)
    {
        rollSetter.setRoll(prevRoll, roll);
    }

    public static void resetRoll()
    {
        rollResetter.run();
    }

    /**
     * The camera-editor scrub offset, or {@code -1} when the editor is closed
     * (load-bearing sentinel — see class doc).
     */
    public static int getOffset()
    {
        return offsetGetter.getAsInt();
    }

    public static void attachOutside()
    {
        outsideAttacher.run();
    }

    public static void detachOutside()
    {
        outsideDetacher.run();
    }

    public static void closeScreenOrCameraEditor()
    {
        screenCloser.run();
    }

    /** Legacy {@code CameraHandler.updatePlayerPosition()}. */
    public static void updatePlayerPosition()
    {
        playerPositionUpdater.run();
    }

    /**
     * Legacy {@code CameraHandler.moveRecordPanel(GuiRecordingEditorPanel)} —
     * the argument is the recording editor panel (client-only type).
     */
    public static void moveRecordPanel(Object panel)
    {
        recordPanelMover.accept(panel);
    }

    /**
     * Legacy {@code CameraHandler.attach(location, scenes)} — open the playback
     * button screen on the given scene location.
     */
    public static void attach(SceneLocation location, List<String> scenes)
    {
        playbackScreenOpener.accept(location, scenes);
    }

    /** Legacy {@code CameraHandler.openCameraEditor()}. */
    public static void openCameraEditor()
    {
        cameraEditorOpener.run();
    }

    /**
     * Resolve the playback-button camera mode from its item NBT (legacy
     * {@code CameraHandler.getModeFromNBT}): {@code 1} when {@code CameraPlay}
     * is present, {@code 2} when {@code CameraProfile} is present, {@code 0}
     * otherwise. {@code CameraPlay} wins if both are somehow set (checked
     * first). Pure — no side effects, exposed for headless tests.
     */
    public static int getModeFromNBT(NbtCompound tag)
    {
        if (tag.contains("CameraPlay"))
        {
            return 1;
        }
        else if (tag.contains("CameraProfile"))
        {
            return 2;
        }

        return 0;
    }

    /**
     * Aperture playback hand-off for the playback button (legacy
     * {@code CameraHandler.handlePlaybackItem}). Only server players trigger a
     * camera: {@code CameraPlay} → play the player's current profile;
     * {@code CameraProfile} → play the named profile. The {@code EntityPlayerMP}
     * guard is preserved so an actor holding a playback item never crashes.
     *
     * <p>The {@code PacketCameraState} camera networking underneath
     * {@link mchorse.aperture.camera.CameraAPI#playCurrentProfile} /
     * {@code playCameraProfile} landed in P182 and is installed by
     * {@code mchorse.aperture.CommonProxy#load}, so both calls send for real.</p>
     */
    public static void handlePlaybackItem(PlayerEntity player, NbtCompound tag)
    {
        /* To allow actors using the playback item without a crash */
        if (player instanceof ServerPlayerEntity serverPlayer)
        {
            if (tag.contains("CameraPlay"))
            {
                CameraAPI.playCurrentProfile(serverPlayer);
            }
            else if (tag.contains("CameraProfile"))
            {
                CameraAPI.playCameraProfile(serverPlayer,
                    RLUtils.create(tag.getString("CameraProfile")));
            }
        }
    }
}
