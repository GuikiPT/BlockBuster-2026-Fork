package mchorse.metamorph.bodypart;

import java.util.Objects;

import javax.vecmath.Matrix4f;
import javax.vecmath.Vector3f;
import javax.vecmath.Vector4f;

import mchorse.blockbuster.legacy.LegacyIdMap;
import mchorse.mclib.utils.Interpolation;
import mchorse.mclib.utils.MatrixUtils;
import mchorse.mclib.utils.NBTUtils;
import mchorse.metamorph.api.Morph;
import mchorse.metamorph.api.MorphUtils;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorphLimbs;
import mchorse.metamorph.api.morphs.utils.Animation;
import mchorse.metamorph.api.morphs.utils.IAnimationProvider;
import mchorse.metamorph.api.morphs.utils.IMorphGenerator;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

/**
 * Morph body part (roadmap P51) — data / NBT / merge / pause layer.
 *
 * <p>Quirks preserved byte-for-byte:</p>
 * <ul>
 *   <li><b>Rotate identity is {@code (180, 0, 0)}, not zero</b>; {@code R} is
 *       omitted from NBT exactly when it equals {@code (180,0,0)}.</li>
 *   <li>{@code Items} is a list of 6 entries when written (empty compounds for
 *       empty slots); it is omitted entirely only when <i>all six</i> are
 *       empty.</li>
 *   <li>{@code Enabled}/{@code Animate} are inverted-omission booleans (written
 *       only when false); {@code Limb} only when non-empty — and re-emitted as
 *       the <i>raw</i> legacy string when P227 translated it, see
 *       {@link #loadedLimb}.</li>
 *   <li>{@code canMerge} <b>always returns true</b>; it captures {@code last*}
 *       tween-source vectors only when the limb names are equal, else nulls
 *       them — tween continuity keys off limb-name equality, not part
 *       identity.</li>
 * </ul>
 *
 * <p>Port note: the <i>drawing</i> half of the client render pipeline
 * ({@code render}, {@code init}, {@code drawAxis}, {@code recordMatrix}) needs a
 * {@code MatrixStack} and the morph render dispatcher, so it lives in the client
 * source set as {@link BodyPartRenderer}. What stays here is the state that
 * pipeline reads and writes — the {@link #entity dummy host}, the
 * {@link #lastMatrix recorded limb matrix}, the {@link #cachedTranslation}
 * hand-off and {@link #updateEntity()} — none of which touches a client-only
 * class ({@code LivingEntity}/{@code EquipmentSlot}/{@code Matrix4f} are all
 * common). The mclib GUI transform seam {@link #addTranslation} <i>does</i> live
 * here — see its javadoc for why it takes a {@code boolean} instead of
 * implementing {@code ITransformationObject}.</p>
 *
 * <p>{@code ItemStack} (de)serialization routes through
 * {@code ItemStack.fromNbt}/{@code writeNbt}; pre-flattening item NBT will go
 * through the P71 id-translation shim when it lands.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/bodypart/BodyPart.java
 */
public class BodyPart
{
    /**
     * Translation handed down from the enclosing morph to the sub-morph being
     * drawn on a limb (legacy {@code BodyPart.cachedTranslation}). It is a
     * <b>static mailbox</b>, not a field: whoever is about to draw a body part
     * writes it, {@link BodyPartRenderer#render} folds it into the sub-morph's
     * own {@code cachedTranslation} and then <i>zeroes it</i>, so it never leaks
     * into the next part. Snowstorm morphs read the result to place emitters in
     * world space.
     */
    public static Vector3f cachedTranslation = new Vector3f();

    /**
     * True while a render pass exists only to capture limb matrices (legacy
     * {@code BodyPart.recording}, set by {@code recordMatrix}). Every
     * {@code BodyPart} draw short-circuits to stamping {@link #lastMatrix} and
     * emits no geometry.
     */
    public static boolean recording = false;

    /**
     * The model-view matrix in effect at this part's limb, stamped by the last
     * {@link BodyPartRenderer#recordMatrix} pass. Null until one has run — the
     * onion-skin composition in {@code GuiBodyPartEditor} keys off exactly that.
     */
    public Matrix4f lastMatrix = null;

