package mchorse.blockbuster.client.gui.dashboard;

import mchorse.blockbuster.ClientProxy;
import mchorse.blockbuster.client.gui.GuiImmersiveEditor;
import mchorse.blockbuster.client.gui.dashboard.panels.GuiTextureManagerPanel;
import mchorse.blockbuster.client.gui.dashboard.panels.model_block.GuiModelBlockPanel;
import mchorse.blockbuster.client.gui.dashboard.panels.model_editor.GuiModelEditorPanel;
import mchorse.blockbuster.client.gui.dashboard.panels.recording_editor.GuiRecordingEditorPanel;
import mchorse.blockbuster.client.gui.dashboard.panels.scene.GuiScenePanel;
import mchorse.blockbuster.client.gui.dashboard.panels.snowstorm.GuiSnowstorm;
import mchorse.blockbuster.client.model.parsing.ModelExtrudedLayer;
import mchorse.blockbuster.utils.mclib.BBIcons;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.mclib.GuiDashboard;
import mchorse.mclib.client.gui.utils.Icons;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.events.McLibEvents;
import mchorse.mclib.events.MultiskinProcessedEvent;
import mchorse.mclib.events.RegisterDashboardPanels;
import mchorse.mclib.events.RemoveDashboardPanels;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.client.gui.creative.GuiCreativeMorphsMenu;
import net.minecraft.client.MinecraftClient;

import java.util.function.Consumer;

/**
 * Port of Blockbuster 2.7.2's {@code GuiBlockbusterPanels} (roadmap P135) —
 * Blockbuster's dashboard GUI entry: it owns the six Blockbuster dashboard
 * panels and registers them, in a fixed order, against the bundled McLib
 * {@link GuiDashboard} when it is (re)built, and hosts the single
 * creative-morph-picker plus immersive-editor instances shared by every panel.
 *
 * <p>Legacy source: blockbuster-1.12/src/main/java/mchorse/blockbuster/client/gui/dashboard/GuiBlockbusterPanels.java</p>
 *
 * <p><b>P135 / C6.</b> The shell is complete: lang keys, icons, NUMPAD keybind
 * order, permission gating, the surviving-reference teardown quirk,
 * {@code GuiModelBlockPanel.lastBlocks.clear()}, {@code ClientProxy.audio.reset()}
 * on close, the {@code onMultiskinLoad} &rarr; {@link ModelExtrudedLayer#forceReload}
 * texture-pipeline hook, and the shared morph-picker plumbing
 * ({@link #morphs}, {@link #immersiveEditor}, {@link #picker},
 * {@link #addMorphs}, {@link #showImmersiveEditor}, {@link #closeImmersiveEditor})
 * bound to the single {@link GuiCreativeMorphsMenu} (S4/P58) instance and the
 * {@link GuiImmersiveEditor} (S14/P143) instance.</p>
 *
 * <p>Forge's {@code @SubscribeEvent} on {@code McLib.EVENT_BUS} becomes
 * subscription to the two ported callback-list events
 * ({@link RegisterDashboardPanels} / {@link RemoveDashboardPanels}); wire it
 * once from {@code BlockbusterClient} via {@link #register()}, mirroring
 * 1.12.2's {@code ClientProxy.panels} field.</p>
 */
public class GuiBlockbusterPanels
{
    public GuiScenePanel scenePanel;
    public GuiModelBlockPanel modelPanel;
    public GuiModelEditorPanel modelEditorPanel;
    public GuiRecordingEditorPanel recordingEditorPanel;
    public GuiTextureManagerPanel texturePanel;
    public GuiSnowstorm particleEditor;

    /**
     * The single shared creative morph-picker menu (S4/P58). One instance is
     * reused across every panel; {@link #picker} re-binds its callback and
     * {@link #addMorphs} parents it under the requesting panel's column.
     */
    public GuiCreativeMorphsMenu morphs;

