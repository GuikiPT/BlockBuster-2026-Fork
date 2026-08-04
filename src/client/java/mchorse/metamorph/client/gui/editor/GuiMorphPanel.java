package mchorse.metamorph.client.gui.editor;

import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;

/**
 * Morph editor panel base (port of Metamorph 1.4's {@code GuiMorphPanel},
 * roadmap P59).
 *
 * <p>Each panel edits one facet of a morph. {@link GuiAbstractMorph} switches
 * between panels and drives the {@link #startEditing()}/{@link #finishEditing()}
 * lifecycle: {@code startEditing} pulls state out of the morph into widgets
 * when the panel becomes active, {@code finishEditing} commits any uncommitted
 * widget state (e.g. a trackpad the user hasn't blurred) back into the morph
 * before its NBT is read. {@link #fromNBT}/{@link #toNBT} persist per-panel
 * editor state (not morph data) across nested edits.</p>
 */
@SuppressWarnings("rawtypes")
public class GuiMorphPanel<T extends AbstractMorph, E extends GuiAbstractMorph> extends GuiElement
{
    public E editor;
    public T morph;

    public GuiMorphPanel(MinecraftClient mc, E editor)
    {
        super(mc);

        this.editor = editor;
    }

    public void startEditing()
    {}

    public void finishEditing()
    {}

    public void fillData(T morph)
    {
        this.morph = morph;
    }

    public void fromNBT(NbtCompound tag)
    {}

    public NbtCompound toNBT()
    {
        return new NbtCompound();
    }
}
