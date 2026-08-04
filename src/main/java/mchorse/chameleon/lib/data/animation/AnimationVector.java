package mchorse.chameleon.lib.data.animation;

import mchorse.mclib.math.molang.expressions.MolangExpression;
import net.minecraft.util.math.Direction;

/**
 * One keyframe of an {@link AnimationChannel}.
 *
 * <p>Each axis carries <b>two</b> Molang expressions, because Bedrock keyframes
 * can be discontinuous: {@code pre*} is the value approached from the left,
 * {@code x/y/z} the value leaving to the right. Hence
 * {@link #getEnd(Direction.Axis)} reads the <i>next</i> keyframe's {@code pre}
 * value rather than its post value — a jump-cut authored in Blockbench must
 * render as a jump.</p>
 *
 * <p>Port note: legacy's {@code net.minecraft.util.EnumFacing.Axis} is
 * {@link net.minecraft.util.math.Direction.Axis} on 1.20.4; nothing else
 * changed.</p>
 *
 * Legacy source: chameleon/.../lib/data/animation/AnimationVector.java
 */
public class AnimationVector
{
    public AnimationVector prev;
    public AnimationVector next;

    public double time;
    public AnimationInterpolation interp = AnimationInterpolation.LINEAR;
    public MolangExpression x;
    public MolangExpression y;
    public MolangExpression z;

    public MolangExpression preX;
    public MolangExpression preY;
    public MolangExpression preZ;

    public double getLengthInTicks()
    {
        return this.next == null ? 0 : (this.next.time - this.time) * 20D;
    }

    public MolangExpression getStart(Direction.Axis axis)
    {
        if (axis == Direction.Axis.X)
        {
            return this.x;
        }
        else if (axis == Direction.Axis.Y)
        {
            return this.y;
        }

        return this.z;
    }

    public MolangExpression getEnd(Direction.Axis axis)
    {
        if (axis == Direction.Axis.X)
        {
            return this.next == null ? this.x : this.next.preX;
        }
        else if (axis == Direction.Axis.Y)
        {
            return this.next == null ? this.y : this.next.preY;
        }

        return this.next == null ? this.z : this.next.preZ;
    }
}
