package mchorse.blockbuster_pack.client.gui;

import java.util.HashMap;
import java.util.List;

import com.google.common.collect.ImmutableList;

import mchorse.blockbuster.utils.mclib.BBIcons;
import mchorse.blockbuster_pack.client.gui.trackers.GuiBaseTracker;
import mchorse.blockbuster_pack.client.gui.trackers.GuiMorphTracking;
import mchorse.blockbuster_pack.morphs.TrackerMorph;
import mchorse.blockbuster_pack.trackers.ApertureCamera;
import mchorse.blockbuster_pack.trackers.BaseTracker;
import mchorse.blockbuster_pack.trackers.MorphTracker;
import mchorse.blockbuster_pack.trackers.TrackerRegistry;
import mchorse.mclib.client.gui.framework.elements.GuiDelegateElement;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiCirculateElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiToggleElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiLabel;
import mchorse.mclib.client.gui.utils.Elements;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.client.gui.editor.GuiAbstractMorph;
import mchorse.metamorph.client.gui.editor.GuiMorphPanel;
import net.minecraft.client.MinecraftClient;

/**
 * Tracker morph editor (port of Blockbuster 2.7.2's {@code GuiTrackerMorph},
 * roadmap P166). A single edit panel: a type-circulate button (cycles the
 * {@link TrackerRegistry} in insertion order), a {@code hidden} toggle, and a
 * delegated per-type sub-panel ({@link GuiBaseTracker} /
 * {@link GuiMorphTracking}).
 *
 * <p>Switching type instantiates the new tracker reflectively and {@code copy()}s
 * the old one's fields — by the {@link BaseTracker#copy(BaseTracker)} contract
 * only {@code name} survives across different tracker classes.</p>
 *
 * <p>Wiring (S22/P248): {@link #registerClientTrackers(MinecraftClient)}
 * populates {@link TrackerRegistry#CLIENT} and is called from
 * {@link BlockbusterMorphEditors#registerMorphEditors} — <b>not</b> from the
 * client initializer. P166 shipped the populator with no callers at all, which
 * made every tracker-morph edit a hard NPE crash at {@code updateTracker}; the
 * obvious fix (call it from {@code BlockbusterClient.onInitializeClient}, as
 * legacy's {@code ClientProxy.load} lines 266–269 did) is wrong on Fabric
 * because {@code ClientModInitializer} runs before
 * {@code MinecraftClient.textRenderer} exists and {@code GuiElement} captures
 * the font once. See {@code BlockbusterMorphEditors} for the full note.</p>
 *
 * Legacy source: blockbuster-1.12/src/main/java/mchorse/blockbuster_pack/client/gui/GuiTrackerMorph.java
 */
public class GuiTrackerMorph extends GuiAbstractMorph<TrackerMorph>
{
    /**
     * The single edit panel. Legacy reached it only through
     * {@code defaultPanel} (typed {@code GuiMorphPanel}); the port keeps a typed
     * handle like its sibling editors ({@code GuiParticleMorph.general} etc.) so
     * the panel is addressable from the headless editor tests.
     */
    public GuiTrackerMorphPanel panel;

    public GuiTrackerMorph(MinecraftClient mc)
    {
        super(mc);

        this.panel = new GuiTrackerMorphPanel(mc, this);

        this.registerPanel(this.defaultPanel = this.panel, IKey.lang("metamorph.gui.edit"), BBIcons.EDITOR);
    }

    @Override
    public boolean canEdit(AbstractMorph morph)
    {
        return morph instanceof TrackerMorph;
    }

