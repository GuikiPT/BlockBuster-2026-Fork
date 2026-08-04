package mchorse.mclib.client.gui.utils;

import mchorse.mclib.ClientProxy;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.client.gui.utils.keys.KeyParser;
import mchorse.mclib.config.values.ValueInt;
import mchorse.mclib.utils.Keys;

import java.util.function.Supplier;

/**
 * Keybind class
 *
 * Port of McLib 2.4.3's {@code Keybind} (roadmap P29 surface, completed in
 * P39). Key codes are legacy LWJGL2 codes — packed combo codes via
 * {@link Keys} are the {@code keybinds.json} persistence contract.
 *
 * {@code equals} compares only {@code keyCode + inside} (ignoring
 * label/callback) — the F9 overlay relies on it for dedupe; kept verbatim.
 */
public class Keybind
{
    public String modid;
    public IKey label;
    public IKey category = IKey.EMPTY;
    public int keyCode;
    public Runnable callback;
    public boolean inside;
    public boolean active = true;
    public Supplier<Boolean> activeSupplier;

    public String labelToken = "";
    public String categoryToken = "";

    public Keybind(String modid, IKey label, int keyCode, Runnable callback)
    {
        this.modid = modid;
        this.label = label;
        this.keyCode = keyCode;
        this.callback = callback;

        this.labelToken = KeyParser.toJson(label);

        ClientProxy.getKeybinds().addKeybind(this);
    }

    public Keybind held(int... keys)
    {
        this.keyCode = Keys.getComboKeyCode(keys, keyCode);

        ClientProxy.getKeybinds().addKeybind(this);

        return this;
    }

    public Keybind inside()
    {
        this.inside = true;

        return this;
    }

    public Keybind active(Supplier<Boolean> active)
    {
        this.activeSupplier = active;

        return this;
    }

    public Keybind active(boolean active)
    {
        this.active = active;

        return this;
    }

    public Keybind category(IKey category)
    {
        ClientProxy.getKeybinds().updateCategory(this, category);

        return this;
    }

    public void setCategory(IKey category)
    {
        this.category = category;
        this.categoryToken = KeyParser.toJson(category);
    }

    public String getKeyCombo()
    {
        ValueInt config = ClientProxy.getKeybinds().getKeybind(this.modid, this.categoryToken, this.labelToken);

        if (config != null)
        {
            return Keys.getComboKeyName(config.get());
        }
        else
        {
            return Keys.getComboKeyName(this.keyCode);
        }
    }

    public boolean check(int keyCode, boolean inside)
    {
        ValueInt config = ClientProxy.getKeybinds().getKeybind(this.modid, this.categoryToken, this.labelToken);
        int check = config == null ? this.keyCode : config.get();

        if (Keys.getMainKey(check) != keyCode || !Keys.checkModifierKeys(check))
        {
            return false;
        }

        if (this.inside)
        {
            return inside;
        }

        return true;
    }

    public boolean isActive()
    {
        if (this.activeSupplier != null)
        {
            return this.activeSupplier.get();
        }

        return this.active;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof Keybind)
        {
            Keybind keybind = (Keybind) obj;

            return this.keyCode == keybind.keyCode && this.inside == keybind.inside;
        }

        return super.equals(obj);
    }
}
