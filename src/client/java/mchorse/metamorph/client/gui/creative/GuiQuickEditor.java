package mchorse.metamorph.client.gui.creative;

import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.GuiScrollElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.framework.elements.list.GuiLabelSearchListElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.Elements;
import mchorse.mclib.client.gui.utils.Label;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.client.gui.editor.GuiAbstractMorph;
import mchorse.metamorph.client.gui.editor.IMorphEditorHost;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;

import java.util.List;
import java.util.Objects;

/**
 * Quick morph editor (port of Metamorph 1.4's
 * {@code client/gui/creative/GuiQuickEditor}, roadmap P58).
 *
 * <p>This GUI is responsible for providing quick access editing of the most
 * used fields in the morph editor. It is the 200 px overlay panel the creative
 * picker slides in on {@code Q} — the width and the {@code setVisible(false)}
 * default live on the host ({@code GuiCreativeMorphsList}, SEAM P58:
 * {@code quickEditor.flex().relative(this).x(1F, -200).wTo(this.flex(), 1F).h(1F)}).</p>
 *
 * <p>Two mutually exclusive tabs sit under a 20 px button row:</p>
 * <ul>
 *   <li><b>Presets</b> — a {@link GuiLabelSearchListElement} of
 *       {@code Label<NbtCompound>}s produced by
 *       {@link GuiAbstractMorph#getPresets}, inset {@code (10, 30)} with
 *       {@code w(1F, -20) h(1F, -60)}, plus a full-width "Random preset"
 *       button hung off its bottom edge ({@code relative(presets).y(1F).w(1F)}).
 *       Applying a preset is a full {@code morph.fromNBT} of the merged tag
 *       (the labels already carry {@code morphTag.copyFrom(preset)}).</li>
 *   <li><b>Quick</b> — a scrolling column of the editor's
 *       {@link GuiAbstractMorph#getFields} widgets
 *       ({@code column(5).vertical().stretch().scroll().padding(10).height(20)}),
 *       hidden at construction.</li>
 * </ul>
 *
 * <p>Legacy quirks preserved verbatim:</p>
 * <ul>
 *   <li>The tab buttons use <em>disabled</em> to mark the active tab
 *       ({@code presetsButton.setEnabled(false)} means "Presets is showing").
 *       At construction the presets tab is visible while both buttons are
 *       still enabled — the first {@link #setMorph} call reconciles it.</li>
 *   <li>{@link #setMorph} is guarded by {@code Objects.equals(last, morph)},
 *       so re-selecting the same morph does <b>not</b> rebuild the fields (and
 *       therefore does not discard in-progress trackpad/text state).
 *       {@code AbstractMorph.equals} is a value comparison, so an
 *       equal-but-distinct morph instance is also skipped — legacy behavior.</li>
 *   <li>{@link #pickRandomPreset} multiplies {@code Math.random()} by the
 *       <em>unfiltered</em> list size, i.e. the search field does not restrict
 *       the random pick. With an empty list it yields index 0, which
 *       {@code setIndex} rejects, so {@code setPreset} sees an empty selection
 *       and no-ops.</li>
 *   <li>{@code presets.filter("", true)} after refilling clears the search box
 *       and the filter together.</li>
 *   <li>The "no presets" placeholder is a hardcoded English string, not a lang
 *       key — kept as-is.</li>
 * </ul>
 *
 * <p>SEAM(P58): legacy typed {@link #parent} as {@code GuiCreativeMorphsList};
 * the Fabric split narrows it to {@link IMorphEditorHost} (the same narrowing
 * {@link GuiAbstractMorph} already uses), which is exactly the surface this
 * panel needs — {@code getSelected()} for preset application and passing the
 * host through to {@code getFields}.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/client/gui/creative/GuiQuickEditor.java
 */
public class GuiQuickEditor extends GuiElement
{
    public IMorphEditorHost parent;

    public GuiButtonElement presetsButton;
    public GuiButtonElement randomPreset;
    public GuiButtonElement quickAccessButton;

    public GuiLabelSearchListElement<NbtCompound> presets;
    public GuiScrollElement quickAccess;

    private AbstractMorph last;

    public GuiQuickEditor(MinecraftClient mc, IMorphEditorHost parent)
    {
        super(mc);

        this.parent = parent;

        this.presetsButton = new GuiButtonElement(mc, IKey.lang("metamorph.gui.creative.presets"), this::toggleVisibility);
        this.quickAccessButton = new GuiButtonElement(mc, IKey.lang("metamorph.gui.creative.quick"), this::toggleVisibility);

        this.presets = new GuiLabelSearchListElement<NbtCompound>(mc, this::setPreset);
        this.presets.flex().relative(this).set(10, 30, 0, 0).w(1F, -20).h(1F, -60);
        this.randomPreset = new GuiButtonElement(mc, IKey.lang("metamorph.gui.creative.random"), this::pickRandomPreset);
        this.randomPreset.flex().relative(this.presets).y(1F).w(1F);

        this.quickAccess = new GuiScrollElement(mc);
        this.quickAccess.setVisible(false);
        this.quickAccess.flex().relative(this).y(20).w(1F).h(1F, -20).column(5).vertical().stretch().scroll().padding(10).height(20);

        GuiElement row = Elements.row(mc, 0, this.presetsButton, this.quickAccessButton);
        row.flex().relative(this).w(1F).h(20);

        this.presets.add(this.randomPreset);
        this.add(row, this.presets, this.quickAccess);
    }

    private void toggleVisibility(GuiButtonElement button)
    {
        if (button == this.presetsButton)
        {
            this.presetsButton.setEnabled(false);
            this.quickAccessButton.setEnabled(true);

            this.presets.setVisible(true);
            this.quickAccess.setVisible(false);
        }
        else
        {
            this.presetsButton.setEnabled(true);
            this.quickAccessButton.setEnabled(false);

            this.presets.setVisible(false);
            this.quickAccess.setVisible(true);
        }
    }

    private void pickRandomPreset(GuiButtonElement button)
    {
        int i = (int) (Math.random() * this.presets.list.getList().size());

        this.presets.list.setIndex(i);
        this.setPreset(this.presets.list.getCurrent());
    }

    public void setMorph(AbstractMorph morph, GuiAbstractMorph<AbstractMorph> editor)
    {
        if (Objects.equals(this.last, morph))
        {
            return;
        }

        /* Fill quick access */
        this.quickAccess.removeAll();

        for (GuiElement element : editor.getFields(this.mc, this.parent, morph))
        {
            this.quickAccess.add(element);
        }

        /* Fill presets */
        List<Label<NbtCompound>> presets = editor.getPresets(morph);

        this.presets.list.clear();
        this.presets.list.add(presets);
        this.presets.list.sort();
        this.presets.filter("", true);

        this.toggleVisibility(this.presets.isVisible() ? this.presetsButton : this.quickAccessButton);
        this.resize();

        this.last = morph;
    }

    protected void setPreset(List<Label<NbtCompound>> label)
    {
        if (!label.isEmpty())
        {
            this.parent.getSelected().fromNBT(label.get(0).value);
        }
    }

    @Override
    public void draw(GuiContext context)
    {
        this.area.draw(0xaa000000);

        if (this.presets.isVisible() && this.presets.list.getList().isEmpty())
        {
            GuiDraw.drawCenteredString(this.font, "No factory presets found...", this.presets.area.mx(), this.presets.area.my() - GuiDraw.fontHeight(this.font) / 2, 0xffffff);
        }

        super.draw(context);
    }
}
