package mchorse.blockbuster.client.gui.dashboard.panels.model_editor.tabs;

import javax.vecmath.Matrix4d;
import javax.vecmath.Vector3f;

import mchorse.blockbuster.api.Model;
import mchorse.blockbuster.api.ModelLimb;
import mchorse.blockbuster.api.ModelLimb.ArmorSlot;
import mchorse.blockbuster.api.ModelLimb.Holding;
import mchorse.blockbuster.api.ModelTransform;
import mchorse.blockbuster.client.gui.dashboard.panels.model_editor.GuiModelEditorPanel;
import mchorse.blockbuster.client.gui.dashboard.panels.model_editor.utils.GuiTextureCanvas;
import mchorse.blockbuster.client.gui.dashboard.panels.model_editor.utils.LimbEditorOps;
import mchorse.blockbuster.client.gui.dashboard.panels.model_editor.utils.ModelEditorMath;
import mchorse.blockbuster.client.gui.dashboard.panels.model_editor.utils.GuiThreeElement;
import mchorse.blockbuster.client.model.ModelCustomRenderer;
import mchorse.blockbuster.client.model.ModelOBJRenderer;
import mchorse.blockbuster.client.model.ModelVoxRenderer;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.GuiScrollElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiCirculateElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiIconElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiToggleElement;
import mchorse.mclib.client.gui.framework.elements.context.GuiSimpleContextMenu;
import mchorse.mclib.client.gui.framework.elements.input.GuiColorElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTrackpadElement;
import mchorse.mclib.client.gui.framework.elements.list.GuiStringListElement;
import mchorse.mclib.client.gui.framework.elements.modals.GuiListModal;
import mchorse.mclib.client.gui.framework.elements.modals.GuiMessageModal;
import mchorse.mclib.client.gui.framework.elements.modals.GuiModal;
import mchorse.mclib.client.gui.framework.elements.modals.GuiPromptModal;
import mchorse.mclib.client.gui.utils.Elements;
import mchorse.mclib.client.gui.utils.Icons;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.utils.Color;
import net.minecraft.client.MinecraftClient;

/**
 * Model editor Limbs tab (roadmap P137) — the limb list, limb CRUD, and the
 * per-limb property/animation editors.
 *
 * <p>Legacy source (ported 1:1):
 * {@code blockbuster-1.12/.../model_editor/tabs/GuiModelLimbs.java}.</p>
 *
 * <p><b>Renderer-class-dependent layout.</b> {@link #getLimbClass} asks the
 * <i>compiled</i> model which renderer a limb produced, and the answer drives
 * everything: a plain {@link ModelCustomRenderer} (a box limb) gets the
 * size/size-offset/texture/mirror panel, anything else (OBJ, VOX) gets the
 * origin panel instead and loses the {@code 3D} toggle; a
 * {@link ModelVoxRenderer} additionally gets the specular colour picker. The
 * same test gates dupe/remove/rename, which refuse on a non-box limb with the
 * {@code obj_limb} message — an OBJ limb's geometry comes from the file, so the
 * blueprint cannot invent or rename one.</p>
 *
 * <p><b>Legacy quirks preserved.</b></p>
 * <ul>
 * <li>The dupe name uniquifier appends {@code "_copy"} in a loop, so duplicating
 * twice yields {@code name_copy} then {@code name_copy_copy}.</li>
 * <li>Remove is refused when the limb's subtree covers the whole model
 * ({@code size == getLimbCount(limb)}) with the {@code last_limb} message.</li>
 * <li>Property callbacks split into <b>rebuild</b> (size, sizeOffset, anchor,
 * origin, mirror, holding — geometry changed) and <b>dirty-only</b> (itemScale,
 * slot, hold, colour, lighting/shading/smooth/3D and every animation flag).
 * That split is a perf contract, not an oversight: a rebuild recompiles the
 * whole {@code ModelCustom}.</li>
 * <li>The origin context menu only appears when the limb's renderer has mesh
 * bounds ({@code min}/{@code max} non-null — i.e. OBJ/VOX). "Move" additionally
 * overwrites the current pose's translate with
 * {@code (-origin.x * 16, origin.y * 16, -origin.z * 16)} — note X and Z are
 * negated and Y is not.</li>
 * <li>The anchor callback only compensates when the {@code 3D} toggle is
 * <i>visible</i> — {@code is3D.isVisible()} is the "this is a box limb" test,
 * not a read of the flag's value.</li>
 * </ul>
 *
 * <p><b>Where the math lives.</b> The three numeric cores legacy kept inline
 * here — {@code fixLimbPosition}, {@code correctTransformationParenting} and the
 * anchor "move" pose overwrite — were extracted to {@link ModelEditorMath} so
 * their sign conventions are golden-tested without GL. This class resolves the
 * GUI/renderer state into their arguments and writes the results back;
 * {@link #limbKind} is the one piece of glue, mapping a compiled limb's renderer
 * class onto the math core's GL-free {@link ModelEditorMath.LimbKind}.</p>
 *
 * <p><b>1.20.4 mapping.</b> {@code Minecraft} &rarr; {@code MinecraftClient};
 * everything else — including the {@code javax.vecmath} matrices behind
 * {@link ModelEditorMath} — is the bundled McLib port, so the parent-correction
 * math is the same code path legacy ran.</p>
 */
