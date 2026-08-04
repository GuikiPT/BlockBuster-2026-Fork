package mchorse.blockbuster_pack.client.gui;

import mchorse.blockbuster.ClientProxy;
import mchorse.blockbuster.client.gui.dashboard.panels.model_editor.utils.GuiPoseTransformations;
import mchorse.blockbuster_pack.morphs.ImageMorph;
import mchorse.mclib.client.gui.framework.GuiBase;
import mchorse.mclib.client.gui.framework.elements.GuiScrollElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiCirculateElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiToggleElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiColorElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTexturePicker;
import mchorse.mclib.client.gui.framework.elements.input.GuiTrackpadElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.framework.elements.utils.GuiLabel;
import mchorse.mclib.client.gui.utils.Elements;
import mchorse.mclib.client.gui.utils.Icons;
import mchorse.mclib.client.gui.utils.Label;
import mchorse.mclib.client.gui.utils.LegacyKeyCodes;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.utils.Direction;
import mchorse.mclib.utils.RenderingUtils;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.client.gui.editor.GuiAbstractMorph;
import mchorse.metamorph.client.gui.editor.GuiAnimation;
import mchorse.metamorph.client.gui.editor.GuiMorphPanel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.nbt.NbtCompound;

import java.util.ArrayList;
import java.util.List;

/**
 * Image morph editor (roadmap P159) — port of 2.7.2's {@code GuiImageMorph}.
 *
 * <p>A single scrollable column exposing the whole {@link ImageMorph} option set:
 * texture picker; shaded / lighting / billboard / remove-parent-space toggles; the
 * 4-mode facing circulate (legacy order {@code rotate_xyz → rotate_y → lookat_xyz
 * → lookat_y}, {@link ImageMorph#EDITOR_FACING_ORDER}); crop L/R/T/B integer
 * trackpads; resize-crop; RGBA colour; UV offset X/Y; rotation; keying / extrusion
 * / shadow toggles; the transition {@link GuiAnimation}; and a live {@code WxH}
 * texture-dimension label. <b>Shift+E</b> opens the texture picker.</p>
 *
 * <p>Load-bearing quirks preserved 1:1 from 1.12.2:</p>
 * <ul>
 *   <li>Crop trackpads bind {@code Left→crop.x}, {@code Right→crop.z},
 *       {@code Top→crop.y}, {@code Bottom→crop.w} — the validated {@code (L,T,R,B)}
 *       NBT order with the {@code x=left, y=top, z=right, w=bottom} vector
 *       packing — each read back as an {@code int}.</li>
 *   <li>Toggling <b>billboard</b> on force-enables <b>remove parent space</b>
 *       (and reflects it in the toggle), matching legacy.</li>
 *   <li>The facing circulate offers only the four editor modes (never
 *       {@code lookat_direction}).</li>
 *   <li>{@code fillData} deliberately does <b>not</b> populate the
 *       <b>rotation</b> trackpad — a 1.12.2 omission kept verbatim so the field
 *       reads 0 on open and only writes {@code morph.rotation} once the user edits
 *       it.</li>
 * </ul>
 *
 * <p><b>batch-4 integration (deferred, documented markers below):</b></p>
 * <ul>
 *   <li>The pose-transform widget ({@link GuiPoseTransformations}) landed with
 *       S22 P226: mounted along the panel bottom
 *       ({@code 256×70}, centred, 75&nbsp;px above the panel bottom) and bound to
 *       the live {@code morph.pose} in {@code fillData}, exactly as 1.12.2.</li>
 *   <li>The {@code image/skins} presets ({@code GuiCustomMorph.addSkins} over
 *       {@code ClientProxy.tree}) landed with P88.1 — see {@link #getPresets}.</li>
 *   <li>Legacy overrode {@code createMorphRenderer} with a {@code GuiMorphRenderer}
 *       drawing the edited morph; the base editor's placeholder viewport stands in
 *       until the P58/P54 morph-preview seam is wired (same as the sibling
 *       editors).</li>
 * </ul>
 *
 * <p>This editor is registered into the creative morph selector by
 * {@code BlockbusterFactory} (P157, parallel) — this class owns only the editor.</p>
 *
 * Legacy source: blockbuster-1.12/src/main/java/mchorse/blockbuster_pack/client/gui/GuiImageMorph.java
 */
public class GuiImageMorph extends GuiAbstractMorph<ImageMorph>
{
    public GuiImageMorphPanel general;

    public GuiImageMorph(MinecraftClient mc)
    {
        super(mc);

        this.defaultPanel = this.general = new GuiImageMorphPanel(mc, this);
        this.registerPanel(this.general, IKey.lang("blockbuster.morph.image"), Icons.GEAR);

        this.keys().register(IKey.lang("blockbuster.gui.builder.pick_texture"), LegacyKeyCodes.KEY_E, () ->
        {
            if (!this.general.picker.hasParent())
            {
                this.general.texture.clickItself(GuiBase.getCurrent());
            }
        }).held(LegacyKeyCodes.KEY_LSHIFT);
    }

