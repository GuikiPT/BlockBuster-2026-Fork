package mchorse.metamorph.client.gui.creative;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.mclib.McLib;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.context.GuiContextMenu;
import mchorse.mclib.client.gui.framework.elements.context.GuiSimpleContextMenu;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.Area;
import mchorse.mclib.client.gui.utils.GuiUtils;
import mchorse.mclib.client.gui.utils.Icon;
import mchorse.mclib.client.gui.utils.Icons;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.utils.ColorUtils;
import mchorse.mclib.utils.Keys;
import mchorse.metamorph.api.creative.MorphFilter;
import mchorse.metamorph.api.creative.categories.MorphCategory;
import mchorse.metamorph.api.creative.categories.UserCategory;
import mchorse.metamorph.api.creative.sections.MorphSection;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.client.MorphRenderUtils;
import net.minecraft.client.MinecraftClient;

import java.util.function.Consumer;

/**
 * Creative morph picker section (roadmap P58).
 *
 * <p>1:1 port of Metamorph 1.4's
 * {@code mchorse.metamorph.client.gui.creative.GuiMorphSection} — the visual
 * heart of the creative picker: a collapsible 20&nbsp;px section header, a stack
 * of collapsible 16&nbsp;px category rows and a grid of 55&times;70 morph cells
 * with drop-circle shadow, hover dim, selection tint/outline, keybind badge,
 * favourite star and the per-cell context menu.</p>
 *
 * <p>Every pixel offset below is the spec; do not "tidy" them. In particular the
 * following legacy quirks are reproduced verbatim:</p>
 *
 * <ul>
 *   <li>{@link #getFullHeight()} adds {@code CATEGORY_HEIGHT + 5} <b>before</b>
 *       the hidden/empty-while-filtering {@code continue}, so a collapsed
 *       category still occupies its 21&nbsp;px row — whereas {@link
 *       #getY(AbstractMorph)} {@code continue}s <b>before</b> adding it. That
 *       asymmetry is legacy and the click walk follows {@code getFullHeight}.</li>
 *   <li>{@code getFullHeight} subtracts the trailing 5&nbsp;px gap only when at
 *       least one category was visible, and adds a 30&nbsp;px tail when
 *       {@link #last} is set.</li>
 *   <li>{@link #handleClick(int, int, int)} keeps walking after a click that
 *       lands in a category's empty trailing cell slot; {@code y} is negative by
 *       then, so the <i>next</i> category's {@code y < CATEGORY_HEIGHT} header
 *       test fires and toggles <b>its</b> {@code hidden} flag. Legacy bug,
 *       preserved.</li>
 *   <li>The cell walk in {@code handleClick} runs for every mouse button, so a
 *       right click both selects the cell and opens the context menu.</li>
 *   <li>Cell hit-testing uses the fractional column width
 *       {@code x / (area.w / (float) row)} while the drawing walk advances a
 *       {@code float} cursor and rounds — the two agree only because both use
 *       the same fractional stride.</li>
 *   <li>The favourite star is drawn after a blend-function reset ("stupid hack
 *       because the morph seems to change the blend function or something") —
 *       on 1.20.4 that is {@link RenderSystem#defaultBlendFunc()}.</li>
 *   <li>{@code errorRendering} cells draw a double red outline (4&nbsp;px
 *       {@code 0x88ff0000} then 2&nbsp;px {@code 0xffff0000}) and skip the model
 *       <b>and</b> the selection tint entirely.</li>
 * </ul>
 *
 * <p>Boundary changes from 1.12.2, all mechanical:</p>
 * <ul>
 *   <li>{@code Minecraft} &rarr; {@code MinecraftClient}; {@code Gui.drawRect},
 *       {@code font.drawStringWithShadow}, {@code font.FONT_HEIGHT} and
 *       {@code font.getStringWidth} go through {@link GuiDraw} statics (P31),
 *       which no-op headlessly.</li>
 *   <li>{@code GuiScreen.setClipboardString} &rarr;
 *       {@link GuiUtils#setClipboardString(String)} (P32 clipboard seam), driven
 *       through the landed {@link MorphContextActions} dispatch.</li>
 *   <li>The {@code filter}/{@code favorite} pair moved into the shared,
 *       headless {@link MorphFilter} (landed with P57); {@link #filter} is public
 *       so the picker can hand every section the same instance.</li>
 *   <li>Arrow-key grid navigation ({@code calculateXY}/{@code getMorphAt} against
 *       the legacy {@code GuiMorphs} cursor) lives in the landed
 *       {@link MorphGridNavigator}; this class no longer re-derives it.</li>
 *   <li>The parent picker is typed as a plain {@link GuiElement} plus the
 *       {@link IMorphPickerParent} capability interface, so this class compiles
 *       (and unit-tests) without {@code GuiCreativeMorphsList}. The picker
 *       implements the interface and is still assignable to {@link #parent}
 *       verbatim.</li>
 * </ul>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/client/gui/creative/GuiMorphSection.java
 */
