package mchorse.chameleon.lib.data.animation;

import java.util.HashMap;
import java.util.Map;

/**
 * One named animation out of a {@code .animation.json} file: its length and the
 * per-bone keyframe channels.
 *
 * <p>Bedrock authors animation length in <b>seconds</b>;
 * {@link #getLengthInTicks()} is the only place it becomes ticks, at 20 per
 * second, floored — the same rounding the action playback counts against.</p>
 *
 * Legacy source: chameleon/.../lib/data/animation/Animation.java
 */
public class Animation
{
    public final String id;

    /**
     * Animation length in seconds
     */
    public double length;

    public Map<String, AnimationPart> parts = new HashMap<String, AnimationPart>();

    public Animation(String id)
    {
        this.id = id;
    }

    public void setLength(double length)
    {
        this.length = length;
    }

    public int getLengthInTicks()
    {
        return (int) Math.floor(this.length * 20);
    }
}