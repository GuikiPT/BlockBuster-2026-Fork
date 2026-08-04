package mchorse.blockbuster.client.gui.dashboard.panels.model_editor;

import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.api.Model;
import mchorse.blockbuster.api.ModelClientLoader;
import mchorse.blockbuster.api.ModelLimb;
import mchorse.blockbuster.api.ModelPose;
import mchorse.blockbuster.api.ModelTransform;
import mchorse.blockbuster.api.loaders.lazy.IModelLazyLoader;
import mchorse.blockbuster.client.gui.dashboard.GuiBlockbusterPanel;
import mchorse.blockbuster.client.gui.dashboard.panels.model_editor.tabs.GuiModelLimbs;
import mchorse.blockbuster.client.gui.dashboard.panels.model_editor.tabs.GuiModelList;
import mchorse.blockbuster.client.gui.dashboard.panels.model_editor.tabs.GuiModelOptions;
import mchorse.blockbuster.client.gui.dashboard.panels.model_editor.tabs.GuiModelPoses;
import mchorse.blockbuster.client.gui.dashboard.panels.model_editor.utils.GuiBBModelRenderer;
import mchorse.blockbuster.client.gui.dashboard.panels.model_editor.utils.GuiPoseTransformations;
import mchorse.blockbuster.client.gui.dashboard.panels.model_editor.utils.ModelEditorSaver;
import mchorse.blockbuster.client.model.ModelCustom;
import mchorse.blockbuster.client.model.parsing.ModelExtrudedLayer;
import mchorse.blockbuster.client.video.ScreenshotCapture;
import mchorse.blockbuster.utils.BlockbusterPaths;
import mchorse.blockbuster.utils.mclib.BBIcons;
import mchorse.mclib.client.gui.framework.GuiBase;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiIconElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTexturePicker;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.mclib.GuiDashboard;
import mchorse.mclib.client.gui.utils.Icons;
import mchorse.mclib.client.gui.utils.LegacyKeyCodes;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.utils.DummyEntity;
import mchorse.blockbuster.ClientProxy;
import mchorse.mclib.utils.files.GlobalTree;
import mchorse.mclib.utils.files.entries.AbstractEntry;
import mchorse.mclib.utils.files.entries.FileEntry;
import mchorse.mclib.utils.files.entries.FolderEntry;
import mchorse.mclib.utils.resources.RLUtils;
import mchorse.mclib.utils.resources.ResourceLocation;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * The model editor panel (roadmap P137) — Blockbuster's McME.
 *
 * <p>Legacy source (ported 1:1):
 * {@code blockbuster-1.12/.../dashboard/panels/model_editor/GuiModelEditorPanel.java}.
 * No permission gate. This panel is deliberately <b>kept</b> (not nulled) on
 * dashboard teardown — see {@code GuiBlockbusterPanels.onUnregister}.</p>
 *
 * <p>The layout: a full-bleed {@link GuiBBModelRenderer} viewport, a bottom-
 * centre {@link GuiPoseTransformations} editor, a right-hand
 * {@link GuiModelLimbs} column, and three <b>mutually exclusive</b> left side
 * tabs ({@link GuiModelList}, {@link GuiModelOptions}, {@link GuiModelPoses})
 * driven by the top-left toolbar. The second toolbar group toggles preview
 * state: one-shot swipe, walk cycle, held items, hitbox, head-follows-mouse,
 * and the skin picker.</p>
 *
 * <p><b>Legacy behaviours preserved.</b></p>
 * <ul>
 * <li>{@link #toggle} hides all three side tabs and then shows the requested one
 * only if it was not already visible — clicking the active tab's icon closes
 * it.</li>
 * <li>The active tab / preview toggles are drawn as translucent black rectangles
 * <b>over</b> their icons ({@code 0xaa000000} for the tabs, {@code 0x66000000}
 * for the preview toggles), and the tab overlay is an {@code if/else if} chain,
 * so only one can ever show.</li>
 * <li>Ctrl+S saves through {@code saveModel.clickItself} rather than calling
 * {@link #saveModel()} directly, so the button visibly flashes.</li>
 * <li>{@link #saveModel(String)} refuses an empty name, writes
 * {@code models/<name>/model.json}, copies the previous loader's OBJ/MTL/VOX
 * companions into the new folder, <b>then</b> re-points {@code modelName} and
 * hot-reloads the pack. A failure only prints a stack trace and answers false —
 * no modal, exactly like legacy.</li>
 * <li>{@link #setModel(String, Model, IModelLazyLoader)} deep-copies the
 * blueprint (the editor never mutates the pack's own {@link Model}), always
 * resets to the {@code standing} pose, and selects the first limb <b>in map
 * iteration order</b> — legacy's {@code limbs.keySet().iterator().next()},
 * which on a {@link java.util.HashMap} is not the file's order and not the
 * sorted list order the limb list shows.</li>
 * <li>{@link #rebuildModel} preserves the previous pose across the recompile and
 * marks the model dirty; {@link #buildModel} clears the extruded-layer cache for
 * the outgoing model first.</li>
 * <li>{@link #getFirstResourceLocation}'s fallback chain: the model's
 * {@code defaultTexture} (an empty path counts as absent) &rarr; the
 * <b>last</b> file in the model's {@code skins} folder (the loop overwrites
 * {@code rl} every iteration despite reading as "first") &rarr;
 * {@code blockbuster:textures/entity/actor.png}.</li>
 * </ul>
 *
 * <p><b>1.20.4 mapping.</b> {@code Keyboard.KEY_S}/{@code KEY_LCONTROL} &rarr;
 * {@link LegacyKeyCodes}; {@code CommonProxy.configFile} &rarr;
 * {@link BlockbusterPaths#models()}; {@code Blockbuster.proxy.pack}/
 * {@code loadModels} &rarr; the static {@link CommonProxy} members;
 * {@code ClientProxy.tree} &rarr; {@link ClientProxy#skins(String)} (the same
 * {@code b.a}-rooted Blockbuster tree, reached through a null-tree guard —
 * a {@link GlobalTree#TREE} lookup would need the {@code "b.a/"} prefix);
 * {@code loader.loadClientModel(...)} &rarr;
 * {@link ModelClientLoader#loadClientModel} (the loaders live in the main source
 * set and cannot name {@link ModelCustom}).</p>
 *
 * <p><b>One added guard.</b> The skin the viewport renders is an
 * {@code Identifier} in this port while the texture picker speaks McLib's
 * {@link ResourceLocation}, so the picked value is kept in {@link #skin} and
 * projected onto {@code modelRenderer.texture} — the same split
 * {@code GuiCustomMorph} uses. Multi-skins therefore preview as their first
 * component rather than the merged texture; noted for the S20 checklist.</p>
 *
 * <p><b>One port addition (S18 P204).</b> An <b>F2</b> keybind — registered on
 * the same {@link #keys()} manager as legacy's Ctrl+S, so it appears in the F9
 * keybind overlay — queues a transparent screenshot of the viewport. There is
 * no legacy counterpart to match: 1.12.2 Blockbuster/McLib/Metamorph/Aperture
 * contain no screenshot code at all (the only {@code glReadPixels} in the legacy
 * trees is {@code GuiModelRenderer}'s stencil limb-pick), so the trigger's shape
 * comes from {@code plan/S18-video-capture.md} P204 ("triggered from the editor
 * GUI, F2-style key handled by the S3 framework"), not from a legacy diff. The
 * key only raises {@link #screenshot}; {@link #draw} consumes it and performs
 * the capture, because the capture must happen on the render thread with a live
 * GUI projection.</p>
 */
public class GuiModelEditorPanel extends GuiBlockbusterPanel
{
    /* GUI stuff */
    public GuiBBModelRenderer modelRenderer;

    private GuiElement icons;
    private GuiIconElement openModels;
    private GuiIconElement openOptions;
    private GuiIconElement openPoses;
    private GuiIconElement saveModel;
    private GuiIconElement swipe;
    private GuiIconElement running;
    private GuiIconElement items;
    private GuiIconElement hitbox;
    private GuiIconElement looking;
    private GuiIconElement skinButton;

    private GuiPoseTransformations poseEditor;
    private GuiModelLimbs limbs;
    private GuiModelPoses poses;
    private GuiModelList models;
    private GuiModelOptions options;

    private GuiTexturePicker picker;

    /* Current data */
    public String modelName;
    public Model model;
    public ModelPose pose;
    public ModelTransform transform;
    public ModelLimb limb;

    /** The picked skin in McLib's resource-location form — see the class doc. */
    public ResourceLocation skin;

    public IModelLazyLoader modelEntry;
    public ModelCustom renderModel;

    private boolean dirty;
    private boolean held;

    /**
     * S18 P204 — a pending transparent-screenshot request. The keybind fires
     * outside a render pass, so it only raises the flag; {@link #draw} consumes
     * it once and performs the capture on the render thread. Same one-shot
     * handshake the world variant uses
     * ({@code ScreenshotCapture.requestWorldCapture}).
     */
    private boolean screenshot;

    public GuiModelEditorPanel(MinecraftClient mc, GuiDashboard dashboard)
    {
        super(mc, dashboard);

        this.modelRenderer = new GuiBBModelRenderer(mc);
        this.modelRenderer.picker(this::setLimb);
        this.modelRenderer.flex().relative(this).wh(1F, 1F);
        this.modelRenderer.origin = this.modelRenderer.items = true;

        this.picker = new GuiTexturePicker(mc, null);
        this.picker.flex().relative(this).wh(1F, 1F);

        this.poseEditor = new GuiModelPoseTransformations(mc, this);
        this.poseEditor.flex().relative(this).set(0, 0, 256, 70).x(0.5F, -128).y(1, -80);

        this.limbs = new GuiModelLimbs(mc, this);
        this.limbs.flex().relative(this).x(1F).w(200).h(1F).anchorX(1F);

        this.poses = new GuiModelPoses(mc, this);
        this.poses.flex().relative(this).y(20).w(140).h(1F, -20);
        this.poses.setVisible(false);

        this.models = new GuiModelList(mc, this);
        this.models.flex().relative(this).y(20).w(140).h(1F, -20);
        this.models.setVisible(false);

        this.options = new GuiModelOptions(mc, this);
        this.options.flex().relative(this).y(20).w(200).h(1F, -20);
        this.options.setVisible(false);

        /* Toolbar buttons */
        this.openModels = new GuiIconElement(mc, Icons.MORE, (b) -> this.toggle(this.models));
        this.openOptions = new GuiIconElement(mc, Icons.GEAR, (b) -> this.toggle(this.options));
        this.openPoses = new GuiIconElement(mc, Icons.POSE, (b) -> this.toggle(this.poses));

        this.saveModel = new GuiIconElement(mc, Icons.SAVED, (b) -> this.saveModel());
        this.saveModel.tooltip(IKey.lang("blockbuster.gui.me.tooltips.save"));

        this.swipe = new GuiIconElement(mc, BBIcons.ARM1, (b) -> this.modelRenderer.swipe());
        this.swipe.tooltip(IKey.lang("blockbuster.gui.me.tooltips.swipe"));
        this.swipe.hovered(BBIcons.ARM2);

        this.running = new GuiIconElement(mc, BBIcons.LEGS1, (b) -> this.modelRenderer.swinging = !this.modelRenderer.swinging);
        this.running.hovered(BBIcons.LEGS2).hoverColor(0xffffffff).tooltip(IKey.lang("blockbuster.gui.me.tooltips.running"));

        this.items = new GuiIconElement(mc, BBIcons.NO_ITEMS, (b) ->
        {
            this.held = !this.held;

            if (this.modelRenderer.getEntity() instanceof DummyEntity)
            {
                ((DummyEntity) this.modelRenderer.getEntity()).toggleItems(this.held);
            }
        });
        this.items.hovered(BBIcons.HELD_ITEMS).tooltip(IKey.lang("blockbuster.gui.me.tooltips.held_items"));

        this.hitbox = new GuiIconElement(mc, BBIcons.HITBOX, (b) -> this.modelRenderer.aabb = !this.modelRenderer.aabb);
        this.hitbox.tooltip(IKey.lang("blockbuster.gui.me.tooltips.hitbox"));

        this.looking = new GuiIconElement(mc, BBIcons.LOOKING, (b) -> this.modelRenderer.looking = !this.modelRenderer.looking);
        this.looking.tooltip(IKey.lang("blockbuster.gui.me.tooltips.looking"));

        this.skinButton = new GuiIconElement(mc, Icons.MATERIAL, (b) -> this.pickTexture(this.skin, (rl) -> this.setSkin(rl)));
        this.skinButton.tooltip(IKey.lang("blockbuster.gui.me.tooltips.skin"));

        this.icons = new GuiElement(mc);
        this.icons.flex().relative(this).h(20).row(0).resize().height(20);
        this.icons.add(this.openModels, this.openOptions, this.openPoses, this.saveModel);

        GuiElement icons = new GuiElement(mc);
        icons.flex().relative(this.icons).x(1F, 20).h(20).row(0).resize().height(20);
        icons.add(this.swipe, this.running, this.items, this.hitbox, this.looking, this.skinButton);

        this.add(this.modelRenderer, this.poses, this.poseEditor, this.limbs, this.models, this.options, this.icons, icons);

        this.keys()
            .register(IKey.lang("blockbuster.gui.me.keys.save"), LegacyKeyCodes.KEY_S, () -> this.saveModel.clickItself(GuiBase.getCurrent()))
            .held(LegacyKeyCodes.KEY_LCONTROL).category(IKey.lang("blockbuster.gui.me.keys.category"));

        /* S18 P204 — the model-editor transparent screenshot. There is no legacy
         * counterpart (1.12.2 shipped no screenshot code at all), so the trigger
         * is the one the plan named: an F2-style key owned by the S3 framework,
         * which also lists it in the F9 keybind overlay. */
        this.keys()
            .register(IKey.lang("blockbuster.gui.me.keys.screenshot"), LegacyKeyCodes.KEY_F2, this::requestScreenshot)
            .category(IKey.lang("blockbuster.gui.me.keys.category"));

        this.setModel("steve");
    }

    private void toggle(GuiElement element)
    {
        boolean visible = element.isVisible();

        this.models.setVisible(false);
        this.poses.setVisible(false);
        this.options.setVisible(false);

        element.setVisible(!visible);
    }

    public void dirty()
    {
        this.dirty(true);
    }

    public void dirty(boolean dirty)
    {
        this.dirty = dirty;
        this.updateSaveButton();
    }

    /** Whether the edited model has unsaved changes (test visibility). */
    public boolean isDirty()
    {
        return this.dirty;
    }

    private void updateSaveButton()
    {
        this.saveModel.both(this.dirty ? Icons.SAVE : Icons.SAVED);
    }

    @Override
    public void open()
    {
        this.models.updateModelList();
    }

    public void pickTexture(ResourceLocation location, Consumer<ResourceLocation> callback)
    {
        this.picker.fill(location);
        this.picker.callback = callback;

        this.picker.resize();
        this.add(this.picker);
    }

    /**
     * Assign the previewed skin. Legacy's callback was
     * {@code (rl) -> this.modelRenderer.texture = rl}; here the McLib location
     * is retained for the picker's round-trip and projected onto the viewport's
     * {@code Identifier}.
     */
    private void setSkin(ResourceLocation location)
    {
        this.skin = location;
        this.modelRenderer.skin = location;
        this.modelRenderer.texture = location == null ? null : location.toIdentifier();
    }

    public void setLimb(String str)
    {
        ModelLimb limb = this.model.limbs.get(str);

        if (limb != null)
        {
            this.limb = limb;

            if (this.pose != null)
            {
                this.transform = this.pose.limbs.get(str);
            }

            this.modelRenderer.limb = limb;
            this.poseEditor.set(this.transform);
            this.limbs.fillLimbData(limb);
            this.limbs.setCurrent(str);
        }
    }

    public void setPose(String str)
    {
        this.setPose(str, false);
    }

    public void setPose(String str, boolean scroll)
    {
        ModelPose pose = this.model.poses.get(str);

        if (pose != null)
        {
            this.pose = pose;
            this.modelRenderer.setPose(pose);

            if (this.renderModel != null)
            {
                this.renderModel.pose = pose;
            }

            if (this.limb != null)
            {
                this.transform = pose.limbs.get(this.limb.name);
            }

            this.poses.setCurrent(str, scroll);
            this.poses.fillPoseData();
            this.poseEditor.set(this.transform);
        }
    }

    public void saveModel()
    {
        this.saveModel(this.modelName);
    }

    /**
     * Save model
     *
     * This method is responsible for saving model into users's config folder.
     *
     * <p>S22/P243: the empty-name refusal, the {@code models/<name>/model.json}
     * destination, the {@code mkdirs} and the UTF-8 write are <b>not</b> written
     * out here any more — they are {@link ModelEditorSaver#saveModel}, which is
     * the unit-tested implementation and used to be an orphan while this method
     * carried a second copy of it. What stays is what the saver deliberately
     * does not do (its javadoc says so): the OBJ/MTL companion copy off the
     * <i>previous</i> loader, the name re-point, the pack hot reload and the
     * dirty-flag flip.</p>
     */
    public boolean saveModel(String name)
    {
        if (!ModelEditorSaver.saveModel(name, this.model, this.modelsDir()))
        {
            return false;
        }

        try
        {
            IModelLazyLoader previous = CommonProxy.pack == null ? null : CommonProxy.pack.models.get(this.modelName);

            /* Copy OBJ files */
            if (previous != null)
            {
                previous.copyFiles(this.modelFolder(name));
            }

            this.modelName = name;
            this.reloadModels();

            this.dirty(false);
        }
        catch (Exception e)
        {
            e.printStackTrace();

            return false;
        }

        return true;
    }

    /**
     * The models root {@link #saveModel(String)} writes into — legacy
     * {@code new File(CommonProxy.configFile, "models/")}. Overridable so the
     * save flow can be driven against a temp directory headlessly.
     */
    protected Path modelsDir()
    {
        return BlockbusterPaths.models();
    }

    /**
     * The per-model folder, i.e. {@link #modelsDir()} resolved against the
     * model name. Kept as a {@link File} because {@code IModelLazyLoader.copyFiles}
     * takes one. A name may contain {@code /} (models live in nested folders),
     * which {@code Path.resolve} handles.
     */
    protected File modelFolder(String name)
    {
        return this.modelsDir().resolve(name).toFile();
    }

    /**
     * The hot reload {@link #saveModel(String)} triggers — legacy
     * {@code Blockbuster.proxy.loadModels(false)}. Overridable for the same
     * reason as {@link #modelFolder(String)}.
     */
    protected void reloadModels()
    {
        CommonProxy.loadModels(false);
    }

    /**
     * Build the model from data model
     */
    public void rebuildModel()
    {
        ModelPose oldPose = this.renderModel == null ? null : this.renderModel.pose;

        if (this.renderModel != null)
        {
            this.renderModel.delete();
        }

        this.renderModel = this.buildModel();
        this.modelRenderer.model = this.renderModel;

        if (this.model != null)
        {
            if (this.renderModel != null)
            {
                this.renderModel.pose = oldPose;
            }

            this.modelRenderer.setPose(oldPose);
            this.poseEditor.set(this.transform);
        }

        this.dirty();
    }

    /**
     * Build the model from data model
     *
     * TODO: optimize by rebuilding only one limb
     *
     * <p>A null {@link #modelEntry} is tolerated (it means "no loader" — an
     * exported, never-saved model, or a pack that has not been scanned):
     * {@link ModelClientLoader} then compiles with no mesh contribution. Legacy
     * dereferenced the loader and let its own {@code catch} turn the NPE into a
     * null model plus a stack trace, which is the same outcome without the
     * noise.</p>
     */
    public ModelCustom buildModel()
    {
        try
        {
            ModelExtrudedLayer.clearByModel(this.renderModel);

            return ModelClientLoader.loadClientModel(this.modelEntry, this.modelName, this.model);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Set a model from the repository
     */
    public void setModel(String name)
    {
        ModelCustom model = ModelCustom.MODELS.get(name);

        if (model != null)
        {
            this.setModel(name, model.model, CommonProxy.pack == null ? null : CommonProxy.pack.models.get(name));
        }
    }

    public void setModel(String name, Model model, IModelLazyLoader loader)
    {
        this.dirty(false);

        this.modelName = name;
        this.model = model.copy();
        this.modelEntry = loader;

        this.renderModel = this.buildModel();
        this.modelRenderer.model = this.renderModel;
        this.setSkin(this.getFirstResourceLocation());
        this.modelRenderer.limb = this.limb;
        this.modelRenderer.setPose(this.pose);

        this.limbs.fillData(model);
        this.poses.fillData(model);
        this.options.fillData(model);

        this.setPose("standing", true);
        this.setLimb(this.model.limbs.keySet().iterator().next());
    }

    /**
     * Get the first available resource location for this model
     */
    private ResourceLocation getFirstResourceLocation()
    {
        ResourceLocation rl = this.model.defaultTexture;

        if (rl != null && rl.getResourcePath().isEmpty())
        {
            rl = null;
        }

        if (rl == null)
        {
            FolderEntry folder = ClientProxy.skins(this.modelName + "/skins");

            if (folder != null)
            {
                for (AbstractEntry file : folder.getEntries())
                {
                    if (file instanceof FileEntry)
                    {
                        rl = ((FileEntry) file).resource;
                    }
                }
            }
        }

        return rl == null ? RLUtils.create("blockbuster", "textures/entity/actor.png") : rl;
    }

    /* ------------------------------------------------------------------ *
     * S18 P204 — model-editor transparent screenshot                      *
     * ------------------------------------------------------------------ */

    /**
     * Queue a transparent capture of the viewport for the next drawn frame.
     * Wired to the F2 keybind registered in the constructor.
     */
    public void requestScreenshot()
    {
        this.screenshot = true;
    }

    /** Whether a capture is pending (does not clear the flag). */
    public boolean isScreenshotPending()
    {
        return this.screenshot;
    }

    /** True at most once per {@link #requestScreenshot()} (clears the flag). */
    public boolean consumeScreenshotRequest()
    {
        boolean pending = this.screenshot;

        this.screenshot = false;

        return pending;
    }

    /**
     * Draw the viewport (and only the viewport — no panel chrome, no input
     * overlay) over P46's fully transparent offscreen target and write
     * {@code screenshots/blockbuster/<model>_<timestamp>.png}.
     *
     * <p>The capture target is the whole window framebuffer rather than the
     * viewport's area, so the GUI projection in force is the one the element was
     * laid out against and the model lands exactly where it sits on screen, with
     * every other pixel left at alpha 0.</p>
     */
    protected void takeScreenshot(GuiContext context)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null || mc.getWindow() == null)
        {
            return;
        }

        int width = mc.getWindow().getFramebufferWidth();
        int height = mc.getWindow().getFramebufferHeight();

        if (width <= 0 || height <= 0)
        {
            return;
        }

        ScreenshotCapture.captureModel(width, height, () -> this.modelRenderer.draw(context), this.modelName);
    }

    @Override
    public void draw(GuiContext context)
    {
        if (this.consumeScreenshotRequest())
        {
            this.takeScreenshot(context);
        }

        if (this.models.isVisible())
        {
            this.openModels.area.draw(0xaa000000);
        }
        else if (this.poses.isVisible())
        {
            this.openPoses.area.draw(0xaa000000);
        }
        else if (this.options.isVisible())
        {
            this.openOptions.area.draw(0xaa000000);
        }

        if (this.modelRenderer.swinging)
        {
            this.running.area.draw(0x66000000);
        }

        if (this.held)
        {
            this.items.area.draw(0x66000000);
        }

        if (this.modelRenderer.aabb)
        {
            this.hitbox.area.draw(0x66000000);
        }

        if (this.modelRenderer.looking)
        {
            this.looking.area.draw(0x66000000);
        }

        super.draw(context);
    }

    /**
     * The panel's pose editor: legacy's inner subclass whose only job is to mark
     * the model dirty on every translate/scale/rotate change.
     */
    public static class GuiModelPoseTransformations extends GuiPoseTransformations
    {
        public GuiModelEditorPanel panel;

        public GuiModelPoseTransformations(MinecraftClient mc, GuiModelEditorPanel panel)
        {
            super(mc);

            this.panel = panel;
        }

        @Override
        public void setT(double x, double y, double z)
        {
            super.setT(x, y, z);
            this.panel.dirty();
        }

        @Override
        public void setS(double x, double y, double z)
        {
            super.setS(x, y, z);
            this.panel.dirty();
        }

        @Override
        public void setR(double x, double y, double z)
        {
            super.setR(x, y, z);
            this.panel.dirty();
        }
    }
}
