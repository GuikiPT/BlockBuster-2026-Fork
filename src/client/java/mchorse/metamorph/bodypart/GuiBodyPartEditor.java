package mchorse.metamorph.bodypart;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.google.common.collect.ImmutableList;

import javax.vecmath.Matrix4f;
import javax.vecmath.Vector3f;

import mchorse.mclib.client.gui.framework.GuiBase;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiIconElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiSlotElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiToggleElement;
import mchorse.mclib.client.gui.framework.elements.context.GuiContextMenu;
import mchorse.mclib.client.gui.framework.elements.context.GuiSimpleContextMenu;
import mchorse.mclib.client.gui.framework.elements.input.GuiTransformations;
import mchorse.mclib.client.gui.framework.elements.list.GuiStringListElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.GuiUtils;
import mchorse.mclib.client.gui.utils.Icons;
import mchorse.mclib.client.gui.utils.LegacyKeyCodes;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.utils.Direction;
import mchorse.mclib.utils.MathUtils;
import mchorse.mclib.utils.MatrixUtils;
import mchorse.mclib.utils.MatrixUtils.RotationOrder;
import mchorse.mclib.utils.MatrixUtils.Transformation;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.utils.IAnimationProvider;
import mchorse.metamorph.api.MorphUtils;
import mchorse.metamorph.client.MorphRenderUtils;
import mchorse.metamorph.client.render.MorphRenderContext;
import mchorse.metamorph.client.gui.creative.GuiCreativeMorphsList.OnionSkin;
import mchorse.metamorph.client.gui.creative.GuiNestedEdit;
import mchorse.metamorph.client.gui.editor.GuiAbstractMorph;
import mchorse.metamorph.client.gui.editor.GuiMorphPanel;
import mchorse.metamorph.client.gui.editor.IMorphEditorHost;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.util.math.RotationAxis;

