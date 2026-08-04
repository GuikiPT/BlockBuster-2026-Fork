package mchorse.metamorph.client.gui.creative;

import mchorse.metamorph.capabilities.render.EntitySelector;
import mchorse.metamorph.capabilities.render.ModelRenderer;

/**
 * Headless, pure editor logic for the selector GUIs (roadmap P54.1).
 *
 * <p>The full {@code GuiSelectorEditor}/{@code GuiSelectorsScreen} screens are
 * a McLib-GUI (S3) surface and are a SEAM here; the load-bearing, testable
 * decision pieces the legacy editor relied on are extracted into this helper so
 * they can be unit-tested on the element tree without a running client:</p>
 * <ul>
 *   <li>the {@code name (type) - MorphName} list-row format,</li>
 *   <li>the 200 ms field-edit debounce that bumps {@link
 *       ModelRenderer#selectorsUpdate} and triggers autosave,</li>
 *   <li>the "removing a selector selects index − 1" rule.</li>
 * </ul>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/client/gui/creative/{GuiSelectorEditor,GuiSelectorsScreen}.java
 */
public final class SelectorEditorLogic
{
    /** Legacy field-edit debounce before the timestamp bump + autosave. */
    public static final long DEBOUNCE_MS = 200L;

    private SelectorEditorLogic()
    {}

    /**
     * Format a selector list row: {@code name (type) - MorphName}.
     */
    public static String formatRow(EntitySelector selector, String morphName)
    {
        String name = selector == null || selector.name == null ? "" : selector.name;
        String type = selector == null || selector.type == null ? "" : selector.type;
        String morph = morphName == null ? "" : morphName;

        return name + " (" + type + ") - " + morph;
    }

    /**
     * Whether the given elapsed time since the last edit has passed the
     * debounce threshold (i.e. the pending timestamp bump + autosave should
     * fire now).
     */
    public static boolean debounceElapsed(long lastEditMs, long nowMs)
    {
        return nowMs - lastEditMs >= DEBOUNCE_MS;
    }

    /**
     * Bump the global selector-update timestamp so every per-entity {@link
     * ModelRenderer} re-evaluates. Returns the new timestamp.
     */
    public static long bumpSelectors()
    {
        return ModelRenderer.selectorsUpdate = System.currentTimeMillis();
    }

    /**
     * The index that should be selected after removing the selector at {@code
     * removedIndex} from a list that had {@code sizeBeforeRemoval} entries.
     * Legacy selected {@code index − 1}; returns −1 when the list becomes
     * empty (nothing to select).
     */
    public static int indexAfterRemoval(int removedIndex, int sizeBeforeRemoval)
    {
        int newSize = sizeBeforeRemoval - 1;

        if (newSize <= 0)
        {
            return -1;
        }

        int index = removedIndex - 1;

        if (index < 0)
        {
            index = 0;
        }
        else if (index >= newSize)
        {
            index = newSize - 1;
        }

        return index;
    }
}
