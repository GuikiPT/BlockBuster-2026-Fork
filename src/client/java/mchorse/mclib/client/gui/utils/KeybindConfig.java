package mchorse.mclib.client.gui.utils;

import java.io.File;
import java.util.Map;

import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.config.Config;
import mchorse.mclib.config.json.ConfigParser;
import mchorse.mclib.config.values.Value;
import mchorse.mclib.config.values.ValueInt;
import mchorse.mclib.utils.Keys;

/**
 * Port of McLib 2.4.3's {@code KeybindConfig} (roadmap P39) — rebind
 * persistence at {@code config/mclib/keybinds.json}.
 *
 * File structure (1.12.2 contract): modid → category → label → int combo
 * code, where the category/label keys are {@code KeyParser} JSON tokens
 * (e.g. {@code {"lang.key":[]}}) — the lang keys ARE the file keys.
 *
 * Port notes:
 * - Legacy iterated Forge's active-mod list to seed one {@code ModKeybinds}
 *   per mod. The bundled port seeds the bundled subsystems (matching the
 *   modid sections a legacy file used) plus the {@code ""} fallback.
 * - Keybinds whose main key is ESC are refused registration (prevents
 *   un-closeable screens) — kept.
 * - Unchanged defaults are not saved ({@code hasChanged()} guard in
 *   {@code ModKeybinds.addKeybind}) so a byte-diff against a legacy file
 *   shows no spurious entries.
 */
public class KeybindConfig extends Config
{
    /**
     * The bundled subsystems that register keybinds — the modid sections of
     * a 1.12.2 {@code keybinds.json}.
     */
    public static final String[] MOD_IDS = {"mclib", "blockbuster", "metamorph", "aperture"};

    public transient Map<String, IKey> keyMap;

    public KeybindConfig(File configFolder)
    {
        super("keybinds", new File(configFolder, "mclib/keybinds.json"));

        this.load();
    }

    public void addKeybind(Keybind key)
    {
        if (Keys.getMainKey(key.keyCode) == 1 /* Keyboard.KEY_ESCAPE */)
        {
            return;
        }

        String modid = key.modid;
        ModKeybinds mod = (ModKeybinds) this.values.get(modid);

        if (mod == null)
        {
            /* Total: unknown modid falls into the "" section (legacy would
             * have NPE'd; a bundled build can register from new packages) */
            mod = (ModKeybinds) this.values.get("");
        }

        mod.addKeybind(key);
    }

    public void updateCategory(Keybind key, IKey categoryKey)
    {
        if (key.category != IKey.EMPTY)
        {
            return;
        }

        Value mod = this.values.get(key.modid);

        if (mod == null)
        {
            mod = this.values.get("");
        }

        Value category = mod.getSubValue("");

        category.removeSubValue(key.labelToken);
        key.setCategory(categoryKey);
        this.addKeybind(key);
    }

    public ValueInt getKeybind(String modid, String categoryId, String id)
    {
        Value category = this.get(modid, categoryId);

        if (category != null)
        {
            return (ValueInt) category.getSubValue(id);
        }
        else
        {
            return null;
        }
    }

    public void load()
    {
        for (String modid : MOD_IDS)
        {
            Value mod = new ModKeybinds(modid);

            mod.setConfig(this);

            this.values.put(mod.id, mod);
        }

        ModKeybinds modKeybinds = new ModKeybinds(null);

        modKeybinds.setConfig(this);

        this.values.put("", modKeybinds);

        ConfigParser.fromJson(this, this.file);
    }

    @Override
    public String getCategoryTitleKey(Value value)
    {
        return value.getLabelKey();
    }

    @Override
    public String getCategoryTooltipKey(Value value)
    {
        return "";
    }

    @Override
    public String getValueLabelKey(Value value)
    {
        return "";
    }

    @Override
    public String getValueCommentKey(Value value)
    {
        return "";
    }
}
