package mchorse.chameleon.lib;

import mchorse.chameleon.Chameleon;
import mchorse.chameleon.lib.data.animation.Animation;
import mchorse.chameleon.lib.data.animation.AnimationChannel;
import mchorse.chameleon.lib.data.animation.AnimationPart;
import mchorse.chameleon.lib.data.animation.AnimationVector;
import mchorse.chameleon.lib.data.model.Model;
import mchorse.chameleon.lib.data.model.ModelBone;
import mchorse.chameleon.lib.data.model.ModelTransform;
import mchorse.mclib.utils.Interpolations;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Direction;

import javax.vecmath.Vector3d;
import java.util.List;

/**
 * Evaluates one {@link Animation} at a given frame and blends the result onto a
 * {@link Model}'s live bone transforms.
 *
 * <p>{@code blend} is a 0..1 weight against whatever is already in
 * {@code bone.current}, which is how the {@code Animator} cross-fades actions:
 * every action in the pipeline applies in turn, each lerping over the last.
 * {@code skipInitial} suppresses the "decay back to the rest pose" branch, so a
 * secondary action (a swipe, a jump) only touches the bones it actually
 * animates.</p>
 *
 * <p>Port note: legacy was {@code @SideOnly(CLIENT)} solely because
 * {@link MolangHelper#setMolangVariables} was; see that class for why the split
 * moved to a seam instead.</p>
 *
 * Legacy source: chameleon/.../lib/ChameleonAnimator.java
 */
public class ChameleonAnimator
{
    public static void resetPose(Model model)
    {
        for (ModelBone bone : model.bones)
        {
            bone.reset();
        }
    }

    public static void animate(LivingEntity target, Model model, Animation animation, float frame, float blend, boolean skipInitial)
    {
        MolangHelper.setMolangVariables(Chameleon.PARSER, target, frame);

        for (ModelBone bone : model.bones)
        {
            animateBone(bone, animation, frame, blend, skipInitial);
        }
    }

    private static void animateBone(ModelBone bone, Animation animation, float frame, float blend, boolean skipInitial)
    {
        boolean applied = false;

        AnimationPart part = animation.parts.get(bone.id);

        if (part != null)
        {
            applyBoneAnimation(bone, part, frame, blend);

            applied = true;
        }

        if (!applied && !skipInitial)
        {
            ModelTransform initial = bone.initial;
            ModelTransform current = bone.current;

            current.translate.x = Interpolations.lerp(current.translate.x, initial.translate.x, blend);
            current.translate.y = Interpolations.lerp(current.translate.y, initial.translate.y, blend);
            current.translate.z = Interpolations.lerp(current.translate.z, initial.translate.z, blend);

            current.scale.x = Interpolations.lerp(current.scale.x, initial.scale.x, blend);
            current.scale.y = Interpolations.lerp(current.scale.y, initial.scale.y, blend);
            current.scale.z = Interpolations.lerp(current.scale.z, initial.scale.z, blend);

            current.rotation.x = Interpolations.lerp(current.rotation.x, initial.rotation.x, blend);
            current.rotation.y = Interpolations.lerp(current.rotation.y, initial.rotation.y, blend);
            current.rotation.z = Interpolations.lerp(current.rotation.z, initial.rotation.z, blend);
        }

        for (ModelBone childBone : bone.children)
        {
            animateBone(childBone, animation, frame, blend, skipInitial);
        }
    }

    private static void applyBoneAnimation(ModelBone bone, AnimationPart animation, float frame, float blend)
    {
        Vector3d pos = interpolateList(animation.position, frame, MolangHelper.Component.POSITION);
        Vector3d rot = interpolateList(animation.rotation, frame, MolangHelper.Component.ROTATION);
        Vector3d scale = interpolateList(animation.scale, frame, MolangHelper.Component.SCALE);

        ModelTransform initial = bone.initial;
        ModelTransform current = bone.current;

        current.translate.x = Interpolations.lerp(current.translate.x, (float) pos.x + initial.translate.x, blend);
        current.translate.y = Interpolations.lerp(current.translate.y, (float) pos.y + initial.translate.y, blend);
        current.translate.z = Interpolations.lerp(current.translate.z, (float) pos.z + initial.translate.z, blend);

        current.scale.x = Interpolations.lerp(current.scale.x, (float) scale.x + initial.scale.x, blend);
        current.scale.y = Interpolations.lerp(current.scale.y, (float) scale.y + initial.scale.y, blend);
        current.scale.z = Interpolations.lerp(current.scale.z, (float) scale.z + initial.scale.z, blend);

        current.rotation.x = Interpolations.lerp(current.rotation.x, (float) rot.x + initial.rotation.x, blend);
        current.rotation.y = Interpolations.lerp(current.rotation.y, (float) rot.y + initial.rotation.y, blend);
        current.rotation.z = Interpolations.lerp(current.rotation.z, (float) rot.z + initial.rotation.z, blend);
    }

    private static Vector3d interpolateList(AnimationChannel channel, float frame, MolangHelper.Component component)
    {
        return interpolate(channel, frame, component);
    }

    private static Vector3d interpolate(AnimationChannel channel, float frame, MolangHelper.Component component)
    {
        Vector3d output = new Vector3d();
        List<AnimationVector> keyframes = channel.keyframes;

        if (keyframes.isEmpty())
        {
            output.set(0, 0, 0);

            return output;
        }

        AnimationVector first = keyframes.get(0);

        if (frame < first.time * 20)
        {
            output.x = MolangHelper.getValue(first.getStart(Direction.Axis.X), component, Direction.Axis.X);
            output.y = MolangHelper.getValue(first.getStart(Direction.Axis.Y), component, Direction.Axis.Y);
            output.z = MolangHelper.getValue(first.getStart(Direction.Axis.Z), component, Direction.Axis.Z);

            return output;
        }

        double duration = first.time * 20;

        for (AnimationVector vector : keyframes)
        {
            double length = vector.getLengthInTicks();

            if (frame >= duration && frame < duration + length)
            {
                double factor = (frame - duration) / length;

                output.x = vector.interp.interpolate(vector, component, Direction.Axis.X, factor);
                output.y = vector.interp.interpolate(vector, component, Direction.Axis.Y, factor);
                output.z = vector.interp.interpolate(vector, component, Direction.Axis.Z, factor);

                return output;
            }

            duration += length;
        }

        AnimationVector last = keyframes.get(keyframes.size() - 1);

        output.x = MolangHelper.getValue(last.getStart(Direction.Axis.X), component, Direction.Axis.X);
        output.y = MolangHelper.getValue(last.getStart(Direction.Axis.Y), component, Direction.Axis.Y);
        output.z = MolangHelper.getValue(last.getStart(Direction.Axis.Z), component, Direction.Axis.Z);

        return output;
    }
}
