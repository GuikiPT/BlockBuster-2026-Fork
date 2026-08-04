package mchorse.mclib.utils;

/**
 * LWJGL2 ↔ GLFW keycode translation table (roadmap P39).
 *
 * <p>Legacy configs store <b>LWJGL2 scancodes</b> (e.g. {@code KEY_LSHIFT =
 * 42}) inside packed combo-key ints (see {@link Keys}) — a persistence/wire
 * contract frozen by 1.12.2's {@code config/mclib/keybinds.json}. 1.20.4
 * delivers GLFW keycodes at runtime, so this class holds the well-known
 * static mapping, applied only at the runtime boundary (screen key events,
 * GLFW key polling); everything inside the framework stays LWJGL2. The
 * packed modifier bits 31/30/29 are keycode-agnostic and need no
 * translation.</p>
 *
 * <p>Conventions (pinned by {@code KeyCodesTest}):</p>
 * <ul>
 * <li>{@link #glfwToLwjgl2}: unknown/unmapped GLFW code → {@code 0}
 * ({@code KEY_NONE}) — no legacy keybind matches it.</li>
 * <li>{@link #lwjgl2ToGlfw}: unknown/unmapped legacy code → {@code -1}
 * ({@code GLFW_KEY_UNKNOWN}) — GLFW polling of it is refused.</li>
 * <li>The mapping is involutive on its domain:
 * {@code glfwToLwjgl2(lwjgl2ToGlfw(k)) == k} for every mapped legacy k, and
 * vice versa.</li>
 * </ul>
 *
 * <p>GLFW constants are inlined as literals (with names in comments) so the
 * main source set does not depend on org.lwjgl; the values are the frozen
 * GLFW 3.x API constants (same ints the client's {@code org.lwjgl.glfw.GLFW}
 * exposes). LWJGL2 keys with no GLFW equivalent (KANA, CONVERT, NOCONVERT,
 * YEN, CIRCUMFLEX, AT, COLON, UNDERLINE, KANJI, STOP, AX, UNLABELED,
 * SECTION, NUMPADCOMMA, FUNCTION, POWER, SLEEP) map to -1; GLFW keys with no
 * LWJGL2 equivalent (WORLD_1/2, F20–F25) map to 0.</p>
 */
public final class KeyCodes
{
    public static final int GLFW_KEY_UNKNOWN = -1;
    public static final int GLFW_KEY_LAST = 348;

    /** Indexed by GLFW keycode → LWJGL2 scancode (0 = none). */
    private static final int[] GLFW_TO_LWJGL2 = new int[GLFW_KEY_LAST + 1];

    /** Indexed by LWJGL2 scancode → GLFW keycode (-1 = unknown). */
    private static final int[] LWJGL2_TO_GLFW = new int[Keys.KEYBOARD_SIZE];

