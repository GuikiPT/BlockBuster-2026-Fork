package mchorse.blockbuster_pack.client.gui;

import java.util.List;
import java.util.function.BiConsumer;

import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.api.ModelLimb;
import mchorse.blockbuster.api.ModelPose;
import mchorse.blockbuster.client.gui.dashboard.panels.model_editor.tabs.GuiModelPoses;
import mchorse.blockbuster.client.gui.dashboard.panels.model_editor.utils.GuiPoseTransformations;
import mchorse.blockbuster.client.gui.utils.GuiShapeKeysEditor;
import mchorse.blockbuster_pack.morphs.CustomMorph;
import mchorse.blockbuster_pack.morphs.CustomMorph.LimbProperties;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiToggleElement;
import mchorse.mclib.client.gui.framework.elements.context.GuiContextMenu;
import mchorse.mclib.client.gui.framework.elements.context.GuiSimpleContextMenu;
import mchorse.mclib.client.gui.framework.elements.input.GuiColorElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTrackpadElement;
import mchorse.mclib.client.gui.framework.elements.list.GuiStringListElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.utils.Direction;
import mchorse.metamorph.client.gui.editor.GuiAnimation;
import mchorse.metamorph.client.gui.editor.GuiMorphPanel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.nbt.NbtCompound;

/**
 * CustomMorph pose panel (roadmap P158).
 *
 * <p>Port of Blockbuster 2.7.2's {@code GuiPosePanel} — the default panel of the
 * flagship {@link GuiCustomMorph}. A dual-mode editor over the same list widget:</p>
 * <ul>
 *   <li><b>Model-pose mode</b> (no {@code customPose}): the list holds the
 *       model's named poses; selecting one sets {@code morph.currentPose}. The
 *       "create" button converts the current pose into an editable
 *       {@code customPose} (via {@link CustomMorph#convertProp}).</li>
 *   <li><b>Custom-pose mode</b> ({@code customPose} set): the list holds the
 *       model's limbs; selecting one binds its {@link LimbProperties} to the
 *       {@link GuiPoseTransformations} widget plus the {@code fixed} toggle, the
 *       {@code glow} trackpad (0..1), the RGBA {@code color} picker and the
 *       {@code absoluteBrightness} toggle. "reset" clears the custom pose.</li>
 * </ul>
 *
 * <p>Also: the {@code poseOnSneak} toggle, the model switcher (a hidden list
 * toggled by the "pick model" button), {@code scale}/{@code scaleGui} trackpads,
 * the {@link GuiAnimation} sub-editor and the {@link GuiShapeKeysEditor} (shown
 * only when {@code model.shapes} is non-empty). Right-click context menus on
 * fixed/glow/color/absoluteBrightness apply the property to every child limb;
 * the list context offers copy/paste-pose over the system clipboard
 * ({@link GuiModelPoses#createCopyPasteMenu}).</p>
 *
 * <p>This panel is an {@link ILimbSelector}: a Ctrl+click limb pick in the
 * preview routes into {@link #setLimb(String)} through the shared
 * {@code GuiBBModelRenderer} picker seam, cross-selecting the limb in the list.</p>
 *
 * <p><b>Parity notes.</b></p>
 * <ul>
 * <li><b>Color callback.</b> Legacy's {@code GuiColorElement} handed the callback
 * a {@code Color}; the bundled McLib element hands an RGBA {@code int}, so
 * {@code color.set(int)} replaces {@code color.set(Color)} — same effect, since
 * {@code Color.set(int)} decodes alpha. {@code picker.setColor(int)} /
 * {@code picker.editAlpha()} are used exactly as 1.12.2.</li>
 * <li><b>Clipboard.</b> Legacy {@code GuiScreen.setClipboardString(...)} becomes
 * {@code MinecraftClient.getInstance().keyboard.setClipboard(...)}, matching the
 * ported {@link GuiModelPoses}.</li>
 * <li><b>Model list source.</b> Legacy read {@code Blockbuster.proxy.models}; the
 * port reads {@link CommonProxy#models} (guarded against a null handler in early
 * / headless load — total, never crashes).</li>
 * <li><b>Unguarded {@code currentLimbProp}.</b> The fixed/glow/color/abs toggles
 * dereference {@code currentLimbProp} exactly as legacy did — it is bound by
 * {@link #setLimb} before any of those widgets is interactive, and the widgets
 * are hidden while {@code customPose == null}. Preserved rather than "fixed".</li>
 * </ul>
 *
 * <p>Legacy source:
 * blockbuster-1.12/src/main/java/mchorse/blockbuster_pack/client/gui/GuiPosePanel.java</p>
 */
