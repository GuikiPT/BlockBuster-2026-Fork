package mchorse.mclib.utils;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.function.IntPredicate;

/**
 * Port of McLib 2.4.3's {@code utils/Keys.java} (roadmap P19) — the <b>pure
 * bit-packing half</b>, testable from the main source set.
 *
 * <p>All key codes here are <b>LWJGL2 scancodes</b> (what legacy configs store
 * on disk in packed combo-key ints — a persistence contract). The LWJGL2↔GLFW
 * translation used at the config-load/save boundary lands with S3 in
 * {@link KeyCodes}. The packed modifier bits 31/30/29 are keycode-agnostic.</p>
 *
 * <p>Split notes:</p>
 * <ul>
 * <li>{@code isKeyDown} polled LWJGL2's {@code Keyboard} — the polling variant
 * moves to the S3 client half (GLFW/InputUtil); the left/right pairing logic is
 * kept here behind an {@link IntPredicate} so it stays testable.</li>
 * <li>Legacy {@code Minecraft.IS_RUNNING_ON_MAC} decided Cmd-vs-Win naming for
 * the meta keys. {@code MinecraftClient.IS_SYSTEM_MAC} is client-only and not
 * reachable from main, so the non-Mac branch is hardcoded — TODO(S3): route
 * through the client half.</li>
 * </ul>
 */
public class Keys
{
    /* LWJGL2 scancodes used by this class (legacy org.lwjgl.input.Keyboard) */
    public static final int KEYBOARD_SIZE = 256;

    public static final int KEY_NONE = 0x00;
    public static final int KEY_MINUS = 0x0C;
    public static final int KEY_EQUALS = 0x0D;
    public static final int KEY_TAB = 0x0F;
    public static final int KEY_LBRACKET = 0x1A;
    public static final int KEY_RBRACKET = 0x1B;
    public static final int KEY_LCONTROL = 0x1D;
    public static final int KEY_SEMICOLON = 0x27;
    public static final int KEY_APOSTROPHE = 0x28;
    public static final int KEY_GRAVE = 0x29;
    public static final int KEY_LSHIFT = 0x2A;
    public static final int KEY_BACKSLASH = 0x2B;
    public static final int KEY_COMMA = 0x33;
    public static final int KEY_PERIOD = 0x34;
    public static final int KEY_SLASH = 0x35;
    public static final int KEY_RSHIFT = 0x36;
    public static final int KEY_MULTIPLY = 0x37;
    public static final int KEY_LMENU = 0x38;
    public static final int KEY_CAPITAL = 0x3A;
    public static final int KEY_SUBTRACT = 0x4A;
    public static final int KEY_ADD = 0x4E;
    public static final int KEY_DECIMAL = 0x53;
    public static final int KEY_RCONTROL = 0x9D;
    public static final int KEY_DIVIDE = 0xB5;
    public static final int KEY_RMENU = 0xB8;
    public static final int KEY_LMETA = 0xDB;
    public static final int KEY_RMETA = 0xDC;

    public static final String[] KEYS = new String[KEYBOARD_SIZE];
    public static final List<Integer> MODIFIERS = ImmutableList.<Integer>of(KEY_LCONTROL, KEY_LSHIFT, KEY_LMENU, KEY_RCONTROL, KEY_RSHIFT, KEY_RMENU);
    public static final String[] MODNAME = new String[] {"Ctrl", "Shift", "Alt"};

    /**
     * LWJGL2's raw key-name table ({@code Keyboard.getKeyName}) — field names
     * of {@code Keyboard.KEY_*} minus the prefix, indexed by scancode.
     * Unfilled entries are null (legacy returned "Unknown key" for those).
     */
    private static final String[] LWJGL2_NAMES = new String[KEYBOARD_SIZE];