public class GuiMorphSection extends GuiElement
{
    public static final int HEADER_HEIGHT = MorphPickerLayout.HEADER_HEIGHT;
    public static final int CATEGORY_HEIGHT = MorphPickerLayout.CATEGORY_HEIGHT;

    /**
     * The capability surface legacy {@code GuiCreativeMorphsList} exposes to its
     * sections. The picker (P58, {@code GuiCreativeMorphsList}) implements this;
     * {@link #parent} stays a {@link GuiElement} field so subclasses such as
     * {@code GuiUserSection} can keep using it as a modal anchor verbatim.
     */
    public interface IMorphPickerParent
    {
        /**
         * Legacy {@code GuiCreativeMorphsList#showGlobalMorphs(AbstractMorph)}:
         * returns the runnable that opens the "add to global category" submenu,
         * or {@code null} when there are no user global categories.
         */
        Runnable showGlobalMorphs(AbstractMorph morph);

        /** Legacy {@code GuiCreativeMorphsList#enterEditMorph(AbstractMorph)}. */
        void enterEditMorph(AbstractMorph morph);
    }

    /**
     * The picker this section belongs to ({@code GuiCreativeMorphsList}), or
     * {@code null} for the survival picker. Deliberately <b>shadows</b>
     * {@link GuiElement}'s protected {@code parent} field, exactly like legacy
     * (which shadowed it with a {@code GuiCreativeMorphsList}-typed field) —
     * {@code getParent()} still returns the element-tree parent.
     */
    public GuiElement parent;
    public MorphSection section;
    public Consumer<GuiMorphSection> callback;

    public int cellWidth = MorphPickerLayout.CELL_WIDTH;
    public int cellHeight = MorphPickerLayout.CELL_HEIGHT;
    public boolean last;

    public AbstractMorph morph;
    public MorphCategory category;

    protected AbstractMorph hoverMorph;
    protected MorphCategory hoverCategory;

    public int height;

    /**
     * Search/favourite filter. Public and swappable so the picker can share one
     * instance across every section (legacy set the same string on each section
     * in a loop, and shares it with {@link MorphGridNavigator}).
     */
    public MorphFilter filter = new MorphFilter();

    /** Clipboard sink — overridable so tests can capture context-menu copies. */
    protected MorphContextActions.Clipboard clipboard = GuiUtils::setClipboardString;

    /**
     * Set by {@link #handleClick(int, int, int)} when a section/category header
     * row was toggled — legacy early-returns {@code true} in those two branches
     * without ever reaching {@code super.mouseClicked}.
     */
    private boolean consumedClick;

    /**
     * Legacy {@code GuiMorphSection.getMorphCommand} — kept as a static for
     * source compatibility; the string builder itself is the headless
     * {@link MorphFilter#getMorphCommand(AbstractMorph)} (drops the {@code Name}
     * tag, since the name is the command's second argument).
     */
    public static String getMorphCommand(AbstractMorph morph)
    {
        return MorphFilter.getMorphCommand(morph);
    }

    public GuiMorphSection(MinecraftClient mc, GuiElement parent, MorphSection section, Consumer<GuiMorphSection> callback)
    {
        super(mc);

        this.parent = parent;
        this.section = section;
        this.callback = callback;
    }