public class GuiModelLimbs extends GuiModelEditorTab
{
    private GuiIconElement addLimb;
    private GuiIconElement dupeLimb;
    private GuiIconElement removeLimb;
    private GuiIconElement renameLimb;
    private GuiIconElement parentLimb;

    private GuiStringListElement limbs;
    private GuiScrollElement scroll;

    /* First category */
    private GuiThreeElement size;
    private GuiTrackpadElement sizeOffset;
    private GuiTrackpadElement itemScale;
    private GuiButtonElement texture;
    private GuiThreeElement anchor;
    private GuiThreeElement origin;

    private GuiTextureCanvas textureEditor;

    /* Second category */
    private GuiToggleElement mirror;
    private GuiToggleElement lighting;
    private GuiToggleElement shading;
    private GuiToggleElement smooth;
    private GuiToggleElement is3D;

    private GuiElement colors;
    private GuiColorElement color;
    private GuiColorElement specular;

    private GuiCirculateElement holding;
    private GuiCirculateElement slot;
    private GuiToggleElement hold;
    private GuiToggleElement swiping;
    private GuiToggleElement lookX;
    private GuiToggleElement lookY;
    private GuiToggleElement swinging;
    private GuiToggleElement idle;
    private GuiToggleElement invert;
    private GuiToggleElement wheel;
    private GuiToggleElement wing;
    private GuiToggleElement roll;
    private GuiToggleElement cape;

    private GuiElement vanillaPanel;
    private GuiElement objPanel;

    private float lastAnchorX;
    private float lastAnchorY;
    private float lastAnchorZ;

