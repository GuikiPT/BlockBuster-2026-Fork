package mchorse.chameleon.metamorph.pose;

import mchorse.mclib.utils.Color;
import mchorse.mclib.utils.Interpolation;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

/**
 * One bone's entry in an {@link AnimatedPose}: the {@link AnimatedTransform}
 * offset plus Chameleon 1.2's per-limb appearance overrides (absolute
 * brightness, glow, tint).
 *
 * <p>{@link #fixed} is a <b>float</b>, not a boolean, despite only ever being
 * saved as one: it is interpolated during a morph transition, so mid-transition
 * a bone is partly fixed. {@code ChameleonMorph.applyPose} multiplies it by the
 * pose-wide {@link AnimatedPose#animated} to get the final "how much of the
 * animation survives" weight — {@link #ANIMATED} (1) keeps the animated value,
 * {@link #FIXED} (0) snaps back to the model's rest pose.</p>
 *
 * Legacy source: chameleon/.../metamorph/pose/AnimatedPoseTransform.java
 */
public class AnimatedPoseTransform extends AnimatedTransform
{
    public static final int FIXED = 0;
    public static final int ANIMATED = 1;

    public float fixed = ANIMATED;

    public boolean absoluteBrightness = false;
    public float glow = 0.0f;
    public Color color = new Color(1f, 1f, 1f, 1f);

    public AnimatedPoseTransform(String name)
    {
        super(name);
    }

    public AnimatedPoseTransform clone()
    {
        AnimatedPoseTransform item = new AnimatedPoseTransform(this.boneName);

        item.copy(this);

        return item;
    }

    public void copy(AnimatedPoseTransform transform)
    {
        this.x = transform.x;
        this.y = transform.y;
        this.z = transform.z;
        this.scaleX = transform.scaleX;
        this.scaleY = transform.scaleY;
        this.scaleZ = transform.scaleZ;
        this.rotateX = transform.rotateX;
        this.rotateY = transform.rotateY;
        this.rotateZ = transform.rotateZ;
        this.fixed = transform.fixed;
        this.absoluteBrightness = transform.absoluteBrightness;
        this.glow = transform.glow;
        this.color.copy(transform.color);
    }

    @Override
    public boolean equals(Object obj)
    {
        boolean result = super.equals(obj);

        if (obj instanceof AnimatedPoseTransform)
        {
            result = result && this.fixed == ((AnimatedPoseTransform) obj).fixed;
            result = result && this.absoluteBrightness == ((AnimatedPoseTransform) obj).absoluteBrightness;
            result = result && Math.abs(this.glow - ((AnimatedPoseTransform) obj).glow) < 0.0001;
            result = result && this.color.equals(((AnimatedPoseTransform) obj).color);
        }

        return result;
    }

    @Override
    public void fromNBT(NbtCompound tag)
    {
        super.fromNBT(tag);

        if (tag.contains("F", NbtElement.BYTE_TYPE)) this.fixed = tag.getBoolean("F") ? ANIMATED : FIXED;
        if (tag.contains("AB", NbtElement.BYTE_TYPE)) this.absoluteBrightness = tag.getBoolean("AB");
        if (tag.contains("G", NbtElement.FLOAT_TYPE)) this.glow = tag.getFloat("G");
        if (tag.contains("C", NbtElement.INT_TYPE)) this.color.set(tag.getInt("C"));
    }

    @Override
    public NbtCompound toNBT(NbtCompound tag)
    {
        tag = super.toNBT(tag);

        if (this.fixed != ANIMATED) tag.putBoolean("F", false);
        if (this.absoluteBrightness) tag.putBoolean("AB", this.absoluteBrightness);
        if (this.glow > 0.0001) tag.putFloat("G", this.glow);
        if (this.color.getRGBAColor() != 0xFFFFFFFF) tag.putInt("C", this.color.getRGBAColor());

        return tag;
    }

    /**
     * Interpolate the appearance overrides alongside the transform.
     *
     * <p>Asymmetric on purpose: when {@code b} is not an
     * {@link AnimatedPoseTransform} (i.e. the morph is transitioning towards a
     * bone with no pose entry) the overrides fade towards their neutral values
     * rather than holding. {@code absoluteBrightness} is a boolean and cannot
     * fade, so it snaps to the destination's value.</p>
     */
    @Override
    public void interpolate(AnimatedTransform a, AnimatedTransform b, float x, Interpolation interp)
    {
        super.interpolate(a, b, x, interp);

        float glow = 0.0f;
        float cr, cg, cb, ca;
        cr = cg = cb = ca = 1.0f;

        if (a instanceof AnimatedPoseTransform)
        {
            AnimatedPoseTransform l = (AnimatedPoseTransform) a;
            glow = l.glow;
            cr = l.color.r;
            cg = l.color.g;
            cb = l.color.b;
            ca = l.color.a;
        }

        if (b instanceof AnimatedPoseTransform)
        {
            AnimatedPoseTransform l = (AnimatedPoseTransform) b;
            glow = interp.interpolate(glow, l.glow, x);
            cr = interp.interpolate(cr, l.color.r, x);
            cg = interp.interpolate(cg, l.color.g, x);
            cb = interp.interpolate(cb, l.color.b, x);
            ca = interp.interpolate(ca, l.color.a, x);

            this.absoluteBrightness = l.absoluteBrightness;
        }
        else
        {
            glow = interp.interpolate(glow, 0.0f, x);
            cr = interp.interpolate(cr, 1.0f, x);
            cg = interp.interpolate(cg, 1.0f, x);
            cb = interp.interpolate(cb, 1.0f, x);
            ca = interp.interpolate(ca, 1.0f, x);

            this.absoluteBrightness = false;
        }

        this.glow = glow;
        this.color.set(cr, cg, cb, ca);
    }
}
