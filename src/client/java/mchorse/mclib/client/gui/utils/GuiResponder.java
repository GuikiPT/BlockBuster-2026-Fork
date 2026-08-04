package mchorse.mclib.client.gui.utils;

/**
 * Port of 1.12.2's {@code net.minecraft.client.gui.GuiPageButtonList.GuiResponder}
 * (roadmap P33) — the callback interface the bundled {@link GuiTextField}
 * notifies on text change. 1.20.4 has no equivalent, so the interface is
 * bundled next to the text field (same three methods, same int-id contract).
 */
public interface GuiResponder
{
    public void setEntryValue(int id, boolean value);

    public void setEntryValue(int id, float value);

    public void setEntryValue(int id, String value);
}