    public GuiMorphSection size(int w, int h)
    {
        this.cellWidth = w;
        this.cellHeight = h;

        return this;
    }

    public void set(AbstractMorph morph, MorphCategory category)
    {
        this.morph = morph;
        this.category = category;
    }

    public void pick(AbstractMorph morph, MorphCategory category)
    {
        this.set(morph, category);

        if (this.callback != null)
        {
            this.callback.accept(this);
        }
    }

    public void reset()
    {
        this.set(null, null);
    }

    /* Searching methods */

    public void setFilter(String filter)
    {
        this.filter.setFilter(filter);
    }

    /**
     * Legacy {@code section.favorite = value} (set by
     * {@code GuiMorphs#setFavorite}). Favourite-only mode overrides the text
     * filter entirely.
     */
    public void setFavorite(boolean favorite)
    {
        this.filter.favorite = favorite;
    }

    public boolean isFavorite()
    {
        return this.filter.favorite;
    }

    public boolean noFilter()
    {
        return this.filter.noFilter();
    }

    public boolean isMatching(AbstractMorph morph)
    {
        return this.filter.isMatching(morph);
    }

    /**
     * Y offset (relative to this section's top) of the row the given morph sits
     * on, or {@code -1} when it isn't visible here. Used by the picker's
     * {@code scrollTo} ({@code scrollIntoView(y + getY(morph), cellHeight + 30)}).
     *
     * <p>Legacy quirk: unlike {@link #getFullHeight()} this walk skips hidden /
     * filtered-empty categories <b>before</b> charging their 21&nbsp;px row.</p>
     */
    public int getY(AbstractMorph selected)
    {
        if (this.section.categories.isEmpty())
        {
            return 0;
        }

        int y = HEADER_HEIGHT;
        int row = this.getPerRow();

        for (MorphCategory category : this.section.categories)
        {
            int count = this.getMorphsSize(category);

            if (category.isHidden() || (count == 0 && !this.noFilter()))
            {
                continue;
            }

            y += CATEGORY_HEIGHT + 5;

            for (int i = 0, j = 0; i < category.getMorphs().size(); i ++)
            {
                AbstractMorph morph = category.getMorphs().get(i);

                if (!this.isMatching(morph))
                {
                    continue;
                }

                if (j != 0 && j % row == 0)
                {
                    y += this.cellHeight;
                }

                if (morph == selected)
                {
                    return y;
                }

                j ++;
            }

            y += this.cellHeight + 5;
        }

        return -1;
    }

    /* Calculation methods */

    public int getMorphsSize(MorphCategory category)
    {
        if (this.noFilter())
        {
            return category.getMorphs().size();
        }

        int count = 0;

        for (AbstractMorph morph : category.getMorphs())
        {
            count += this.isMatching(morph) ? 1 : 0;
        }

        return count;
    }

    /**
     * Legacy {@code Math.max(this.area.w / this.cellWidth, 1)} — identical to
     * {@link MorphPickerLayout#perRow(int)} for the default 55&nbsp;px cell.
     */
    public int getPerRow()
    {
        return Math.max(this.area.w / this.cellWidth, 1);
    }

    public int getCategoryHeight(MorphCategory category)
    {
        return this.getCategoryHeight(this.getMorphsSize(category));
    }

    public int getCategoryHeight(int given)
    {
        int size = Math.max(given, 1);

        return (int) Math.ceil(size / (float) this.getPerRow()) * this.cellHeight;
    }

    public int getFullHeight()
    {
        int h = HEADER_HEIGHT + (this.last ? MorphPickerLayout.LAST_SECTION_TAIL : 0);

        if (!this.section.hidden)
        {
            int visibleCategories = 0;

            for (MorphCategory category : this.section.categories)
            {
                int count = this.getMorphsSize(category);

                h += CATEGORY_HEIGHT + 5;

                if (category.isHidden() || (count == 0 && !this.noFilter()))
                {
                    continue;
                }

                h += this.getCategoryHeight(category) + 5;
                visibleCategories += 1;
            }

            if (visibleCategories > 0)
            {
                h -= 5;
            }
        }

        return h;
    }