    static
    {
        String[][] names = {
            {"0", "NONE"}, {"1", "ESCAPE"}, {"2", "1"}, {"3", "2"}, {"4", "3"}, {"5", "4"}, {"6", "5"},
            {"7", "6"}, {"8", "7"}, {"9", "8"}, {"10", "9"}, {"11", "0"}, {"12", "MINUS"}, {"13", "EQUALS"},
            {"14", "BACK"}, {"15", "TAB"}, {"16", "Q"}, {"17", "W"}, {"18", "E"}, {"19", "R"}, {"20", "T"},
            {"21", "Y"}, {"22", "U"}, {"23", "I"}, {"24", "O"}, {"25", "P"}, {"26", "LBRACKET"}, {"27", "RBRACKET"},
            {"28", "RETURN"}, {"29", "LCONTROL"}, {"30", "A"}, {"31", "S"}, {"32", "D"}, {"33", "F"}, {"34", "G"},
            {"35", "H"}, {"36", "J"}, {"37", "K"}, {"38", "L"}, {"39", "SEMICOLON"}, {"40", "APOSTROPHE"},
            {"41", "GRAVE"}, {"42", "LSHIFT"}, {"43", "BACKSLASH"}, {"44", "Z"}, {"45", "X"}, {"46", "C"},
            {"47", "V"}, {"48", "B"}, {"49", "N"}, {"50", "M"}, {"51", "COMMA"}, {"52", "PERIOD"}, {"53", "SLASH"},
            {"54", "RSHIFT"}, {"55", "MULTIPLY"}, {"56", "LMENU"}, {"57", "SPACE"}, {"58", "CAPITAL"},
            {"59", "F1"}, {"60", "F2"}, {"61", "F3"}, {"62", "F4"}, {"63", "F5"}, {"64", "F6"}, {"65", "F7"},
            {"66", "F8"}, {"67", "F9"}, {"68", "F10"}, {"69", "NUMLOCK"}, {"70", "SCROLL"}, {"71", "NUMPAD7"},
            {"72", "NUMPAD8"}, {"73", "NUMPAD9"}, {"74", "SUBTRACT"}, {"75", "NUMPAD4"}, {"76", "NUMPAD5"},
            {"77", "NUMPAD6"}, {"78", "ADD"}, {"79", "NUMPAD1"}, {"80", "NUMPAD2"}, {"81", "NUMPAD3"},
            {"82", "NUMPAD0"}, {"83", "DECIMAL"}, {"87", "F11"}, {"88", "F12"}, {"100", "F13"}, {"101", "F14"},
            {"102", "F15"}, {"103", "F16"}, {"104", "F17"}, {"105", "F18"}, {"112", "KANA"}, {"113", "F19"},
            {"121", "CONVERT"}, {"123", "NOCONVERT"}, {"125", "YEN"}, {"141", "NUMPADEQUALS"}, {"144", "CIRCUMFLEX"},
            {"145", "AT"}, {"146", "COLON"}, {"147", "UNDERLINE"}, {"148", "KANJI"}, {"149", "STOP"}, {"150", "AX"},
            {"151", "UNLABELED"}, {"156", "NUMPADENTER"}, {"157", "RCONTROL"}, {"163", "SECTION"},
            {"179", "NUMPADCOMMA"}, {"181", "DIVIDE"}, {"183", "SYSRQ"}, {"184", "RMENU"}, {"196", "FUNCTION"},
            {"197", "PAUSE"}, {"199", "HOME"}, {"200", "UP"}, {"201", "PRIOR"}, {"203", "LEFT"}, {"205", "RIGHT"},
            {"207", "END"}, {"208", "DOWN"}, {"209", "NEXT"}, {"210", "INSERT"}, {"211", "DELETE"},
            {"219", "LMETA"}, {"220", "RMETA"}, {"221", "APPS"}, {"222", "POWER"}, {"223", "SLEEP"}
        };

        for (String[] entry : names)
        {
            LWJGL2_NAMES[Integer.parseInt(entry[0])] = entry[1];
        }
    }

    public static String getKeyName(int key)
    {
        if (key < KEY_NONE || key >= KEYBOARD_SIZE)
        {
            return null;
        }

        /* Adding this line prevents a null-pointer exception */
        if (KEYS[key] == null)
        {
            KEYS[key] = getKey(key);

            if (KEYS[key] == null)
            {
                return "Unknown key";
            }
        }

        return KEYS[key];
    }

    private static String getKey(int key)
    {
        switch (key)
        {
            case KEY_MINUS:
                return "-";
            case KEY_EQUALS:
                return "=";
            case KEY_LBRACKET:
                return "[";
            case KEY_RBRACKET:
                return "]";
            case KEY_SEMICOLON:
                return ";";
            case KEY_APOSTROPHE:
                return "'";
            case KEY_BACKSLASH:
                return "\\";
            case KEY_COMMA:
                return ",";
            case KEY_PERIOD:
                return ".";
            case KEY_SLASH:
                return "/";
            case KEY_GRAVE:
                return "`";
            case KEY_TAB:
                return "Tab";
            case KEY_CAPITAL:
                return "Caps Lock";
            case KEY_LSHIFT:
                return "L. Shift";
            case KEY_LCONTROL:
                return "L. Ctrl";
            case KEY_LMENU:
                return "L. Alt";
            case KEY_LMETA:
                /* TODO(S3): "L. Cmd" on macOS (client-side check) */
                return "L. Win";
            case KEY_RSHIFT:
                return "R. Shift";
            case KEY_RCONTROL:
                return "R. Ctrl";
            case KEY_RMENU:
                return "R. Alt";
            case KEY_RMETA:
                /* TODO(S3): "R. Cmd" on macOS (client-side check) */
                return "R. Win";
            case KEY_DIVIDE:
                return "Numpad /";
            case KEY_MULTIPLY:
                return "Numpad *";
            case KEY_SUBTRACT:
                return "Numpad -";
            case KEY_ADD:
                return "Numpad +";
            case KEY_DECIMAL:
                return "Numpad .";
        }

        String name = LWJGL2_NAMES[key];

        /* Adding this line prevents a null-pointer exception */
        if (name == null)
        {
            return null;
        }

        if (name.length() > 1)
        {
            name = name.substring(0, 1) + name.substring(1).toLowerCase();
        }

        if (name.startsWith("Numpad"))
        {
            name = name.replace("Numpad", "Numpad ");
        }

        return name;
    }