    /**
     * Dummy host entity for the {@code !useTarget} path — the sub-morph is
     * animated against a private standing entity rather than the entity wearing
     * the parent morph. Filled by {@link BodyPartRenderer#init}; stays null on a
     * dedicated server and in headless tests, where every consumer treats it as
     * "not initialized" and no-ops.
     */
    public LivingEntity entity;

    public Morph morph = new Morph();
    public ItemStack[] slots = new ItemStack[] {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};

    /* Raw legacy per-slot item NBT, preserved verbatim for byte-parity re-emit
     * (mirrors BlockMorph/ItemMorph). loadedSlots[i] is non-null only for a slot
     * whose stack was loaded from disk; it travels with the slot through
     * copy()/canMerge() and is cleared for slots reassigned from a source that
     * had none. */
    protected NbtCompound[] loadedSlots = new NbtCompound[6];
    public Vector3f translate = new Vector3f();
    public Vector3f scale = new Vector3f(1, 1, 1);
    public Vector3f rotate = new Vector3f(180, 0, 0);
    public boolean useTarget = false;
    public boolean enabled = true;
    public boolean animate = true;
    public String limb = "";

    /**
     * Raw legacy {@code Limb} string this part was loaded with, kept for
     * byte-parity re-emit whenever {@link #limb} was rewritten by
     * {@link #translateLegacyLimb()} (roadmap P227). Null on a part that was
     * never translated — i.e. everything a modern file or the editor produces.
     *
     * <p>Same contract as {@link #loadedSlots}: the shim result is what runs,
     * the raw value is what goes back on disk, so a 1.12.2 file that this client
     * opens and saves still opens in 1.12.2.</p>
     */
    protected String loadedLimb;

    private Vector3f lastTranslate;
    private Vector3f lastScale;
    private Vector3f lastRotate;

    /**
     * Add a translation delta to this part (body-part editor trackpad drag,
     * roadmap P59.1).
     *
     * <p>Faithful port of Metamorph 1.4's {@code BodyPart.addTranslation}. In the
     * <b>GLOBAL</b> orientation ({@code local == false}) the delta lands on
     * {@link #translate} verbatim. In the <b>LOCAL</b> orientation
     * ({@code local == true}) it is first rotated through this part's own
     * rotation, using the intrinsic matrix built in the
     * {@link MatrixUtils.RotationOrder#XYZ} order (rot Z, then Y, then X —
     * matching the draw order in {@code BodyPartRenderer.render}) from
     * {@link #rotate} converted degrees→radians. That is what lets you slide a
     * prop out along a <i>rotated</i> arm instead of along the world axes.</p>
     *
     * <p><b>{@link #rotate} defaults to {@code (180, 0, 0)}, not zero</b>, so the
     * LOCAL matrix on a fresh part is a 180° flip about X: a {@code (0, 1, 0)}
     * delta lands as {@code (0, -1, 0)}. That is legacy behaviour, not a sign
     * bug.</p>
     *
     * <p>Port note: legacy declared this through mclib's
     * {@code ITransformationObject}, whose one method names the client-only type
     * {@code GuiTransformations.TransformOrientation} — which a main-side class
     * cannot see. The interface is therefore <b>not</b> ported; the boolean
     * carries the same information and the caller
     * ({@code GuiBodyPartEditor.GuiBodyPartTransformations#localTranslate}) maps
     * {@code getOrientation() == LOCAL} onto it. Same convention as
     * {@code ModelTransform.addTranslation}.</p>
     */
    public void addTranslation(double x, double y, double z, boolean local)
    {
        Vector4f trans = new Vector4f((float) x, (float) y, (float) z, 1);

        if (local)
        {
            float rotX = (float) Math.toRadians(this.rotate.x);
            float rotY = (float) Math.toRadians(this.rotate.y);
            float rotZ = (float) Math.toRadians(this.rotate.z);

            MatrixUtils.getRotationMatrix(rotX, rotY, rotZ, MatrixUtils.RotationOrder.XYZ).transform(trans);
        }

        this.translate.add(new Vector3f(trans.x, trans.y, trans.z));
    }