    /**
     * The single shared immersive editor screen (S14/P143). It owns its own
     * {@code morphs} menu whose callback is also re-bound by {@link #picker}.
     */
    public GuiImmersiveEditor immersiveEditor;

    /**
     * Legacy {@code picker(Consumer)}: detach the flat morph menu from whatever
     * panel currently hosts it and re-bind the selection callback for BOTH the
     * flat menu and the immersive editor's own menu, so a subsequently opened
     * picker (flat or immersive) reports back to the requesting panel.
     */
    public void picker(Consumer<AbstractMorph> callback)
    {
        this.morphs.removeFromParent();
        this.morphs.callback = callback;
        this.immersiveEditor.morphs.callback = callback;
    }

    /**
     * Legacy {@code addMorphs(parent, editing, morph)}: parent the shared flat
     * morph menu under {@code parent} filling it, reload it and select
     * {@code morph}. Silently refuses when the menu is already parented (a
     * second panel cannot steal it mid-pick).
     */
    public void addMorphs(GuiElement parent, boolean editing, AbstractMorph morph)
    {
        if (this.morphs.hasParent())
        {
            return;
        }

        parent.add(this.morphs);

        this.morphs.reload();
        this.morphs.flex().reset().relative(parent).wh(1F, 1F);
        this.morphs.resize();
        this.morphs.setSelected(morph);

        if (editing)
        {
            this.morphs.enterEditMorph();
        }
    }

    /**
     * Legacy {@code showImmersiveEditor(editing, morph)}: pop the immersive
     * editor over the world, select {@code morph}, and null out its menu's
     * per-session hooks ({@code updateCallback}/{@code target}/
     * {@code frameProvider}/{@code beforeRender}/{@code afterRender}) so the
     * caller re-installs only the ones it needs.
     */
    public GuiImmersiveEditor showImmersiveEditor(boolean editing, AbstractMorph morph)
    {
        this.immersiveEditor.show();
        this.immersiveEditor.morphs.setSelected(morph);
        this.immersiveEditor.morphs.updateCallback = null;
        this.immersiveEditor.morphs.target = null;
        this.immersiveEditor.morphs.frameProvider = null;
        this.immersiveEditor.morphs.beforeRender = null;
        this.immersiveEditor.morphs.afterRender = null;

        if (editing)
        {
            this.immersiveEditor.morphs.enterEditMorph();
        }

        return this.immersiveEditor;
    }

    public void closeImmersiveEditor()
    {
        this.immersiveEditor.closeThisScreen();
    }

    /**
     * Subscribe the register/teardown handlers to the ported McLib dashboard
     * lifecycle events. Call once from the client entrypoint (mirrors
     * 1.12.2's {@code ClientProxy.panels} being registered on
     * {@code McLib.EVENT_BUS}).
     */
    public void register()
    {
        RegisterDashboardPanels.CALLBACKS.add(this::onRegister);
        RemoveDashboardPanels.CALLBACKS.add(this::onUnregister);

        /* onMultiskinLoad (S7/P91 + S6/P79 both landed): regenerate extruded
         * 3D skin layers whenever a multiskin re-composites. This hook is
         * texture-pipeline, not GUI — it is subscribed here once (from the
         * client entrypoint, same as legacy's @SubscribeEvent method) so it
         * survives even when no dashboard is open. */
        McLibEvents.MULTISKIN_PROCESSED.register(this::onMultiskinLoad);
    }

    /**
     * Legacy {@code onMultiskinLoad(MultiskinProcessedEvent)}: hand the freshly
     * composited multiskin image to {@link ModelExtrudedLayer#forceReload}
     * (S6/P79) so extruded skin layers rebuild. {@code event.location} is a
     * {@code MultiResourceLocation} (a {@code ResourceLocation} subtype) and
     * {@code event.image} the CPU-side {@link java.awt.image.BufferedImage},
     * matching {@code forceReload(ResourceLocation, BufferedImage)} exactly.
     */
    public void onMultiskinLoad(MultiskinProcessedEvent event)
    {
        ModelExtrudedLayer.forceReload(event.location, event.image);
    }

