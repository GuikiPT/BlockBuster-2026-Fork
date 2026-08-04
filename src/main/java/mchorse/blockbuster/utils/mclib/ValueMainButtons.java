package mchorse.blockbuster.utils.mclib;

import mchorse.mclib.config.values.ValueGUI;

/**
 * Port of Blockbuster 2.7.2's {@code utils/mclib/ValueMainButtons} (S19 P208).
 *
 * <p>Legacy this was a {@code ValueGUI} whose {@code @SideOnly(CLIENT)
 * getFields} built the wiki/discord/tutorial/models/skins button rows for the
 * {@code general} config category. In the full port the GUI-widget
 * contribution moves out of the value classes and into the client-side
 * {@code ConfigGuiProviders} factory registry (see that class), so this
 * main-source node carries <b>no</b> GUI method — it exists only to occupy the
 * {@code general.buttons} slot and serialize as an empty object ({@code {}}),
 * exactly like the legacy {@code ValueGUI} did on disk. The actual button row
 * widget lands with P210, keyed on this class in the client factory.</p>
 */
public class ValueMainButtons extends ValueGUI
{
    public ValueMainButtons(String id)
    {
        super(id);
    }
}
