package mchorse.chameleon.lib.data.animation;

import java.util.ArrayList;
import java.util.List;

/**
 * A single animated channel (position, scale or rotation) of one bone: a
 * time-sorted list of {@link AnimationVector} keyframes.
 *
 * <p>{@link #sort()} also relinks the {@code prev}/{@code next} chain, which the
 * interpolators depend on — hermite reads both neighbours, and every keyframe's
 * <i>end</i> value is its successor's {@code pre} value. Always call it after
 * adding keyframes.</p>
 *
 * Legacy source: chameleon/.../lib/data/animation/AnimationChannel.java
 */
public class AnimationChannel
{
    public List<AnimationVector> keyframes = new ArrayList<AnimationVector>();

    public void sort()
    {
        this.keyframes.sort((a, b) ->
        {
            double diff = a.time - b.time;

            return diff < 0 ? -1 : (diff >= 0 ? 1 : 0);
        });

        AnimationVector previous = null;

        for (AnimationVector vector : this.keyframes)
        {
            if (previous != null)
            {
                previous.next = vector;
                vector.prev = previous;
            }

            previous = vector;
        }
    }
}