    public void onRegister(RegisterDashboardPanels event)
    {
        if (!(event.dashboard instanceof GuiDashboard))
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        GuiDashboard dashboard = (GuiDashboard) event.dashboard;

        this.scenePanel = new GuiScenePanel(mc, dashboard);
        this.modelPanel = new GuiModelBlockPanel(mc, dashboard);
        this.modelEditorPanel = new GuiModelEditorPanel(mc, dashboard);
        this.recordingEditorPanel = new GuiRecordingEditorPanel(mc, dashboard);
        this.texturePanel = new GuiTextureManagerPanel(mc, dashboard);
        this.particleEditor = new GuiSnowstorm(mc, dashboard);

        this.morphs = new GuiCreativeMorphsMenu(mc, null);
        this.immersiveEditor = new GuiImmersiveEditor(mc);

        /* Registration order == NUMPAD keybind order. The framework config
         * panel already holds the first slot (NUMPAD0), so these six take
         * NUMPAD1..6 — parity-critical. */
        dashboard.panels.registerPanel(this.scenePanel, IKey.lang("blockbuster.gui.dashboard.director"), BBIcons.SCENE);
        dashboard.panels.registerPanel(this.modelPanel, IKey.lang("blockbuster.gui.dashboard.model"), Icons.BLOCK);
        dashboard.panels.registerPanel(this.modelEditorPanel, IKey.lang("blockbuster.gui.dashboard.model_editor"), Icons.POSE);
        dashboard.panels.registerPanel(this.recordingEditorPanel, IKey.lang("blockbuster.gui.dashboard.player_recording"), BBIcons.EDITOR);
        dashboard.panels.registerPanel(this.texturePanel, IKey.lang("blockbuster.gui.dashboard.texture"), Icons.MATERIAL);
        dashboard.panels.registerPanel(this.particleEditor, IKey.lang("blockbuster.gui.dashboard.particle"), BBIcons.PARTICLE);
    }

    /**
     * Legacy asymmetric teardown: scene/model/recording panels (plus the shared
     * morph menu and immersive editor) are dropped, but
     * modelEditorPanel/texturePanel/particleEditor references are DELIBERATELY
     * KEPT so their state survives dashboard rebuilds across world loads. This
     * is a load-bearing quirk — do NOT "fix" the surviving references.
     */
    public void onUnregister(RemoveDashboardPanels event)
    {
        /* P136: clear the model-block panel's static recently-edited BlockPos
         * cache on world exit (legacy onUnregister). */
        GuiModelBlockPanel.lastBlocks.clear();

        /* Legacy: ClientProxy.audio.reset() — stop + drop every loaded .wav on
         * dashboard close (P188 AudioLibrary landed). Guarded because the audio
         * lifecycle may not be instantiated in every headless/early path; once
         * ClientProxy.audio is instantiated at client init the guard is a
         * harmless safety net. */
        if (ClientProxy.audio != null)
        {
            ClientProxy.audio.reset();
        }

        this.scenePanel = null;
        this.modelPanel = null;
        this.recordingEditorPanel = null;

        this.morphs = null;
        this.immersiveEditor = null;

        /* Defensive: GuiImmersiveEditor.current gates three global mixins (the
         * HUD HEAD-cancel, the FOV override and the roll zeroing). It is a new
         * static with a wider lifetime than the per-instance Forge event
         * registration it replaces, and dropping the panels reference above
         * removes the last UI path that could clear it — a world unload with
         * the editor still open would otherwise suppress the HUD forever. */
        GuiImmersiveEditor.current = null;

        /* KEPT (legacy quirk): modelEditorPanel, texturePanel, particleEditor. */
    }
}