    @Override
    public boolean mouseClicked(GuiContext context)
    {
        boolean result = this.handleClick(context.mouseX, context.mouseY, context.mouseButton);

        if (this.consumedClick)
        {
            this.consumedClick = false;

            return true;
        }

        return super.mouseClicked(context) || result;
    }

    /**
     * Headless core of {@link #mouseClicked(GuiContext)}: the click y-walk that
     * mirrors the drawing walk. Extracted (coordinates instead of a
     * {@link GuiContext}) purely so it can be unit tested; the body is verbatim
     * legacy, including the "kept walking with a negative y" quirk documented on
     * the class.
     *
     * @return legacy's return value: {@code true} when a morph cell was picked
     *         <b>or</b> a section/category header row was toggled (the two
     *         header branches early-return {@code true}), {@code false}
     *         otherwise — including the "clicked empty space, deselect" case.
     */
    protected boolean handleClick(int mouseX, int mouseY, int mouseButton)
    {
        boolean result = false;

        this.consumedClick = false;

        if (this.area.isInside(mouseX, mouseY) && !this.section.categories.isEmpty())
        {
            if (mouseY - this.area.y < HEADER_HEIGHT && mouseButton == 0)
            {
                this.section.hidden = !this.section.hidden;
                this.consumedClick = true;

                return true;
            }

            int x = mouseX - this.area.x;
            int y = mouseY - this.area.y - HEADER_HEIGHT;
            int row = this.getPerRow();

            category:
            for (MorphCategory category : this.section.categories)
            {
                int count = this.getMorphsSize(category);

                if (y < CATEGORY_HEIGHT && mouseButton == 0)
                {
                    category.hidden = !category.hidden;
                    this.consumedClick = true;

                    return true;
                }

                y -= CATEGORY_HEIGHT + 5;

                if (category.isHidden() || (count == 0 && !this.noFilter()))
                {
                    continue;
                }

                int ix = (int) (x / (this.area.w / (float) row));
                int iy = y / this.cellHeight;
                int i = ix + (y < 0 ? -1 : iy) * row;

                if (i >= 0 && i < count)
                {
                    int real = category.getMorphs().size();

                    if (count == real)
                    {
                        this.pick(category.getMorphs().get(i), category);

                        result = true;

                        break;
                    }
                    else
                    {
                        for (int j = 0, k = -1; j < real; j ++)
                        {
                            AbstractMorph morph = category.getMorphs().get(j);

                            if (this.isMatching(morph))
                            {
                                k ++;
                            }

                            if (i == k)
                            {
                                this.pick(morph, category);

                                result = true;
                                break category;
                            }
                        }
                    }
                }

                y -= this.getCategoryHeight(count) + 5;
            }

            if (!result)
            {
                this.pick(null, null);
            }
        }

        return result;
    }

    /**
     * Legacy gates <b>only</b> on {@code this.parent == null} — and
     * {@code GuiUserSection.createContextMenu} relies on that contract: it casts
     * {@code super.createContextMenu(context)} to {@link GuiSimpleContextMenu}
     * and immediately calls {@code action(...)} on it whenever
     * {@code this.parent != null}. Gating on {@link #picker()} instead would
     * hand it a {@code null} menu (and an NPE) for any parent that is not an
     * {@link IMorphPickerParent}, so the null check stays on {@code parent} and
     * the two picker capabilities are individually null-guarded.
     */
    @Override
    public GuiContextMenu createContextMenu(GuiContext context)
    {
        if (this.parent == null)
        {
            return super.createContextMenu(context);
        }

        GuiSimpleContextMenu contextMenu = new GuiSimpleContextMenu(this.mc);
        AbstractMorph morph = this.hoverMorph;
        IMorphPickerParent picker = this.picker();

        if (morph != null)
        {
            if (picker != null && !(this.hoverCategory instanceof UserCategory))
            {
                Runnable runnable = picker.showGlobalMorphs(morph);

                if (runnable != null)
                {
                    contextMenu.action(Icons.UPLOAD, IKey.lang("metamorph.gui.creative.context.add_global"), runnable);
                }
            }

            if (picker != null)
            {
                contextMenu.action(Icons.EDIT, IKey.lang("metamorph.gui.creative.context.edit"), () -> picker.enterEditMorph(morph));
            }

            contextMenu.action(Icons.COPY, IKey.lang("metamorph.gui.creative.context.copy_command"), () -> MorphContextActions.copyCommand(morph, this.clipboard));
            contextMenu.action(Icons.COPY, IKey.lang("metamorph.gui.creative.context.copy"), () -> MorphContextActions.copyNbt(morph, this.clipboard));
        }

        return contextMenu;
    }

