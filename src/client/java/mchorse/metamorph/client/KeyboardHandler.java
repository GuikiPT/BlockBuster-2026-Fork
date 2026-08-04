package mchorse.metamorph.client;

import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.creative.CreativeMorphNetwork;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.network.Dispatcher;
import mchorse.metamorph.network.common.survival.PacketAction;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import mchorse.mclib.utils.KeyCodes;
import mchorse.mclib.utils.Keys;
import mchorse.metamorph.client.gui.creative.GuiCreativeScreen;
import mchorse.metamorph.client.gui.creative.GuiSelectorsScreen;
import net.minecraft.client.gui.screen.Screen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import org.lwjgl.glfw.GLFW;

/**
 * Bundled Metamorph client keybind family (roadmap P60) — port of legacy
 * {@code mchorse.metamorph.client.KeyboardHandler}.
 *
 * <p>Registers the five legacy keybinds (category {@code key.metamorph}) with
 * their exact defaults — action = V, creative menu = B, selector menu = MINUS,
 * survival menu = X, demorph = PERIOD — and wires the two legacy input paths:</p>
 *
 * <ul>
 *   <li><b>edge</b> (legacy {@code onKey}, Forge {@code KeyInputEvent} which only
 *       fired with no GUI open — matched by Fabric's {@code KeyBinding.wasPressed()}
 *       press queue, only fed while {@code currentScreen == null}): open the
 *       creative / selector / survival menus (OP / selector gated), run the morph
 *       action (client-side instant feedback + a SEAM(P55) action packet), and
 *       demorph;</li>
 *   <li><b>global keybind dispatch</b> (legacy tail of {@code onKey}): any raw key
 *       press <i>not</i> consumed by one of the five bindings walks the whole
 *       {@link MorphManager#list} and morphs into the first morph whose stored
 *       {@code keybind} matches. Fed from {@link #onRawKey(int, int)} at the
 *       {@code Keyboard.onKey} mixin tail (the raw-key equivalent of legacy
 *       {@code Keyboard.getEventKey()}).</li>
 * </ul>
 *
 * <h2>Keycode contract</h2>
 *
 * <p>Stored morph {@code keybind} ints are <b>LWJGL2 keycodes</b> (a disk/wire
 * contract inside morph NBT), with the legacy {@code char + 256} synthetic for
 * character-only keys where {@code Keyboard.getEventKey()} returned 0. The GLFW
 * runtime key is <b>translated</b> to that space through {@link KeyCodes} (never
 * re-encoded) so old saved keybinds keep matching; see
 * {@link #legacyKeyCode(int, char)}.</p>
 *
 * <p>The creative ({@code B}), selector ({@code -}) and survival ({@code X})
 * branches all open their real screen through the {@link #setScreen} seam —
 * {@link GuiCreativeScreen} / {@link GuiSelectorsScreen} (P58) and the cached
 * {@link mchorse.metamorph.client.gui.survival.GuiSurvivalScreen} handed out by
 * {@link MetamorphClient#getSurvivalScreen(MinecraftClient)} (P61). Only the
 * survival one is cached, because legacy cached it on {@code ClientProxy} so
 * the menu keeps its scroll and selection between openings.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/client/KeyboardHandler.java
 */
public class KeyboardHandler
{
    public static final KeyboardHandler HANDLER = new KeyboardHandler();

    /* Action + menu keys */
    private KeyBinding keyAction;
    private KeyBinding keyCreativeMenu;
    private KeyBinding keySelectorMenu;
    private KeyBinding keySurvivalMenu;

    /* Morph related keys */
    public KeyBinding keyDemorph;

    private boolean registered;

