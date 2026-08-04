package mchorse.mclib;

import mchorse.mclib.client.gui.utils.ValueColors;
import mchorse.mclib.commands.utils.L10n;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.config.ConfigBuilder;
import mchorse.mclib.config.values.ValueBoolean;
import mchorse.mclib.config.values.ValueInt;
import mchorse.mclib.config.values.ValueRL;
import mchorse.mclib.events.RegisterConfigEvent;
import mchorse.mclib.events.RegisterPermissionsEvent;
import mchorse.mclib.permissions.DefaultPermissionLevel;
import mchorse.mclib.permissions.McLibPermissions;
import mchorse.mclib.permissions.PermissionCategory;
import mchorse.mclib.permissions.PermissionFactory;
import mchorse.mclib.utils.ColorUtils;
import mchorse.mclib.utils.PayloadASM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bundled McLib static holder — port of legacy {@code mchorse.mclib.McLib}
 * (roadmap P22). Forge {@code @Mod} lifecycle is replaced by explicit calls
 * from the Blockbuster mod initializer; {@code EVENT_BUS} is replaced by
 * {@link mchorse.mclib.events.McLibEvents}.
 *
 * <p>Port deviation (recorded): legacy left the config {@code Value*} statics
 * null until {@code onConfigRegister} ran at startup. Here they are
 * initialized inline with the same defaults so headless tests and
 * pre-registration reads are safe; {@link #onConfigRegister} re-registers the
 * SAME instances into the config tree (via {@code ConfigBuilder.register}),
 * so post-registration they are config-backed and persistent with the exact
 * legacy category/id layout.</p>
 */
public class McLib
{
    public static final String MOD_ID = "mclib";
    public static final String VERSION = "2.4.3";

    public static final Logger LOGGER = LoggerFactory.getLogger(McLib.MOD_ID);

    public static L10n l10n = new L10n(MOD_ID);

    /**
     * A factory containing all permissions that have been registered through the {@link RegisterPermissionsEvent}.
     * (Same instance as {@link PermissionFactory#INSTANCE}, which S2 packets use.)
     */
    public static final PermissionFactory permissionFactory = PermissionFactory.INSTANCE;

    /**
     * P44 seam: the legacy sided proxy holding the live
     * {@code ConfigManager} (see {@link CommonProxy}).
     */
    public static CommonProxy proxy = new CommonProxy();

    /* Configuration (legacy defaults from McLib.onConfigRegister, except
     * primaryColor — see its javadoc for the one recorded deviation) */
    public static ValueBoolean opDropItems = new ValueBoolean("drop_items", true);

    /* Appearance category */
    public static ValueBoolean debugPanel = (ValueBoolean) new ValueBoolean("debug_panel", false).invisible();
    public static ValueColors favoriteColors = new ValueColors("favorite_colors");
    /**
     * The GUI accent colour — every button fill, tab/list highlight, toggle,
     * dragged-trackpad bar, context-menu hover, drop shadow and dark tooltip
     * border in the bundled McLib/Metamorph/Aperture/Blockbuster GUIs reads
     * this one value.
     *
     * <p><b>Deliberate deviation from the 1.12.2 bar</b> (user-requested,
     * 2026-07-26): legacy McLib 2.4.3 defaulted this to {@code 0x0088ff}
     * (blue); the port defaults to {@code 0xcc0000} (red). This is a *default*
     * only — the value stays user-configurable under
     * {@code appearance/primary_color}, so anyone who wants the legacy blue
     * types it back in. Recorded in {@code plan/CROSS_CUTTING.md} §1.10.</p>
     *
     * <p>Format: {@code .color()} means RGB with <b>no</b> alpha byte — call
     * sites add their own (e.g. {@code 0xff000000 + McLib.primaryColor.get()},
     * {@code ColorUtils.HALF_BLACK + McLib.primaryColor.get()}). Storing an
     * {@code 0xAARRGGBB} value here would double-add alpha and overflow.</p>
     */
    public static ValueInt primaryColor = new ValueInt("primary_color", 0xcc0000).color();
    public static ValueBoolean enableBorders = new ValueBoolean("enable_borders", false);
    public static ValueBoolean enableCheckboxRendering = new ValueBoolean("enable_checkbox_rendering", false);
    public static ValueBoolean enableTrackpadIncrements = new ValueBoolean("enable_trackpad_increments", true);
    public static ValueBoolean enableGridRendering = new ValueBoolean("enable_grid_rendering", true);
    public static ValueInt userIntefaceScale = new ValueInt("user_interface_scale", 2, 0, 4);
    public static ValueInt tooltipStyle = new ValueInt("tooltip_style", 1, 0, 1);
    public static ValueInt trackpadDecimalPlaces = new ValueInt("trackpad_decimal_places", 6, 3, 31);
    public static ValueBoolean renderTranslateTextColors = new ValueBoolean("render_translation_text_colours", false);

    /* Tutorials category */
    public static ValueBoolean enableCursorRendering = new ValueBoolean("enable_mouse_rendering", false);
    public static ValueBoolean enableMouseButtonRendering = new ValueBoolean("enable_mouse_buttons_rendering", false);
    public static ValueBoolean enableKeystrokeRendering = new ValueBoolean("enable_keystrokes_rendering", false);
    public static ValueInt keystrokeOffset = new ValueInt("keystroke_offset", 10, 0, 20);
    public static ValueInt keystrokeMode = new ValueInt("keystroke_position", 1, 0, 4);

    /* Background category */
    public static ValueRL backgroundImage = new ValueRL("image", null);
    public static ValueInt backgroundColor = new ValueInt("color", 0xcc000000).colorAlpha();

    /* Scrollbars category */
    public static ValueBoolean scrollbarFlat = new ValueBoolean("flat", false);
    public static ValueInt scrollbarShadow = new ValueInt("shadow", ColorUtils.HALF_BLACK).colorAlpha();
    public static ValueInt scrollbarWidth = new ValueInt("width", 4, 2, 10);

    /* Multiskin category */
    public static ValueBoolean multiskinMultiThreaded = new ValueBoolean("multithreaded", true);
    public static ValueBoolean multiskinClear = new ValueBoolean("clear", true);

    /* Vanilla category */
    public static ValueInt maxPacketSize = new ValueInt("max_packet_size", PayloadASM.MIN_SIZE, PayloadASM.MIN_SIZE, Integer.MAX_VALUE / 4);

    /**
     * Legacy {@code McLib.onConfigRegister} — identical category/id layout;
     * registers the pre-built static instances (see class javadoc).
     */
    public static void onConfigRegister(RegisterConfigEvent event)
    {
        event.opAccess.category(MOD_ID).register(opDropItems);

        /* McLib's options */
        ConfigBuilder builder = event.createBuilder(MOD_ID);

        /* Appearance category */
        builder.category("appearance").register(debugPanel);
        builder.register(primaryColor);
        builder.register(enableBorders);
        builder.register(enableCheckboxRendering);
        builder.register(enableTrackpadIncrements);
        builder.register(trackpadDecimalPlaces);
        builder.register(enableGridRendering);
        builder.register(userIntefaceScale);
        tooltipStyle.modes(
            IKey.lang("mclib.tooltip_style.light"),
            IKey.lang("mclib.tooltip_style.dark")
        );
        builder.register(tooltipStyle);
        builder.register(renderTranslateTextColors);

        builder.register(favoriteColors);

        builder.getCategory().markClientSide();

        /* Tutorials category */
        builder.category("tutorials").register(enableCursorRendering);
        builder.register(enableMouseButtonRendering);
        builder.register(enableKeystrokeRendering);
        builder.register(keystrokeOffset);
        keystrokeMode.modes(
            IKey.lang("mclib.keystrokes_position.auto"),
            IKey.lang("mclib.keystrokes_position.bottom_left"),
            IKey.lang("mclib.keystrokes_position.bottom_right"),
            IKey.lang("mclib.keystrokes_position.top_right"),
            IKey.lang("mclib.keystrokes_position.top_left")
        );
        builder.register(keystrokeMode);

        builder.getCategory().markClientSide();

        /* Background category */
        builder.category("background").register(backgroundImage);
        builder.register(backgroundColor);

        builder.getCategory().markClientSide();

        /* Scrollbars category */
        builder.category("scrollbars").register(scrollbarFlat);
        builder.register(scrollbarShadow);
        builder.register(scrollbarWidth);

        builder.getCategory().markClientSide();

        /* Multiskin category */
        builder.category("multiskin").register(multiskinMultiThreaded);
        builder.register(multiskinClear);

        builder.getCategory().markClientSide();

        /* Vanilla category */
        builder.category("vanilla").register(maxPacketSize);
        maxPacketSize.syncable();
    }

    /**
     * Legacy {@code McLib.onPermissionRegister}.
     */
    public static void onPermissionRegister(RegisterPermissionsEvent event)
    {
        event.registerMod(MOD_ID, DefaultPermissionLevel.OP);

        event.registerPermission(McLibPermissions.configEdit = new PermissionCategory("edit_config"));

        event.registerCategory(new PermissionCategory("gui"));
        event.registerPermission(McLibPermissions.accessGui = new PermissionCategory("access_gui"));

        event.endMod();
    }
}
