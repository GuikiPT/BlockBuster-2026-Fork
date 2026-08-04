package mchorse.metamorph.client.gui.creative;

import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiToggleElement;
import mchorse.mclib.client.gui.framework.elements.context.GuiSimpleContextMenu;
import mchorse.mclib.client.gui.framework.elements.input.GuiTextElement;
import mchorse.mclib.client.gui.framework.elements.list.GuiListElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.framework.elements.utils.GuiLabel;
import mchorse.mclib.client.gui.utils.Elements;
import mchorse.mclib.client.gui.utils.Icons;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.utils.Timer;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.capabilities.render.EntitySelector;
import mchorse.metamorph.client.EntityModelHandler;
import mchorse.metamorph.client.MetamorphClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.StringNbtReader;

import java.util.List;
import java.util.function.Consumer;

/**
 * Entity selector editor (roadmap P58 / P54.1).
 *
 * <p>1:1 port of Metamorph 1.4's
 * {@code mchorse.metamorph.client.gui.creative.GuiSelectorEditor}: the left
 * column of the selectors screen — a sortable list of {@link EntitySelector}s
 * over {@link EntityModelHandler#selectors} with a right-click add/remove
 * context menu, and a form (name / entity type / matching NBT / enabled) that
 * writes straight through to the selected selector.</p>
 *
 * <p>Every field edit does the same two things legacy did: bump
 * {@code ModelRenderer.selectorsUpdate} so each per-entity renderer
 * re-evaluates which selector applies to it, and {@code mark()} the 200 ms
 * {@link Timer} whose expiry — checked in {@link #draw(GuiContext)}, i.e. on
 * the render thread — writes {@code selectors.json}. That debounce is why
 * typing a name does not hit the disk per keystroke. Both live in
 * {@link SelectorEditorLogic}, which is where the row format and the
 * post-removal index rule are pinned by tests.</p>
 *
 * <h2>Two modes</h2>
 *
 * <p>Standalone (the {@code menu == false} constructor, used by the creative
 * screen's toggled side panel) shows a "Pick morph" button and only accepts a
 * morph while {@link #selecting} — you arm it, then click a morph in the
 * picker. Menu mode ({@code menu == true}, used by {@link GuiSelectorsScreen},
 * where the picker is permanently on screen next to it) drops the button and
 * takes every pick. Legacy's asymmetry, preserved.</p>
 *
 * <p>Boundary changes, all mechanical: {@code Minecraft} &rarr;
 * {@code MinecraftClient}; {@code JsonToNBT.getTagFromJson} &rarr;
 * {@link StringNbtReader#parse(String)} (still swallowing a parse failure into
 * a null {@code match} — an unparseable tag disables matching rather than
 * throwing); {@code ClientProxy.models.saveSelectors()} &rarr;
 * {@link EntityModelHandler#saveSelectors(java.io.File)} at
 * {@link MetamorphClient#selectorsFile()}; {@code drawCenteredString} &rarr;
 * {@link GuiDraw} (no-op headless).</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/client/gui/creative/GuiSelectorEditor.java
 */
public class GuiSelectorEditor extends GuiElement
{
    public GuiListElement<EntitySelector> selectors;

    public GuiElement form;
    public GuiTextElement name;
    public GuiTextElement type;
    public GuiTextElement match;
    public GuiToggleElement active;
    public GuiButtonElement pick;

    private EntitySelector selector;
    private Timer timer = new Timer(SelectorEditorLogic.DEBOUNCE_MS);
    private boolean selecting;
    private boolean menu;

    public GuiSelectorEditor(MinecraftClient mc)
    {
        this(mc, false);
    }

    public GuiSelectorEditor(MinecraftClient mc, boolean menu)
    {
        super(mc);

        this.menu = menu;

        this.selectors = new GuiSelectorListElement(mc, this::fillData);
        this.selectors.sorting().background().setList(EntityModelHandler.selectors);
        this.selectors.context(() ->
        {
            GuiSimpleContextMenu contextMenu = new GuiSimpleContextMenu(mc).action(Icons.ADD, IKey.lang("metamorph.gui.selectors.add"), this::addSelector);

            if (!this.selectors.getCurrent().isEmpty())
            {
                contextMenu.action(Icons.REMOVE, IKey.lang("metamorph.gui.selectors.remove"), this::removeSelector);
            }

            return contextMenu;
        });

        this.form = new GuiElement(mc);
        this.name = new GuiTextElement(mc, 1000, (name) ->
        {
            this.selector.name = name;
            this.updateTime();
            this.timer.mark();
        });
        this.type = new GuiTextElement(mc, 1000, (name) ->
        {
            this.selector.type = name;
            this.updateTime();
            this.timer.mark();
        });
        this.match = new GuiTextElement(mc, 10000, (value) ->
        {
            try
            {
                this.selector.match = StringNbtReader.parse(value);
                this.updateTime();
                this.timer.mark();
            }
            catch (Exception e)
            {
                this.selector.match = null;
                this.updateTime();
                this.timer.mark();
            }
        });
        this.match.tooltip(IKey.lang("metamorph.gui.selectors.match_tooltip"));
        this.active = new GuiToggleElement(mc, IKey.lang("metamorph.gui.selectors.enabled"), (toggle) ->
        {
            this.selector.enabled = toggle.isToggled();
            this.updateTime();
            this.timer.mark();
        });
        this.pick = new GuiButtonElement(mc, IKey.lang("metamorph.gui.body_parts.pick"), (button) -> this.armPicking());

        this.form.flex().relative(this).w(1F).column(5).vertical().stretch().height(20).padding(10);
        this.selectors.flex().relative(this.form).y(1F).w(1F).hTo(this.flex(), 1F);

        GuiLabel title = Elements.label(IKey.lang("metamorph.gui.selectors.title"));
        GuiLabel name = Elements.label(IKey.lang("metamorph.gui.selectors.name"));
        GuiLabel type = Elements.label(IKey.lang("metamorph.gui.selectors.type"));
        GuiLabel match = Elements.label(IKey.lang("metamorph.gui.selectors.match"));

        name.marginTop(8);
        type.marginTop(8);
        match.marginTop(8);

        this.form.add(title.tooltip(IKey.lang("metamorph.gui.selectors.tooltip")), name, this.name, type, this.type, match, this.match, this.active);

        if (!this.menu)
        {
            this.form.add(this.pick);
        }

        this.markContainer().add(this.form, this.selectors);

        this.selectors.setIndex(0);
        this.fillData(this.selectors.getCurrent());
    }