    /** The picker capability of {@link #parent}, or {@code null}. */
    protected IMorphPickerParent picker()
    {
        return this.parent instanceof IMorphPickerParent ? (IMorphPickerParent) this.parent : null;
    }

    /* Hovering (exposed for the picker's tooltip/context-menu plumbing) */

    public AbstractMorph getHoverMorph()
    {
        return this.hoverMorph;
    }

    public MorphCategory getHoverCategory()
    {
        return this.hoverCategory;
    }

    @Override
    public void draw(GuiContext context)
    {
        this.drawMorphs(context);

        int h = this.getFullHeight();

        if (this.area.h != h)
        {
            this.flex().h(h);

            /* Legacy: this.getParent().getParent().resize(). Note getParent() is
             * the TREE parent (GuiMorphs) — deliberately not this.parent, which
             * shadows GuiElement's field with the picker (same as legacy, where
             * the shadowing field was typed GuiCreativeMorphsList). Null-guarded
             * so a detached section (unit tests, a section built before being
             * attached) doesn't NPE; in the real tree both always exist. */
            GuiElement treeParent = this.getParent();
            GuiElement grandparent = treeParent == null ? null : treeParent.getParent();

            if (grandparent != null)
            {
                grandparent.resize();
            }
        }

        super.draw(context);
    }

    /**
     * Draw morphs
     */
    protected void drawMorphs(GuiContext context)
    {
        if (this.section.categories.isEmpty())
        {
            return;
        }

        /* Draw header */
        Icon toggleIcon = this.section.hidden ? Icons.MOVE_DOWN : Icons.MOVE_UP;
        int fontHeight = GuiDraw.fontHeight(this.font);

        GuiDraw.drawRect(this.area.x, this.area.y, this.area.ex(), this.area.y + HEADER_HEIGHT, 0xbb000000);

        GuiDraw.drawStringWithShadow(this.font, this.section.getTitle(), this.area.x + 7, this.area.y + 10 - fontHeight / 2, 0xffffff);
        toggleIcon.render(this.area.ex() - 18 - 3, this.area.y + 10 + (this.section.hidden ? 1 : -1), 0, 0.5F);

        /* Draw categories */
        int y = HEADER_HEIGHT;

        this.hoverMorph = null;
        this.hoverCategory = null;

        if (this.section.hidden)
        {
            return;
        }

        Area viewport = context.getViewport();
        int row = this.getPerRow();

        category:
        for (MorphCategory category : this.section.categories)
        {
            int count = this.getMorphsSize(category);

            GuiDraw.drawTextBackground(this.font, category.getTitle(), this.area.x + 7, this.area.y + y + 8 - fontHeight / 2, 0xeeeeee, ColorUtils.HALF_BLACK, 2);

            (category.hidden ? Icons.MOVE_DOWN : Icons.MOVE_UP).render(this.area.ex() - 18 - 3, this.area.y + y + CATEGORY_HEIGHT / 2 + (category.hidden ? 1 : -1), 0, 0.5F);

            Area.SHARED.copy(this.area);
            Area.SHARED.y = this.area.y + y;
            Area.SHARED.h = CATEGORY_HEIGHT + this.getCategoryHeight(category);

            if (Area.SHARED.isInside(context.mouseX, context.mouseY))
            {
                this.hoverCategory = category;
            }

            float x = 0;
            y += CATEGORY_HEIGHT + 5;

            if (category.isHidden() || (count == 0 && !this.noFilter()))
            {
                continue;
            }

            for (int i = 0, j = 0; i < category.getMorphs().size(); i ++)
            {
                AbstractMorph morph = category.getMorphs().get(i);

                if (!this.isMatching(morph))
                {
                    continue;
                }

                if (j != 0 && j % row == 0)
                {
                    x = 0;
                    y += this.cellHeight;
                }

                int mx = this.area.x + Math.round(x);
                int my = this.area.y + y;

                x += this.area.w / (float) row;
                int w = Math.round(x - (mx - this.area.x));

                Area.SHARED.set(mx, my, w, this.cellHeight);

                if (Area.SHARED.isInside(context.mouseX, context.mouseY))
                {
                    this.hoverMorph = morph;
                }

                if (Area.SHARED.intersects(viewport))
                {
                    GuiDraw.scissor(mx, my, w, this.cellHeight, context);
                    this.drawMorph(context, morph, mx, my, w, this.cellHeight, this.hoverMorph == morph, this.morph == morph);
                    GuiDraw.unscissor(context);
                }

                if (Area.SHARED.y > viewport.ey())
                {
                    break category;
                }

                j ++;
            }

            y += this.cellHeight + 5;
        }
    }