    /**
     * Populate the client editor-panel map ({@link TrackerRegistry#CLIENT}).
     * Mirrors legacy {@code ClientProxy}: MorphTracker → GuiMorphTracking,
     * ApertureCamera → the plain base panel.
     *
     * <p>Called by {@link BlockbusterMorphEditors#registerMorphEditors} (S22/P248).
     * Rebuilding the map on every editor-list build is legacy-equivalent: legacy
     * also kept one process-wide map shared by every {@code GuiTrackerMorph}, and
     * rebuilding keeps the panels' {@code mc} in step with the editors'.</p>
     *
     * <p>{@code mc} may be {@code null} — the S3 GUI framework is display-free
     * until {@code draw()} and {@code GuiTextField} has a headless font
     * fallback, so the headless tests build real panels this way.</p>
     */
    public static void registerClientTrackers(MinecraftClient mc)
    {
        TrackerRegistry.CLIENT = new HashMap<Class<? extends BaseTracker>, Object>();
        TrackerRegistry.CLIENT.put(MorphTracker.class, new GuiMorphTracking(mc));
        TrackerRegistry.CLIENT.put(ApertureCamera.class, new GuiBaseTracker<>(mc));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static class GuiTrackerMorphPanel extends GuiMorphPanel<TrackerMorph, GuiTrackerMorph>
    {
        public GuiCirculateElement type;
        public GuiToggleElement hidden;
        public GuiDelegateElement<GuiBaseTracker> trackerPanel;

        private List<String> trackers;

        public GuiTrackerMorphPanel(MinecraftClient mc, GuiTrackerMorph editor)
        {
            super(mc, editor);

            this.type = new GuiCirculateElement(mc, (element) ->
            {
                BaseTracker tracker = this.morph.tracker;
                Class<? extends BaseTracker> clazz = TrackerRegistry.ID_TO_CLASS.get(this.trackers.get(element.getValue()));

                if (clazz != null)
                {
                    try
                    {
                        this.morph.tracker = clazz.getDeclaredConstructor().newInstance();
                        this.morph.tracker.copy(tracker);
                    }
                    catch (ReflectiveOperationException e)
                    {
                        e.printStackTrace();
                    }
                }

                this.updateTracker();
            });

            this.trackers = ImmutableList.copyOf(TrackerRegistry.ID_TO_CLASS.keySet());

            for (String tracker : this.trackers)
            {
                this.type.addLabel(IKey.lang("blockbuster.gui.tracker_morph.type." + tracker));
            }

            this.hidden = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.tracker_morph.hidden"), element ->
            {
                this.morph.hidden = element.isToggled();
            });

            GuiElement elements = Elements.column(mc, 10, 10, Elements.label(IKey.lang("blockbuster.gui.tracker_morph.type.title")), this.type, this.hidden);
            elements.flex().relative(this).xy(0, 0).w(170);

            this.trackerPanel = new GuiDelegateElement<GuiBaseTracker>(mc, null);
            this.trackerPanel.flex().relative(elements).x(0).y(1F).wTo(this.area, 1F).hTo(this.area, 1F);

            this.add(elements, this.trackerPanel);
        }

        @Override
        public void fillData(TrackerMorph morph)
        {
            super.fillData(morph);

            this.hidden.toggled(morph.hidden);
            this.updateTracker();
        }

        private void updateTracker()
        {
            this.trackerPanel.setDelegate(null);
            this.type.setValue(0);

            if (this.morph.tracker == null)
            {
                this.morph.tracker = new MorphTracker();
            }

            /* S22/P248 belt-and-braces: BlockbusterMorphEditors.registerMorphEditors
             * is the wiring, but this read used to be unguarded and a missing map
             * was a hard client crash rather than a degraded panel. Build it here
             * too if some future path reaches an editor without going through the
             * registrar — from `this.mc`, which by construction is the editor's own
             * live client, so the panels get a real font. */
            if (TrackerRegistry.CLIENT == null)
            {
                registerClientTrackers(this.mc);
            }

            this.type.setValue(this.trackers.indexOf(TrackerRegistry.CLASS_TO_ID.get(this.morph.tracker.getClass())));
            this.trackerPanel.setDelegate((GuiBaseTracker) TrackerRegistry.CLIENT.get(this.morph.tracker.getClass()));

            if (this.trackerPanel.delegate != null)
            {
                this.trackerPanel.delegate.fill(this.morph.tracker);
            }
        }
    }
}
