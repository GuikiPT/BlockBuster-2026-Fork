package mchorse.mclib.client.gui.utils;

import mchorse.mclib.client.gui.utils.keys.IKey;

import java.util.Objects;

/**
 * Port of McLib 2.4.3's {@code Label} (roadmap P35).
 */
public class Label<T>
{
    public IKey title;
    public T value;

    public Label(IKey title, T value)
    {
        this.title = title;
        this.value = value;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof Label)
        {
            Label label = (Label) obj;

            return Objects.equals(this.title, label.title) && Objects.equals(this.value, label.value);
        }

        return super.equals(obj);
    }
}