    /**
     * Draw individual morph
     */
    protected void drawMorph(GuiContext context, AbstractMorph morph, int x, int y, int w, int h, boolean hover, boolean selected)
    {
        if (selected && !morph.errorRendering)
        {
            GuiDraw.drawRect(x, y, x + w, y + h, 0xaa000000 + McLib.primaryColor.get());
        }
        else if (hover)
        {
            GuiDraw.drawRect(x, y, x + w, y + h, 0x66000000);
        }

        int spot = (int) (w * 0.4F);
        int spotX = x + w / 2;
        int spotY = y + h / 2;

        GuiDraw.drawDropCircleShadow(spotX, spotY, spot, (int) (spot * 0.65F), 10, 0x44000000, 0);

        if (morph.errorRendering)
        {
            GuiDraw.drawOutline(x, y, x + w, y + h, 0x88ff0000, 4);
            GuiDraw.drawOutline(x, y, x + w, y + h, 0xffff0000, 2);

            return;
        }

        if (!this.renderMorphOnScreen(morph, x + w / 2, y + (int) (h * 0.7F), w * 0.4F, 1))
        {
            return;
        }

        if (selected)
        {
            GuiDraw.drawOutline(x, y, x + w, y + h, 0xff000000 + McLib.primaryColor.get(), 2);
        }

        if (morph.keybind != -1)
        {
            String key = Keys.getKeyName(morph.keybind);
            int fontHeight = GuiDraw.fontHeight(this.font);
            int kw = GuiDraw.textWidth(this.font, key);
            int kx = x + w - 6 - kw;
            int ky = y + h - 6 - fontHeight;

            GuiDraw.drawRect(kx - 3, ky - 2, kx + kw + 3, ky + fontHeight + 2, 0xff000000);
            GuiDraw.drawStringWithShadow(this.font, key, kx, ky, 0xffffff);
        }

        if (morph.favorite)
        {
            /* Stupid hack because the morph seems to change the blend function or something */
            RenderSystem.defaultBlendFunc();
            GuiDraw.drawOutlinedIcon(Icons.FAVORITE, x + 2, y + 2, 0xffffffff);
        }
    }

    /**
     * Legacy {@code MorphUtils.renderOnScreen(morph, player, x, y, scale, alpha)}:
     * null/error guard, {@code isRenderingOnScreen} flag, error-trapped draw,
     * {@code errorRendering} latch on failure and the shared-tessellator
     * recovery — see {@link MorphRenderUtils#renderOnScreen}.
     */
    protected boolean renderMorphOnScreen(AbstractMorph morph, int x, int y, float scale, float alpha)
    {
        return MorphRenderUtils.renderOnScreen(morph, this.mc == null ? null : this.mc.player, x, y, scale, alpha);
    }
}