public class GuiPosePanel extends GuiMorphPanel<CustomMorph, GuiCustomMorph> implements ILimbSelector
{
    /* Custom pose editor */
    public GuiButtonElement reset;
    public GuiButtonElement create;
    public GuiStringListElement list;
    public GuiToggleElement absoluteBrightness;
    public GuiTrackpadElement glow;
    public GuiColorElement color;
    public GuiToggleElement fixed;
    public GuiToggleElement poseOnSneak;
    public GuiPoseTransformations transforms;

    public GuiAnimation animation;
    public GuiShapeKeysEditor shapeKeys;

    /* General options */
    public GuiStringListElement models;
    public GuiButtonElement model;
    public GuiTrackpadElement scale;
    public GuiTrackpadElement scaleGui;

    public LimbProperties currentLimbProp;

    public GuiPosePanel(MinecraftClient mc, GuiCustomMorph editor)
    {
        super(mc, editor);

        /* Custom pose editor */
        this.reset = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.morphs.reset"), (b) ->
        {
            if (this.morph.customPose == null)
            {
                this.morph.currentPose = "";
                this.list.setIndex(-1);
            }
            else
            {
                this.editor.morph.customPose = null;
                this.editor.bbRenderer.limb = null;
                this.updateList();
                this.updateElements();
            }

            this.editor.updateModelRenderer();
        });
        this.reset.flex().relative(this).xy(10, 10).w(110);