    public GuiModelLimbs(MinecraftClient mc, GuiModelEditorPanel panel)
    {
        super(mc, panel);

        this.title = IKey.lang("blockbuster.gui.me.limbs.title");

        this.limbs = new GuiStringListElement(mc, (str) -> this.setLimb(str.get(0)));
        this.limbs.background().flex().relative(this).y(20).w(1F).h(100);

        this.scroll = new GuiScrollElement(mc);
        this.scroll.scroll.scrollSpeed = 15;
        this.scroll.flex().relative(this.limbs).y(1F).w(1F).hTo(this.area, 1F).column(5).vertical().stretch().scroll().height(20).padding(10);

        this.textureEditor = new GuiTextureCanvas(mc, this);
        this.textureEditor.flex().relative(this.limbs).y(1F).w(1F).hTo(this.area, 1F);

        /* First category */
        this.size = new GuiThreeElement(mc, (values) ->
        {
            this.panel.limb.size[0] = values[0].intValue();
            this.panel.limb.size[1] = values[1].intValue();
            this.panel.limb.size[2] = values[2].intValue();
            this.panel.rebuildModel();
        });
        this.size.setLimit(0, 8192, true);
        this.sizeOffset = new GuiTrackpadElement(mc, (value) ->
        {
            this.panel.limb.sizeOffset = value.floatValue();
            this.panel.rebuildModel();
        });
        this.itemScale = new GuiTrackpadElement(mc, (value) ->
        {
            this.panel.limb.itemScale = value.floatValue();
            this.panel.dirty();
        });
        this.texture = new GuiButtonElement(mc, IKey.comp(IKey.lang("blockbuster.gui.edit"), IKey.str("...")), (b) ->
        {
            this.textureEditor.toggleVisible();
            this.textureEditor.setSize(this.panel.model.texture[0], this.panel.model.texture[1]);
        });
        this.anchor = new GuiThreeElement(mc, (values) ->
        {
            if (this.is3D.isVisible())
            {
                this.fixLimbPosition(values[0].floatValue(), values[1].floatValue(), values[2].floatValue());
                this.lastAnchorX = this.panel.limb.anchor[0] = values[0].floatValue();
                this.lastAnchorY = this.panel.limb.anchor[1] = values[1].floatValue();
                this.lastAnchorZ = this.panel.limb.anchor[2] = values[2].floatValue();
            }
            else
            {
                this.panel.limb.anchor[0] = values[0].floatValue();
                this.panel.limb.anchor[1] = values[1].floatValue();
                this.panel.limb.anchor[2] = values[2].floatValue();
            }

            this.panel.rebuildModel();
        });
        this.origin = new GuiThreeElement(mc, (values) ->
        {
            this.fixLimbPosition(values[0].floatValue(), values[1].floatValue(), values[2].floatValue());
            this.lastAnchorX = this.panel.limb.origin[0] = values[0].floatValue();
            this.lastAnchorY = this.panel.limb.origin[1] = values[1].floatValue();
            this.lastAnchorZ = this.panel.limb.origin[2] = values[2].floatValue();
            this.panel.rebuildModel();
        });
        this.origin.context(() ->
        {
            ModelCustomRenderer renderer = this.panel.renderModel == null ? null : this.panel.renderModel.get(this.panel.limb.name);

            if (renderer != null && renderer.min != null && renderer.max != null)
            {
                return new GuiSimpleContextMenu(this.mc)
                    .action(Icons.FULLSCREEN, IKey.lang("blockbuster.gui.me.limbs.context.anchor_setup"), () -> this.setupAnchorPoint(renderer, false))
                    .action(Icons.DOWNLOAD, IKey.lang("blockbuster.gui.me.limbs.context.anchor_move"), () -> this.setupAnchorPoint(renderer, true));
            }

            return null;
        });
        this.slot = new GuiCirculateElement(mc, (b) ->
        {
            this.panel.limb.slot = ArmorSlot.values()[this.slot.getValue()];
            this.panel.dirty();
        });
        this.slot.tooltip(IKey.lang("blockbuster.gui.me.limbs.slot"));
        this.hold = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.me.limbs.holding"), false, (b) ->
        {
            this.panel.limb.hold = b.isToggled();
            this.panel.dirty();
        });

        for (ArmorSlot slot : ArmorSlot.values())
        {
            this.slot.addLabel(IKey.lang("blockbuster.gui.me.limbs.slots." + slot.name));
        }

