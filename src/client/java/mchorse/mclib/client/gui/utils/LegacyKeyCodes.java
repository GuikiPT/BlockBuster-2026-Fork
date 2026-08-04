package mchorse.mclib.client.gui.utils;

import mchorse.mclib.utils.KeyCodes;

/**
 * GLFW → legacy LWJGL2 keycode translation (roadmap P29 boundary shim).
 *
 * The whole bundled GUI framework keeps 1.12.2's LWJGL2 {@code Keyboard.KEY_*}
 * codes as its internal (and, since P39, persistent/wire) representation, so
 * {@code config/mclib/keybinds.json} files from 1.12.2 installs keep working.
 * {@code GuiBase} translates GLFW keycodes through this table at the vanilla
 * {@code Screen.keyPressed} boundary — the only place GLFW codes exist.
 *
 * P39 absorbed the actual table into {@link KeyCodes} (main source set, both
 * directions, pinned by test); this class stays as the client-side facade the
 * P29 core compiled against.
 */
public class LegacyKeyCodes
{
    /* LWJGL2 constants used directly by the framework */
    public static final int KEY_NONE = 0;
    public static final int KEY_ESCAPE = 1;
    public static final int KEY_TAB = 15;
    public static final int KEY_RETURN = 28;
    public static final int KEY_UP = 200;
    public static final int KEY_DOWN = 208;
    public static final int KEY_F9 = 67;

    /* P43/P44 additions (LWJGL2 Keyboard.KEY_* values) */
    public static final int KEY_A = 30;
    public static final int KEY_LCONTROL = 29;
    public static final int KEY_HOME = 199;
    public static final int KEY_NUMPAD0 = 82;
    public static final int KEY_NUMPAD1 = 79;
    public static final int KEY_NUMPAD2 = 80;
    public static final int KEY_NUMPAD3 = 81;
    public static final int KEY_NUMPAD4 = 75;
    public static final int KEY_NUMPAD5 = 76;
    public static final int KEY_NUMPAD6 = 77;
    public static final int KEY_NUMPAD7 = 71;
    public static final int KEY_NUMPAD8 = 72;
    public static final int KEY_NUMPAD9 = 73;

    /* P42 additions — GuiModelRenderer flight keys (LWJGL2 Keyboard.KEY_* values) */
    public static final int KEY_W = 17;
    public static final int KEY_S = 31;
    public static final int KEY_D = 32;
    public static final int KEY_SPACE = 57;
    public static final int KEY_LSHIFT = 42;
    public static final int KEY_LMENU = 56;

    /* P183 additions — camera editor keybind roster (LWJGL2 Keyboard.KEY_* values) */
    public static final int KEY_1 = 2;
    public static final int KEY_F1 = 59;
    public static final int KEY_B = 48;
    public static final int KEY_C = 46;
    public static final int KEY_F = 33;
    public static final int KEY_I = 23;
    public static final int KEY_L = 38;
    public static final int KEY_M = 50;
    public static final int KEY_N = 49;
    public static final int KEY_O = 24;
    public static final int KEY_V = 47;
    public static final int KEY_Y = 21;
    public static final int KEY_Z = 44;
    public static final int KEY_LEFT = 203;
    public static final int KEY_RIGHT = 205;
    public static final int KEY_LBRACKET = 26;
    public static final int KEY_RBRACKET = 27;

    /* P61 additions — survival morph menu keybinds (LWJGL2 Keyboard.KEY_* values) */
    public static final int KEY_BACK = 14;
    public static final int KEY_K = 37;
    /** The demorph keybind's default, which the survival menu refuses to reassign. */
    public static final int KEY_PERIOD = 52;

    /* P184 additions — fixture panel keybinds (LWJGL2 Keyboard.KEY_* values) */
    public static final int KEY_E = 18;
    public static final int KEY_R = 19;
    public static final int KEY_P = 25;

    /* Wave B additions — creative morphs / recording editor keybinds (LWJGL2 Keyboard.KEY_* values) */
    public static final int KEY_Q = 16;
    public static final int KEY_T = 20;
    public static final int KEY_X = 45;
    public static final int KEY_DELETE = 211;

    /* S18 P204 — the model editor's transparent-screenshot key (LWJGL2 Keyboard.KEY_F2). */
    public static final int KEY_F2 = 60;

    /**
     * Translate a GLFW keycode into the legacy LWJGL2 keycode; unknown or
     * unmapped keys become {@link #KEY_NONE} (0), which no legacy keybind
     * matches.
     */
    public static int glfwToLegacy(int glfwKeyCode)
    {
        return KeyCodes.glfwToLwjgl2(glfwKeyCode);
    }
}
