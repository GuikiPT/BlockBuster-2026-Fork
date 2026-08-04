package mchorse.chameleon.metamorph.editor;

import mchorse.chameleon.lib.ChameleonModel;
import mchorse.chameleon.metamorph.ChameleonMorph;
import mchorse.chameleon.metamorph.pose.AnimatedPose;
import mchorse.chameleon.metamorph.pose.AnimatedPoseTransform;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiToggleElement;
import mchorse.mclib.client.gui.framework.elements.context.GuiContextMenu;
import mchorse.mclib.client.gui.framework.elements.context.GuiSimpleContextMenu;
import mchorse.mclib.client.gui.framework.elements.input.GuiColorElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTexturePicker;
import mchorse.mclib.client.gui.framework.elements.input.GuiTrackpadElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTransformations;
import mchorse.mclib.client.gui.framework.elements.list.GuiStringListElement;
import mchorse.mclib.client.gui.utils.GuiUtils;
import mchorse.mclib.client.gui.utils.Icons;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.utils.Direction;
import mchorse.mclib.utils.resources.RLUtils;
import mchorse.metamorph.client.gui.editor.GuiAnimation;
import mchorse.metamorph.client.gui.editor.GuiMorphPanel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Custom model morph panel which allows editing custom textures for materials of the custom model morph
 *
 * <p>In practice: the skin picker, the per-bone pose editor (transform, fixate,
 * glow, colour, plus "apply to children" for the last three), the two scales,
 * and the morph-transition settings with the action-player toggle.</p>
 *
 * <p>The pose is <b>opt-in</b>: {@code morph.pose} is null until "Create pose"
 * builds one entry per bone, and the button doubles as "Reset pose" (setting it
 * back to null). Everything below the button is hidden while it is null.</p>
 *
 * <p>Port note: {@code JsonToNBT.getTagFromJson} /
 * {@code GuiScreen.get/setClipboardString} become
 * {@link StringNbtReader#parse} / {@link GuiUtils#getClipboardString()}
 * / {@link GuiUtils#setClipboardString(String)}.</p>
 *
 * Legacy source: chameleon/.../metamorph/editor/GuiChameleonMainPanel.java
 */
public class GuiChameleonMainPanel extends GuiMorphPanel<ChameleonMorph, GuiChameleonMorph> implements IBonePicker
{
    /* Materials */
    public GuiButtonElement skin;
    public GuiTexturePicker picker;

    public GuiButtonElement createPose;
    public GuiStringListElement bones;
    public GuiToggleElement absoluteBrightness;
    public GuiTrackpadElement glow;
    public GuiColorElement color;
    public GuiToggleElement fixed;
    public GuiToggleElement animated;
    public GuiPoseTransformations transforms;
    public GuiAnimation animation;
    public GuiToggleElement player;

    public GuiTrackpadElement scale;
    public GuiTrackpadElement scaleGui;

    private IKey createLabel = IKey.lang("chameleon.gui.editor.create_pose");
    private IKey resetLabel = IKey.lang("chameleon.gui.editor.reset_pose");

    private AnimatedPoseTransform transform;

    /**
     * The bone-list context menu: always a "copy pose", plus a "paste pose" when
     * the clipboard happens to hold a parseable pose compound.
     */
    public static GuiContextMenu createCopyPasteMenu(Runnable copy, Consumer<AnimatedPose> paste)
    {
        GuiSimpleContextMenu menu = new GuiSimpleContextMenu(MinecraftClient.getInstance());
        AnimatedPose pose = null;

        try
        {
            NbtCompound tag = StringNbtReader.parse(GuiUtils.getClipboardString());
            AnimatedPose loaded = new AnimatedPose();

            loaded.fromNBT(tag);

            pose = loaded;
        }
        catch (Exception e)
        {
        }

        menu.action(Icons.COPY, IKey.lang("chameleon.gui.editor.context.copy"), copy);

        if (pose != null)
        {
            final AnimatedPose innerPose = pose;

            menu.action(Icons.PASTE, IKey.lang("chameleon.gui.editor.context.paste"), () -> paste.accept(innerPose));
        }

        return menu;
    }

    public GuiChameleonMainPanel(MinecraftClient mc, GuiChameleonMorph editor)
    {
        super(mc, editor);

        /* Materials view */
        this.skin = new GuiButtonElement(mc, IKey.lang("chameleon.gui.editor.pick_skin"), (b) ->
        {
            this.picker.refresh();
            this.picker.fill(this.morph.skin);
            this.add(this.picker);
            this.picker.resize();
        });
        this.picker = new GuiTexturePicker(mc, (rl) -> this.morph.skin = RLUtils.clone(rl));

        this.createPose = new GuiButtonElement(mc, this.createLabel, this::createResetPose);
        this.bones = new GuiStringListElement(mc, this::pickBone);
        this.bones.background().context(() -> createCopyPasteMenu(this::copyCurrentPose, this::pastePose));
        this.absoluteBrightness = new GuiToggleElement(mc, IKey.lang("chameleon.gui.editor.absolute_brightness"), this::toggleAbsoluteBrightness);
        this.glow = new GuiTrackpadElement(mc, this::setGlow).limit(0, 1).values(0.01, 0.1, 0.001);
        this.glow.tooltip(IKey.lang("chameleon.gui.editor.glow"));
        this.color = new GuiColorElement(mc, this::setColor).direction(Direction.RIGHT);
        this.color.tooltip(IKey.lang("chameleon.gui.editor.color"));
        this.color.picker.editAlpha();
        this.fixed = new GuiToggleElement(mc, IKey.lang("chameleon.gui.editor.fixed"), this::toggleFixed);
        this.animated = new GuiToggleElement(mc, IKey.lang("chameleon.gui.editor.animated"), this::toggleAnimated);
        this.transforms = new GuiPoseTransformations(mc);
        this.animation = new GuiAnimation(mc, false);
        this.player = new GuiToggleElement(mc, IKey.lang("chameleon.gui.editor.player"), this::togglePlayer);

        this.scale = new GuiTrackpadElement(mc, (value) -> this.morph.scale = value.floatValue());
        this.scale.tooltip(IKey.lang("chameleon.gui.editor.scale"));
        this.scaleGui = new GuiTrackpadElement(mc, (value) -> this.morph.scaleGui = value.floatValue());
        this.scaleGui.tooltip(IKey.lang("chameleon.gui.editor.scale_gui"));

        this.skin.flex().relative(this).set(10, 10, 110, 20);
        this.picker.flex().relative(this).wh(1F, 1F);

        this.createPose.flex().relative(this.skin).y(1F, 5).w(1F).h(20);
        this.bones.flex().relative(this.createPose).y(1F, 5).w(1F).hTo(this.absoluteBrightness.flex(), -10);
        this.animated.flex().relative(this).x(10).y(1F, -10).w(110).anchorY(1);
        this.fixed.flex().relative(this.animated).y(-1F, -5).w(1F);
        this.color.flex().relative(this.fixed).y(-1F, -10).w(1F);
        this.glow.flex().relative(this.color).y(-1F, -10).w(1F);
        this.absoluteBrightness.flex().relative(this.glow).y(-1F, -10).w(1F);
        this.transforms.flex().relative(this).set(0, 0, 256, 70).x(0.5F, -128).y(1, -80);
        this.animation.flex().relative(this).x(1F, -130).w(130);

        this.player.flex().relative(this.animation.pickInterpolation).x(0F).y(1F, 5).w(1F);
        this.player.tooltip(IKey.lang("chameleon.gui.editor.player_tooltip"));
        this.animation.addBefore(this.animation.interpolations, this.player);

        GuiSimpleContextMenu abMenu = new GuiSimpleContextMenu(MinecraftClient.getInstance());
        GuiSimpleContextMenu glowMenu = new GuiSimpleContextMenu(MinecraftClient.getInstance());
        GuiSimpleContextMenu colorMenu = new GuiSimpleContextMenu(MinecraftClient.getInstance());
        GuiSimpleContextMenu fixateMenu = new GuiSimpleContextMenu(MinecraftClient.getInstance());

        abMenu.action(IKey.lang("chameleon.gui.editor.context.children"), this.applyToChildren((p, c) -> c.absoluteBrightness = p.absoluteBrightness));
        glowMenu.action(IKey.lang("chameleon.gui.editor.context.children"), this.applyToChildren((p, c) -> c.glow = p.glow));
        colorMenu.action(IKey.lang("chameleon.gui.editor.context.children"), this.applyToChildren((p, c) -> c.color.copy(p.color)));
        fixateMenu.action(IKey.lang("chameleon.gui.editor.context.children"), this.applyToChildren((p, c) -> c.fixed = p.fixed));

        this.absoluteBrightness.context(() -> abMenu);
        this.glow.context(() -> glowMenu);
        this.color.context(() -> colorMenu);
        this.fixed.context(() -> fixateMenu);

        GuiElement lowerBottom = new GuiElement(mc);

        lowerBottom.flex().relative(this).xy(1F, 1F).w(130).anchor(1F, 1F).column(5).vertical().stretch().padding(10);
        lowerBottom.add(this.scale, this.scaleGui);

        this.add(this.skin, this.createPose, this.animated, this.fixed, this.color, this.glow, this.absoluteBrightness, this.bones, this.transforms, this.animation, lowerBottom);
    }

    private void copyCurrentPose()
    {
        if (this.morph.pose == null)
        {
            return;
        }

        GuiUtils.setClipboardString(this.morph.pose.toNBT().toString());
    }

    private void pastePose(AnimatedPose pose)
    {
        if (this.morph.pose == null)
        {
            return;
        }

        this.morph.pose.copy(pose);
        this.transforms.set(this.transforms.trans);
    }

    /**
     * Push one property of the selected bone down its whole subtree.
     *
     * <p>The subtree comes from {@code ChameleonModel.getChildren}, which
     * <b>includes the bone itself</b> as its first entry — so the parent is
     * assigned to from itself, harmlessly, and every descendant follows.</p>
     */
    private Runnable applyToChildren(BiConsumer<AnimatedPoseTransform, AnimatedPoseTransform> apply)
    {
        return () ->
        {
            ChameleonModel model = this.morph == null ? null : this.morph.getModel();

            if (model == null || this.morph.pose == null)
            {
                return;
            }

            String bone = this.bones.getCurrentFirst();
            AnimatedPoseTransform anim = this.morph.pose.bones.get(bone);

            if (anim == null)
            {
                return;
            }

            List<String> children = model.getChildren(bone);

            for (String child : children)
            {
                AnimatedPoseTransform childAnim = this.morph.pose.bones.get(child);

                if (childAnim != null)
                {
                    apply.accept(anim, childAnim);
                }
            }
        };
    }

    private void createResetPose(GuiButtonElement button)
    {
        if (this.morph.pose == null)
        {
            ChameleonModel model = this.morph.getModel();

            /* The button is only visible with a resolved model, so this cannot
             * normally fire — but the model can vanish underneath an open editor
             * (the folder scan runs on every picker open), and legacy would NPE
             * on exactly that. */
            if (model == null)
            {
                return;
            }

            AnimatedPose pose = new AnimatedPose();
            List<String> bones = model.getBoneNames();

            for (String bone : bones)
            {
                pose.bones.put(bone, new AnimatedPoseTransform(bone));
            }

            this.morph.pose = pose;
        }
        else
        {
            this.morph.pose = null;
            this.editor.chameleonModelRenderer.boneName = "";
        }

        this.setPoseEditorVisible();
    }

    private void pickBone(List<String> bone)
    {
        this.pickBone(bone.get(0));
    }

    @Override
    public void pickBone(String bone)
    {
        if (this.morph.pose == null)
        {
            return;
        }

        this.transform = this.morph.pose.bones.get(bone);

        if (this.transform == null)
        {
            this.transform = new AnimatedPoseTransform(bone);
            this.morph.pose.bones.put(bone, this.transform);
        }

        this.bones.setCurrentScroll(bone);
        this.animated.toggled(this.morph.pose.animated == AnimatedPoseTransform.ANIMATED);
        this.fixed.toggled(this.transform.fixed == AnimatedPoseTransform.FIXED);
        this.color.picker.setColor(this.transform.color.getRGBAColor());
        this.absoluteBrightness.toggled(this.transform.absoluteBrightness);
        this.glow.setValue(this.transform.glow);
        this.transforms.set(this.transform);
        this.editor.chameleonModelRenderer.boneName = bone;
    }

    private void toggleAbsoluteBrightness(GuiToggleElement toggle)
    {
        this.transform.absoluteBrightness = toggle.isToggled();
    }

    private void setGlow(Double value)
    {
        this.transform.glow = value.floatValue();
    }

    private void setColor(int color)
    {
        this.transform.color.set(color);
    }

    private void toggleFixed(GuiToggleElement toggle)
    {
        this.transform.fixed = toggle.isToggled() ? AnimatedPoseTransform.FIXED : AnimatedPoseTransform.ANIMATED;
    }

    private void toggleAnimated(GuiToggleElement toggle)
    {
        this.morph.pose.animated = toggle.isToggled() ? AnimatedPoseTransform.ANIMATED : AnimatedPoseTransform.FIXED;
    }

    private void togglePlayer(GuiToggleElement toggle)
    {
        this.morph.isActionPlayer = toggle.isToggled();
    }

    @Override
    public void fillData(ChameleonMorph morph)
    {
        super.fillData(morph);

        this.picker.removeFromParent();
        this.setPoseEditorVisible();

        this.animation.fill(morph.animation);
        this.scale.setValue(morph.scale);
        this.scaleGui.setValue(morph.scaleGui);
        this.player.toggled(morph.isActionPlayer);
    }

    @Override
    public void finishEditing()
    {
        this.picker.close();
    }

    private void setPoseEditorVisible()
    {
        ChameleonModel model = this.morph.getModel();
        AnimatedPose pose = this.morph.pose;

        this.createPose.setVisible(model != null && !model.getBoneNames().isEmpty());
        this.createPose.label = pose == null ? this.createLabel : this.resetLabel;
        this.bones.setVisible(model != null && pose != null);
        this.absoluteBrightness.setVisible(model != null && pose != null);
        this.glow.setVisible(model != null && pose != null);
        this.color.setVisible(model != null && pose != null);
        this.fixed.setVisible(model != null && pose != null);
        this.animated.setVisible(model != null && pose != null);
        this.transforms.setVisible(model != null && pose != null);

        if (model != null)
        {
            this.bones.clear();
            this.bones.add(model.getBoneNames());
            this.bones.sort();

            if (this.morph.pose != null && !model.getBoneNames().isEmpty())
            {
                this.pickBone(model.getBoneNames().get(0));
            }
        }
    }

    /**
     * The transform widget bound to an {@link AnimatedPoseTransform}.
     *
     * <p>Two conversions live here and nowhere else, both legacy:
     * <b>X translation is negated</b> in both directions (Chameleon's bone space
     * is mirrored on X — see {@code ModelParser}), and <b>rotation is stored in
     * radians</b> while the widget shows degrees. Legacy's own comment on the
     * latter reads "That was a bad idea...", but the radians are what the saved
     * NBT holds.</p>
     */
    public static class GuiPoseTransformations extends GuiTransformations
    {
        public AnimatedPoseTransform trans;

        public GuiPoseTransformations(MinecraftClient mc)
        {
            super(mc);
        }

        @Override
        protected void localTranslate(double x, double y, double z)
        {
            if (this.trans == null)
            {
                return;
            }

            this.trans.addTranslation(x, y, z,
                GuiStaticTransformOrientation.getOrientation() == TransformOrientation.LOCAL);

            this.fillT(this.trans.x, this.trans.y, this.trans.z);
        }

        public void set(AnimatedPoseTransform trans)
        {
            this.trans = trans;

            if (trans != null)
            {
                this.fillT(-trans.x, trans.y, trans.z);
                this.fillS(trans.scaleX, trans.scaleY, trans.scaleZ);
                this.fillR(trans.rotateX / (float) Math.PI * 180, trans.rotateY / (float) Math.PI * 180, trans.rotateZ / (float) Math.PI * 180);
            }
        }

        @Override
        public void setT(double x, double y, double z)
        {
            this.trans.x = (float) -x;
            this.trans.y = (float) y;
            this.trans.z = (float) z;
        }

        @Override
        public void setS(double x, double y, double z)
        {
            this.trans.scaleX = (float) x;
            this.trans.scaleY = (float) y;
            this.trans.scaleZ = (float) z;
        }

        @Override
        public void setR(double x, double y, double z)
        {
            /* That was a bad idea... */
            this.trans.rotateX = (float) (x / 180F * (float) Math.PI);
            this.trans.rotateY = (float) (y / 180F * (float) Math.PI);
            this.trans.rotateZ = (float) (z / 180F * (float) Math.PI);
        }
    }
}