    /* Combo keys */

    public static int getComboKeyCode(int[] held, int keyCode)
    {
        int comboKey = keyCode;
        int modifierIndex = MODIFIERS.indexOf(keyCode) % 3;

        if (held != null)
        {
            for (int heldKey : held)
            {
                int index = MODIFIERS.indexOf(heldKey) % 3;

                if (index >= 0 && index != modifierIndex)
                {
                    comboKey |= 1 << 31 - index;
                }
            }
        }

        return comboKey;
    }

    public static int getMainKey(int comboKey)
    {
        int key = comboKey & 0x1FFFFFFF;

        if (key >= KEYBOARD_SIZE)
        {
            key = KEY_NONE;
        }

        return key;
    }

    public static String getComboKeyName(int comboKey)
    {
        StringBuilder builder = new StringBuilder();
        int mainKey = getMainKey(comboKey);

        if (mainKey == KEY_NONE)
        {
            return getKeyName(mainKey);
        }

        for (int i = 0; i < 3; i++)
        {
            if ((comboKey & 1 << 31 - i) != 0)
            {
                builder.append(MODNAME[i]).append(" + ");
            }
        }

        builder.append(getKeyName(mainKey));

        return builder.toString();
    }

    /**
     * The raw "is this LWJGL2 scancode held" oracle used by the single-arg
     * {@link #isKeyDown(int)}/{@link #checkModifierKeys(int)} (P39). Defaults
     * to "nothing held" so the main source set stays headless-safe; the S3
     * client half installs the GLFW-backed poller
     * ({@code GuiUtils.installKeyPoller()}), and tests may override it.
     */
    public static IntPredicate keyDownPoller = key -> false;

    /** Legacy single-arg {@code checkModifierKeys} over {@link #keyDownPoller}. */
    public static boolean checkModifierKeys(int comboKey)
    {
        return checkModifierKeys(comboKey, keyDownPoller);
    }

    /** Legacy single-arg {@code isKeyDown} over {@link #keyDownPoller}. */
    public static boolean isKeyDown(int key)
    {
        return isKeyDown(key, keyDownPoller);
    }

    /**
     * Legacy {@code checkModifierKeys(int)} polled LWJGL2 directly. The port
     * takes the raw "is this scancode held" oracle as a predicate; the S3
     * client half supplies the GLFW-backed one.
     */
    public static boolean checkModifierKeys(int comboKey, IntPredicate isRawKeyDown)
    {
        int index = MODIFIERS.indexOf(getMainKey(comboKey)) % 3;

        for (int i = 0; i < 3; i++)
        {
            if (i == index)
            {
                continue;
            }

            if ((comboKey & 1 << 31 - i) != 0 != isKeyDown(MODIFIERS.get(i), isRawKeyDown))
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Left/right modifier pairing (legacy {@code isKeyDown}): either side of a
     * modifier pair counts as the modifier being held.
     */
    public static boolean isKeyDown(int key, IntPredicate isRawKeyDown)
    {
        if (key == KEY_LSHIFT || key == KEY_RSHIFT)
        {
            return isRawKeyDown.test(KEY_LSHIFT) || isRawKeyDown.test(KEY_RSHIFT);
        }
        else if (key == KEY_LCONTROL || key == KEY_RCONTROL)
        {
            return isRawKeyDown.test(KEY_LCONTROL) || isRawKeyDown.test(KEY_RCONTROL);
        }
        else if (key == KEY_LMENU || key == KEY_RMENU)
        {
            return isRawKeyDown.test(KEY_LMENU) || isRawKeyDown.test(KEY_RMENU);
        }

        return isRawKeyDown.test(key);
    }
}
