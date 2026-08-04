package mchorse.chameleon.metamorph.pose;

import net.minecraft.nbt.NbtCompound;

import java.util.HashMap;
import java.util.Map;

/**
 * A whole pose: one {@link AnimatedPoseTransform} per bone, plus the pose-wide
 * {@link #animated} weight.
 *
 * <p>{@link #copy} is <b>not</b> {@code putAll} — it only overwrites bones this
 * pose already has, and ignores bones the source has that this one does not.
 * That is what makes "paste pose" safe across two different models, and it is
 * why the editor creates an entry for every bone up front.</p>
 *
 * Legacy source: chameleon/.../metamorph/pose/AnimatedPose.java
 */
public class AnimatedPose
{
    public final Map<String, AnimatedPoseTransform> bones = new HashMap<String, AnimatedPoseTransform>();
    public float animated = AnimatedPoseTransform.ANIMATED;

    public void copy(AnimatedPose pose)
    {
        for (Map.Entry<String, AnimatedPoseTransform> entry : this.bones.entrySet())
        {
            AnimatedPoseTransform transform = pose.bones.get(entry.getKey());

            if (transform != null)
            {
                entry.getValue().copy(transform);
            }
        }
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof AnimatedPose)
        {
            AnimatedPose pose = (AnimatedPose) obj;

            return this.bones.equals(pose.bones)
                && this.animated == pose.animated;
        }

        return super.equals(obj);
    }

    public AnimatedPose clone()
    {
        AnimatedPose pose = new AnimatedPose();

        for (Map.Entry<String, AnimatedPoseTransform> entry : this.bones.entrySet())
        {
            pose.bones.put(entry.getKey(), entry.getValue().clone());
        }

        pose.animated = this.animated;

        return pose;
    }

    public void fromNBT(NbtCompound tag)
    {
        if (tag.contains("Pose"))
        {
            NbtCompound pose = tag.getCompound("Pose");

            for (String key : pose.getKeys())
            {
                AnimatedPoseTransform config = new AnimatedPoseTransform(key);

                config.fromNBT(pose.getCompound(key));
                this.bones.put(key, config);
            }
        }

        if (tag.contains("Animated"))
        {
            this.animated = tag.getBoolean("Animated") ? AnimatedPoseTransform.ANIMATED : AnimatedPoseTransform.FIXED;
        }
    }

    public NbtCompound toNBT()
    {
        NbtCompound tag = new NbtCompound();
        NbtCompound pose = new NbtCompound();

        for (Map.Entry<String, AnimatedPoseTransform> entry : this.bones.entrySet())
        {
            pose.put(entry.getKey(), entry.getValue().toNBT(null));
        }

        tag.put("Pose", pose);

        if (this.animated != AnimatedPoseTransform.ANIMATED)
        {
            tag.putBoolean("Animated", false);
        }

        return tag;
    }
}