    @Override
    public boolean canEdit(AbstractMorph morph)
    {
        return morph instanceof ImageMorph;
    }

    @Override
    public List<Label<NbtCompound>> getPresets(ImageMorph morph)
    {
        List<Label<NbtCompound>> list = new ArrayList<Label<NbtCompound>>();

        /* One preset per texture under config/blockbuster/models/image/skins
         * (P88.1). ClientProxy.skins is ClientProxy.tree.getByPath(path, null)
         * with a null-tree guard for headless. */
        GuiCustomMorph.addSkins(morph, list, "Texture", ClientProxy.skins("image/skins"));

        return list;
    }

    public static class GuiImageMorphPanel extends GuiMorphPanel<ImageMorph, GuiImageMorph>
    {
        public static final RenderingUtils.Facing[] SORTED_FACING_MODES = ImageMorph.EDITOR_FACING_ORDER;

        public GuiPoseTransformations pose;
        public GuiTexturePicker picker;
        public GuiButtonElement texture;
        public GuiToggleElement shaded;
        public GuiToggleElement lighting;
        public GuiToggleElement billboard;
        public GuiToggleElement removeParentScaleRotation;
        public GuiLabel facingModeLabel;
        public GuiCirculateElement facingMode;

        public GuiTrackpadElement left;
        public GuiTrackpadElement right;
        public GuiTrackpadElement top;
        public GuiTrackpadElement bottom;
        public GuiToggleElement resizeCrop;
        public GuiColorElement color;

        public GuiTrackpadElement offsetX;
        public GuiTrackpadElement offsetY;
        public GuiTrackpadElement rotation;
        public GuiToggleElement keying;
        public GuiToggleElement thickness;
        public GuiToggleElement shadow;

        public GuiAnimation animation;

        public GuiImageMorphPanel(MinecraftClient mc, GuiImageMorph editor)
        {
            super(mc, editor);

            this.pose = new GuiPoseTransformations(mc);
            this.pose.flex().relative(this.area).set(0, 0, 256, 70).x(0.5F, -128).y(1, -75);
            this.texture = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.builder.pick_texture"), (b) ->
            {
                this.picker.refresh();
                this.picker.fill(this.morph.texture);

                this.add(this.picker);
                this.picker.resize();
            });

            this.shaded = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.me.limbs.shading"), false, (b) -> this.morph.shaded = b.isToggled());
            this.lighting = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.me.limbs.lighting"), false, (b) -> this.morph.lighting = b.isToggled());
            this.billboard = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.billboard"), false, (b) ->
            {
                this.morph.billboard = b.isToggled();

                if (b.isToggled())
                {
                    this.morph.removeParentScaleRotation = true;

                    this.removeParentScaleRotation.toggled(true);
                }
            });
            this.picker = new GuiTexturePicker(mc, (rl) -> this.morph.texture = rl);
            this.removeParentScaleRotation = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.image.remove_parent_space_effects"), false, (b) -> this.morph.removeParentScaleRotation = b.isToggled());
            this.removeParentScaleRotation.tooltip(IKey.lang("blockbuster.gui.image.remove_parent_space_effects_tooltip"));

            this.facingMode = new GuiCirculateElement(mc, (b) ->
            {
                this.morph.facing = SORTED_FACING_MODES[this.facingMode.getValue()];
            });
            this.facingMode.addLabel(IKey.lang("blockbuster.gui.snowstorm.appearance.camera_facing.rotate_xyz"));
            this.facingMode.addLabel(IKey.lang("blockbuster.gui.snowstorm.appearance.camera_facing.rotate_y"));
            this.facingMode.addLabel(IKey.lang("blockbuster.gui.snowstorm.appearance.camera_facing.lookat_xyz"));
            this.facingMode.addLabel(IKey.lang("blockbuster.gui.snowstorm.appearance.camera_facing.lookat_y"));
            this.facingModeLabel = Elements.label(IKey.lang("blockbuster.gui.snowstorm.appearance.camera_facing.label"), 20).anchor(0, 0.5F);

            this.left = new GuiTrackpadElement(mc, (value) -> this.morph.crop.x = value.intValue());
            this.left.tooltip(IKey.lang("blockbuster.gui.image.left"));
            this.left.integer();
            this.right = new GuiTrackpadElement(mc, (value) -> this.morph.crop.z = value.intValue());
            this.right.tooltip(IKey.lang("blockbuster.gui.image.right"));
            this.right.integer();
            this.top = new GuiTrackpadElement(mc, (value) -> this.morph.crop.y = value.intValue());
            this.top.tooltip(IKey.lang("blockbuster.gui.image.top"));
            this.top.integer();
            this.bottom = new GuiTrackpadElement(mc, (value) -> this.morph.crop.w = value.intValue());
            this.bottom.tooltip(IKey.lang("blockbuster.gui.image.bottom"));
            this.bottom.integer();
            this.resizeCrop = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.image.resize_crop"), false, (b) -> this.morph.resizeCrop = b.isToggled());
            this.color = new GuiColorElement(mc, (value) -> this.morph.color = value).direction(Direction.TOP);
            this.color.picker.editAlpha();