    private void updateTime()
    {
        SelectorEditorLogic.bumpSelectors();
    }

    public EntitySelector getSelector()
    {
        return this.selector;
    }

    /**
     * Arm the standalone editor for a single pick (the "Pick morph" button):
     * the next {@link #setMorph} is taken, and the button greys out until it
     * arrives.
     */
    protected void armPicking()
    {
        this.selecting = true;
        this.pick.setEnabled(false);
    }

    /** Context-menu action: append a blank selector and select it. */
    protected void addSelector()
    {
        EntityModelHandler.selectors.add(new EntitySelector());
        this.selectors.update();
        this.timer.mark();

        this.selectors.setIndex(this.selectors.getList().size() - 1);
        this.fillData(this.selectors.getCurrent());
    }

    /**
     * Legacy blanked the removed selector's fields <b>before</b> dropping it
     * from the list. That is not redundant: a {@code ModelRenderer} may still
     * hold this exact instance from its last evaluation, and an empty
     * name/type/morph is what makes it stop matching before the next
     * {@code selectorsUpdate} sweep reaches it.
     *
     * <p>Context-menu action, offered only while something is selected.</p>
     */
    protected void removeSelector()
    {
        if (!this.selectors.current.isEmpty())
        {
            EntitySelector selector = this.selectors.getCurrent().get(0);

            selector.name = "";
            selector.type = "";
            selector.morph = null;
            this.updateTime();

            int current = this.selectors.current.get(0);

            EntityModelHandler.selectors.remove(current);
            this.selectors.setIndex(current - 1);
            this.fillData(this.selectors.getCurrent());
            this.selectors.update();
            this.timer.mark();
        }
    }

    private void fillData(List<EntitySelector> selectors)
    {
        this.selector = null;
        this.selecting = false;
        this.form.setVisible(!selectors.isEmpty());
        this.pick.setEnabled(true);

        if (selectors.isEmpty())
        {
            return;
        }

        EntitySelector selector = selectors.get(0);

        this.selector = selector;
        this.name.setText(selector.name);
        this.type.setText(selector.type);
        this.match.setText(selector.match == null ? "" : selector.match.toString());
        this.active.toggled(selector.enabled);
    }

    /**
     * Take a morph from the picker. Legacy's gate, preserved: nothing happens
     * while hidden or with no selector chosen, and the morph is only stored
     * when the "Pick morph" button armed {@link #selecting} — or unconditionally
     * in {@link #menu} mode. Note the {@code pick}/{@code selecting}/timer
     * updates run even when the morph was <i>not</i> stored, so a stray pick
     * still re-enables the button.
     */
    public void setMorph(AbstractMorph morph)
    {
        if (!this.isVisible() || this.selector == null)
        {
            return;
        }

        if (this.selecting || this.menu)
        {
            this.selector.morph = morph == null ? null : morph.toNBT();
        }

        this.pick.setEnabled(true);
        this.selecting = false;
        this.updateTime();
        this.timer.mark();
    }

    /** Save-on-idle seam — overridden headlessly so tests never touch disk. */
    protected void save()
    {
        EntityModelHandler.INSTANCE.saveSelectors(MetamorphClient.selectorsFile());
    }

    @Override
    public void draw(GuiContext context)
    {
        if (this.timer.checkReset())
        {
            this.save();
        }

        this.area.draw(0xaa000000);

        super.draw(context);

        if (this.selectors.getList().isEmpty())
        {
            GuiDraw.drawCenteredString(this.font, "Right click here...", this.selectors.area.mx(), this.selectors.area.my(), 0x888888);
        }
    }

    /**
     * The selector list. Row text is {@code name (type) - MorphName} and rows
     * are 16 px (legacy {@code scrollItemSize = 16}).
     */
    public static class GuiSelectorListElement extends GuiListElement<EntitySelector>
    {
        public GuiSelectorListElement(MinecraftClient mc, Consumer<List<EntitySelector>> callback)
        {
            super(mc, callback);

            this.scroll.scrollItemSize = 16;
        }

        @Override
        protected String elementToString(EntitySelector element)
        {
            return SelectorEditorLogic.formatRow(element, element.morph == null ? "null" : element.morph.getString("Name"));
        }
    }
}