    /** {lwjgl2, glfw} pairs — the involutive domain. */
    private static final int[][] PAIRS = {
        /* Top row / printable (GLFW uses ASCII codes) */
        {1, 256},   /* ESCAPE */
        {2, 49},    /* 1 */
        {3, 50},    /* 2 */
        {4, 51},    /* 3 */
        {5, 52},    /* 4 */
        {6, 53},    /* 5 */
        {7, 54},    /* 6 */
        {8, 55},    /* 7 */
        {9, 56},    /* 8 */
        {10, 57},   /* 9 */
        {11, 48},   /* 0 */
        {12, 45},   /* MINUS */
        {13, 61},   /* EQUALS / GLFW_KEY_EQUAL */
        {14, 259},  /* BACK / GLFW_KEY_BACKSPACE */
        {15, 258},  /* TAB */
        {16, 81},   /* Q */
        {17, 87},   /* W */
        {18, 69},   /* E */
        {19, 82},   /* R */
        {20, 84},   /* T */
        {21, 89},   /* Y */
        {22, 85},   /* U */
        {23, 73},   /* I */
        {24, 79},   /* O */
        {25, 80},   /* P */
        {26, 91},   /* LBRACKET */
        {27, 93},   /* RBRACKET */
        {28, 257},  /* RETURN / GLFW_KEY_ENTER */
        {29, 341},  /* LCONTROL */
        {30, 65},   /* A */
        {31, 83},   /* S */
        {32, 68},   /* D */
        {33, 70},   /* F */
        {34, 71},   /* G */
        {35, 72},   /* H */
        {36, 74},   /* J */
        {37, 75},   /* K */
        {38, 76},   /* L */
        {39, 59},   /* SEMICOLON */
        {40, 39},   /* APOSTROPHE */
        {41, 96},   /* GRAVE / GLFW_KEY_GRAVE_ACCENT */
        {42, 340},  /* LSHIFT */
        {43, 92},   /* BACKSLASH */
        {44, 90},   /* Z */
        {45, 88},   /* X */
        {46, 67},   /* C */
        {47, 86},   /* V */
        {48, 66},   /* B */
        {49, 78},   /* N */
        {50, 77},   /* M */
        {51, 44},   /* COMMA */
        {52, 46},   /* PERIOD */
        {53, 47},   /* SLASH */
        {54, 344},  /* RSHIFT */
        {55, 332},  /* MULTIPLY / GLFW_KEY_KP_MULTIPLY */
        {56, 342},  /* LMENU / GLFW_KEY_LEFT_ALT */
        {57, 32},   /* SPACE */
        {58, 280},  /* CAPITAL / GLFW_KEY_CAPS_LOCK */
        {59, 290},  /* F1 */
        {60, 291},  /* F2 */
        {61, 292},  /* F3 */
        {62, 293},  /* F4 */
        {63, 294},  /* F5 */
        {64, 295},  /* F6 */
        {65, 296},  /* F7 */
        {66, 297},  /* F8 */
        {67, 298},  /* F9 */
        {68, 299},  /* F10 */
        {69, 282},  /* NUMLOCK / GLFW_KEY_NUM_LOCK */
        {70, 281},  /* SCROLL / GLFW_KEY_SCROLL_LOCK */
        {71, 327},  /* NUMPAD7 */
        {72, 328},  /* NUMPAD8 */
        {73, 329},  /* NUMPAD9 */
        {74, 333},  /* SUBTRACT / GLFW_KEY_KP_SUBTRACT */
        {75, 324},  /* NUMPAD4 */
        {76, 325},  /* NUMPAD5 */
        {77, 326},  /* NUMPAD6 */
        {78, 334},  /* ADD / GLFW_KEY_KP_ADD */
        {79, 321},  /* NUMPAD1 */
        {80, 322},  /* NUMPAD2 */
        {81, 323},  /* NUMPAD3 */
        {82, 320},  /* NUMPAD0 */
        {83, 330},  /* DECIMAL / GLFW_KEY_KP_DECIMAL */
        {87, 300},  /* F11 */
        {88, 301},  /* F12 */
        {100, 302}, /* F13 */
        {101, 303}, /* F14 */
        {102, 304}, /* F15 */
        {103, 305}, /* F16 */
        {104, 306}, /* F17 */
        {105, 307}, /* F18 */
        {113, 308}, /* F19 */
        {141, 336}, /* NUMPADEQUALS / GLFW_KEY_KP_EQUAL */
        {156, 335}, /* NUMPADENTER / GLFW_KEY_KP_ENTER */
        {157, 345}, /* RCONTROL */
        {181, 331}, /* DIVIDE / GLFW_KEY_KP_DIVIDE */
        {183, 283}, /* SYSRQ / GLFW_KEY_PRINT_SCREEN */
        {184, 346}, /* RMENU / GLFW_KEY_RIGHT_ALT */
        {197, 284}, /* PAUSE */
        {199, 268}, /* HOME */
        {200, 265}, /* UP */
        {201, 266}, /* PRIOR / GLFW_KEY_PAGE_UP */
        {203, 263}, /* LEFT */
        {205, 262}, /* RIGHT */
        {207, 269}, /* END */
        {208, 264}, /* DOWN */
        {209, 267}, /* NEXT / GLFW_KEY_PAGE_DOWN */
        {210, 260}, /* INSERT */
        {211, 261}, /* DELETE */
        {219, 343}, /* LMETA / GLFW_KEY_LEFT_SUPER */
        {220, 347}, /* RMETA / GLFW_KEY_RIGHT_SUPER */
        {221, 348}, /* APPS / GLFW_KEY_MENU */
    };

    static
    {
        for (int i = 0; i < LWJGL2_TO_GLFW.length; i++)
        {
            LWJGL2_TO_GLFW[i] = GLFW_KEY_UNKNOWN;
        }

        for (int[] pair : PAIRS)
        {
            LWJGL2_TO_GLFW[pair[0]] = pair[1];
            GLFW_TO_LWJGL2[pair[1]] = pair[0];
        }

        /* KEY_NONE (0) is "no key" on both sides of the boundary */
        LWJGL2_TO_GLFW[0] = GLFW_KEY_UNKNOWN;
    }

    private KeyCodes()
    {}

    /**
     * GLFW keycode → legacy LWJGL2 scancode; unknown/unmapped → 0
     * ({@code Keys.KEY_NONE}).
     */
    public static int glfwToLwjgl2(int glfwKeyCode)
    {
        if (glfwKeyCode < 0 || glfwKeyCode >= GLFW_TO_LWJGL2.length)
        {
            return Keys.KEY_NONE;
        }

        return GLFW_TO_LWJGL2[glfwKeyCode];
    }

    /**
     * Legacy LWJGL2 scancode → GLFW keycode; unknown/unmapped → -1
     * ({@code GLFW_KEY_UNKNOWN}).
     */
    public static int lwjgl2ToGlfw(int lwjgl2KeyCode)
    {
        if (lwjgl2KeyCode < 0 || lwjgl2KeyCode >= LWJGL2_TO_GLFW.length)
        {
            return GLFW_KEY_UNKNOWN;
        }

        return LWJGL2_TO_GLFW[lwjgl2KeyCode];
    }
}