        /* Second category */
        this.color = new GuiColorElement(mc, (eh) ->
        {
            Color color = this.color.picker.color;

            this.panel.limb.color[0] = color.r;
            this.panel.limb.color[1] = color.g;
            this.panel.limb.color[2] = color.b;
            this.panel.limb.opacity = color.a;
            this.panel.dirty();
        });
        this.color.picker.editAlpha();
        this.color.tooltip(IKey.lang("blockbuster.gui.me.limbs.color"));
        this.specular = new GuiColorElement(mc, (eh) ->
        {
            Color color = this.specular.picker.color;

            this.panel.limb.specular = color.getRGBAColor();
            this.panel.dirty();
        });
        this.specular.picker.editAlpha();
        this.specular.tooltip(IKey.lang("blockbuster.gui.me.limbs.specular"));
        this.mirror = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.me.limbs.mirror"), false, (b) ->
        {
            this.panel.limb.mirror = b.isToggled();
            this.panel.rebuildModel();
        });
        this.mirror.flex().h(20);
        this.lighting = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.me.limbs.lighting"), false, (b) ->
        {
            this.panel.limb.lighting = b.isToggled();
            this.panel.dirty();
        });
        this.shading = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.me.limbs.shading"), false, (b) ->
        {
            this.panel.limb.shading = b.isToggled();
            this.panel.dirty();
        });
        this.smooth = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.me.limbs.smooth"), false, (b) ->
        {
            this.panel.limb.smooth = b.isToggled();
            this.panel.dirty();
        });
        this.is3D = new GuiToggleElement(mc, IKey.str("3D"), false, (b) ->
        {
            this.panel.limb.is3D = b.isToggled();
            this.panel.dirty();
        });

        this.holding = new GuiCirculateElement(mc, (b) ->
        {
            this.panel.limb.holding = Holding.values()[this.holding.getValue()];
            this.panel.rebuildModel();
        });
        this.holding.tooltip(IKey.lang("blockbuster.gui.me.limbs.hold"));
        this.holding.addLabel(IKey.lang("blockbuster.gui.me.limbs.none"));
        this.holding.addLabel(IKey.lang("blockbuster.gui.me.limbs.right"));
        this.holding.addLabel(IKey.lang("blockbuster.gui.me.limbs.left"));

        this.swiping = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.me.limbs.swiping"), false, (b) ->
        {
            this.panel.limb.swiping = b.isToggled();
            this.panel.dirty();
        });
        this.lookX = new GuiToggleElement(mc, IKey.comp(IKey.lang("blockbuster.gui.me.limbs.looking"), IKey.str(" X")), false, (b) ->
        {
            this.panel.limb.lookX = b.isToggled();
            this.panel.dirty();
        });
        this.lookY = new GuiToggleElement(mc, IKey.comp(IKey.lang("blockbuster.gui.me.limbs.looking"), IKey.str(" Y")), false, (b) ->
        {
            this.panel.limb.lookY = b.isToggled();
            this.panel.dirty();
        });
        this.swinging = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.me.limbs.swinging"), false, (b) ->
        {
            this.panel.limb.swinging = b.isToggled();
            this.panel.dirty();
        });
        this.idle = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.me.limbs.idle"), false, (b) ->
        {
            this.panel.limb.idle = b.isToggled();
            this.panel.dirty();
        });
        this.invert = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.me.limbs.invert"), false, (b) ->
        {
            this.panel.limb.invert = b.isToggled();
            this.panel.dirty();
        });
        this.wheel = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.me.limbs.wheel"), false, (b) ->
        {
            this.panel.limb.wheel = b.isToggled();
            this.panel.dirty();
        });
        this.wing = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.me.limbs.wing"), false, (b) ->
        {
            this.panel.limb.wing = b.isToggled();
            this.panel.dirty();
        });
        this.roll = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.me.limbs.roll"), false, (b) ->
        {
            this.panel.limb.roll = b.isToggled();
            this.panel.dirty();
        });
        this.cape = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.me.limbs.cape"), false, (b) ->
        {
            this.panel.limb.cape = b.isToggled();
            this.panel.dirty();
        });

        this.vanillaPanel = Elements.column(mc, 5);
        this.vanillaPanel.add(Elements.label(IKey.lang("blockbuster.gui.me.limbs.size")).background(), this.size);
        this.vanillaPanel.add(Elements.label(IKey.lang("blockbuster.gui.me.limbs.size_offset")).background(), this.sizeOffset);
        this.vanillaPanel.add(Elements.label(IKey.lang("blockbuster.gui.me.limbs.texture")).background().marginTop(12), Elements.row(mc, 5, 0, 20, this.texture, this.mirror));

        this.objPanel = Elements.column(mc, 5);
        this.objPanel.add(Elements.label(IKey.lang("blockbuster.gui.me.limbs.origin")).background(), this.origin);

        GuiElement appearance = new GuiElement(mc);

        appearance.flex().grid(5).items(2).resizes(true);
        appearance.add(this.lighting, this.shading);
        appearance.add(this.smooth, this.is3D);

        this.colors = new GuiElement(mc);

        this.colors.flex().grid(5).items(1).resizes(true);
        this.colors.add(this.color);

        GuiElement animation = new GuiElement(mc);

        animation.flex().grid(5).items(2).resizes(true);
        animation.add(this.lookX, this.lookY);
        animation.add(this.idle, this.swinging);
        animation.add(this.invert, this.swiping);
        animation.add(this.hold, this.wheel);
        animation.add(this.wing, this.roll);
        animation.add(this.cape);

        this.scroll.add(Elements.label(IKey.lang("blockbuster.gui.me.limbs.anchor")).background().marginTop(12), this.anchor);
        this.scroll.add(Elements.row(mc, 5, this.slot, this.holding));
        this.scroll.add(Elements.label(IKey.lang("blockbuster.gui.me.limbs.item_scale")).background(), this.itemScale);
        this.scroll.add(Elements.label(IKey.lang("blockbuster.gui.me.limbs.appearance")).background().marginTop(12), appearance, this.colors);
        this.scroll.add(Elements.label(IKey.lang("blockbuster.gui.me.limbs.animation")).background().marginTop(12), animation);

        /* Buttons */
        this.addLimb = new GuiIconElement(mc, Icons.ADD, (b) -> this.addLimb());
        this.dupeLimb = new GuiIconElement(mc, Icons.DUPE, (b) -> this.dupeLimb());
        this.removeLimb = new GuiIconElement(mc, Icons.REMOVE, (b) -> this.removeLimb());
        this.renameLimb = new GuiIconElement(mc, Icons.EDIT, (b) -> this.renameLimb());
        this.parentLimb = new GuiIconElement(mc, Icons.LIMB, (b) -> this.parentLimb());

        GuiElement sidebar = Elements.row(mc, 0, 0, 20, this.addLimb, this.dupeLimb, this.parentLimb, this.renameLimb, this.removeLimb);

        sidebar.flex().relative(this).x(1F).h(20).anchorX(1F).row(0).resize();

        this.add(sidebar, this.limbs, this.scroll, this.textureEditor);
    }

    private void setupAnchorPoint(ModelCustomRenderer renderer, boolean move)
    {
        GuiAnchorModal modal = new GuiAnchorModal(this.mc, IKey.lang("blockbuster.gui.me.limbs.anchor_modal"), (anchor) -> this.doSetupAnchorPoint(renderer, anchor, move));

        this.panel.modelRenderer.anchorPreview = modal.preview;
        GuiModal.addFullModal(this, () -> modal);
    }

    private void doSetupAnchorPoint(ModelCustomRenderer renderer, Vector3f anchor, boolean move)
    {
        float[] origin = ModelEditorMath.anchorLerpOrigin(renderer.min, renderer.max, anchor);

        renderer.limb.origin[0] = origin[0];
        renderer.limb.origin[1] = origin[1];
        renderer.limb.origin[2] = origin[2];

        if (move)
        {
            float[] translate = this.panel.pose.limbs.get(renderer.limb.name).translate;
            float[] moved = ModelEditorMath.anchorMoveTranslate(renderer.limb.origin);

            translate[0] = moved[0];
            translate[1] = moved[1];
            translate[2] = moved[2];
        }

        this.panel.modelRenderer.anchorPreview = null;
        this.panel.setLimb(renderer.limb.name);
        this.panel.rebuildModel();
    }

    private void addLimb()
    {
        GuiModal.addFullModal(this, () ->
        {
            GuiPromptModal modal = new GuiPromptModal(this.mc, IKey.lang("blockbuster.gui.me.limbs.new_limb"), this::addLimb);

            return modal.setValue(this.panel.limb.name);
        });
    }

    private void addLimb(String text)
    {
        if (!this.panel.model.limbs.containsKey(text))
        {
            this.panel.model.addLimb(text);
            this.limbs.add(text);
            this.limbs.setCurrent(text);
            this.panel.rebuildModel();
            this.panel.setLimb(text);
        }
    }

    private void dupeLimb()
    {
        if (LimbEditorOps.canDupe(this.limbKind()) != LimbEditorOps.Guard.OK)
        {
            GuiModal.addFullModal(this, () -> new GuiMessageModal(this.mc, IKey.lang("blockbuster.gui.me.limbs.obj_limb")));
            return;
        }

        ModelLimb limb = this.panel.limb.clone();

        /* It must be unique name */
        limb.name = LimbEditorOps.uniqueDupeName(this.panel.model, limb.name);

        this.panel.model.addLimb(limb);
        this.limbs.add(limb.name);
        this.limbs.setCurrent(limb.name);
        this.panel.rebuildModel();
        this.panel.setLimb(limb.name);
    }

    private void removeLimb()
    {
        LimbEditorOps.Guard guard = LimbEditorOps.canRemove(this.limbKind(), this.panel.model, this.panel.limb);

        if (guard == LimbEditorOps.Guard.OBJ_LIMB)
        {
            GuiModal.addFullModal(this, () -> new GuiMessageModal(this.mc, IKey.lang("blockbuster.gui.me.limbs.obj_limb")));
        }
        else if (guard == LimbEditorOps.Guard.LAST_LIMB)
        {
            GuiModal.addFullModal(this, () -> new GuiMessageModal(this.mc, IKey.lang("blockbuster.gui.me.limbs.last_limb")));
        }
        else
        {
            this.panel.model.removeLimb(this.panel.limb);

            String newLimb = this.panel.model.limbs.keySet().iterator().next();

            this.fillData(this.panel.model);
            this.panel.rebuildModel();
            this.panel.setLimb(newLimb);
        }
    }

    private void renameLimb()
    {
        if (LimbEditorOps.canRename(this.limbKind()) != LimbEditorOps.Guard.OK)
        {
            GuiModal.addFullModal(this, () -> new GuiMessageModal(this.mc, IKey.lang("blockbuster.gui.me.limbs.obj_limb")));
        }
        else
        {
            GuiModal.addFullModal(this, () -> new GuiPromptModal(this.mc, IKey.lang("blockbuster.gui.me.limbs.rename_limb"), this::renameLimb).setValue(this.panel.limb.name));
        }
    }

    private void renameLimb(String text)
    {
        if (this.panel.model.renameLimb(this.panel.limb, text))
        {
            this.limbs.replace(text);
            this.panel.rebuildModel();
        }
    }

    private void parentLimb()
    {
        GuiModal.addFullModal(this, () ->
        {
            GuiToggleElement button = new GuiToggleElement(this.mc, IKey.lang("blockbuster.gui.me.limbs.parent_limb_transform_correction"), true, null);
            button.tooltip(IKey.lang("blockbuster.gui.me.limbs.parent_limb_transform_correction_tooltip"));

            GuiListModal modal = new GuiListModal(this.mc, IKey.lang("blockbuster.gui.me.limbs.parent_limb"),
                (text) -> this.parentLimb(text, button.isToggled()));

            button.flex().relative(modal.bar).x(10).w(1F, -20).y(-0.25F, -5);
            modal.add(button);

            return modal.addValues(this.panel.model.limbs.keySet()).setValue(this.panel.limb.parent);
        });
    }

    /**
     * Rewrite the current pose's transform for the selected limb so that
     * re-parenting it to {@code newParent} leaves it visually where it was.
     *
     * <p>Legacy math verbatim: the correction is
     * {@code newParentWorld⁻¹ · limbWorld}, or — when the new parent has no
     * renderer (re-parenting to a limb that produced none, or clearing the
     * parent) — {@code modelMatrix⁻¹ · limbModelView} with {@code 24/16}
     * subtracted from {@code m13}, mirroring the 24-pixel body offset
     * {@code ModelCustomRenderer.applyTransform} applies to root limbs. The
     * rotation sub-matrix is <b>transposed</b> before decomposition, translation
     * is scaled by 16 (blueprint units are 1/16 of a block), and Y/Z are negated
     * on both translation and rotation.</p>
     */
    private void correctTransformationParenting(String newParent)
    {
        ModelCustomRenderer currentLimbRenderer = null;
        ModelCustomRenderer newParentRenderer = null;

        for (ModelCustomRenderer limbRenderer : this.panel.renderModel.limbs)
        {
            if (limbRenderer.limb == this.panel.limb)
            {
                currentLimbRenderer = limbRenderer;
            }
            else if (limbRenderer.limb.name.equals(newParent))
            {
                newParentRenderer = limbRenderer;
            }
        }

        if (currentLimbRenderer == null) return;

        ModelTransform transform = currentLimbRenderer.model.pose.limbs.get(this.panel.limb.name);

        if (transform == null)
        {
            return;
        }

        ModelEditorMath.ParentCorrection correction = ModelEditorMath.computeParentCorrection(
            currentLimbRenderer.getWorldTransformation(),
            newParentRenderer == null ? null : newParentRenderer.getWorldTransformation(),
            this.panel.modelRenderer.getModelMatrix(),
            currentLimbRenderer.getModelView());

        transform.translate = correction.translate;
        transform.rotate = correction.rotate;
        transform.scale = correction.scale;
    }

    private void parentLimb(String text, boolean correctTransformation)
    {
        if (!this.panel.limb.name.equals(text))
        {
            this.panel.limb.parent = text;

            if (correctTransformation) this.correctTransformationParenting(text);

            this.panel.rebuildModel();
        }
    }

    private void setLimb(String str)
    {
        this.panel.setLimb(str);
        this.fillLimbData(this.panel.limb);
    }

    public void setCurrent(String str)
    {
        this.limbs.setCurrent(str);
        this.fillLimbData(this.panel.limb);
    }

    public void fillData(Model model)
    {
        this.limbs.clear();
        this.limbs.add(model.limbs.keySet());
        this.limbs.sort();

        this.textureEditor.setVisible(false);
    }

    public void fillLimbData(ModelLimb limb)
    {
        this.textureEditor.x.setValue(this.panel.limb.texture[0]);
        this.textureEditor.y.setValue(this.panel.limb.texture[1]);

        this.size.setValues(limb.size[0], limb.size[1], limb.size[2]);
        this.sizeOffset.setValue(limb.sizeOffset);
        this.itemScale.setValue(limb.itemScale);
        this.anchor.setValues(limb.anchor[0], limb.anchor[1], limb.anchor[2]);
        this.origin.setValues(limb.origin[0], limb.origin[1], limb.origin[2]);
        this.color.picker.setColor(limb.color[0], limb.color[1], limb.color[2], limb.opacity);
        this.mirror.toggled(limb.mirror);
        this.lighting.toggled(limb.lighting);
        this.shading.toggled(limb.shading);
        this.smooth.toggled(limb.smooth);
        this.is3D.toggled(limb.is3D);

        this.holding.setValue(limb.holding.ordinal());
        this.slot.setValue(limb.slot.ordinal());
        this.hold.toggled(limb.hold);
        this.swiping.toggled(limb.swiping);
        this.lookX.toggled(limb.lookX);
        this.lookY.toggled(limb.lookY);
        this.swinging.toggled(limb.swinging);
        this.idle.toggled(limb.idle);
        this.invert.toggled(limb.invert);
        this.wheel.toggled(limb.wheel);
        this.wing.toggled(limb.wing);
        this.roll.toggled(limb.roll);
        this.cape.toggled(limb.cape);

        boolean isObj = this.getLimbClass(limb) != ModelCustomRenderer.class;
        boolean isVOX = this.getLimbClass(limb) == ModelVoxRenderer.class;
        this.vanillaPanel.removeFromParent();
        this.objPanel.removeFromParent();
        this.is3D.setVisible(!isObj);

        if (isObj)
        {
            this.lastAnchorX = limb.origin[0];
            this.lastAnchorY = limb.origin[1];
            this.lastAnchorZ = limb.origin[2];
            this.scroll.prepend(this.objPanel);
        }
        else
        {
            this.lastAnchorX = limb.anchor[0];
            this.lastAnchorY = limb.anchor[1];
            this.lastAnchorZ = limb.anchor[2];
            this.scroll.prepend(this.vanillaPanel);
        }

        if (isVOX)
        {
            if (!this.specular.hasParent())
            {
                this.colors.flex().grid(5).items(2).resizes(true);
                this.colors.add(this.specular);
            }

            this.specular.picker.setColor(limb.specular);
        }
        else
        {
            this.specular.removeFromParent();
            this.colors.flex().grid(5).items(1).resizes(true);
        }

        this.scroll.resize();
    }

    /**
     * Compensate the limb's pose translation for a change of its anchor (box
     * limbs) or origin (OBJ/VOX limbs) so the limb does not visually jump. See
     * the class javadoc for the two sign conventions.
     */
    private void fixLimbPosition(float x, float y, float z)
    {
        ModelEditorMath.fixLimbPosition(this.panel.transform, this.panel.limb.size,
            this.panel.model.legacyObj, this.limbKind(),
            this.lastAnchorX, this.lastAnchorY, this.lastAnchorZ, x, y, z);
    }

    /** The selected limb's kind — the single {@code getLimbClass} projection. */
    private ModelEditorMath.LimbKind limbKind()
    {
        return limbKind(this.getLimbClass(this.panel.limb));
    }

    /**
     * Map a compiled limb's renderer class onto the GL-free
     * {@link ModelEditorMath.LimbKind} the math core and {@link LimbEditorOps}
     * switch on.
     *
     * <p>S22/P243 correction: a limb with no renderer (a failed compile) is
     * {@link ModelEditorMath.LimbKind#NONE}, <b>not</b> {@code VANILLA}. Every
     * legacy consumer spelled the test {@code clazz != ModelCustomRenderer.class},
     * which a {@code null} class satisfies — so legacy sent a rendererless limb
     * down the non-box branch and refused to dupe/rename/remove it. Mapping
     * {@code null} to {@code VANILLA} (as this method previously did, with a
     * comment asserting the opposite) inverted both.</p>
     */
    static ModelEditorMath.LimbKind limbKind(Class<? extends ModelCustomRenderer> clazz)
    {
        if (clazz == ModelVoxRenderer.class)
        {
            return ModelEditorMath.LimbKind.VOX;
        }

        if (clazz == ModelOBJRenderer.class)
        {
            return ModelEditorMath.LimbKind.OBJ;
        }

        return clazz == ModelCustomRenderer.class ? ModelEditorMath.LimbKind.VANILLA : ModelEditorMath.LimbKind.NONE;
    }

    /**
     * The renderer class the compiled model produced for this limb, or
     * {@code null} when the limb has no renderer. Legacy indexed
     * {@code panel.renderModel.limbs} directly; the extra null guard covers a
     * failed compile ({@code ModelParser} is total and can answer {@code null}),
     * which legacy would have NPE'd on.
     */
    private Class<? extends ModelCustomRenderer> getLimbClass(ModelLimb limb)
    {
        if (this.panel.renderModel == null || this.panel.renderModel.limbs == null)
        {
            return null;
        }

        for (ModelCustomRenderer limbRenderer : this.panel.renderModel.limbs)
        {
            if (limbRenderer.limb.name.equals(limb.name))
            {
                return limbRenderer.getClass();
            }
        }

        return null;
    }
}
