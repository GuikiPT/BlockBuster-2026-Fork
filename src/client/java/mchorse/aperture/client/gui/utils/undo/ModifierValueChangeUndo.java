package mchorse.aperture.client.gui.utils.undo;

/**
 * Modifier value change undo (P183) — also snapshots the modifiers panel
 * scroll (consumed by the P185 modifiers manager).
 *
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/client/gui/utils/undo/ModifierValueChangeUndo.java
 */
public class ModifierValueChangeUndo extends FixtureValueChangeUndo
{
    private int panelScroll;

    public ModifierValueChangeUndo(int index, int panelScroll, String name, Object oldValue, Object newValue)
    {
        super(index, name, oldValue, newValue);

        this.panelScroll = panelScroll;
    }

    public int getPanelScroll()
    {
        return this.panelScroll;
    }
}
