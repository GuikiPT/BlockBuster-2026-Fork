package mchorse.blockbuster_pack.client.gui;

import mchorse.blockbuster.ClientProxy;
import mchorse.blockbuster.api.ModelPose;
import mchorse.blockbuster.api.formats.obj.OBJMaterial;
import mchorse.blockbuster.client.gui.dashboard.panels.model_editor.utils.GuiBBModelRenderer;
import mchorse.blockbuster.client.model.ModelCustom;
import mchorse.blockbuster.client.render.LayerBodyPart;
import mchorse.blockbuster.client.render.RenderCustomModel;
import mchorse.blockbuster_pack.morphs.CustomMorph;
import mchorse.mclib.client.gui.framework.GuiBase;
import mchorse.mclib.client.gui.framework.elements.GuiModelRenderer;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiToggleElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTexturePicker;
import mchorse.mclib.client.gui.framework.elements.list.GuiStringListElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDrawable;
import mchorse.mclib.client.gui.utils.Icons;
import mchorse.mclib.client.gui.utils.Label;
import mchorse.mclib.client.gui.utils.LegacyKeyCodes;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.utils.ColorUtils;
import mchorse.mclib.utils.Direction;
import mchorse.mclib.utils.files.entries.AbstractEntry;
import mchorse.mclib.utils.files.entries.FileEntry;
import mchorse.mclib.utils.files.entries.FolderEntry;
import mchorse.mclib.utils.resources.RLUtils;
import mchorse.mclib.utils.resources.ResourceLocation;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.bodypart.BodyPart;
import mchorse.metamorph.bodypart.GuiBodyPartEditor;
import mchorse.metamorph.client.gui.editor.GuiAbstractMorph;
import mchorse.metamorph.client.gui.editor.GuiMorphPanel;
import mchorse.metamorph.client.render.MorphRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Custom morph editor (roadmap P158) — the flagship three-panel model-morph editor.
 *
 * <p>Port of Blockbuster 2.7.2's {@code GuiCustomMorph}. Hosts three panels over
 * a 3D {@link GuiModelRendererBodyPart} preview:</p>
 * <ul>
 *   <li>{@link GuiPosePanel} — the <b>default</b> panel: named/custom pose editing,
 *       per-limb transforms + fixed/glow/color/absoluteBrightness, model switcher,
 *       scale/scaleGui, animation and shape-keys.</li>
 *   <li>{@link GuiCustomBodyPartEditor} — Metamorph's body-part editor specialised
 *       to cross-highlight the picked limb in the preview
 *       ({@link ILimbSelector}).</li>
 *   <li>{@link GuiMaterialsPanel} — per-OBJ-material texture picking, the skin
 *       picker and the chroma-key toggle.</li>
 * </ul>
 *
 * <p>Ctrl+click limb-picking in the preview routes the resolved limb name to
 * whichever panel is shown when it is an {@link ILimbSelector} (installed via
 * {@link GuiBBModelRenderer#pickLimbs}). {@code Shift+P} / {@code Shift+E} jump to
 * the materials panel and open the skin / texture picker, exactly as 1.12.2.</p>
 *
 * <p>{@link #getPresets} surfaces both preset sources: the JSON
 * {@code model.presets} fragments and — since P88.1 landed the {@code b.a} skin
 * tree — one preset per texture under the model's own {@code skins} folder and
 * under the folder its model borrows skins from ({@link #addSkins}).</p>
 *
 * <p>Legacy source:
 * blockbuster-1.12/src/main/java/mchorse/blockbuster_pack/client/gui/GuiCustomMorph.java</p>
 */
public class GuiCustomMorph extends GuiAbstractMorph<CustomMorph>
{
    public GuiPosePanel poseEditor;
    public GuiCustomBodyPartEditor bodyPart;
    public GuiMaterialsPanel materials;
    public GuiModelRendererBodyPart bbRenderer;

    /**
     * Recursively turn a skins folder into morph presets: one preset per texture
     * file, labelled by the path <em>after</em> the {@code "/skins/"} segment,
     * whose NBT is the current morph with {@code name} ({@code "Skin"} /
     * {@code "Texture"}) set to that texture's RL.
     *
     * <p>Legacy {@code GuiCustomMorph.addSkins} — verbatim, including the
     * {@code isTop()} check that keeps the recursion from walking back up
     * through {@code "../"} entries (which delegate their listing to the parent
     * and would otherwise loop forever).</p>
     */
    public static void addSkins(AbstractMorph morph, List<Label<NbtCompound>> list, String name, FolderEntry entry)
    {
        if (entry == null)
        {
            return;
        }

        for (AbstractEntry childEntry : entry.getEntries())
        {
            if (childEntry instanceof FileEntry)
            {
                ResourceLocation location = ((FileEntry) childEntry).resource;
                String label = location.getResourcePath();
                int index = label.indexOf("/skins/");

                if (index != -1)
                {
                    label = label.substring(index + 7);
                }

                addPreset(morph, list, name, label, location);
            }
            else if (childEntry instanceof FolderEntry)
            {
                FolderEntry childFolder = (FolderEntry) childEntry;

                if (!childFolder.isTop())
                {
                    addSkins(morph, list, name, childFolder);
                }
            }
        }
    }

    /**
     * Legacy {@code GuiCustomMorph.addPreset}: the morph's own NBT with one
     * string key overwritten by the skin RL. The swallowed exception is legacy's
     * (a morph whose {@code toNBT} throws simply contributes no preset).
     */
    public static void addPreset(AbstractMorph morph, List<Label<NbtCompound>> list, String name, String label, ResourceLocation skin)
    {
        try
        {
            NbtCompound tag = morph.toNBT();

            tag.putString(name, skin.toString());
            list.add(new Label<NbtCompound>(IKey.str(label), tag));
        }
        catch (Exception e)
        {}
    }

    public GuiCustomMorph(MinecraftClient mc)
    {
        super(mc);

        /* Nice shadow on bottom */
        this.prepend(new GuiDrawable((context) ->
        {
            GuiDraw.drawVerticalGradientRect(0, this.area.ey() - 30, this.area.w, this.area.ey(), 0, ColorUtils.HALF_BLACK);
        }));

        /* Morph panels */
        this.poseEditor = new GuiPosePanel(mc, this);
        this.bodyPart = new GuiCustomBodyPartEditor(mc, this);
        this.materials = new GuiMaterialsPanel(mc, this);

        this.defaultPanel = this.poseEditor;
        this.registerPanel(this.materials, IKey.lang("blockbuster.gui.builder.materials"), Icons.MATERIAL);
        this.registerPanel(this.bodyPart, IKey.lang("blockbuster.gui.builder.body_part"), Icons.LIMB);
        this.registerPanel(this.poseEditor, IKey.lang("blockbuster.gui.builder.pose_editor"), Icons.POSE);

        this.keys().register(IKey.lang("blockbuster.gui.builder.pick_skin"), LegacyKeyCodes.KEY_P, () ->
        {
            this.setPanel(this.materials);

            if (!this.materials.picker.hasParent())
            {
                this.materials.skin.clickItself(GuiBase.getCurrent());
            }
        }).held(LegacyKeyCodes.KEY_LSHIFT);

        this.keys().register(IKey.lang("blockbuster.gui.builder.pick_texture"), LegacyKeyCodes.KEY_E, () ->
        {
            this.setPanel(this.materials);

            if (!this.materials.picker.hasParent())
            {
                this.materials.texture.clickItself(GuiBase.getCurrent());
            }
        }).held(LegacyKeyCodes.KEY_LSHIFT);
    }

    @Override
    protected GuiModelRenderer createMorphRenderer(MinecraftClient mc)
    {
        /* Called from the super constructor (virtual dispatch) — assigns the
         * bbRenderer field before this constructor body runs. */
        this.bbRenderer = new GuiModelRendererBodyPart(mc);
        this.bbRenderer.looking = false;
        this.bbRenderer.origin = true;

        /* Legacy installed the limb picker as:
         *   this.bbRenderer.picker((limb) -> {
         *       if (this.view.delegate instanceof ILimbSelector)
         *           ((ILimbSelector) this.view.delegate).setLimb(limb);
         *   });
         * pickLimbs(...) is that body, resolving the shown panel lazily per pick. */
        this.bbRenderer.pickLimbs(() -> this.view.delegate);

        return this.bbRenderer;
    }

    @Override
    protected void setupRenderer(CustomMorph morph)
    {
        super.setupRenderer(morph);

        ModelPose pose = morph.getCurrentPose();

        if (pose != null)
        {
            this.bbRenderer.setScale(1.25F + pose.size[0]);
            this.bbRenderer.setPosition(0, pose.size[1] / 2F, 0);
        }
    }

    @Override
    public void setPanel(GuiMorphPanel panel)
    {
        this.bbRenderer.limb = null;
        this.updateModelRenderer();

        super.setPanel(panel);
    }

    /**
     * The body-part panel wants the {@code B} hotkey (SEAM P59.1 — the base
     * registers it when this returns true).
     */
    @Override
    protected boolean wantsBodyPartKey(GuiMorphPanel panel)
    {
        return panel instanceof GuiBodyPartEditor;
    }

    /**
     * This editor can only edit if the morph has a resolved model
     * (legacy {@code canEdit}).
     */
    @Override
    public boolean canEdit(AbstractMorph morph)
    {
        return morph instanceof CustomMorph && ((CustomMorph) morph).model != null;
    }

    @Override
    public void startEdit(CustomMorph morph)
    {
        /* Force a re-init: the morph handed to the editor is a copy whose parts
         * carry no dummy host entities, and its manager's initiated latch may
         * already be set from the original. Without this the parts render blank
         * for the whole editing session. */
        morph.parts.reinitBodyParts();

        this.bodyPart.setLimbs(morph.model.limbs.keySet());

        this.bbRenderer.morph = morph;
        this.bbRenderer.limb = null;

        super.startEdit(morph);

        this.updateModelRenderer();
    }

    @Override
    public List<Label<NbtCompound>> getPresets(CustomMorph morph)
    {
        List<Label<NbtCompound>> list = new ArrayList<Label<NbtCompound>>();

        /* Presets baked into the model.json (name -> SNBT fragment), merged over
         * the current morph NBT. */
        if (morph != null && morph.model != null)
        {
            for (Map.Entry<String, String> entry : morph.model.presets.entrySet())
            {
                this.addPreset(morph, list, entry.getKey(), entry.getValue());
            }
        }

        /* ...then one preset per texture in the morph's own skins folder and in
         * the folder its model borrows skins from (P88.1). Legacy passed
         * ClientProxy.tree.getByPath(..., null) directly; ClientProxy.skins is
         * the same call with a null-tree guard for headless. Note both calls
         * append under the same "Skin" key, so a model whose skins field points
         * at itself lists every skin twice — legacy behaviour. */
        if (morph != null && morph.model != null)
        {
            addSkins(morph, list, "Skin", ClientProxy.skins(morph.getKey() + "/skins"));
            addSkins(morph, list, "Skin", ClientProxy.skins(morph.model.skins + "/skins"));
        }

        return list;
    }

    /**
     * Refresh the live 3D preview from the current morph: model blueprint, skin
     * (falling back to the model's default texture), material overrides and the
     * resolved current pose. Mirrors legacy {@code updateModelRenderer}.
     */
    public void updateModelRenderer()
    {
        CustomMorph custom = this.morph;

        if (custom == null || this.bbRenderer == null || custom.model == null)
        {
            return;
        }

        ResourceLocation texture = custom.skin == null ? custom.model.defaultTexture : custom.skin;

        this.bbRenderer.materials = custom.materials;
        this.bbRenderer.model = ModelCustom.MODELS.get(custom.getKey());
        /* Both forms: the Identifier picks the render layer, the McLib location
         * is what an is3D limb extrudes from (see GuiBBModelRenderer.skin). */
        this.bbRenderer.skin = texture;
        this.bbRenderer.texture = texture == null ? null : texture.toIdentifier();
        this.bbRenderer.setPose(custom.getCurrentPose());
    }

    /**
     * Custom model morph panel which allows editing custom textures for
     * materials of the custom model morph.
     */
    public static class GuiMaterialsPanel extends GuiMorphPanel<CustomMorph, GuiCustomMorph>
    {
        /* Materials */
        public GuiButtonElement skin;
        public GuiButtonElement texture;
        public GuiStringListElement materials;
        public GuiTexturePicker picker;
        public GuiToggleElement keying;

        public GuiMaterialsPanel(MinecraftClient mc, GuiCustomMorph editor)
        {
            super(mc, editor);

            Consumer<ResourceLocation> skin = (rl) ->
            {
                this.morph.skin = RLUtils.clone(rl);
                this.editor.updateModelRenderer();
            };

            Consumer<ResourceLocation> material = this::setCurrentMaterialRL;

            /* Materials view */
            this.skin = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.builder.pick_skin"), (b) ->
            {
                this.picker.refresh();
                this.picker.fill(this.morph.skin);
                this.picker.callback = skin;
                this.add(this.picker);
                this.picker.resize();
            });
            this.texture = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.builder.pick_texture"), (b) ->
            {
                ResourceLocation location = this.morph.materials.get(this.materials.getCurrentFirst());

                this.picker.refresh();
                this.picker.fill(location);
                this.picker.callback = material;
                this.add(this.picker);
                this.picker.resize();
            });
            this.materials = new GuiStringListElement(mc, (str) -> this.materials.setCurrent(str.get(0)));
            this.materials.background();
            this.keying = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.image.keying"), false, (b) -> this.morph.keying = b.isToggled());
            this.keying.tooltip(IKey.lang("blockbuster.gui.image.keying_tooltip"), Direction.TOP);
            this.picker = new GuiTexturePicker(mc, skin);

            this.skin.flex().relative(this).set(10, 10, 110, 20);
            this.texture.flex().relative(this.skin).set(0, 25, 110, 20);
            this.materials.flex().relative(this.texture).set(0, 25, 110, 0).hTo(this.keying.flex(), -5);
            this.keying.flex().relative(this).x(10).w(110).y(1F, -24);
            this.picker.flex().relative(this).wh(1F, 1F);

            this.add(this.skin, this.texture, this.keying, this.materials);
        }

        /* Package-private (legacy: private) so the material apply logic is
         * headlessly testable; behavior is identical to 1.12.2. */
        void setCurrentMaterialRL(ResourceLocation rl)
        {
            String key = this.materials.getCurrentFirst();

            if (rl == null)
            {
                this.morph.materials.remove(key);
            }
            else
            {
                this.morph.materials.put(key, rl);
            }

            this.editor.updateModelRenderer();
        }

        @Override
        public void fillData(CustomMorph morph)
        {
            super.fillData(morph);

            this.materials.clear();

            for (Map.Entry<String, OBJMaterial> entry : morph.model.materials.entrySet())
            {
                if (entry.getValue().useTexture)
                {
                    this.materials.add(entry.getKey());
                }
            }

            this.materials.sort();
            this.picker.removeFromParent();

            boolean noMaterials = this.materials.getList().isEmpty();

            if (!noMaterials)
            {
                this.materials.setIndex(0);
            }

            this.materials.setVisible(!noMaterials);
            this.texture.setVisible(!noMaterials);
            this.keying.toggled(morph.keying);
        }

        @Override
        public void finishEditing()
        {
            this.picker.close();

            super.finishEditing();
        }

        @Override
        public void draw(GuiContext context)
        {
            if (this.materials.isVisible())
            {
                GuiDraw.drawStringWithShadow(this.font, I18n.translate("blockbuster.gui.builder.obj_materials"), this.materials.area.x, this.materials.area.y - 12, 0xffffff);
            }

            super.draw(context);
        }
    }

    /**
     * Model renderer, but it also renders body parts (legacy
     * {@code GuiModelRendererBodyPart}). Scales the preview by the morph's own
     * {@code scale}.
     */
    public static class GuiModelRendererBodyPart extends GuiBBModelRenderer
    {
        public CustomMorph morph;

        public GuiModelRendererBodyPart(MinecraftClient mc)
        {
            super(mc);
        }

        @Override
        protected float getScale()
        {
            return this.morph == null ? 1F : this.morph.scale;
        }

        /**
         * Legacy {@code renderModel} ended with
         * {@code LayerBodyPart.renderBodyParts(dummy, this.morph, this.model, 0,
         * 0, partialTicks, dummy.ticksExisted + partialTicks, headYaw,
         * headPitch, factor)} — the same overlay the in-world renderer draws,
         * with the viewport's own pose inputs.
         *
         * <p>On 1.20.4 the body parts draw nested morphs, which need a
         * {@link MorphRenderContext} frame; the GUI 3D pass has none of its own
         * (it draws the base model straight through {@code ModelCustom.render}),
         * so one is installed around the layer over <b>this</b> matrix stack —
         * the same shape {@code CustomMorphRenderer.renderOnScreen} uses. The
         * consumers are flushed inside the frame because the caller pops the
         * viewport's model-view stack right after this returns.</p>
         */
        @Override
        protected void renderModel(MatrixStack matrices)
        {
            super.renderModel(matrices);

            if (this.morph == null || this.model == null || this.entity == null)
            {
                return;
            }

            try
            {
                /* GL-guarded, like the rest of this viewport: with no client
                 * there are no shared consumers, and the layer still runs (the
                 * frame carries a null provider) so the attach/skip decisions
                 * stay drivable headlessly. */
                VertexConsumerProvider.Immediate consumers = this.mc == null
                    ? null
                    : this.mc.getBufferBuilders().getEntityVertexConsumers();

                MorphRenderContext.push(matrices, consumers, MorphRenderContext.FULL_BRIGHT, OverlayTexture.DEFAULT_UV, this.partialTicks);

                try
                {
                    LayerBodyPart.renderBodyParts(this.entity, this.morph, this.morph.parts, this.model,
                        this.poseContext(), this.partialTicks, RenderCustomModel.BODY_PART_SCALE);

                    if (consumers != null)
                    {
                        consumers.draw();
                    }
                }
                finally
                {
                    MorphRenderContext.pop();
                }
            }
            catch (Exception e)
            {
                /* Total: a broken body part never takes the editor down. */
            }
        }
    }

    /**
     * Metamorph's body-part editor specialised for the custom-model editor
     * (legacy {@code GuiCustomBodyPartEditor}): selecting a body part or picking a
     * limb (in the list or the preview) highlights that limb in the viewport.
     * Nested here because this unit owns {@link GuiCustomMorph} and its renderer.
     */
    public static class GuiCustomBodyPartEditor extends GuiBodyPartEditor implements ILimbSelector
    {
        public GuiCustomBodyPartEditor(MinecraftClient mc, GuiAbstractMorph editor)
        {
            super(mc, editor);
        }

        @Override
        protected void setPart(BodyPart part)
        {
            super.setPart(part);

            if (part != null)
            {
                GuiCustomMorph parent = (GuiCustomMorph) this.editor;

                parent.bbRenderer.limb = parent.morph.model.limbs.get(part.limb);
            }
        }

        @Override
        protected void pickLimb(String limbName)
        {
            GuiCustomMorph parent = (GuiCustomMorph) this.editor;

            super.pickLimb(limbName);
            parent.bbRenderer.limb = parent.morph.model.limbs.get(limbName);
        }

        @Override
        public void setLimb(String limb)
        {
            try
            {
                this.pickLimb(limb);
                this.limbs.setCurrent(limb);
            }
            catch (Exception e) {}
        }
    }
}
