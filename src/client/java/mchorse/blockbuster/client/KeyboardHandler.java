package mchorse.blockbuster.client;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.BlockbusterClient;
import mchorse.blockbuster.ClientProxy;
import mchorse.blockbuster.client.gui.GuiGun;
import mchorse.mclib.utils.OpHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * Blockbuster's global client keybind family (roadmap P145) — port of legacy
 * {@code mchorse.blockbuster.client.KeyboardHandler}.
 *
 * <p>Registers the seven bindings under the single legacy category
 * {@code key.blockbuster.category} with the verified legacy translation keys and
 * defaults, and wires the two legacy handlers:</p>
 *
 * <ul>
 *   <li><b>onKey</b> (legacy Forge {@code KeyInputEvent}, which only fired while
 *       no GUI was open — matched by Fabric's {@code KeyBinding.wasPressed()}
 *       press queue, only fed while {@code currentScreen == null}): the
 *       {@code plause}/{@code record}/{@code pause} keys forward to the scene
 *       (director) panel's like-named methods when the panel exists, and
 *       {@code openGun} opens the gun GUI when the player is creative + OP and
 *       holding the gun item;</li>
 *   <li><b>onUserLogOut</b> (legacy Forge {@code ClientDisconnectionFromServerEvent}
 *       → Fabric {@code ClientPlayConnectionEvents.DISCONNECT}): reset the client
 *       record manager, hide the recording overlay, flush the Bedrock emitters
 *       and schedule the structure-morph cache cleanup.</li>
 * </ul>
 *
 * <h2>Default-key parity (LWJGL2 → GLFW)</h2>
 * <table>
 *   <tr><td>plause</td><td>{@code Keyboard.KEY_RCONTROL}</td><td>{@code GLFW_KEY_RIGHT_CONTROL}</td></tr>
 *   <tr><td>record</td><td>{@code Keyboard.KEY_RMENU} (RAlt)</td><td>{@code GLFW_KEY_RIGHT_ALT}</td></tr>
 *   <tr><td>pause</td><td>{@code Keyboard.KEY_RSHIFT}</td><td>{@code GLFW_KEY_RIGHT_SHIFT}</td></tr>
 *   <tr><td>openGun</td><td>{@code Keyboard.KEY_END}</td><td>{@code GLFW_KEY_END}</td></tr>
 *   <tr><td>zoom</td><td>{@code 0} (unbound)</td><td>{@code GLFW_KEY_UNKNOWN}</td></tr>
 *   <tr><td>gunReload</td><td>{@code 19} (LWJGL2 {@code R})</td><td>{@code GLFW_KEY_R}</td></tr>
 *   <tr><td>gunShoot</td><td>{@code -100} (mouse left)</td><td>{@code InputUtil.Type.MOUSE} button 0</td></tr>
 * </table>
 *
 * <p>{@code zoom}, {@code gunReload} and {@code gunShoot} stay {@code public
 * static} exactly like legacy so S17's gun zoom / input handling can consume
 * them. {@code gunShoot} is a <b>mouse</b> binding (legacy {@code -100}); Fabric
 * needs {@code InputUtil.Type.MOUSE} for it, and {@code zoom} stays unbound
 * ({@code GLFW_KEY_UNKNOWN}) — it is not defaulted to a key.</p>
 *
 * Legacy source: blockbuster-1.12/.../client/KeyboardHandler.java
 */
public class KeyboardHandler
{
    /** GLFW unbound sentinel (legacy LWJGL2 {@code 0} / {@code KEY_NONE}). */
    private static final int NONE = GLFW.GLFW_KEY_UNKNOWN;

    /* Misc. */
    private KeyBinding plause;
    private KeyBinding record;
    private KeyBinding pause;
    private KeyBinding openGun;

    /** Gun zoom — consumed by S17 gun rendering (unbound by default). */
    public static KeyBinding zoom;
    /** Gun reload — consumed by S17 gun input handling (default {@code R}). */
    public static KeyBinding gunReload;
    /** Gun shoot — consumed by S17 gun input handling (default mouse left). */
    public static KeyBinding gunShoot;

    private boolean registered;

