package mchorse.chameleon.lib.data.animation;

/**
 * One bone's slice of an {@link Animation} — its three keyframe channels.
 *
 * Legacy source: chameleon/.../lib/data/animation/AnimationPart.java
 */
public class AnimationPart
{
    public AnimationChannel position = new AnimationChannel();
    public AnimationChannel scale = new AnimationChannel();
    public AnimationChannel rotation = new AnimationChannel();
}
