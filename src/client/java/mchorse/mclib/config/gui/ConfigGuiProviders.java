package mchorse.mclib.config.gui;

import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiCirculateElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiToggleElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiColorElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiKeybindElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTextElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTexturePicker;
import mchorse.mclib.client.gui.framework.elements.input.GuiTrackpadElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiLabel;
import mchorse.mclib.client.gui.utils.Elements;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.config.values.IConfigGuiProvider;
import mchorse.mclib.config.values.Value;
import mchorse.mclib.config.values.ValueBoolean;
import mchorse.mclib.config.values.ValueDouble;
import mchorse.mclib.config.values.ValueFloat;
import mchorse.mclib.config.values.ValueInt;
import mchorse.mclib.config.values.ValueLong;
import mchorse.mclib.config.values.ValueRL;
import mchorse.mclib.config.values.ValueString;
import net.minecraft.client.MinecraftClient;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The S3 client-side value→widget factory registry (roadmap P44).
 *
 * <p>Legacy McLib put {@code getFields(Minecraft, GuiConfigPanel)} bodies on
 * the {@code Value*} classes themselves ({@code IConfigGuiProvider}); the S1
 * port stripped those because the value classes live in the main source set.
 * This registry holds the exact legacy bodies, keyed by value class:</p>
 *
 * <table>
 * <tr><th>Value type</th><th>Widget row</th></tr>
 * <tr><td>{@link ValueBoolean}</td><td>{@code GuiToggleElement} (flex reset — the toggle IS the row)</td></tr>
 * <tr><td>{@link ValueInt} INTEGER</td><td>label + {@code GuiTrackpadElement} (90 px)</td></tr>
 * <tr><td>{@link ValueInt} COLOR / COLOR_ALPHA</td><td>label + {@code GuiColorElement}</td></tr>
 * <tr><td>{@link ValueInt} KEYBIND / COMBOKEY</td><td>label + {@code GuiKeybindElement}</td></tr>
 * <tr><td>{@link ValueInt} MODES</td><td>label + {@code GuiCirculateElement} with the value's labels</td></tr>
 * <tr><td>{@link ValueLong}, {@link ValueFloat}, {@link ValueDouble}</td><td>label + {@code GuiTrackpadElement}</td></tr>
 * <tr><td>{@link ValueString}</td><td>label + {@code GuiTextElement}</td></tr>
 * <tr><td>{@link ValueRL}</td><td>label + "pick texture" button opening the shared {@code GuiTexturePicker} (P40)</td></tr>
 * </table>
 *
 * <p>Deliberately absent (legacy 2.4.3 parity): {@code ValueColors},
 * {@code ValueRotationOrder}, {@code ValueItemSlots}, {@code ValueColor} and
 * {@code ValueGUI} were <b>not</b> {@code IConfigGuiProvider}s — the config
 * panel simply skipped them (favorite colors are edited from the color
 * picker, rotation order/item slots from Blockbuster's own GUIs). Client-side
 * values (like P39's {@code ModKeybinds.KeybindCategory}) implement
 * {@link IConfigGuiProvider} directly and bypass this registry.</p>
 *
 * <p>The shared texture picker instance lives here (legacy field
 * {@code ValueRL.picker} — moved because {@code ValueRL} is main-source now);
 * the {@code KeyboardHandler} session cleanup nulls it exactly like legacy
 * nulled {@code ValueRL.picker}.</p>
 */
public class ConfigGuiProviders
{
    /**
     * Legacy {@code ValueRL.picker} — one picker shared by every RL row of
     * the session, cleared by {@code KeyboardHandler} on session end.
     */
    public static GuiTexturePicker picker;

    private static final Map<Class<? extends Value>, IValueGuiFactory<? extends Value>> FACTORIES = new LinkedHashMap<Class<? extends Value>, IValueGuiFactory<? extends Value>>();

    /**
     * A factory producing the config rows for one value type — the exact
     * shape of the legacy per-value {@code getFields} bodies, with the value
     * passed in instead of being {@code this}.
     */
    public interface IValueGuiFactory<T extends Value>
    {
        public List<GuiElement> getFields(MinecraftClient mc, GuiConfigPanel gui, T value);
    }

    static
    {
        register(ValueBoolean.class, ConfigGuiProviders::booleanFields);
        register(ValueInt.class, ConfigGuiProviders::intFields);
        register(ValueLong.class, ConfigGuiProviders::longFields);
        register(ValueFloat.class, ConfigGuiProviders::floatFields);
        register(ValueDouble.class, ConfigGuiProviders::doubleFields);
        register(ValueString.class, ConfigGuiProviders::stringFields);
        register(ValueRL.class, ConfigGuiProviders::rlFields);
    }

    public static <T extends Value> void register(Class<T> type, IValueGuiFactory<T> factory)
    {
        FACTORIES.put(type, factory);
    }

    public static boolean has(Value value)
    {
        return value instanceof IConfigGuiProvider || get(value.getClass()) != null;
    }

    public static IValueGuiFactory<? extends Value> get(Class<? extends Value> type)
    {
        Class<?> current = type;

        while (current != null && Value.class.isAssignableFrom(current))
        {
            IValueGuiFactory<? extends Value> factory = FACTORIES.get(current);

            if (factory != null)
            {
                return factory;
            }

            current = current.getSuperclass();
        }

        return null;
    }

    /**
     * The config panel's entry point: self-providing client-side values
     * first (legacy {@code instanceof IConfigGuiProvider} path), then the
     * registry; unproviderable values yield {@code null} (the panel skips
     * them, exactly like legacy skipped non-providers).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static List<GuiElement> getFields(MinecraftClient mc, GuiConfigPanel gui, Value value)
    {
        if (value instanceof IConfigGuiProvider)
        {
            return ((IConfigGuiProvider) value).getFields(mc, gui);
        }

        IValueGuiFactory factory = get(value.getClass());

        return factory == null ? null : factory.getFields(mc, gui, value);
    }

    /* Legacy getFields bodies, verbatim (ValueBoolean.getFields etc.) */

    private static List<GuiElement> booleanFields(MinecraftClient mc, GuiConfigPanel gui, ValueBoolean value)
    {
        GuiToggleElement toggle = new GuiToggleElement(mc, value);

        toggle.flex().reset();

        return Arrays.asList(toggle);
    }

    private static List<GuiElement> intFields(MinecraftClient mc, GuiConfigPanel gui, ValueInt value)
    {
        GuiElement element = new GuiElement(mc);
        GuiLabel label = Elements.label(IKey.lang(value.getLabelKey()), 0).anchor(0, 0.5F);

        element.flex().row(0).preferred(0).height(20);
        element.add(label);

        if (value.getSubtype() == ValueInt.Subtype.COLOR || value.getSubtype() == ValueInt.Subtype.COLOR_ALPHA)
        {
            GuiColorElement color = new GuiColorElement(mc, value);

            color.flex().w(90);
            element.add(color.removeTooltip());
        }
        else if (value.getSubtype() == ValueInt.Subtype.KEYBIND || value.getSubtype() == ValueInt.Subtype.COMBOKEY)
        {
            GuiKeybindElement keybind = new GuiKeybindElement(mc, value);

            keybind.flex().w(90);
            element.add(keybind.removeTooltip());
        }
        else if (value.getSubtype() == ValueInt.Subtype.MODES)
        {
            GuiCirculateElement button = new GuiCirculateElement(mc, null);

            for (IKey key : value.getLabels())
            {
                button.addLabel(key);
            }

            button.callback = (b) -> value.set(button.getValue());
            button.setValue(value.get());
            button.flex().w(90);
            element.add(button);
        }
        else
        {
            GuiTrackpadElement trackpad = new GuiTrackpadElement(mc, value);

            trackpad.flex().w(90);
            element.add(trackpad.removeTooltip());
        }

        return Arrays.asList(element.tooltip(IKey.lang(value.getCommentKey())));
    }

    private static List<GuiElement> longFields(MinecraftClient mc, GuiConfigPanel gui, ValueLong value)
    {
        GuiElement element = new GuiElement(mc);
        GuiLabel label = Elements.label(IKey.lang(value.getLabelKey()), 0).anchor(0, 0.5F);

        element.flex().row(0).preferred(0).height(20);
        element.add(label);

        GuiTrackpadElement trackpad = new GuiTrackpadElement(mc, value);

        trackpad.flex().w(90);
        element.add(trackpad.removeTooltip());

        return Arrays.asList(element.tooltip(IKey.lang(value.getCommentKey())));
    }

    private static List<GuiElement> floatFields(MinecraftClient mc, GuiConfigPanel gui, ValueFloat value)
    {
        GuiElement element = new GuiElement(mc);
        GuiLabel label = Elements.label(IKey.lang(value.getLabelKey()), 0).anchor(0, 0.5F);
        GuiTrackpadElement trackpad = new GuiTrackpadElement(mc, value);

        trackpad.flex().w(90);

        element.flex().row(0).preferred(0).height(20);
        element.add(label, trackpad.removeTooltip());

        return Arrays.asList(element.tooltip(IKey.lang(value.getCommentKey())));
    }

    private static List<GuiElement> doubleFields(MinecraftClient mc, GuiConfigPanel gui, ValueDouble value)
    {
        GuiElement element = new GuiElement(mc);
        GuiLabel label = Elements.label(IKey.lang(value.getLabelKey()), 0).anchor(0, 0.5F);
        GuiTrackpadElement trackpad = new GuiTrackpadElement(mc, value);

        trackpad.flex().w(90);

        element.flex().row(0).preferred(0).height(20);
        element.add(label, trackpad.removeTooltip());

        return Arrays.asList(element.tooltip(IKey.lang(value.getCommentKey())));
    }

    private static List<GuiElement> stringFields(MinecraftClient mc, GuiConfigPanel gui, ValueString value)
    {
        GuiElement element = new GuiElement(mc);
        /* Legacy quirk: ValueString resolved its keys through
         * getConfig().getValueLabelKey(this) instead of getLabelKey() —
         * identical result, kept verbatim */
        GuiLabel label = Elements.label(IKey.lang(value.getConfig().getValueLabelKey(value)), 0).anchor(0, 0.5F);
        GuiTextElement textbox = new GuiTextElement(mc, value);

        textbox.field.setMaxStringLength(10000);
        textbox.setText(value.get());
        textbox.flex().w(90);

        element.flex().row(0).preferred(0).height(20);
        element.add(label, textbox.removeTooltip());

        return Arrays.asList(element.tooltip(IKey.lang(value.getConfig().getValueCommentKey(value))));
    }

    private static List<GuiElement> rlFields(MinecraftClient mc, GuiConfigPanel gui, ValueRL value)
    {
        GuiElement element = new GuiElement(mc);
        GuiLabel label = Elements.label(IKey.lang(value.getLabelKey()), 0).anchor(0, 0.5F);
        GuiButtonElement pick = new GuiButtonElement(mc, IKey.lang("mclib.gui.pick_texture"), (button) ->
        {
            if (picker == null)
            {
                picker = new GuiTexturePicker(mc, null);
            }

            picker.callback = value::set;
            picker.fill(value.get());
            picker.flex().relative(gui).wh(1F, 1F);
            picker.resize();

            if (picker.hasParent())
            {
                picker.removeFromParent();
            }

            gui.add(picker);
        });

        pick.flex().w(90);

        element.flex().row(0).preferred(0).height(20);
        element.add(label, pick);

        return Arrays.asList(element.tooltip(IKey.lang(value.getCommentKey())));
    }
}