    /**
     * Rewrite {@link #limb} from a 1.12 {@code ModelBase} field name to the yarn
     * model-part name that addresses the same physical limb (roadmap P227),
     * stashing the raw string in {@link #loadedLimb} so {@link #toNBT} can put
     * it back verbatim.
     *
     * <p>Called on load, from {@code EntityMorph.fromNBT} only — see
     * {@link EntityMorphLimbs} for why a {@code CustomMorph}'s parts must
     * <b>not</b> go through here. Idempotent: a second call finds the translated
     * name already in place and changes nothing.</p>
     *
     * @return whether the name actually changed
     */
    public boolean translateLegacyLimb()
    {
        if (this.limb == null || this.limb.isEmpty())
        {
            return false;
        }

        String translated = EntityMorphLimbs.translate(this.limb);

        if (translated.equals(this.limb))
        {
            return false;
        }

        this.loadedLimb = this.limb;
        this.limb = translated;

        return true;
    }

    /**
     * The string {@link #toNBT} writes for {@code Limb}: the raw legacy one when
     * {@link #limb} is still exactly what translating it produced, else
     * {@link #limb} itself.
     *
     * <p>That equality check is the guard against a stale re-emit. {@link #limb}
     * is a public field and the body-part editor assigns it directly (three call
     * sites in {@code GuiBodyPartEditor}); once a user re-picks the limb, the
     * invariant breaks and the freshly picked yarn name is what gets saved.</p>
     */
    protected String limbForNBT()
    {
        if (this.loadedLimb != null && this.limb.equals(EntityMorphLimbs.translate(this.loadedLimb)))
        {
            return this.loadedLimb;
        }

        return this.limb;
    }

    /**
     * Push this part's six item slots onto the {@link #entity dummy host}, so
     * the sub-morph's held-item / armor layers have something to draw. No-op
     * before {@link BodyPartRenderer#init} has supplied a host.
     *
     * <p>Legacy indexed {@code EntityEquipmentSlot.values()[i]}; yarn's
     * {@link EquipmentSlot} declares the same six constants in the same order
     * (MAINHAND, OFFHAND, FEET, LEGS, CHEST, HEAD), so the slot array's on-disk
     * index meaning is unchanged.</p>
     */
    public void updateEntity()
    {
        if (this.entity == null)
        {
            return;
        }

        for (int i = 0; i < this.slots.length; i++)
        {
            this.entity.equipStack(EquipmentSlot.values()[i], this.slots[i]);
        }
    }

    /**
     * Tick the sub-morph. On the {@code !useTarget} path this ages the private
     * {@link #entity dummy host} by one tick (that counter is what drives every
     * idle animation the sub-morph has) and then updates against it.
     *
     * <p>The yaw rebase mirrors the one in {@link BodyPartRenderer#render}: the
     * morph is <i>updated</i> in the same body-local frame it is <i>drawn</i>
     * in, otherwise a look-around would desync animation from geometry. Only the
     * four head/body fields are rebased here — legacy left {@code rotationYaw}
     * alone on the update path while rebasing it on the render path, and that
     * asymmetry is preserved.</p>
     */
    public void update(AbstractMorph parent, LivingEntity entity)
    {
        entity = this.useTarget ? entity : this.entity;

        if (entity != null && this.enabled)
        {
            if (!this.useTarget)
            {
                this.entity.age++;
            }

            AbstractMorph morph = this.morph.get();

            if (morph != null)
            {
                float bodyYaw = entity.bodyYaw;
                float prevBodyYaw = entity.prevBodyYaw;
                float headYaw = entity.headYaw;
                float prevHeadYaw = entity.prevHeadYaw;

                entity.headYaw = entity.headYaw - entity.bodyYaw;
                entity.prevHeadYaw = entity.prevHeadYaw - entity.prevBodyYaw;
                entity.bodyYaw = entity.prevBodyYaw = 0;

                morph.update(entity);

                entity.bodyYaw = bodyYaw;
                entity.prevBodyYaw = prevBodyYaw;
                entity.headYaw = headYaw;
                entity.prevHeadYaw = prevHeadYaw;
            }
        }
    }

    /* Tween sources for the animated-transform path. Package-private: the only
     * reader is BodyPartRenderer, which sits in this package in the client
     * source set. They stay private to everything else because canMerge/pause
     * own their lifecycle. */