        this.create = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.morphs.create"), (b) ->
        {
            if (this.morph.customPose == null)
            {
                this.morph.customPose = this.morph.convertProp(this.morph.getPose(this.mc.player, 0).copy());
            }

            this.updateList();
            this.updateElements();
            this.editor.updateModelRenderer();
        });
        this.create.flex().relative(this.reset).y(25).w(110);

        this.poseOnSneak = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.builder.pose_sneak"), false, (b) ->
        {
            this.morph.currentPoseOnSneak = this.poseOnSneak.isToggled();
            this.editor.updateModelRenderer();
        });
        this.poseOnSneak.flex().relative(this).x(10).y(1F, -24).w(110);

        this.list = new GuiStringListElement(mc, (str) ->
        {
            if (this.morph.customPose == null)
            {
                this.morph.currentPose = str.get(0);
                this.editor.updateModelRenderer();
            }
            else
            {
                this.setLimb(str.get(0));
            }
        });
        this.list.background();
        this.list.flex().xy(0, 40).w(110).hTo(this.poseOnSneak.area, -5);
        this.list.context(this::limbContextMenu);

        this.transforms = new GuiPoseTransformations(mc);
        this.transforms.flex().relative(this.area).set(0, 0, 256, 70).x(0.5F, -128).y(1, -75);

        this.animation = new GuiAnimation(mc, true);
        this.animation.flex().relative(this).x(1F, -130).w(130);
        this.animation.interpolations.removeFromParent();

        /* General options */
        this.model = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.builder.pick_model"), (b) -> this.models.toggleVisible());
        this.scale = new GuiTrackpadElement(mc, (value) ->
        {
            this.morph.scale = value.floatValue();
        });
        this.scale.tooltip(IKey.lang("blockbuster.gui.me.options.scale"));
        this.scaleGui = new GuiTrackpadElement(mc, (value) ->
        {
            this.morph.scaleGui = value.floatValue();
        });
        this.scaleGui.tooltip(IKey.lang("blockbuster.gui.me.options.scale_gui"));

        this.models = new GuiStringListElement(mc, (string) ->
        {
            this.morph.changeModel(string.get(0));
            this.editor.updateModelRenderer();
            this.editor.poseEditor.fillData(this.morph);
            this.editor.bodyPart.setLimbs(this.morph.model.limbs.keySet());
        });
        this.models.background();
        this.models.flex().relative(this.model).w(1F).h(96).anchorY(1F);

        GuiElement options = new GuiElement(mc);

        options.flex().relative(this).x(1F, -130).y(1F).w(130).anchorY(1F).column(5).vertical().stretch().height(20).padding(10);
        options.add(this.model, this.scale, this.scaleGui);

        this.fixed = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.builder.limb.fixed"), (b) ->
        {
            this.currentLimbProp.fixed = this.fixed.isToggled() ? 1F : 0F;
        });
        this.fixed.flex().relative(this.model).x(0F).y(0F, -20).w(1F);

        this.color = new GuiColorElement(mc, (color) ->
        {
            this.currentLimbProp.color.set(color);
        }).direction(Direction.LEFT);
        this.color.flex().relative(this.fixed).x(0F).y(0F, -25).w(1F).h(20);
        this.color.picker.editAlpha();
        this.color.tooltip(IKey.lang("blockbuster.gui.builder.limb.color"));

        this.glow = new GuiTrackpadElement(mc, (value) ->
        {
            this.currentLimbProp.glow = value.floatValue();
        }).limit(0, 1).values(0.01, 0.001, 0.1);
        this.glow.flex().relative(this.color).x(0F).y(0F, -30).w(1F);
        this.glow.tooltip(IKey.lang("blockbuster.gui.builder.limb.glow"));

        this.absoluteBrightness = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.builder.limb.absolute_brighness"), (b) ->
        {
            this.currentLimbProp.absoluteBrightness = this.absoluteBrightness.isToggled();
        });
        this.absoluteBrightness.flex().relative(this.glow).x(0F).y(0F, -30).w(1F);

        GuiSimpleContextMenu abMenu = new GuiSimpleContextMenu(mc);
        GuiSimpleContextMenu glowMenu = new GuiSimpleContextMenu(mc);
        GuiSimpleContextMenu colorMenu = new GuiSimpleContextMenu(mc);
        GuiSimpleContextMenu fixateMenu = new GuiSimpleContextMenu(mc);

        abMenu.action(IKey.lang("blockbuster.gui.builder.context.children"), this.applyToChildren((p, c) -> c.absoluteBrightness = p.absoluteBrightness));
        glowMenu.action(IKey.lang("blockbuster.gui.builder.context.children"), this.applyToChildren((p, c) -> c.glow = p.glow));
        colorMenu.action(IKey.lang("blockbuster.gui.builder.context.children"), this.applyToChildren((p, c) -> c.color.copy(p.color)));
        fixateMenu.action(IKey.lang("blockbuster.gui.builder.context.children"), this.applyToChildren((p, c) -> c.fixed = p.fixed));

        this.absoluteBrightness.context(() -> abMenu);
        this.glow.context(() -> glowMenu);
        this.color.context(() -> colorMenu);
        this.fixed.context(() -> fixateMenu);

        this.shapeKeys = new GuiShapeKeysEditor(mc, () -> this.morph.model);
        this.shapeKeys.flex().relative(this.poseOnSneak).y(-125).w(1F).h(120);

        this.add(this.reset, this.create, this.poseOnSneak, this.shapeKeys, this.list, this.animation, options, this.fixed, this.color, this.glow, this.absoluteBrightness, this.transforms, this.models, this.animation.interpolations);
    }

    private GuiContextMenu limbContextMenu()
    {
        if (this.morph.customPose == null)
        {
            return null;
        }

        return GuiModelPoses.createCopyPasteMenu(this::copyPose, this::pastePose);
    }

    private void copyPose()
    {
        MinecraftClient.getInstance().keyboard.setClipboard(this.morph.customPose.toNBT(new NbtCompound()).toString());
    }

    private void pastePose(ModelPose pose)
    {
        ModelPose currentPose = this.morph.model.getPose(this.morph.currentPose);

        this.morph.customPose.copy(pose);
        this.transforms.set(this.transforms.trans, currentPose == null ? null : currentPose.limbs.get(this.list.getCurrentFirst()));
        this.updateShapeKeys();
    }

    private Runnable applyToChildren(BiConsumer<LimbProperties, LimbProperties> apply)
    {
        return () ->
        {
            String bone = this.list.getCurrentFirst();
            LimbProperties anim = (LimbProperties) this.morph.customPose.limbs.get(bone);
            List<String> children = this.morph.model.getChildren(bone);

            for (String child : children)
            {
                LimbProperties childAnim = (LimbProperties) this.morph.customPose.limbs.get(child);

                apply.accept(anim, childAnim);
            }
        };
    }

    private void updateShapeKeys()
    {
        this.shapeKeys.setVisible(!morph.model.shapes.isEmpty() && this.morph.customPose != null);

        if (this.shapeKeys.isVisible())
        {
            this.shapeKeys.fillData(this.morph.getCurrentPose().shapes);
            this.list.flex().xy(0, 40).w(110).hTo(this.shapeKeys.area, -10);
        }
        else
        {
            this.list.flex().xy(0, 40).w(110).hTo(this.poseOnSneak.area, -5);
        }

        this.resize();
    }

    @Override
    public void setLimb(String limbName)
    {
        if (this.morph.customPose == null)
        {
            return;
        }

        ModelLimb limb = this.morph.model.limbs.get(limbName);
        ModelPose pose = this.morph.model.getPose(this.morph.currentPose);

        this.editor.bbRenderer.limb = limb;
        this.list.setCurrent(limbName);
        this.currentLimbProp = (LimbProperties) this.morph.customPose.limbs.get(limbName);
        this.transforms.set(this.currentLimbProp, pose == null ? null : pose.limbs.get(limbName));
        this.absoluteBrightness.toggled(this.currentLimbProp.absoluteBrightness);
        this.glow.setValue(this.currentLimbProp.glow);
        this.color.picker.setColor(this.currentLimbProp.color.getRGBAColor());
        this.fixed.toggled(this.currentLimbProp.fixed != 0F);
    }

    @Override
    public void fillData(CustomMorph morph)
    {
        super.fillData(morph);

        this.updateList();
        this.updateElements();

        this.poseOnSneak.toggled(morph.currentPoseOnSneak);
        this.animation.fill(morph.animation);

        this.scale.setValue(morph.scale);
        this.scaleGui.setValue(morph.scaleGui);

        this.models.setVisible(false);
        this.models.clear();

        if (CommonProxy.models != null)
        {
            this.models.add(CommonProxy.models.models.keySet());
        }

        this.models.sort();
        this.models.setCurrentScroll(morph.getKey());
    }

    @Override
    public void startEditing()
    {
        super.startEditing();

        this.updateList();
        this.updateElements();
    }

    private void updateElements()
    {
        this.create.setVisible(this.morph.customPose == null);
        this.transforms.setVisible(this.morph.customPose != null);
        this.list.flex().relative(this.morph.customPose == null ? this.create : this.reset);
        this.list.resize();
        this.absoluteBrightness.setVisible(this.morph.customPose != null);
        this.glow.setVisible(this.morph.customPose != null);
        this.color.setVisible(this.morph.customPose != null);
        this.fixed.setVisible(this.morph.customPose != null);

        this.updateShapeKeys();
    }

    private void updateList()
    {
        String current;

        this.list.clear();

        if (this.morph.customPose == null)
        {
            current = this.morph.currentPose;
            this.list.add(this.morph.model.poses.keySet());
            this.list.sort();
        }
        else
        {
            this.list.add(this.morph.model.limbs.keySet());
            this.list.sort();
            this.list.setIndex(0);
            current = this.list.getCurrentFirst();

            if (!this.list.isDeselected())
            {
                this.setLimb(this.list.getCurrentFirst());
            }
        }

        this.list.setCurrent(current);
    }

    @Override
    public void draw(GuiContext context)
    {
        if (this.morph.customPose == null)
        {
            GuiDraw.drawStringWithShadow(this.font, I18n.translate("blockbuster.gui.builder.pose"), this.list.area.x, this.list.area.y - 12, 0xffffff);
        }
        else
        {
            GuiDraw.drawStringWithShadow(this.font, I18n.translate("blockbuster.gui.builder.limbs"), this.list.area.x, this.list.area.y - 12, 0xffffff);
        }

        super.draw(context);
    }
}
