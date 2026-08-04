package mchorse.metamorph.client.gui.creative;

import mchorse.metamorph.api.creative.MorphFilter;
import mchorse.metamorph.api.morphs.AbstractMorph;

/**
 * Headless dispatch for the creative picker cell's context-menu clipboard
 * actions (roadmap P58).
 *
 * <p>Legacy {@code GuiMorphSection.createContextMenu} wires two clipboard
 * entries: {@code copy_command} copies {@code getMorphCommand(morph)} (the
 * {@code /morph} command with the {@code Name} tag stripped from the NBT) and
 * {@code copy} copies the raw {@code morph.toNBT().toString()} (Name tag
 * included). Both go through {@code GuiScreen.setClipboardString}; on Fabric the
 * GL screen supplies {@code MinecraftClient.getInstance().keyboard::setClipboard}
 * as the {@link Clipboard}. Splitting the string-building out lets the dispatch
 * be unit-tested with a captured clipboard.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/client/gui/creative/GuiMorphSection.java
 */
public final class MorphContextActions
{
    private MorphContextActions()
    {}

    /** Sink for clipboard writes (real screen: keyboard::setClipboard). */
    public interface Clipboard
    {
        void set(String text);
    }

    /**
     * Copy the {@code /morph @p <name> <nbt-without-Name>} command for the morph.
     */
    public static void copyCommand(AbstractMorph morph, Clipboard clipboard)
    {
        clipboard.set(MorphFilter.getMorphCommand(morph));
    }

    /**
     * Copy the morph's raw NBT (with the {@code Name} tag intact), matching the
     * legacy {@code copy} context action. A {@code null} morph copies an empty
     * string rather than crashing (total-reader rule).
     */
    public static void copyNbt(AbstractMorph morph, Clipboard clipboard)
    {
        clipboard.set(morph == null ? "" : morph.toNBT().toString());
    }
}