    Vector3f getLastTranslate()
    {
        return this.lastTranslate;
    }

    Vector3f getLastScale()
    {
        return this.lastScale;
    }

    Vector3f getLastRotate()
    {
        return this.lastRotate;
    }

    public boolean canMerge(BodyPart part)
    {
        this.morph.set(part.morph.copy());

        if (Objects.equals(this.limb, part.limb))
        {
            this.lastTranslate = new Vector3f(this.translate);
            this.lastScale = new Vector3f(this.scale);
            this.lastRotate = new Vector3f(this.rotate);
        }
        else
        {
            this.lastTranslate = null;
            this.lastScale = null;
            this.lastRotate = null;
        }

        this.translate.set(part.translate);
        this.scale.set(part.scale);
        this.rotate.set(part.rotate);
        this.useTarget = part.useTarget;
        this.enabled = part.enabled;
        this.animate = part.animate;

        for (int i = 0; i < part.slots.length; i++)
        {
            this.slots[i] = part.slots[i];
            this.loadedSlots[i] = part.loadedSlots[i];
        }

        this.limb = part.limb;
        this.loadedLimb = part.loadedLimb;

        return true;
    }

    public void pause(BodyPart previous, int offset)
    {
        if (previous != null && Objects.equals(this.limb, previous.limb))
        {
            this.lastTranslate = new Vector3f(previous.translate);
            this.lastScale = new Vector3f(previous.scale);
            this.lastRotate = new Vector3f(previous.rotate);
        }

        MorphUtils.pause(this.morph.get(), previous == null ? null : previous.morph.get(), offset);
    }

    public BodyPart genCurrentBodyPart(AbstractMorph morph, float partialTicks)
    {
        BodyPart part = this.copy();

        if (morph instanceof IAnimationProvider)
        {
            Animation animation = ((IAnimationProvider) morph).getAnimation();

            if (animation.isInProgress() && this.lastTranslate != null && this.animate)
            {
                Interpolation inter = animation.interp;
                float factor = animation.getFactor(partialTicks);

                part.translate.x = inter.interpolate(this.lastTranslate.x, this.translate.x, factor);
                part.translate.y = inter.interpolate(this.lastTranslate.y, this.translate.y, factor);
                part.translate.z = inter.interpolate(this.lastTranslate.z, this.translate.z, factor);
                part.scale.x = inter.interpolate(this.lastScale.x, this.scale.x, factor);
                part.scale.y = inter.interpolate(this.lastScale.y, this.scale.y, factor);
                part.scale.z = inter.interpolate(this.lastScale.z, this.scale.z, factor);
                part.rotate.x = inter.interpolate(this.lastRotate.x, this.rotate.x, factor);
                part.rotate.y = inter.interpolate(this.lastRotate.y, this.rotate.y, factor);
                part.rotate.z = inter.interpolate(this.lastRotate.z, this.rotate.z, factor);
            }
        }

        if (this.morph.get() instanceof IMorphGenerator)
        {
            IMorphGenerator generator = (IMorphGenerator) this.morph.get();

            if (generator.canGenerate())
            {
                part.morph.setDirect(generator.genCurrentMorph(partialTicks));
            }
        }

        return part;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof BodyPart)
        {
            BodyPart part = (BodyPart) obj;

            boolean result = Objects.equals(this.morph, part.morph);
            result = result && Objects.equals(this.translate, part.translate);
            result = result && Objects.equals(this.scale, part.scale);
            result = result && Objects.equals(this.rotate, part.rotate);
            result = result && this.useTarget == part.useTarget;
            result = result && this.enabled == part.enabled;
            result = result && this.animate == part.animate;

            for (int i = 0; i < this.slots.length; i++)
            {
                result = result && ItemStack.areEqual(this.slots[i], part.slots[i]);
            }

            result = result && Objects.equals(this.limb, part.limb);

            return result;
        }

