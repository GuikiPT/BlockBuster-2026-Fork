package mchorse.blockbuster_pack.client.gui;

import mchorse.blockbuster.BlockbusterClient;
import mchorse.blockbuster_pack.morphs.RecordMorph;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiToggleElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTrackpadElement;
import mchorse.mclib.client.gui.framework.elements.list.GuiStringSearchListElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.Icons;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.metamorph.api.MorphUtils;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.client.gui.creative.GuiMorphRenderer;
import mchorse.metamorph.client.gui.creative.GuiNestedEdit;
import mchorse.metamorph.client.gui.editor.GuiAbstractMorph;
import mchorse.metamorph.client.gui.editor.GuiMorphPanel;
import mchorse.metamorph.client.gui.editor.IMorphEditorHost;
import net.minecraft.client.MinecraftClient;

/**
 * Editor GUI for {@link RecordMorph} (roadmap P161) — port of 2.7.2
 * {@code mchorse.blockbuster_pack.client.gui.GuiRecordMorph}.
 *
 * <p>A single {@link GuiRecordMorphPanel} holding a searchable record list (the
 * left column), a nested initial-morph edit widget, a loop toggle and a
 * random-skip trackpad. Picking a record calls {@link RecordMorph#setRecord(String)}
 * which flags {@code reload} so the ghost actor is rebuilt on the next render.</p>
 *
 * <p><b>Batch-4 seams resolved.</b> Both legacy couplings that were originally
 * deferred are now available in the Fabric split and are wired here:</p>
 * <ul>
 *   <li>The nested initial-morph editor — {@link GuiNestedEdit} +
 *       {@code morphs.nestEdit(record.initial, …)} + the {@link GuiMorphRenderer}
 *       preview assignment (A2/B1/P58). The editor's {@code morphs} host is the
 *       narrowed {@link IMorphEditorHost}, which carries {@code nestEdit}
 *       itself since V-N — this used to be a hard {@code (GuiCreativeMorphsList)}
 *       cast on the assumption that the creative picker is the only possible
 *       host, which is a latent {@code ClassCastException} rather than an
 *       invariant.</li>
 *   <li>The record list populated from the dashboard recording-editor panel's
 *       own list (P138). The null-guard mirrors legacy: the list stays empty
 *       when the dashboard panel was never opened — the coupling is intentional,
 *       do not "fix" it by loading records directly.</li>
 * </ul>
 */
public class GuiRecordMorph extends GuiAbstractMorph<RecordMorph>
{
    public GuiRecordMorphPanel general;

    public GuiRecordMorph(MinecraftClient mc)
    {
        super(mc);

        this.defaultPanel = this.general = new GuiRecordMorphPanel(mc, this);
        this.registerPanel(this.general, IKey.lang("blockbuster.morph.record"), Icons.GEAR);
    }

    @Override
    public boolean canEdit(AbstractMorph morph)
    {
        return morph instanceof RecordMorph;
    }

    /**
     * Legacy relied on {@code GuiAbstractMorph}'s default renderer being a
     * {@link GuiMorphRenderer} (which draws the edited morph and carries the
     * {@code morph} field). The Fabric base class narrows that default to a
     * placeholder {@code GuiModelRenderer} (P58 seam), so the
     * {@code (GuiMorphRenderer) this.editor.renderer} casts in the panel below
     * (nested-edit callback + {@link GuiRecordMorphPanel#fillData}) would throw
     * {@code ClassCastException}. Restore the legacy renderer type here — the
     * same override pattern {@code GuiCustomMorph} uses — so the initial-morph
     * preview works and the casts are sound.
     */
    @Override
    protected GuiMorphRenderer createMorphRenderer(MinecraftClient mc)
    {
        return new GuiMorphRenderer(mc);
    }

    public static class GuiRecordMorphPanel extends GuiMorphPanel<RecordMorph, GuiRecordMorph>
    {
        private GuiStringSearchListElement records;
        private GuiNestedEdit pick;
        private GuiToggleElement loop;
        private GuiTrackpadElement randomSkip;

        public GuiRecordMorphPanel(MinecraftClient mc, GuiRecordMorph editor)
        {
            super(mc, editor);

            this.records = new GuiStringSearchListElement(mc, (str) -> this.morph.setRecord(str.get(0)));
            this.records.list.background();

            this.pick = new GuiNestedEdit(mc, (editing) ->
            {
                RecordMorph record = this.morph;
                IMorphEditorHost morphs = this.editor == null ? null : this.editor.morphs;

                if (morphs == null)
                {
                    return;
                }

                morphs.nestEdit(record.initial, editing, (morph) ->
                {
                    record.initial = MorphUtils.copy(morph);
                    ((GuiMorphRenderer) this.editor.renderer).morph = record.initial;
                });
            });
            this.loop = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.director.loops"), true, (b) ->
            {
                this.morph.loop = this.loop.isToggled();
            });
            this.randomSkip = new GuiTrackpadElement(mc, (value) -> this.morph.randomSkip = value.intValue());
            this.randomSkip.tooltip(IKey.lang("blockbuster.gui.record_morph.random_skip"));
            this.randomSkip.limit(0, Integer.MAX_VALUE, true);

            GuiElement element = new GuiElement(mc);

            element.flex().relative(this).y(1F).w(130).anchorY(1F).column(5).stretch().vertical().height(20).padding(10);
            element.add(this.pick, this.loop, this.randomSkip);

            this.records.flex().relative(this).set(10, 25, 110, 20).hTo(element.flex());

            this.add(element, this.records);
        }

        @Override
        public void fillData(RecordMorph morph)
        {
            super.fillData(morph);

            this.records.list.clear();

            if (BlockbusterClient.panels != null && BlockbusterClient.panels.recordingEditorPanel != null)
            {
                this.records.list.add(BlockbusterClient.panels.recordingEditorPanel.records.records.list.getList());
                this.records.filter("", true);
            }

            this.records.list.setCurrent(morph.record);
            this.loop.toggled(morph.loop);
            this.randomSkip.setValue(morph.randomSkip);

            ((GuiMorphRenderer) this.editor.renderer).morph = morph.initial;

            this.records.resize();
        }

        @Override
        public void draw(GuiContext context)
        {
            GuiDraw.drawStringWithShadow(this.font, IKey.lang("blockbuster.gui.director.id").get(), this.records.area.x, this.records.area.y - 12, 0xcccccc);
            super.draw(context);
        }
    }
}
