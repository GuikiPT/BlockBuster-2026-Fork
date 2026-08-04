package mchorse.chameleon.metamorph.pose;

import mchorse.mclib.utils.Interpolation;
import mchorse.mclib.utils.MatrixUtils;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

import javax.vecmath.Vector4f;

/**
 * Configuration class for general transformation
 *
 * <p>The rotation fields are stored in <b>radians</b>, unlike Blockbuster's own
 * {@code ModelTransform} (degrees) — the editor converts on the way in and out,
 * and {@code ChameleonMorph.applyPose} multiplies by {@code 180/pi} before
 * handing them to a bone. Legacy's own comment on the matter reads
 * "That was a bad idea...", but it is what the saved NBT contains.</p>
 *
 * <p>Port note: legacy implemented the client-only {@code ITransformationObject},
 * whose sole method names {@code GuiTransformations.TransformOrientation}. That
 * interface is not ported (see {@code BodyPart.addTranslation}); the boolean
 * {@code local} carries the same information and the editor maps
 * {@code getOrientation() == LOCAL} onto it.</p>
 *
 * Legacy source: chameleon/.../metamorph/pose/AnimatedTransform.java
 */
public class AnimatedTransform
{
    public String boneName;

    /* Translate */
    public float x;
    public float y;
    public float z;

    /* Scale */
    public float scaleX = 1;
    public float scaleY = 1;
    public float scaleZ = 1;

    /* Rotate */
    public float rotateX;
    public float rotateY;
    public float rotateZ;

    public AnimatedTransform(String name)
    {
        this.boneName = name;
    }

    public void interpolate(AnimatedTransform a, AnimatedTransform b, float x, Interpolation interp)
    {
        this.x = interp.interpolate(a.x, b.x, x);
        this.y = interp.interpolate(a.y, b.y, x);
        this.z = interp.interpolate(a.z, b.z, x);
        this.scaleX = interp.interpolate(a.scaleX, b.scaleX, x);
        this.scaleY = interp.interpolate(a.scaleY, b.scaleY, x);
        this.scaleZ = interp.interpolate(a.scaleZ, b.scaleZ, x);
        this.rotateX = interp.interpolate(a.rotateX, b.rotateX, x);
        this.rotateY = interp.interpolate(a.rotateY, b.rotateY, x);
        this.rotateZ = interp.interpolate(a.rotateZ, b.rotateZ, x);
    }

    /**
     * Offset the translation, optionally along the bone's own axes.
     *
     * <p>The Y and Z rotations are negated when building the local basis — a
     * legacy quirk that follows from Chameleon's mirrored X axis (see
     * {@code ModelParser}). Kept verbatim.</p>
     */
    public void addTranslation(double x, double y, double z, boolean local)
    {
        Vector4f trans = new Vector4f((float) x, (float) y, (float) z, 1);

        if (local)
        {
            MatrixUtils.getRotationMatrix(this.rotateX, -this.rotateY, -this.rotateZ, MatrixUtils.RotationOrder.XYZ).transform(trans);
        }

        this.x += trans.x;
        this.y += trans.y;
        this.z += trans.z;
    }

    /**
     * Clone this object
     */
    @Override
    public AnimatedTransform clone()
    {
        AnimatedTransform item = new AnimatedTransform(this.boneName);

        item.x = this.x;
        item.y = this.y;
        item.z = this.z;
        item.scaleX = this.scaleX;
        item.scaleY = this.scaleY;
        item.scaleZ = this.scaleZ;
        item.rotateX = this.rotateX;
        item.rotateY = this.rotateY;
        item.rotateZ = this.rotateZ;

        return item;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof AnimatedTransform)
        {
            AnimatedTransform config = (AnimatedTransform) obj;

            boolean result = config.x == this.x && config.y == this.y && config.z == this.z;

            result = result && config.scaleX == this.scaleX && config.scaleY == this.scaleY && config.scaleZ == this.scaleZ;
            result = result && config.rotateX == this.rotateX && config.rotateY == this.rotateY && config.rotateZ == this.rotateZ;

            return result;
        }

        return super.equals(obj);
    }

    public void fromNBT(NbtCompound tag)
    {
        if (tag.contains("X", NbtElement.NUMBER_TYPE))
        {
            this.x = tag.getFloat("X");
        }

        if (tag.contains("Y", NbtElement.NUMBER_TYPE))
        {
            this.y = tag.getFloat("Y");
        }

        if (tag.contains("Z", NbtElement.NUMBER_TYPE))
        {
            this.z = tag.getFloat("Z");
        }

        if (tag.contains("SX", NbtElement.NUMBER_TYPE))
        {
            this.scaleX = tag.getFloat("SX");
        }

        if (tag.contains("SY", NbtElement.NUMBER_TYPE))
        {
            this.scaleY = tag.getFloat("SY");
        }

        if (tag.contains("SZ", NbtElement.NUMBER_TYPE))
        {
            this.scaleZ = tag.getFloat("SZ");
        }

        if (tag.contains("RX", NbtElement.NUMBER_TYPE))
        {
            this.rotateX = tag.getFloat("RX");
        }

        if (tag.contains("RY", NbtElement.NUMBER_TYPE))
        {
            this.rotateY = tag.getFloat("RY");
        }

        if (tag.contains("RZ", NbtElement.NUMBER_TYPE))
        {
            this.rotateZ = tag.getFloat("RZ");
        }
    }

    public NbtCompound toNBT(NbtCompound tag)
    {
        if (tag == null)
        {
            tag = new NbtCompound();
        }

        if (this.x != 0) tag.putFloat("X", this.x);
        if (this.y != 0) tag.putFloat("Y", this.y);
        if (this.z != 0) tag.putFloat("Z", this.z);
        if (this.scaleX != 1) tag.putFloat("SX", this.scaleX);
        if (this.scaleY != 1) tag.putFloat("SY", this.scaleY);
        if (this.scaleZ != 1) tag.putFloat("SZ", this.scaleZ);
        if (this.rotateX != 0) tag.putFloat("RX", this.rotateX);
        if (this.rotateY != 0) tag.putFloat("RY", this.rotateY);
        if (this.rotateZ != 0) tag.putFloat("RZ", this.rotateZ);

        return tag;
    }
}
