package mchorse.aperture.client;

import mchorse.aperture.Aperture;
import mchorse.aperture.ClientProxy;
import mchorse.aperture.camera.CameraControl;
import mchorse.mclib.utils.OpHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Bundled Aperture keybind family (S15 P186) — port of legacy
 * {@code mchorse.aperture.client.KeyboardHandler}.
 *
 * <p>Registers the four keybind categories with the verified legacy
 * translation keys and defaults (P / Z / C bound, everything else unbound) and
 * wires the two legacy handlers:</p>
 *
 * <ul>
 *   <li><b>edge</b> (legacy {@code onKey}, Forge {@code KeyInputEvent} which
 *       only fired with no GUI open — matched by Fabric's
 *       {@code KeyBinding.wasPressed()} press queue, only fed while
 *       {@code currentScreen == null}): toggle path render, toggle playback,
 *       add path point, reset roll / FOV, open camera editor, smooth camera;</li>
 *   <li><b>held / per-tick</b> (legacy {@code onClientTick}): roll ±0.5/tick and
 *       FOV ±0.25/tick while smooth camera is off, and the step/rotate keys —
 *       teleport by {@code step_factor}/{@code rotate_factor}, the step vector
 *       rotated by yaw, velocity zeroed, <b>silently OP-gated
 *       ({@link OpHelper#isPlayerOp()}) including rotation-only changes</b>.</li>
 * </ul>
 *
 * <p><b>ReplayMod interop (S21 P218.2)</b>: a static {@link #inReplay} flag, set
 * on client connect via {@link #checkReplayWorld()} when {@code replaymod} is
 * loaded, makes both handlers early-return inside a ReplayMod replay world so
 * the two camera systems don't fight. It gates <b>only</b> the keyboard/tick
 * camera controls — scene playback and camera-profile <i>rendering</i> keep
 * running, because ReplayMod users record Blockbuster scenes with ReplayMod's
 * own camera. On disconnect the cached editor is dropped, {@link CameraControl}
 * reset (auto-saving dirty profiles via its own hook) and
 * {@code ClientProxy.server} cleared so the next session defaults to client
 * destinations.</p>
 *
 * <p>Documented non-parity: 1.20.4 FOV is an integer game option, so the
 * ±0.25/tick FOV ramp accumulates in a float and applies the rounded value
 * (legacy {@code fovSetting} was a float). Granularity differs; the observable
 * ramp does not.</p>
 *
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/client/KeyboardHandler.java
 */
public class KeyboardHandler
{
    /** GLFW unbound sentinel (legacy {@code Keyboard.KEY_NONE}). */
    private static final int NONE = GLFW.GLFW_KEY_UNKNOWN;

    public static final KeyboardHandler HANDLER = new KeyboardHandler();

    /** Whether the client is currently inside a ReplayMod replay world. */
    public static boolean inReplay;

    /* Camera profile keys */
    private KeyBinding toggleRender;
    private KeyBinding toggleRunning;
    private KeyBinding addPoint;

    /* Roll and FOV */
    public KeyBinding addRoll;
    public KeyBinding reduceRoll;
    private KeyBinding resetRoll;

    public KeyBinding addFov;
    public KeyBinding reduceFov;
    private KeyBinding resetFov;

    /* Camera control */
    private KeyBinding stepUp;
    private KeyBinding stepDown;
    private KeyBinding stepLeft;
    private KeyBinding stepRight;
    private KeyBinding stepFront;
    private KeyBinding stepBack;

    private KeyBinding rotateUp;
    private KeyBinding rotateDown;
    private KeyBinding rotateLeft;
    private KeyBinding rotateRight;

    /* Misc. */
    public KeyBinding cameraEditor;
    private KeyBinding smoothCamera;

    /** Float accumulator backing the int FOV option (see class doc). */
    private float fovAccumulator;

    private boolean registered;

    /**
     * Register the keybind family + the client tick / connection hooks
     * (idempotent).
     */
    public void register()
    {
        if (this.registered)
        {
            return;
        }

        this.registered = true;

        this.createBindings();

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onUserLogIn(isReplayModLoaded()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onUserLogOut(isReplayModLoaded()));
    }

    /** Whether ReplayMod is installed (legacy {@code Loader.isModLoaded}). */
    static boolean isReplayModLoaded()
    {
        return FabricLoader.getInstance().isModLoaded("replaymod");
    }

    /**
     * Legacy {@code onUserLogIn(ClientConnectedToServerEvent)}. ReplayMod opens
     * a replay as a (fake) client connection, so evaluating the probe **once**
     * here is sufficient — do not "improve" this into per-tick polling (P218.2).
     *
     * <p>The flag argument is the {@code isModLoaded} answer, passed in so the
     * phase's tests can drive both branches headlessly.</p>
     */
    static void onUserLogIn(boolean replayModLoaded)
    {
        if (replayModLoaded)
        {
            inReplay = checkReplayWorld();
        }
    }

    /** Legacy {@code onUserLogOut(ClientDisconnectionFromServerEvent)}. */
    static void onUserLogOut(boolean replayModLoaded)
    {
        /* Deliberate divergence from legacy, which nulled the editor first.
         * CameraControl.reset() is what runs the camera.auto_save pass, and the
         * saver walks ClientProxy.cameraEditor's profile list — so clearing the
         * field on the line above meant the saver always saw null and returned
         * without writing anything. Auto-save has therefore never once fired on
         * leaving a world, in this port or in 1.12.2: every unsaved edit was
         * dropped, and a profile that had not been saved even once was lost
         * outright, since no file existed to fall back to.
         *
         * Reset first, then drop the editor. Nothing in reset() reads the field
         * except through the saver, so the order is free. */
        ClientProxy.control.reset();
        ClientProxy.cameraEditor = null;
        ClientProxy.server = false;

        /* Legacy quirk kept: the reset is itself behind the isModLoaded check
         * (harmless — the flag can only be true when ReplayMod is present). */
        if (replayModLoaded)
        {
            inReplay = false;
        }
    }

    /**
     * Create the full keybind family in legacy registration order (four
     * categories; P / Z / C bound, the rest unbound). Split out from
     * {@link #register()} so headless tests can build the table without the
     * global tick / connection event wiring.
     */
    protected void createBindings()
    {
        /* Key categories (legacy strings) */
        String camera = "key.aperture.camera";
        String profile = "key.aperture.profile.title";
        String control = "key.aperture.control.title";
        String misc = "key.aperture.misc";

        /* Camera profile keys */
        this.toggleRender = this.create("key.aperture.profile.toggle", GLFW.GLFW_KEY_P, profile);
        this.toggleRunning = this.create("key.aperture.profile.playback", GLFW.GLFW_KEY_Z, profile);
        this.addPoint = this.create("key.aperture.profile.point", NONE, profile);

        /* Roll and FOV */
        this.addRoll = this.create("key.aperture.roll.add", NONE, camera);
        this.reduceRoll = this.create("key.aperture.roll.reduce", NONE, camera);
        this.resetRoll = this.create("key.aperture.roll.reset", NONE, camera);

        this.addFov = this.create("key.aperture.fov.add", NONE, camera);
        this.reduceFov = this.create("key.aperture.fov.reduce", NONE, camera);
        this.resetFov = this.create("key.aperture.fov.reset", NONE, camera);

        /* Camera control */
        this.stepUp = this.create("key.aperture.control.stepUp", NONE, control);
        this.stepDown = this.create("key.aperture.control.stepDown", NONE, control);
        this.stepLeft = this.create("key.aperture.control.stepLeft", NONE, control);
        this.stepRight = this.create("key.aperture.control.stepRight", NONE, control);
        this.stepFront = this.create("key.aperture.control.stepFront", NONE, control);
        this.stepBack = this.create("key.aperture.control.stepBack", NONE, control);

        this.rotateUp = this.create("key.aperture.control.rotateUp", NONE, control);
        this.rotateDown = this.create("key.aperture.control.rotateDown", NONE, control);
        this.rotateLeft = this.create("key.aperture.control.rotateLeft", NONE, control);
        this.rotateRight = this.create("key.aperture.control.rotateRight", NONE, control);

        /* Misc */
        this.cameraEditor = this.create("key.aperture.camera_editor", GLFW.GLFW_KEY_C, misc);
        this.smoothCamera = this.create("key.aperture.smooth_camera", NONE, misc);
    }

    private KeyBinding create(String translation, int code, String category)
    {
        return this.registerBinding(new KeyBinding(translation, InputUtil.Type.KEYSYM, code, category));
    }

    /**
     * Register a keybind with the game. Overridable so headless tests can keep
     * the raw binding (the Fabric {@code KeyBindingHelper} needs a live client).
     */
    protected KeyBinding registerBinding(KeyBinding binding)
    {
        return KeyBindingHelper.registerKeyBinding(binding);
    }

    /** ReplayMod's replay module — the class the legacy probe reflects into. */
    static final String REPLAY_MOD_REPLAY = "com.replaymod.replay.ReplayModReplay";

    /**
     * Whether ReplayMod's replay is currently running (legacy reflective probe
     * into {@code com.replaymod.replay.ReplayModReplay.instance.replayHandler}).
     *
     * <p>P218.2 — <b>verified</b> against the shipped Fabric artifact
     * <code>replaymod-1.20.4-2.6.23.jar</code> (Modrinth, latest 1.20.4 build):
     * {@code javap -p com.replaymod.replay.ReplayModReplay} still reports
     * {@code public static ReplayModReplay instance} and
     * {@code private ReplayHandler replayHandler}, so the legacy body is
     * bit-for-bit correct on 1.20.4 and runs first. Two fallbacks follow it
     * (the public {@code getReplayHandler()} getter, then a declared-field scan
     * for a {@code *ReplayHandler}-typed instance field) purely as future-proofing
     * if ReplayMod renames the private field; every step still swallows
     * everything and answers {@code false}.</p>
     */
    public static boolean checkReplayWorld()
    {
        return checkReplayWorld(REPLAY_MOD_REPLAY);
    }

    /**
     * The probe body, parameterised on the class name so headless tests can
     * point it at a fake, a wrong-shaped fake and an absent class without
     * forking a JVM. Never throws.
     */
    static boolean checkReplayWorld(String className)
    {
        try
        {
            Class<?> replayMod = Class.forName(className);
            Field replayField = replayMod.getField("instance");
            Object instance = replayField.get(null);

            /* Legacy path (verified present on 1.20.4-2.6.23) */
            try
            {
                Field replayHandlerField = replayMod.getDeclaredField("replayHandler");

                replayHandlerField.setAccessible(true);

                return replayHandlerField.get(instance) != null;
            }
            catch (Exception e)
            {
                /* fall through to the fallback chain */
            }

            /* Fallback 1: the public getter (the more stable API surface) */
            try
            {
                Method getter = replayMod.getMethod("getReplayHandler");

                getter.setAccessible(true);

                return getter.invoke(instance) != null;
            }
            catch (Exception e)
            {
                /* fall through */
            }

            /* Fallback 2: any instance field typed *ReplayHandler */
            for (Field field : replayMod.getDeclaredFields())
            {
                if (!Modifier.isStatic(field.getModifiers()) && field.getType().getName().endsWith("ReplayHandler"))
                {
                    field.setAccessible(true);

                    return field.get(instance) != null;
                }
            }
        }
        catch (Exception e)
        {
            /* ReplayMod absent or internals changed — not in a replay */
        }
        catch (LinkageError e)
        {
            /* a half-loadable fake/shaded class — still not in a replay */
        }

        return false;
    }

    private void onClientTick(MinecraftClient mc)
    {
        this.dispatchTick(mc, mc.player);
    }

    /**
     * The no-player / ReplayMod gate plus the two handler dispatches.
     *
     * <p>P218.2 test seam: package-visible, and it dereferences neither
     * argument before the gate, so a headless test can drive it and assert that
     * {@code inReplay} costs exactly zero camera actions. This single gate
     * stands in for legacy's <i>two</i> ({@code onKey} line 215 and
     * {@code onClientTick} line 286) because the Forge {@code KeyInputEvent}
     * handler became the {@code wasPressed()} queue drained from this same
     * tick — see {@link #handleEdge}.</p>
     */
    void dispatchTick(MinecraftClient mc, ClientPlayerEntity player)
    {
        if (player == null || inReplay)
        {
            return;
        }

        /* Edge handling (legacy onKey) — wasPressed() is only fed while no
         * screen is open, matching the legacy Forge KeyInputEvent gating. */
        this.handleEdge(mc, player);

        /* Held handling (legacy onClientTick) — in-world only */
        if (!this.screenOpen(mc))
        {
            this.handleHeld(player);
        }
    }

    /** Overridable so the P218.2 gate test needs no live client. */
    protected boolean screenOpen(MinecraftClient mc)
    {
        return mc.currentScreen != null;
    }

    protected void handleEdge(MinecraftClient mc, ClientPlayerEntity player)
    {
        CameraControl control = ClientProxy.control;

        while (this.toggleRender.wasPressed())
        {
            this.toggleProfileRender();
        }

        while (this.toggleRunning.wasPressed())
        {
            if (ClientProxy.canUseCameraEditor())
            {
                ClientProxy.runner.toggle(ClientProxy.control.currentProfile, 0);
            }
        }

        while (this.resetRoll.wasPressed())
        {
            control.resetRoll();
        }

        while (this.resetFov.wasPressed())
        {
            control.resetFOV();
        }

        while (this.cameraEditor.wasPressed())
        {
            if (mc.currentScreen == null && mc.world != null && ClientProxy.canUseCameraEditor())
            {
                ClientProxy.openCameraEditor();
            }
        }

        while (this.addPoint.wasPressed())
        {
            /* Legacy KeyboardHandler.onKey:235-238 — same `canUseCameraEditor`
             * gate, and deliberately no `currentScreen == null` guard: the
             * editor is created lazily by getCameraEditor(), so the binding
             * appends to whatever path fixture the editor currently has
             * selected, from in world. */
            if (ClientProxy.canUseCameraEditor())
            {
                ClientProxy.getCameraEditor().addPathPoint();
            }
        }

        while (this.smoothCamera.wasPressed())
        {
            ApertureClient.setSmoothCamera(!ClientProxy.renderer.smooth.enabled.get());
        }
    }

    private void toggleProfileRender()
    {
        Aperture.profileRender.set(!Aperture.profileRender.get());

        if (Aperture.profileRender.getConfig() != null)
        {
            Aperture.profileRender.getConfig().save();
        }
    }

    protected void handleHeld(ClientPlayerEntity player)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (!ClientProxy.renderer.smooth.enabled.get())
        {
            CameraControl control = ClientProxy.control;

            /* Roll ramp */
            if (this.addRoll.isPressed())
            {
                control.roll += 0.5F;
            }
            else if (this.reduceRoll.isPressed())
            {
                control.roll -= 0.5F;
            }

            /* FOV ramp (int option, float accumulator — see class doc) */
            if (mc.options != null)
            {
                if (this.addFov.isPressed() || this.reduceFov.isPressed())
                {
                    this.fovAccumulator += this.addFov.isPressed() ? 0.25F : -0.25F;
                    mc.options.getFov().setValue(Math.round(this.fovAccumulator));
                }
                else
                {
                    this.fovAccumulator = mc.options.getFov().getValue();
                }
            }
        }

        double factor = Aperture.stepFactor.get();
        double angleFactor = Aperture.rotateFactor.get();

        float yaw = player.getYaw();
        float pitch = player.getPitch();

        if (this.rotateUp.isPressed() || this.rotateDown.isPressed())
        {
            pitch += (this.rotateUp.isPressed() ? -angleFactor : angleFactor);
        }

        if (this.rotateLeft.isPressed() || this.rotateRight.isPressed())
        {
            yaw += (this.rotateLeft.isPressed() ? -angleFactor : angleFactor);
        }

        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        double xx = 0;
        double yy = 0;
        double zz = 0;

        if (this.stepUp.isPressed() || this.stepDown.isPressed())
        {
            yy = (this.stepUp.isPressed() ? factor : -factor);
        }

        if (this.stepLeft.isPressed() || this.stepRight.isPressed())
        {
            xx = (this.stepLeft.isPressed() ? factor : -factor);
        }

        if (this.stepFront.isPressed() || this.stepBack.isPressed())
        {
            zz = (this.stepFront.isPressed() ? factor : -factor);
        }

        /* Silently OP-gated, including rotation-only changes (legacy) */
        if (!OpHelper.isPlayerOp())
        {
            return;
        }

        if (xx != 0 || yy != 0 || zz != 0 || yaw != player.getYaw() || pitch != player.getPitch())
        {
            Vec3d vec = new Vec3d(xx, yy, zz);

            vec = vec.rotateY(-yaw / 180F * (float) Math.PI);

            x += vec.x;
            y += vec.y;
            z += vec.z;

            player.refreshPositionAndAngles(x, y, z, yaw, pitch);
            player.setVelocity(0, 0, 0);
        }
    }
}