    /**
     * Register the keybind family + the client tick hook (idempotent). The raw
     * {@code Keyboard.onKey} tail feeds {@link #onRawKey(int, int)} via mixin.
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
    }

    /**
     * Create the five keybinds in legacy registration order. Split out from
     * {@link #register()} so headless tests can build the table without the
     * global tick wiring (mirrors the bundled Aperture handler).
     */
    protected void createBindings()
    {
        String category = "key.metamorph";

        this.keyAction = this.create("key.metamorph.action", GLFW.GLFW_KEY_V, category);
        this.keyCreativeMenu = this.create("key.metamorph.creative_menu", GLFW.GLFW_KEY_B, category);
        this.keySelectorMenu = this.create("key.metamorph.selector_menu", GLFW.GLFW_KEY_MINUS, category);
        this.keySurvivalMenu = this.create("key.metamorph.survival_menu", GLFW.GLFW_KEY_X, category);

        this.keyDemorph = this.create("key.metamorph.demorph", GLFW.GLFW_KEY_PERIOD, category);
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

    private void onClientTick(MinecraftClient mc)
    {
        ClientPlayerEntity player = mc.player;

        if (player == null)
        {
            return;
        }

        IMorphing morphing = Morphing.get(player);
        boolean spectator = player.isSpectator();

        /* Creative morphs menu (OP / creative gated) */
        while (this.keyCreativeMenu.wasPressed())
        {
            if (CreativeMorphNetwork.INSTANCE.canUse(player))
            {
                this.setScreen(mc, new GuiCreativeScreen(mc));
            }
        }

        /* Entity selector menu */
        while (this.keySelectorMenu.wasPressed())
        {
            if (canEditSelectors())
            {
                this.setScreen(mc, new GuiSelectorsScreen(mc));
            }
        }

        /* Action — client-side instant feedback + a SEAM(P55) action packet */
        while (this.keyAction.wasPressed())
        {
            if (!spectator)
            {
                Dispatcher.sendToServer(new PacketAction());

                if (morphing != null && morphing.isMorphed())
                {
                    AbstractMorph morph = morphing.getCurrentMorph();

                    if (morph != null)
                    {
                        morph.action(player);
                    }
                }
            }
        }

        /* Survival morphs menu */
        while (this.keySurvivalMenu.wasPressed())
        {
            if (!spectator)
            {
                this.setScreen(mc, MetamorphClient.getSurvivalScreen(mc).open());
            }
        }

        /* Demorph from the current morph (legacy PacketSelectMorph(-1)) */
        while (this.keyDemorph.wasPressed())
        {
            if (!spectator && morphing != null && morphing.isMorphed())
            {
                CreativeMorphNetwork.INSTANCE.selectMorph(-1);
            }
        }
    }

    /**
     * The demorph keybind's currently bound key, in the legacy LWJGL2 keycode
     * space — legacy {@code ClientProxy.keys.keyDemorph.getKeyCode()}.
     *
     * <p>Two callers care: the survival menu's keybind capture field, which
     * <b>rejects</b> this key so a morph can never be bound to the key that
     * demorphs, and the survival screen's key handler, which demorphs when it
     * sees it. Both compare against stored morph keybinds, which are LWJGL2
     * codes, so the bound GLFW keysym is translated here rather than at each
     * call site. Returns {@link Keys#KEY_NONE} when the binding does not exist
     * yet (headless) or is unbound.</p>
     */
    public int demorphLegacyKeyCode()
    {
        if (this.keyDemorph == null || this.keyDemorph.isUnbound())
        {
            return Keys.KEY_NONE;
        }

        InputUtil.Key bound = KeyBindingHelper.getBoundKeyOf(this.keyDemorph);

        if (bound == null || bound.getCategory() != InputUtil.Type.KEYSYM)
        {
            return Keys.KEY_NONE;
        }

        return KeyCodes.glfwToLwjgl2(bound.getCode());
    }

    /**
     * Raw key hook (legacy tail of {@code onKey}). Called from the
     * {@code Keyboard.onKey} mixin at {@code TAIL} on a key <b>press</b> while no
     * screen is open. If the key is not one of the five metamorph bindings
     * (legacy {@code wasUsed} guard) it walks the global morph keybind list.
     *
     * @param glfwKey the GLFW keysym
     * @param scancode the GLFW scancode (unused on the keysym path; kept for the
     *        mixin signature)
     */
    public void onRawKey(int glfwKey, int scancode)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;

        if (player == null || mc.currentScreen != null)
        {
            return;
        }

        if (this.isBoundKey(glfwKey))
        {
            return;
        }

        dispatchGlobalKey(player, glfwKey, '\0');
    }

    /**
     * Whether the given GLFW keysym is bound to one of the five metamorph
     * keybinds (the {@code wasUsed} guard — a bound key never double-fires the
     * global dispatch).
     */
    private boolean isBoundKey(int glfwKey)
    {
        return matches(this.keyAction, glfwKey)
            || matches(this.keyCreativeMenu, glfwKey)
            || matches(this.keySelectorMenu, glfwKey)
            || matches(this.keySurvivalMenu, glfwKey)
            || matches(this.keyDemorph, glfwKey);
    }

    private static boolean matches(KeyBinding binding, int glfwKey)
    {
        return binding != null && !binding.isUnbound() && binding.matchesKey(glfwKey, 0);
    }

    /* ------------------------------------------------------------------ */
    /* Pure, headless-testable dispatch + keycode translation             */
    /* ------------------------------------------------------------------ */

    /**
     * Walk the global morph keybind list with the given raw key (legacy
     * {@code MorphManager.INSTANCE.list.keyTyped(player, key)}), translating the
     * GLFW key into the stored LWJGL2 keycode space first.
     *
     * @return true if a morph consumed the key
     */
    public static boolean dispatchGlobalKey(PlayerEntity player, int glfwKey, char typedChar)
    {
        int key = legacyKeyCode(glfwKey, typedChar);

        if (key == Keys.KEY_NONE)
        {
            return false;
        }

        return MorphManager.INSTANCE.list.keyTyped(player, key);
    }

    /**
     * Translate a GLFW key press into the legacy LWJGL2 keycode a stored morph
     * {@code keybind} is compared against.
     *
     * <ul>
     *   <li>A mapped GLFW keysym → its LWJGL2 keycode (via {@link KeyCodes}).</li>
     *   <li>An unmapped keysym with a typed character → {@code char + 256} — the
     *       legacy {@code Keyboard.getEventKey() == 0} synthetic, so old keybinds
     *       stored for character-only keys still round-trip.</li>
     *   <li>Otherwise {@link Keys#KEY_NONE} (0) — no keybind matches.</li>
     * </ul>
     */
    public static int legacyKeyCode(int glfwKey, char typedChar)
    {
        int legacy = KeyCodes.glfwToLwjgl2(glfwKey);

        if (legacy != Keys.KEY_NONE)
        {
            return legacy;
        }

        if (typedChar != '\0')
        {
            return typedChar + 256;
        }

        return Keys.KEY_NONE;
    }

    /**
     * Screen-open seam. Production calls {@code MinecraftClient.setScreen};
     * headless tests override it to record what would have opened, since
     * constructing the real screens needs a live client.
     */
    protected void setScreen(MinecraftClient mc, Screen screen)
    {
        mc.setScreen(screen);
    }

    /**
     * Selector-menu gate (legacy {@code ClientProxy.canEditSelectors}): the
     * {@code entity_selectors} OP-access toggle plus local-player OP.
     */
    private static boolean canEditSelectors()
    {
        return MetamorphClient.canEditSelectors();
    }
}
