package mchorse.chameleon.mclib;

import mchorse.mclib.config.values.ValueGUI;

/**
 * The {@code general.buttons} slot of Chameleon's config module.
 *
 * <p>Port split, matching {@code mchorse.blockbuster.utils.mclib.ValueMainButtons}
 * (P208/P210): the value node lives in the <b>main</b> source set so
 * {@code Chameleon.onConfigRegister} registers it on both sides and
 * {@code config/chameleon/config.json} keeps the legacy shape (it serializes
 * as {@code {}}); the four buttons themselves need client-only McLib types and
 * are supplied by {@code ChameleonConfigButtons} through the client
 * {@code ConfigGuiProviders} registry, keyed on this class.</p>
 *
 * Legacy source: chameleon/.../mclib/ValueButtons.java
 */
public class ValueButtons extends ValueGUI
{
    public ValueButtons(String id)
    {
        super(id);
    }
}