    /**
     * Register the keybind family + the client-tick / disconnect hooks
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
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> this.onLogout());
    }

    /**
     * Create the seven bindings in legacy registration order under the single
     * legacy category. Split out from {@link #register()} so headless tests can
     * build the table without the global tick / connection event wiring.
     */
    protected void createBindings()
    {
        /* Key categories */
        String category = "key.blockbuster.category";

        /* Misc */
        this.plause = this.create("key.blockbuster.plause_director", GLFW.GLFW_KEY_RIGHT_CONTROL, category);
        this.record = this.create("key.blockbuster.record_director", GLFW.GLFW_KEY_RIGHT_ALT, category);
        this.pause = this.create("key.blockbuster.pause_director", GLFW.GLFW_KEY_RIGHT_SHIFT, category);
        this.openGun = this.create("key.blockbuster.open_gun", GLFW.GLFW_KEY_END, category);
        zoom = this.create("key.blockbuster.zoom", NONE, category);
        gunReload = this.create("key.blockbuster.gun_reload", GLFW.GLFW_KEY_R, category);
        gunShoot = this.createMouse("key.blockbuster.gun_shoot", 0, category);
    }

    private KeyBinding create(String translation, int code, String category)
    {
        return this.registerBinding(new KeyBinding(translation, InputUtil.Type.KEYSYM, code, category));
    }

    private KeyBinding createMouse(String translation, int button, String category)
    {
        return this.registerBinding(new KeyBinding(translation, InputUtil.Type.MOUSE, button, category));
    }

    /**
     * Register a keybind with the game. Overridable so headless tests can keep
     * the raw binding (the Fabric {@code KeyBindingHelper} needs a live client).
     */
    protected KeyBinding registerBinding(KeyBinding binding)
    {
        return KeyBindingHelper.registerKeyBinding(binding);
    }

    /**
     * Legacy {@code onKey(InputEvent.KeyInputEvent)}. Fabric's
     * {@code wasPressed()} press queue is only fed while no screen is open, which
     * reproduces the legacy Forge {@code KeyInputEvent} gating (director keys and
     * the gun GUI only trigger from the in-world state, not from inside a GUI).
     */
    private void onClientTick(MinecraftClient mc)
    {
        ClientPlayerEntity player = mc.player;

        if (player == null)
        {
            /* Drain the queues so a later join doesn't replay stale presses. */
            this.drain();

            return;
        }

        while (this.plause.wasPressed())
        {
            if (BlockbusterClient.panels.scenePanel != null)
            {
                BlockbusterClient.panels.scenePanel.plause();
            }
        }

        while (this.record.wasPressed())
        {
            if (BlockbusterClient.panels.scenePanel != null)
            {
                BlockbusterClient.panels.scenePanel.record();
            }
        }

        while (this.pause.wasPressed())
        {
            if (BlockbusterClient.panels.scenePanel != null)
            {
                BlockbusterClient.panels.scenePanel.pause();
            }
        }

        while (this.openGun.wasPressed())
        {
            this.tryOpenGun(mc, player);
        }
    }

    /** Discards any queued presses of this handler's four polled bindings. */
    private void drain()
    {
        while (this.plause.wasPressed()) {}
        while (this.record.wasPressed()) {}
        while (this.pause.wasPressed()) {}
        while (this.openGun.wasPressed()) {}
    }

    /**
     * Legacy gun-GUI open: {@code openGun.isPressed() &&
     * player.capabilities.isCreativeMode && OpHelper.isPlayerOp()} then, if the
     * main-hand item is the gun, {@code mc.displayGuiScreen(new GuiGun(stack))}.
     */
    private void tryOpenGun(MinecraftClient mc, ClientPlayerEntity player)
    {
        if (!player.getAbilities().creativeMode || !OpHelper.isPlayerOp())
        {
            return;
        }

        ItemStack stack = player.getMainHandStack();

        if (canOpenGun(true, true, stack.getItem() == Blockbuster.GUN))
        {
            /* Legacy mc.displayGuiScreen(new GuiGun(stack)) (P193 landed). The
             * server logic re-checks creative/OP on the resulting PacketGunInfo. */
            mc.setScreen(new GuiGun(stack));
        }
    }

    /**
     * The client-side open-gun eligibility gate (legacy: creative + OP + holding
     * the gun). Pure so the creative/OP/held-item combinations are headlessly
     * testable without a live client.
     */
    public static boolean canOpenGun(boolean creative, boolean op, boolean holdingGun)
    {
        return creative && op && holdingGun;
    }

    /**
     * Legacy {@code onUserLogOut(ClientDisconnectionFromServerEvent)}: reset the
     * client record manager, hide the recording overlay, flush the Bedrock
     * emitters and schedule the structure-morph cache cleanup.
     */
    public void onLogout()
    {
        ClientProxy.manager.reset();
        ClientProxy.recordingOverlay.setVisible(false);
        RenderingHandler.resetEmitters();

        /* SEAM(S14): MinecraftClient.getInstance().execute(StructureMorph::cleanUp)
         * — StructureMorph (and its shared fake-world cache) is ported in S14;
         * until then this scheduled cache-clear is a no-op. */
    }
}