            this.offsetX = new GuiTrackpadElement(mc, (value) -> this.morph.offsetX = value.floatValue());
            this.offsetX.tooltip(IKey.lang("blockbuster.gui.image.offset_x"));
            this.offsetY = new GuiTrackpadElement(mc, (value) -> this.morph.offsetY = value.floatValue());
            this.offsetY.tooltip(IKey.lang("blockbuster.gui.image.offset_y"));
            this.rotation = new GuiTrackpadElement(mc, (value) -> this.morph.rotation = value.floatValue());
            this.rotation.tooltip(IKey.lang("blockbuster.gui.image.rotation"));
            this.keying = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.image.keying"), false, (b) -> this.morph.keying = b.isToggled());
            this.keying.tooltip(IKey.lang("blockbuster.gui.image.keying_tooltip"), Direction.TOP);
            this.thickness = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.image.thickness"), false, (b) -> this.morph.thickness = b.isToggled());
            this.thickness.tooltip(IKey.lang("blockbuster.gui.image.thickness_tooltip"), Direction.TOP);
            this.shadow = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.image.shadow"), false, (b) -> this.morph.shadow = b.isToggled());
            this.shadow.tooltip(IKey.lang("blockbuster.gui.image.shadow_tooltip"), Direction.TOP);

            this.picker.flex().relative(this.area).wh(1F, 1F);

            GuiScrollElement column = new GuiScrollElement(mc);

            column.scroll.opposite = true;
            column.flex().relative(this).w(150).h(1F).column(5).vertical().stretch().scroll().height(20).padding(10);
            /* Legacy inserted a never-instantiated `scale` trackpad (a Forge-era
             * null-element accident) between texture and shaded; parity is the
             * visible layout, so it is left out here (see plan S14 P159). */
            column.add(this.texture, this.shaded, this.lighting, this.billboard, this.removeParentScaleRotation, this.facingModeLabel, this.facingMode, Elements.label(IKey.lang("blockbuster.gui.image.crop")));
            column.add(this.left, this.right, this.top, this.bottom, this.resizeCrop, this.color, this.offsetX, this.offsetY, this.rotation, this.keying, this.thickness, this.shadow);

            this.animation = new GuiAnimation(mc, true);
            this.animation.flex().relative(this).x(1F, -130).w(130);

            this.add(this.pose, column, this.animation);
        }

        @Override
        public void fillData(ImageMorph morph)
        {
            super.fillData(morph);

            this.picker.removeFromParent();

            this.pose.set(morph.pose);
            this.shaded.toggled(morph.shaded);
            this.lighting.toggled(morph.lighting);
            this.billboard.toggled(morph.billboard);
            this.removeParentScaleRotation.toggled(morph.removeParentScaleRotation);
            this.facingMode.setValue(indexOfFacing(this.morph.facing));

            this.left.setValue(morph.crop.x);
            this.right.setValue(morph.crop.z);
            this.top.setValue(morph.crop.y);
            this.bottom.setValue(morph.crop.w);
            this.resizeCrop.toggled(morph.resizeCrop);

            this.color.picker.setColor(morph.color);
            this.offsetX.setValue(morph.offsetX);
            this.offsetY.setValue(morph.offsetY);
            /* Legacy omitted rotation.setValue(...) here — kept verbatim. */
            this.keying.toggled(morph.keying);
            this.thickness.toggled(morph.thickness);
            this.shadow.toggled(morph.shadow);

            this.animation.fill(morph.animation);
        }

        /**
         * Index of the given facing in the 4-mode editor cycle, or {@code -1} when
         * absent — mirroring legacy {@code ArrayUtils.indexOf(SORTED_FACING_MODES,
         * facing)}. A facing outside the editor set (e.g. {@code lookat_direction},
         * the 5th enum value never offered here) yields {@code -1}, and
         * {@link GuiCirculateElement#setValue(int)} clamps a negative index to the
         * <b>last</b> label ({@code labels.size() - 1}, i.e. {@code lookat_y}) — the
         * exact 1.12.2 on-open display for such a morph.
         */
        private static int indexOfFacing(RenderingUtils.Facing facing)
        {
            for (int i = 0; i < SORTED_FACING_MODES.length; i++)
            {
                if (SORTED_FACING_MODES[i] == facing)
                {
                    return i;
                }
            }

            return -1;
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
            int w = this.morph.getWidth();
            int h = this.morph.getHeight();
            String label = I18n.translate("blockbuster.gui.image.dimensions", w, h);

            GuiDraw.drawStringWithShadow(this.font, label, this.area.x(0.5F, GuiDraw.textWidth(this.font, label)), this.area.y + 16, 0xaaaaaa);

            super.draw(context);
        }
    }
}
