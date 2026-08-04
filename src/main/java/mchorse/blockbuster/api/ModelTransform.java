package mchorse.blockbuster.api;

import com.google.common.base.MoreObjects;

import mchorse.mclib.utils.Interpolation;
import mchorse.mclib.utils.MatrixUtils;
import mchorse.mclib.utils.NBTUtils;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import javax.vecmath.Vector4f;

/**
 * Transform class
 *
 * This class simply holds basic transformation data for every limb.
 *
 * <p>The GL apply methods ({@code transform}/{@code applyTranslate}/
 * {@code applyRotate}/{@code applyScale}) from 1.12.2 are render concerns that
 * re-land with S6; the load-bearing spec they carry is the rotation order
 * <b>Z, then Y, then X</b> (see {@code applyRotate} in the legacy source). Only
 * the data + (de)serialization layer, plus the pure-math editor op
 * {@link #addTranslation(double, double, double, boolean)} (P72), live here.</p>
 */
public class ModelTransform
{
    /**
     * Default model transform. Please don't modify its values.
     */
    public static final ModelTransform DEFAULT = new ModelTransform();

    public float[] translate = new float[] {0, 0, 0};
    public float[] scale = new float[] {1, 1, 1};
    public float[] rotate = new float[] {0, 0, 0};

    /**
     * Add a translation delta to this transform (model-editor drag op, P72).
     *
     * <p>Faithful port of 1.12.2's {@code ModelTransform.addTranslation}. In the
     * <b>GLOBAL</b> orientation ({@code local == false}) the delta is added to
     * {@link #translate} verbatim. In the <b>LOCAL</b> orientation
     * ({@code local == true}) the delta is first rotated through this transform's
     * own rotation, using the intrinsic rotation matrix built in the
     * {@link MatrixUtils.RotationOrder#XYZ} order (rot Z, then Y, then X — matching
     * {@code applyRotate}) from {@link #rotate} converted degrees→radians, exactly
     * as legacy did via {@code MatrixUtils.getRotationMatrix(...).transform(...)}.</p>
     *
     * <p>SEAM(S13): the legacy signature took a
     * {@code GuiTransformations.TransformOrientation} (client source set). The GUI
     * model editor landing in S13 maps {@code orientation == LOCAL} to
     * {@code local = true} when it calls this pure-domain op; keeping the boolean
     * here avoids a main→client source-set dependency.</p>
     */
    public void addTranslation(double x, double y, double z, boolean local)
    {
        Vector4f trans = new Vector4f((float) x, (float) y, (float) z, 1);

        if (local)
        {
            float rotX = (float) Math.toRadians(this.rotate[0]);
            float rotY = (float) Math.toRadians(this.rotate[1]);
            float rotZ = (float) Math.toRadians(this.rotate[2]);

            MatrixUtils.getRotationMatrix(rotX, rotY, rotZ, MatrixUtils.RotationOrder.XYZ).transform(trans);
        }

        this.translate[0] += trans.x;
        this.translate[1] += trans.y;
        this.translate[2] += trans.z;
    }

    public static boolean equalFloatArray(float[] a, float[] b)
    {
        if (a.length != b.length)
        {
            return false;
        }

        for (int i = 0; i < a.length; i++)
        {
            if (Math.abs(a[i] - b[i]) > 0.0001F)
            {
                return false;
            }
        }

        return true;
    }

    public boolean isDefault()
    {
        return this.equals(DEFAULT);
    }

    /**
     * Pose-freeze blend factor of this transform (roadmap P82 seam).
     *
     * <p>The base transform is never frozen, so this returns {@code 0}. The
     * {@code CustomMorph.LimbProperties} subclass (blockbuster_pack morph phase,
     * S4) overrides it to return its {@code fixed} field. {@code ModelCustom
     * .applyLimbPose} turns this into the pose-blend factor {@code anim = 1 -
     * fixed} consumed by the P82 rotation pass. Exposing it as a base-class hook
     * keeps the pose runtime free of a hard {@code LimbProperties} dependency
     * until that phase lands, while preserving the exact legacy semantics
     * (a plain {@link ModelTransform} endpoint contributes {@code fixed == 0}).</p>
     */
    public float getFixed()
    {
        return 0F;
    }

    public void copy(ModelTransform transform)
    {
        for (int i = 0; i < 3; i++)
            this.translate[i] = transform.translate[i];
        for (int i = 0; i < 3; i++)
            this.scale[i] = transform.scale[i];
        for (int i = 0; i < 3; i++)
            this.rotate[i] = transform.rotate[i];
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof ModelTransform)
        {
            ModelTransform transform = (ModelTransform) obj;

            return equalFloatArray(this.translate, transform.translate) && equalFloatArray(this.rotate, transform.rotate) && equalFloatArray(this.scale, transform.scale);
        }

        return super.equals(obj);
    }

    /**
     * Clone a model transform
     */
    @Override
    public ModelTransform clone()
    {
        ModelTransform b = new ModelTransform();

        b.translate = new float[] {this.translate[0], this.translate[1], this.translate[2]};
        b.rotate = new float[] {this.rotate[0], this.rotate[1], this.rotate[2]};
        b.scale = new float[] {this.scale[0], this.scale[1], this.scale[2]};

        return b;
    }

    public void fromNBT(NbtCompound tag)
    {
        if (tag.contains("P", NbtElement.LIST_TYPE)) NBTUtils.readFloatList(tag.getList("P", 5), this.translate);
        if (tag.contains("S", NbtElement.LIST_TYPE)) NBTUtils.readFloatList(tag.getList("S", 5), this.scale);
        if (tag.contains("R", NbtElement.LIST_TYPE)) NBTUtils.readFloatList(tag.getList("R", 5), this.rotate);
    }

    public NbtCompound toNBT()
    {
        NbtCompound tag = new NbtCompound();

        if (!this.isDefault())
        {
            if (!equalFloatArray(DEFAULT.translate, this.translate)) tag.put("P", NBTUtils.writeFloatList(new NbtList(), this.translate));
            if (!equalFloatArray(DEFAULT.scale, this.scale)) tag.put("S", NBTUtils.writeFloatList(new NbtList(), this.scale));
            if (!equalFloatArray(DEFAULT.rotate, this.rotate)) tag.put("R", NBTUtils.writeFloatList(new NbtList(), this.rotate));
        }

        return tag;
    }

    @Override
    public String toString()
    {
        return MoreObjects.toStringHelper(this).add("translate", this.translate).add("scale", this.scale).add("rotate", this.rotate).toString();
    }

    public void interpolate(ModelTransform a, ModelTransform b, float x, Interpolation interp)
    {
        for (int i = 0; i < this.translate.length; i++)
        {
            this.translate[i] = interp.interpolate(a.translate[i], b.translate[i], x);
            this.scale[i] = interp.interpolate(a.scale[i], b.scale[i], x);
            this.rotate[i] = interp.interpolate(a.rotate[i], b.rotate[i], x);
        }
    }
}
