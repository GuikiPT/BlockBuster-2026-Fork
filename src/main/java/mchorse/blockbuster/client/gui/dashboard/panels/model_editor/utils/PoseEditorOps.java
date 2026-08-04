package mchorse.blockbuster.client.gui.dashboard.panels.model_editor.utils;

import java.util.List;

import mchorse.blockbuster.api.Model;
import mchorse.blockbuster.api.ModelPose;
import mchorse.blockbuster.api.ModelTransform;
import mchorse.mclib.utils.NBTUtils;
import net.minecraft.nbt.NbtCompound;

/**
 * P137 — headless pose-editor logic extracted from {@code GuiModelPoses} so the
 * clipboard codec, the {@code pasted_pose} uniquifier and the copy-pose /
 * apply-pose limb-transform propagation can be golden-tested without a GUI.
 *
 * <p>The GUI keeps the modal/context-menu wiring; it calls into these pure
 * operations. All behavior is a 1:1 port of
 * {@code blockbuster-1.12/.../model_editor/tabs/GuiModelPoses.java}.</p>
 */
public final class PoseEditorOps
{
    private PoseEditorOps()
    {}

    /**
     * Serialize a pose to the string that goes on the system clipboard — legacy
     * {@code pose.toNBT(new NBTTagCompound()).toString()}.
     */
    public static String serializePose(ModelPose pose)
    {
        return pose.toNBT(new NbtCompound()).toString();
    }

    /**
     * Try to parse a clipboard string into a {@link ModelPose}. Mirrors the
     * {@code createCopyPasteMenu} guard: the paste option is only offered when the
     * clipboard parses into a pose. Returns {@code null} on any failure (total
     * reader — never throws), which the GUI reads as "no paste action".
     */
    public static ModelPose parseClipboardPose(String clipboard)
    {
        NbtCompound tag = NBTUtils.parseSnbtCompound(clipboard);

        if (tag == null)
        {
            return null;
        }

        try
        {
            ModelPose loaded = new ModelPose();

            loaded.fromNBT(tag);

            return loaded;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * Port of {@code pastePose}'s default-name uniquifier: base {@code pasted_pose},
     * suffixed {@code _1}, {@code _2} … until it does not collide with an existing
     * pose name.
     */
    public static String uniquePasteName(Model model)
    {
        String base = "pasted_pose";
        String name = base;
        int index = 1;

        while (model.poses.containsKey(name))
        {
            name = base + "_" + (index++);
        }

        return name;
    }

    /**
     * Port of "Copy pose": pull a limb's transform FROM {@code sourcePose} INTO the
     * currently-edited transform. No-op when the source pose is missing. Returns
     * {@code true} when a copy happened (i.e. the panel should be marked dirty).
     */
    public static boolean copyPoseInto(Model model, String sourcePose, String limbName, ModelTransform into)
    {
        ModelPose pose = model.poses.get(sourcePose);

        if (pose == null)
        {
            return false;
        }

        into.copy(pose.limbs.get(limbName));

        return true;
    }

    /**
     * Port of "Apply pose": push the current limb's transform (taken from
     * {@code sourcePose}) TO each of {@code targetPoses}, skipping poses that lack
     * the limb. No-op (returns {@code false}) when the source pose/limb is missing
     * or the target list is empty. Returns {@code true} when at least the guard
     * passed (matches legacy, which marks dirty whenever it reaches the loop).
     */
    public static boolean applyLimbToPoses(Model model, String sourcePose, String limbName, List<String> targetPoses)
    {
        ModelPose pose = model.poses.get(sourcePose);

        if (pose == null)
        {
            return false;
        }

        ModelTransform current = pose.limbs.get(limbName);

        if (current == null || targetPoses.isEmpty())
        {
            return false;
        }

        for (String name : targetPoses)
        {
            ModelPose target = model.poses.get(name);

            if (target != null)
            {
                ModelTransform transform = target.limbs.get(limbName);

                if (transform != null)
                {
                    transform.copy(current);
                }
            }
        }

        return true;
    }
}