        return super.equals(obj);
    }

    public BodyPart copy()
    {
        BodyPart part = new BodyPart();

        part.morph.copy(this.morph);
        part.translate.set(this.translate);
        part.scale.set(this.scale);
        part.rotate.set(this.rotate);
        part.useTarget = this.useTarget;
        part.enabled = this.enabled;
        part.animate = this.animate;

        for (int i = 0; i < this.slots.length; i++)
        {
            part.slots[i] = this.slots[i];
            part.loadedSlots[i] = this.loadedSlots[i];
        }

        part.limb = this.limb;
        part.loadedLimb = this.loadedLimb;

        return part;
    }

    public void fromNBT(NbtCompound tag)
    {
        if (tag.contains("Morph", NbtElement.COMPOUND_TYPE))
        {
            this.morph.fromNBT(tag.getCompound("Morph"));
        }

        if (tag.contains("Items", NbtElement.LIST_TYPE))
        {
            NbtList items = tag.getList("Items", NbtElement.COMPOUND_TYPE);

            for (int i = 0, c = Math.min(items.size(), this.slots.length); i < c; i++)
            {
                NbtCompound compound = items.getCompound(i);

                /* P71: route each slot's item NBT through the central id shim so
                 * a legacy meta-subtype item (wool@14 -> red_wool) renders
                 * instead of resolving to air; nesting cannot smuggle an
                 * untranslated id (S20 P212 step 5). The raw NBT is kept in
                 * loadedSlots for byte-parity re-emit. Empty slots ({}) keep no
                 * loaded NBT — they re-emit as {} either way. */
                ItemStack stack = LegacyIdMap.itemStack(compound);

                this.slots[i] = stack;
                this.loadedSlots[i] = stack.isEmpty() ? null : compound.copy();
            }
        }

        NBTUtils.readFloatList(tag.getList("T", NbtElement.FLOAT_TYPE), this.translate);
        NBTUtils.readFloatList(tag.getList("S", NbtElement.FLOAT_TYPE), this.scale);
        NBTUtils.readFloatList(tag.getList("R", NbtElement.FLOAT_TYPE), this.rotate);

        if (tag.contains("Target")) this.useTarget = tag.getBoolean("Target");
        if (tag.contains("Enabled")) this.enabled = tag.getBoolean("Enabled");
        if (tag.contains("Animate")) this.animate = tag.getBoolean("Animate");
        if (tag.contains("Limb")) this.limb = tag.getString("Limb");

        /* A part re-read from NBT is by definition not carrying a translation
         * yet; EntityMorph.fromNBT applies one right after (P227). */
        this.loadedLimb = null;
    }

    public void toNBT(NbtCompound tag)
    {
        NbtCompound morph = this.morph.toNBT();

        if (morph != null)
        {
            tag.put("Morph", morph);
        }

        NbtList list = new NbtList();
        int empty = 0;

        for (int i = 0; i < this.slots.length; i++)
        {
            NbtCompound compound = new NbtCompound();
            ItemStack stack = this.slots[i];

            if (!stack.isEmpty())
            {
                if (this.loadedSlots[i] != null)
                {
                    /* Byte-parity: re-emit the raw legacy slot NBT verbatim
                     * rather than the shim-flattened stack (mirrors BlockMorph
                     * /ItemMorph). */
                    compound = this.loadedSlots[i].copy();
                }
                else
                {
                    stack.writeNbt(compound);
                }
            }
            else
            {
                empty += 1;
            }

            list.add(compound);
        }

        if (empty != this.slots.length)
        {
            tag.put("Items", list);
        }

        if (this.translate.x != 0 || this.translate.y != 0 || this.translate.z != 0)
        {
            tag.put("T", NBTUtils.writeFloatList(new NbtList(), this.translate));
        }

        if (this.scale.x != 1 || this.scale.y != 1 || this.scale.z != 1)
        {
            tag.put("S", NBTUtils.writeFloatList(new NbtList(), this.scale));
        }

        if (this.rotate.x != 180 || this.rotate.y != 0 || this.rotate.z != 0)
        {
            tag.put("R", NBTUtils.writeFloatList(new NbtList(), this.rotate));
        }

        if (this.useTarget) tag.putBoolean("Target", this.useTarget);
        if (!this.enabled) tag.putBoolean("Enabled", this.enabled);
        if (!this.animate) tag.putBoolean("Animate", this.animate);
        String limb = this.limbForNBT();

        if (!limb.isEmpty()) tag.putString("Limb", limb);
    }
}
