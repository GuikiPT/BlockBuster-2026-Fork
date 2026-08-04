package mchorse.metamorph.client.gui.creative;

/**
 * Pinned layout constants for the creative morph picker (roadmap P58).
 *
 * <p>These are a wire/behaviour contract: the legacy 1.12.2 picker laid cells
 * and headers out on exactly these pixel sizes, and scene/dashboard code that
 * embeds the picker relies on the resulting geometry. They live in one place so
 * a constants test can pin them against drift, mirroring the values baked into
 * the legacy client classes.</p>
 *
 * <ul>
 *   <li>{@link #CELL_WIDTH}/{@link #CELL_HEIGHT} — {@code GuiMorphSection}
 *       {@code cellWidth = 55}, {@code cellHeight = 70}.</li>
 *   <li>{@link #HEADER_HEIGHT} — {@code GuiMorphSection.HEADER_HEIGHT = 20} (the
 *       clickable section header that toggles {@code section.hidden}).</li>
 *   <li>{@link #CATEGORY_HEIGHT} — {@code GuiMorphSection.CATEGORY_HEIGHT = 16}
 *       (the clickable category row that toggles {@code category.hidden}).</li>
 *   <li>{@link #LAST_SECTION_TAIL} — the 30&nbsp;px tail added to the final
 *       section's full height and used by {@code scrollTo}
 *       ({@code cellHeight + 30}).</li>
 *   <li>{@link #SCROLL_SPEED} — {@code GuiMorphs} sets
 *       {@code scroll.scrollSpeed = 45}.</li>
 *   <li>{@link #QUICK_EDITOR_WIDTH} — the 200&nbsp;px right-hand quick-editor
 *       panel ({@code GuiQuickEditor}).</li>
 * </ul>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/client/gui/{GuiMorphs.java,creative/GuiMorphSection.java,creative/GuiQuickEditor.java}
 */
public final class MorphPickerLayout
{
    public static final int CELL_WIDTH = 55;
    public static final int CELL_HEIGHT = 70;
    public static final int HEADER_HEIGHT = 20;
    public static final int CATEGORY_HEIGHT = 16;
    public static final int LAST_SECTION_TAIL = 30;
    public static final int SCROLL_SPEED = 45;
    public static final int QUICK_EDITOR_WIDTH = 200;

    private MorphPickerLayout()
    {}

    /**
     * Number of cells that fit across a section of the given pixel width,
     * clamped to at least one (legacy {@code GuiMorphSection.getPerRow}:
     * {@code Math.max(area.w / cellWidth, 1)}).
     */
    public static int perRow(int areaWidth)
    {
        return Math.max(areaWidth / CELL_WIDTH, 1);
    }
}
