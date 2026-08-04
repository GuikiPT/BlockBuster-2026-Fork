package mchorse.blockbuster.utils.mclib;

import mchorse.mclib.config.values.ValueGUI;

/**
 * Port of Blockbuster 2.7.2's {@code utils/mclib/ValueAudioButtons} (S19 P208).
 *
 * <p>Legacy {@code ValueGUI} whose {@code @SideOnly(CLIENT) getFields} built
 * the reset-audio / open-audio-folder button row for the {@code audio} config
 * category. As with {@link ValueMainButtons}, the port relocates the widget to
 * the client-side {@code ConfigGuiProviders} factory (P210); here the node only
 * occupies the {@code audio.buttons} slot and serializes as {@code {}}.</p>
 */
public class ValueAudioButtons extends ValueGUI
{
    public ValueAudioButtons(String id)
    {
        super(id);
    }
}