/**
 * Body part editor GUI (port of Metamorph 1.4's {@code GuiBodyPartEditor},
 * roadmap P59.1). Verbatim port of the 753-line legacy class: the part list
 * with add/dupe/remove + a static session-scoped copy/paste buffer, per-part
 * NBT copy/paste context menu, enabled/animate/useTarget toggles, six item
 * slots, the T/R/S {@link GuiBodyPartTransformations} widget, the limb list
 * with alt-click reassignment (world-transform-preserving), Up/Down cycle
 * keys, the {@code IAnimationProvider}-gated animate toggle, and the
 * {@link TransformedOnionSkinMorph} gray onion-skin wrapper.
 *
 * <p>Port boundaries (nothing here invents the missing side):</p>
 * <ul>
 *   <li><b>P54</b> — the BodyPart client render surface has landed as
 *       {@link BodyPartRenderer}, so the matrix captures this editor depends on
 *       are live: the onion skin ({@link #generateOnionSkin}) and alt-click
 *       limb reassignment ({@link #pickLimb}) both record real limb matrices and
 *       feed the pure helpers {@link #composeWorld},
 *       {@link #reassignLimbTransform} and {@link #composeOnionSkin}, and
 *       {@link TransformedOnionSkinMorph#render} draws the composed ghost into
 *       the {@code MorphRenderContext} frame the onion-skin pass opens.
 *       {@code BodyPart.addTranslation} has landed too, so the LOCAL-orientation
 *       trackpad drag ({@link GuiBodyPartTransformations#localTranslate}) moves
 *       the part along its own rotated axes. The mclib
 *       {@code ITransformationObject} interface is deliberately <b>not</b>
 *       ported: its one method names a client-only type, so the main-side
 *       {@code BodyPart} takes a {@code boolean local} instead — the same
 *       convention as {@code ModelTransform.addTranslation}.</li>
 *   <li><b>P58</b> — nested morph editing goes through
 *       {@link mchorse.metamorph.client.gui.editor.IMorphEditorHost}, which
 *       carries {@code nestEdit} and the two onion-skin lists. It did not until
 *       V-N: {@link #pickMorph}'s callback recovered them with a hard
 *       {@code (GuiCreativeMorphsList) this.editor.morphs} cast, a latent
 *       {@code ClassCastException} for any host that is not the creative
 *       picker. The interface was widened to the nested-edit surface legacy's
 *       concrete {@code GuiCreativeMorphsList} field provided, and the cast is
 *       gone — see {@code IMorphEditorHost}'s class javadoc.</li>
 * </ul>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/bodypart/GuiBodyPartEditor.java
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class GuiBodyPartEditor extends GuiMorphPanel<AbstractMorph, GuiAbstractMorph>
{
    public static List<BodyPart> buffer = new ArrayList<BodyPart>();

    protected GuiBodyPartListElement bodyParts;
    protected GuiNestedEdit pickMorph;
    protected GuiToggleElement useTarget;
    protected GuiToggleElement enabled;
    protected GuiToggleElement animate;

    protected GuiIconElement add;
    protected GuiIconElement dupe;
    protected GuiIconElement remove;
    protected GuiIconElement copy;
    protected GuiIconElement paste;

    protected GuiBodyPartTransformations transformations;

    protected GuiStringListElement limbs;
    protected GuiElement elements;
    protected GuiElement bottomEditor;

    protected BodyPartManager parts;
    protected BodyPart part;

    protected GuiElement stacks;
    protected GuiSlotElement[] slots = new GuiSlotElement[6];

    public GuiBodyPartEditor(MinecraftClient mc, GuiAbstractMorph editor)
    {
        super(mc, editor);

        this.limbs = new GuiStringListElement(mc, (str) -> this.pickLimb(str.get(0)));
        this.limbs.background();

        this.bodyParts = new GuiBodyPartListElement(mc, (part) -> this.setPart(part.isEmpty() ? null : part.get(0)));
        this.bodyParts.background().sorting();
        this.bodyParts.context(this::bodyPartContextMenu);

        this.pickMorph = new GuiNestedEdit(mc, (editing) ->
        {
            BodyPart part = this.part;

            if (part == null)
            {
                return;
            }

            /* The host is reached through IMorphEditorHost, which now carries
             * the nested-edit surface itself (P58). This used to be a hard
             * (GuiCreativeMorphsList) cast — a latent ClassCastException for any
             * host that is not the creative picker. Null only headlessly, or
             * before the host has attached itself. */
            IMorphEditorHost morphs = this.editor == null ? null : this.editor.morphs;

            if (morphs == null)
            {
                return;
            }

            TransformedOnionSkinMorph skin = this.generateOnionSkin(part);

            morphs.nestEdit(part.morph.get(), editing, (morph) ->
            {
                AbstractMorph copy = MorphUtils.copy(morph);

                part.morph.setDirect(copy);
                this.applyUseTarget(part, copy);
            });

            /* Legacy wrapped the composed skin in a half-alpha gray OnionSkin;
             * a null skin (no render frame yet, or a degenerate matrix) simply
             * leaves the previous onion skins alone. */
            if (skin != null)
            {
                morphs.setLastOnionSkins(ImmutableList.<OnionSkin>of(new OnionSkin().morph(skin).color(0.5F, 0.5F, 0.5F, 0.5F)));
            }
        });

        this.add = new GuiIconElement(mc, Icons.ADD, this::addPart);
        this.add.tooltip(IKey.lang("metamorph.gui.body_parts.add_tooltip"));
        this.add.flex().w(20);
        this.dupe = new GuiIconElement(mc, Icons.DUPE, this::dupePart);
        this.dupe.tooltip(IKey.lang("metamorph.gui.body_parts.dupe_tooltip"));
        this.remove = new GuiIconElement(mc, Icons.REMOVE, this::removePart);
        this.remove.tooltip(IKey.lang("metamorph.gui.body_parts.remove_tooltip"));
        this.copy = new GuiIconElement(mc, Icons.COPY, this::copyParts);
        this.copy.tooltip(IKey.lang("metamorph.gui.body_parts.copy_tooltip"));
        this.paste = new GuiIconElement(mc, Icons.PASTE, this::pasteParts);
        this.paste.tooltip(IKey.lang("metamorph.gui.body_parts.paste_tooltip"));
        this.paste.flex().w(20);

        this.useTarget = new GuiToggleElement(mc, IKey.lang("metamorph.gui.body_parts.use_target"), false, this::toggleTarget);
        this.enabled = new GuiToggleElement(mc, IKey.lang("metamorph.gui.body_parts.enabled"), false, this::toggleEnabled);
        this.animate = new GuiToggleElement(mc, IKey.lang("metamorph.gui.body_parts.animate"), false, this::toggleAnimate);
        this.animate.tooltip(IKey.lang("metamorph.gui.body_parts.animate_tooltip"), Direction.LEFT);
        this.transformations = new GuiBodyPartTransformations(mc);

        int width = 110;

        GuiElement sidebar = new GuiElement(mc);

        sidebar.flex().relative(this).x(10).y(1, -30).wh(width, 20).row(0).height(20);
        sidebar.add(this.add, this.dupe, this.remove, this.copy, this.paste);

        this.bottomEditor = new GuiElement(mc);
        this.bottomEditor.flex().relative(this).x(1, -115).y(1, -10).w(width).anchorY(1F);
        this.bottomEditor.flex().column(5).vertical().stretch();
        this.bottomEditor.add(this.enabled, this.animate, this.useTarget);

        this.transformations.flex().relative(this.area).x(0.5F, -128).y(1, -10).wh(256, 70).anchorY(1F);
        this.limbs.flex().relative(this).set(0, 50, width, 90).x(1, -115).hTo(this.bottomEditor.area, -5);
        this.pickMorph.flex().relative(this).set(0, 10, width, 20).x(1, -115);
        this.bodyParts.flex().relative(this).set(10, 22, width, 0).hTo(this.transformations.flex(), 1F, -20);

        this.elements = new GuiElement(mc).noCulling();
        this.elements.add(this.bottomEditor, this.limbs, this.pickMorph, this.transformations);
        this.add(sidebar, this.bodyParts, this.elements);

        /* Inventory */
        this.stacks = new GuiElement(mc);
        this.stacks.flex().relative(this).x(0.5F).y(10).anchor(0.5F, 0).row(5).resize();

        for (int i = 0; i < this.slots.length; i++)
        {
            int slot = i;

            this.slots[i] = new GuiSlotElement(mc, i, (stack) -> this.pickItem(stack, slot));
            this.stacks.add(this.slots[i]);
        }

        this.elements.add(this.stacks);

        this.bodyParts.keys().register(IKey.lang("metamorph.gui.body_parts.keys.select_prev"), LegacyKeyCodes.KEY_UP, () -> this.moveIndex(-1)).category(GuiAbstractMorph.KEY_CATEGORY);
        this.bodyParts.keys().register(IKey.lang("metamorph.gui.body_parts.keys.select_next"), LegacyKeyCodes.KEY_DOWN, () -> this.moveIndex(1)).category(GuiAbstractMorph.KEY_CATEGORY);
    }

    /**
     * @param part
     * @return true if the provided instance reference matches the current selected bodypart
     */
    public boolean isSelected(BodyPart part)
    {
        return part == this.part;
    }

    protected void applyUseTarget(BodyPart part, AbstractMorph copy)
    {
        if (copy == null)
        {
            return;
        }

        if (copy.useTargetDefault())
        {
            part.useTarget = true;
            this.useTarget.toggled(part.useTarget);
        }
    }

    private GuiContextMenu bodyPartContextMenu()
    {
        GuiSimpleContextMenu menu = new GuiSimpleContextMenu(mc);
        String text = GuiUtils.getClipboardString();
        BodyPart part = null;

        try
        {
            NbtCompound tag = StringNbtReader.parse(text);

            part = new BodyPart();
            part.fromNBT(tag);
        }
        catch (Exception e)
        {}

        if (!this.bodyParts.isDeselected())
        {
            menu.action(Icons.COPY, IKey.lang("metamorph.gui.body_parts.context.copy"), () ->
            {
                NbtCompound tag = new NbtCompound();

                this.bodyParts.getCurrentFirst().toNBT(tag);
                GuiUtils.setClipboardString(tag.toString());
            });
        }

        if (part != null)
        {
            final BodyPart destination = part;

            menu.action(Icons.PASTE, IKey.lang("metamorph.gui.body_parts.context.paste"), () -> this.addPart(destination.copy()));
        }

        return menu.actions.getList().isEmpty() ? null : menu;
    }

    /**
     * Compose {@code base ∘ T(part)} where {@code T} is the part's local
     * transform ({@code translate}, then Z/Y/X rotation in degrees, then
     * non-uniform scale) — the exact accumulation legacy performs on a recorded
     * limb matrix before extracting transformations. Operates on a copy so the
     * caller's {@code base} is untouched. Package-private + static for headless
     * numeric verification.
     */
    static Matrix4f composeWorld(Matrix4f base, BodyPart part)
    {
        Matrix4f last = new Matrix4f(base);
        Matrix4f transform = new Matrix4f();

        transform.setIdentity();
        transform.setTranslation(part.translate);
        last.mul(transform);
        transform.rotZ((float) Math.toRadians(part.rotate.z));
        last.mul(transform);
        transform.rotY((float) Math.toRadians(part.rotate.y));
        last.mul(transform);
        transform.rotX((float) Math.toRadians(part.rotate.x));
        last.mul(transform);
        transform.setIdentity();
        transform.m00 = part.scale.x;
        transform.m11 = part.scale.y;
        transform.m22 = part.scale.z;
        last.mul(transform);

        return last;
    }

    /**
     * Port of the alt-click limb-reassignment math (legacy {@code pickLimb}).
     * Given the part's world matrix recorded on its old limb ({@code oldLimb})
     * and on its new limb ({@code newLimb}), rewrite {@code part}'s
     * translate/rotate/scale so the part visually stays put. Returns whether the
     * transform was applied (false when extraction failed, matching legacy which
     * leaves the part untouched). Package-private + static for headless tests.
     */
    static boolean reassignLimbTransform(Matrix4f oldLimb, Matrix4f newLimb, BodyPart part)
    {
        Matrix4f last = composeWorld(oldLimb, part);
        Transformation extract = MatrixUtils.extractTransformations(newLimb, last);

        if (extract.getCreationException() == null)
        {
            Vector3f rotate = extract.getRotation(RotationOrder.XYZ);

            if (rotate != null)
            {
                part.translate.set(extract.getTranslation3f());
                part.rotate.set(rotate);
                part.scale.set(extract.getScale());

                return true;
            }
        }

        return false;
    }

    /**
     * Port of the gray onion-skin composition (legacy {@code generateOnionSkin}
     * inner math). Given the recorded limb matrix {@code last} and the parent
     * morph copy to wrap, build a {@link TransformedOnionSkinMorph} whose
     * translate/rotate/scale reproduce {@code last ∘ T(part)} as extracted about
     * identity — or null when extraction fails. Package-private + static for
     * headless numeric verification.
     */
    static TransformedOnionSkinMorph composeOnionSkin(Matrix4f last, AbstractMorph parentCopy, BodyPart part)
    {
        Matrix4f world = composeWorld(last, part);
        Matrix4f identity = new Matrix4f();

        identity.setIdentity();

        Transformation extract = MatrixUtils.extractTransformations(world, identity);

        if (extract.getCreationException() == null)
        {
            Vector3f rotate = extract.getRotation(RotationOrder.XYZ);

            if (rotate != null)
            {
                TransformedOnionSkinMorph morph = new TransformedOnionSkinMorph();

                Vector3f vec = extract.getTranslation3f();
                morph.translate[0] = vec.x;
                morph.translate[1] = vec.y;
                morph.translate[2] = vec.z;

                morph.rotate[0] = rotate.x;
                morph.rotate[1] = rotate.y;
                morph.rotate[2] = rotate.z;

                vec = extract.getScale();
                morph.scale[0] = vec.x;
                morph.scale[1] = vec.y;
                morph.scale[2] = vec.z;

                morph.morph = parentCopy;

                return morph;
            }
        }

        return null;
    }

    /**
     * The gray half-alpha ghost of the whole parent morph, shown behind the
     * nested editor while you edit one part's sub-morph, positioned so the part
     * you are editing sits where it will actually be.
     *
     * <p>It works by asking the render pipeline where the limb <i>is</i>:
     * {@link BodyPartRenderer#recordMatrix} runs one throwaway render that makes
     * every body part stamp its own model-view matrix, then
     * {@link #composeOnionSkin} folds this part's transform onto that matrix and
     * extracts translate/rotate/scale from the result.</p>
     *
     * <p>Two legacy details that look incidental and are not: the viewport
     * entity's pitch and both yaw pairs are <b>zeroed first</b>, so the recorded
     * matrix is in a canonical orientation rather than whatever the user has the
     * preview rotated to; and the parent copy is taken with
     * {@code part.enabled = false}, so the ghost does not contain a second copy
     * of the very part being edited. The copy's animation is frozen
     * ({@code animates = false}) so the ghost does not drift while the editor is
     * open.</p>
     */
    protected TransformedOnionSkinMorph generateOnionSkin(BodyPart part)
    {
        LivingEntity entity = this.editor == null || this.editor.renderer == null ? null : this.editor.renderer.getEntity();

        if (entity == null || this.morph == null)
        {
            return null;
        }

        entity.prevPitch = 0;
        entity.setPitch(0);
        entity.prevHeadYaw = entity.headYaw = 0;
        entity.prevBodyYaw = entity.bodyYaw = 0;

        part.lastMatrix = null;
        BodyPartRenderer.recordMatrix(this.morph, entity, 0F);

        Matrix4f last = part.lastMatrix;

        if (last == null)
        {
            return null;
        }

        boolean enabled = part.enabled;

        part.enabled = false;

        AbstractMorph copy;

        try
        {
            copy = this.morph.copy();
        }
        finally
        {
            part.enabled = enabled;
        }

        if (copy instanceof IAnimationProvider)
        {
            ((IAnimationProvider) copy).getAnimation().animates = false;
        }

        return composeOnionSkin(last, copy, part);
    }

    protected void addPart(GuiIconElement b)
    {
        BodyPart part = new BodyPart();

        this.setupNewBodyPart(part);
        this.addPart(part);
    }

    protected void addPart(BodyPart part)
    {
        /* Gives the part its dummy host entity — a part added mid-session is
         * past the manager's initiated latch and would otherwise never get
         * one, so the !useTarget path would draw nothing. */
        BodyPartRenderer.init(part);

        this.parts.parts.add(part);
        this.setPart(part);

        this.bodyParts.setCurrentDirect(part);
        this.bodyParts.update();
    }

    protected void setupNewBodyPart(BodyPart part)
    {}

    protected void dupePart(GuiIconElement b)
    {
        if (this.bodyParts.isDeselected())
        {
            return;
        }

        BodyPart part = this.bodyParts.getCurrentFirst().copy();

        BodyPartRenderer.init(part);

        this.parts.parts.add(part);
        this.setPart(part);

        this.bodyParts.setCurrentDirect(part);
        this.bodyParts.update();
    }

    protected void removePart(GuiIconElement b)
    {
        if (this.bodyParts.isDeselected())
        {
            return;
        }

        List<BodyPart> parts = this.parts.parts;
        int index = -1;

        for (int i = 0; i < parts.size(); i ++)
        {
            if (parts.get(i) == this.part)
            {
                index = i;

                break;
            }
        }

        if (index != -1)
        {
            parts.remove(this.part);
            this.bodyParts.update();
            index--;

            if (parts.size() >= 1)
            {
                this.setPart(parts.get(MathUtils.clamp(index, 0, parts.size() - 1)));
            }
            else
            {
                this.setPart(null);
            }
        }

        this.bodyParts.update();
    }

    protected void copyParts(GuiIconElement b)
    {
        buffer.clear();

        for (BodyPart part : this.parts.parts)
        {
            buffer.add(part.copy());
        }
    }

    protected void pasteParts(GuiIconElement b)
    {
        for (BodyPart part : buffer)
        {
            BodyPart clone = part.copy();

            this.parts.parts.add(clone);

            BodyPartRenderer.init(clone);
        }

        if (!this.parts.parts.isEmpty())
        {
            this.setPart(this.parts.parts.get(this.parts.parts.size() - 1));
        }

        this.bodyParts.update();
    }

    protected void toggleTarget(GuiToggleElement b)
    {
        if (this.part != null)
        {
            this.part.useTarget = b.isToggled();
        }
    }

    protected void toggleEnabled(GuiToggleElement b)
    {
        if (this.part != null)
        {
            this.part.enabled = b.isToggled();
        }
    }

    protected void toggleAnimate(GuiToggleElement b)
    {
        if (this.part != null)
        {
            this.part.animate = b.isToggled();
        }
    }

    protected void pickItem(ItemStack stack, int slot)
    {
        if (this.part == null)
        {
            return;
        }

        this.part.slots[slot] = stack;

        /* Push the changed slot onto the dummy render entity, so the sub-morph's
         * held-item / armor layers pick it up on the next frame. */
        this.part.updateEntity();
    }

    @Override
    public void fillData(AbstractMorph morph)
    {
        super.fillData(morph);

        if (morph instanceof IBodyPartProvider)
        {
            BodyPartManager manager = ((IBodyPartProvider) morph).getBodyPart();

            this.parts = manager;

            this.bodyParts.setList(manager.parts);
            this.bodyParts.update();
        }
    }

    @Override
    public void startEditing()
    {
        super.startEditing();

        if (this.parts != null)
        {
            this.setPart(this.parts.parts.isEmpty() ? null : this.parts.parts.get(0));
        }
    }

    public void setLimbs(Collection<String> limbs)
    {
        this.limbs.clear();
        this.limbs.add(limbs);
        this.limbs.sort();
    }

    protected void setPart(BodyPart part)
    {
        this.part = part;
        this.elements.setVisible(part != null);

        if (this.part != null)
        {
            this.fillBodyPart(part);
            this.limbs.setCurrent(part.limb);
            this.bodyParts.setCurrentDirect(part);
            this.pickMorph.setMorph(part.morph.get());
        }
    }

    protected void pickLimb(String str)
    {
        BodyPart part = this.part;
        boolean convert = GuiUtils.isAltKeyDown();

        if (part.limb.equals(str))
        {
            return;
        }

        if (part.limb.isEmpty() || !convert)
        {
            part.limb = str;
            return;
        }

        /* Alt-click limb reassignment, preserving the world transform: record
         * the part's matrix on the old limb, reassign, record it again on the
         * new limb, then rewrite translate/rotate/scale so the part visually
         * stays exactly where it was. Both captures go through the same
         * identity-based recording pass, which is what makes the two matrices
         * comparable. A capture that fails (no render frame, no viewport entity)
         * degrades to the plain reassignment rather than moving the part. */
        LivingEntity entity = this.editor == null || this.editor.renderer == null ? null : this.editor.renderer.getEntity();
        float partialTicks = GuiBase.getCurrent() == null ? 0F : GuiBase.getCurrent().partialTicks;

        if (entity == null || this.morph == null)
        {
            part.limb = str;
            return;
        }

        part.lastMatrix = null;
        BodyPartRenderer.recordMatrix(this.morph, entity, partialTicks);

        Matrix4f old = part.lastMatrix;

        part.limb = str;

        if (old == null)
        {
            return;
        }

        part.lastMatrix = null;
        BodyPartRenderer.recordMatrix(this.morph, entity, partialTicks);

        Matrix4f current = part.lastMatrix;

        if (current == null)
        {
            return;
        }

        reassignLimbTransform(old, current, part);
        this.fillBodyPart(part);
    }

    public void fillBodyPart(BodyPart part)
    {
        if (part != null)
        {
            this.bottomEditor.removeAll();

            if (this.morph instanceof IAnimationProvider)
            {
                this.bottomEditor.add(this.enabled, this.animate, this.useTarget);
            }
            else
            {
                this.bottomEditor.add(this.enabled, this.useTarget);
            }

            this.elements.resize();
            this.transformations.setBodyPart(part);

            this.enabled.toggled(part.enabled);
            this.useTarget.toggled(part.useTarget);
            this.animate.toggled(part.animate);

            for (int i = 0; i < this.slots.length; i++)
            {
                this.slots[i].setStack(part.slots[i]);
            }
        }
    }

    private void moveIndex(int index)
    {
        if (index != 0)
        {
            index = MathUtils.cycler(this.bodyParts.getIndex() + index, 0, this.bodyParts.getList().size() - 1);

            this.bodyParts.setIndex(index);
            this.fillBodyPart(this.bodyParts.getCurrentFirst());
        }
    }

    @Override
    public void draw(GuiContext context)
    {
        GuiDraw.drawStringWithShadow(this.font, I18n.translate("metamorph.gui.body_parts.parts"), this.bodyParts.area.x, this.bodyParts.area.y - 12, 0xffffff);

        if (this.elements.isVisible())
        {
            GuiDraw.drawStringWithShadow(this.font, I18n.translate("metamorph.gui.body_parts.limbs"), this.limbs.area.x, this.limbs.area.y - 12, 0xffffff);
        }

        super.draw(context);
    }

    @Override
    public void fromNBT(NbtCompound tag)
    {
        super.fromNBT(tag);

        this.bodyParts.setIndex(tag.getInt("Index"));

        BodyPart part = this.bodyParts.getCurrentFirst();

        if (part != null)
        {
            this.setPart(part);
        }
    }

    @Override
    public NbtCompound toNBT()
    {
        NbtCompound tag = super.toNBT();

        tag.putInt("Index", this.bodyParts.getIndex());

        return tag;
    }

    public static class GuiBodyPartTransformations extends GuiTransformations
    {
        public BodyPart part;

        public GuiBodyPartTransformations(MinecraftClient mc)
        {
            super(mc);
        }

        /**
         * The relative X/Y/Z trackpads mounted under the <b>LOCAL</b> orientation
         * (see {@link GuiTransformations#updateFields()} — GLOBAL mounts the
         * absolute {@code tx/ty/tz}, which go through {@code setT}).
         *
         * <p>Legacy passed the orientation enum straight into
         * {@code BodyPart.addTranslation}; the port maps it to the boolean the
         * main-side {@link BodyPart#addTranslation} takes, for the source-set
         * reason spelled out there.</p>
         */
        @Override
        public void localTranslate(double x, double y, double z)
        {
            this.part.addTranslation(x, y, z, GuiStaticTransformOrientation.getOrientation() == TransformOrientation.LOCAL);

            this.fillT(this.part.translate.x, this.part.translate.y, this.part.translate.z);
        }

        public void setBodyPart(BodyPart part)
        {
            this.part = part;

            if (part != null)
            {
                this.fillT(part.translate.x, part.translate.y, part.translate.z);
                this.fillS(part.scale.x, part.scale.y, part.scale.z);
                this.fillR(part.rotate.x, part.rotate.y, part.rotate.z);
            }
        }

        @Override
        public void setT(double x, double y, double z)
        {
            this.part.translate.x = (float) x;
            this.part.translate.y = (float) y;
            this.part.translate.z = (float) z;
        }

        @Override
        public void setS(double x, double y, double z)
        {
            this.part.scale.x = (float) x;
            this.part.scale.y = (float) y;
            this.part.scale.z = (float) z;
        }

        @Override
        public void setR(double x, double y, double z)
        {
            this.part.rotate.x = (float) x;
            this.part.rotate.y = (float) y;
            this.part.rotate.z = (float) z;
        }
    }

    public static class TransformedOnionSkinMorph extends AbstractMorph
    {
        public float[] translate = new float[3];
        public float[] rotate = new float[3];
        public float[] scale = new float[3];
        public AbstractMorph morph = null;

        @Override
        public void renderOnScreen(PlayerEntity player, int x, int y, float scale, float alpha)
        {}

        /**
         * Draw the wrapped parent morph under this wrapper's transform.
         *
         * <p>Legacy pushed the fixed-function {@code GL_MODELVIEW} and applied
         * <b>translate → rotZ → rotY → rotX → scale</b> before
         * {@code MorphUtils.renderDirect}. In 1.20.4 the ambient matrix is the
         * {@link MorphRenderContext} frame's {@link MatrixStack} — the same
         * substitution {@link BodyPartRenderer#render} makes — and the frame is
         * opened by the caller ({@code GuiMorphRenderer.renderInFrame}, via the
         * creative list's onion-skin pass). No frame means no draw, rather than
         * a draw into nothing.</p>
         *
         * <p>{@code renderDirect}, not {@code render}: the ghost pass runs with
         * {@code GuiModelRenderer}'s rendering flag off, so the shadow-pass
         * filter would apply to a preview that is not a world pass at all.</p>
         */
        @Override
        public void render(LivingEntity entity, double x, double y, double z, float entityYaw, float partialTicks)
        {
            if (this.morph == null)
            {
                return;
            }

            MorphRenderContext context = MorphRenderContext.current();

            if (context == null || context.matrices == null)
            {
                return;
            }

            MatrixStack matrices = context.matrices;

            matrices.push();

            try
            {
                matrices.translate(this.translate[0], this.translate[1], this.translate[2]);

                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(this.rotate[2]));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(this.rotate[1]));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(this.rotate[0]));

                matrices.scale(this.scale[0], this.scale[1], this.scale[2]);

                MorphRenderUtils.renderDirect(this.morph, entity, x, y, z, entityYaw, partialTicks);
            }
            finally
            {
                matrices.pop();
            }
        }

        @Override
        public AbstractMorph create()
        {
            return null;
        }

        @Override
        public float getWidth(LivingEntity target)
        {
            return 0;
        }

        @Override
        public float getHeight(LivingEntity target)
        {
            return 0;
        }
    }
}
