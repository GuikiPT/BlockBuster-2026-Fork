package mchorse.blockbuster.client.render;

import mchorse.blockbuster.api.ModelPose;
import mchorse.blockbuster.client.model.ModelCustom;
import mchorse.blockbuster.client.model.ModelCustomRenderer;
import mchorse.blockbuster.client.model.PoseContext;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.bodypart.BodyPart;
import mchorse.metamorph.bodypart.BodyPartManager;
import mchorse.metamorph.bodypart.BodyPartRenderer;
import mchorse.metamorph.client.render.MorphRenderContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;

/**
 * Body-part feature layer (roadmap P54 / P80).
 *
 * <p>The layer that draws a morph's body parts: for each part,
 * find the parent model limb it names, move to that limb's frame
 * ({@code postRender}) and hand off to {@link BodyPartRenderer#render}. Legacy
 * ran as a {@code LayerRenderer} in {@code RenderCustomModel}'s layer list; here
 * it is invoked directly by the callers that own the frame — the in-world morph
 * renderer and the GUI preview — since our renderer has no vanilla layer
 * pipeline to register into.</p>
 *
 * <p>Also home to the load-bearing rendering quirk in Metamorph's
 * {@code BodyPart.render}: before drawing a nested morph on a limb, the host
 * entity's yaw fields are temporarily rebased so the body faces forward (body
 * yaw zeroed), then restored. That rebase is factored out as the pure
 * {@link #renderWithYawRebase} so it can be verified without a render frame;
 * {@link BodyPartRenderer} moves the values between it and the live entity.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../bodypart/BodyPart.java (render)
 *                blockbuster-1.12/.../blockbuster_pack/client/render/layers/LayerBodyPart.java
 */
public final class LayerBodyPart
{
    /**
     * The state-restore contract, kept as a string because two of its three
     * clauses are structural rather than executable on 1.20.4 (see
     * {@link #renderBodyParts}): after rendering <b>each</b> body part the layer
     * restores {@code model.pose} (legacy also {@code model.swingProgress}) and
     * re-runs {@code setRotationAngles}, because a nested morph render clobbers
     * shared model state; after <b>all</b> parts it resets
     * {@code renderer.current} and re-runs {@code renderer.setupModel(...)},
     * because {@code ModelCustom.render} nulls {@code current}. Skipping the
     * final restore breaks every later layer for multi-part morphs.
     */
    public static final String LAYER_STATE_RESTORE_NOTE =
        "restore swingProgress+pose+setRotationAngles per part; restore renderer.current+setupModel after all parts";

    private LayerBodyPart()
    {
    }

    /**
     * Draw every body part of {@code morph} onto {@code model}'s limbs.
     *
     * <p>Ported structure, including the two things it deliberately does not do:
     * a part naming a limb the model does not have is <b>skipped entirely</b>
     * (legacy: "no point to render here since if a limb wasn't found then it
     * wouldn't be transformed correctly"), and the per-part state restore runs
     * even for those skipped parts.</p>
     *
     * <p><b>Restore on 1.20.4.</b> Legacy snapshotted {@code model.pose} and
     * {@code model.swingProgress} — two mutable fields on the shared model — and
     * re-ran {@code setRotationAngles(limbSwing, …, target)} with the layer's
     * own arguments. Here {@code swingProgress} is not model state at all: it
     * lives on the {@link PoseContext} the caller passes in, so re-running
     * {@link ModelCustom#setRotationAngles(PoseContext)} with that same context
     * <i>is</i> the swing restore. Only {@code pose} still needs an explicit
     * snapshot. A null context means the caller has no pose pass to redo (the
     * static GUI preview), and the re-run is skipped.</p>
     *
     * @param target the entity wearing {@code morph}
     * @param parent the morph owning the parts — the animation source for
     *        the per-part transform tween
     * @param parts the parent's body part manager
     * @param model the parent model whose limbs the parts attach to
     * @param context the pose input to re-apply after each part, or null
     * @param partialTicks render partial ticks
     * @param scale the model scale {@code postRender} works in (legacy 1/16)
     */
    public static void renderBodyParts(LivingEntity target, AbstractMorph parent, BodyPartManager parts, ModelCustom model, PoseContext context, float partialTicks, float scale)
    {
        if (parent == null || parts == null || model == null || model.limbs == null)
        {
            return;
        }

        MorphRenderContext frame = MorphRenderContext.current();

        if (frame == null || frame.matrices == null)
        {
            return;
        }

        MatrixStack matrices = frame.matrices;
        ModelPose pose = model.pose;

        for (BodyPart part : parts.parts)
        {
            for (ModelCustomRenderer limb : model.limbs)
            {
                if (limb.limb != null && limb.limb.name.equals(part.limb))
                {
                    matrices.push();

                    try
                    {
                        limb.postRender(matrices, scale);
                        BodyPartRenderer.render(part, parent, target, partialTicks);
                    }
                    finally
                    {
                        matrices.pop();
                    }

                    break;
                }
            }

            /* Restore back properties — for found and not-found limbs alike. */
            model.pose = pose;

            if (context != null)
            {
                model.setRotationAngles(context);
            }
        }
    }

    /**
     * Mutable snapshot of the six host-entity yaw fields the rebase touches.
     * Yarn 1.20.4 names in parentheses:
     * <ul>
     *   <li>{@code yaw} — legacy {@code rotationYaw} (getYaw/setYaw)</li>
     *   <li>{@code prevYaw} — legacy {@code prevRotationYaw}</li>
     *   <li>{@code bodyYaw} — legacy {@code renderYawOffset}</li>
     *   <li>{@code prevBodyYaw} — legacy {@code prevRenderYawOffset}</li>
     *   <li>{@code headYaw} — legacy {@code rotationYawHead}</li>
     *   <li>{@code prevHeadYaw} — legacy {@code prevRotationYawHead}</li>
     * </ul>
     */
    public static final class YawState
    {
        public float yaw;
        public float prevYaw;
        public float bodyYaw;
        public float prevBodyYaw;
        public float headYaw;
        public float prevHeadYaw;

        public YawState(float yaw, float prevYaw, float bodyYaw, float prevBodyYaw, float headYaw, float prevHeadYaw)
        {
            this.yaw = yaw;
            this.prevYaw = prevYaw;
            this.bodyYaw = bodyYaw;
            this.prevBodyYaw = prevBodyYaw;
            this.headYaw = headYaw;
            this.prevHeadYaw = prevHeadYaw;
        }
    }

    /**
     * Rebase the entity yaw so the body faces forward, run the nested render,
     * then restore — <b>ported verbatim, including the absence of a
     * try/finally</b>. Legacy {@code BodyPart.render} restores the yaw fields on
     * the normal path only; if {@code nestedRender} throws, the fields stay in
     * their rebased (zeroed-body-yaw) state. This is not a bug to "fix": the
     * safety net is {@code MorphUtils.render} swallowing morph render errors
     * (P80.2) so the throw never escapes to here in practice. Preserving the
     * missing finally keeps the exact legacy behavior.
     *
     * <p>The rebase itself: subtract the body yaw out of the two yaw fields and
     * the two head-yaw fields, then zero both body-yaw fields — so the nested
     * morph is drawn in body-local space.</p>
     *
     * @param state the entity yaw fields, mutated in place
     * @param nestedRender the {@code MorphUtils.render(...)} call (+ axis draw)
     */
    public static void renderWithYawRebase(YawState state, Runnable nestedRender)
    {
        float yaw = state.yaw;
        float prevYaw = state.prevYaw;
        float bodyYaw = state.bodyYaw;
        float prevBodyYaw = state.prevBodyYaw;
        float headYaw = state.headYaw;
        float prevHeadYaw = state.prevHeadYaw;

        state.yaw = state.yaw - state.bodyYaw;
        state.prevYaw = state.prevYaw - state.prevBodyYaw;
        state.headYaw = state.headYaw - state.bodyYaw;
        state.prevHeadYaw = state.prevHeadYaw - state.prevBodyYaw;
        state.bodyYaw = state.prevBodyYaw = 0;

        /* No try/finally — legacy parity (see javadoc). */
        nestedRender.run();

        state.yaw = yaw;
        state.prevYaw = prevYaw;
        state.bodyYaw = bodyYaw;
        state.prevBodyYaw = prevBodyYaw;
        state.headYaw = headYaw;
        state.prevHeadYaw = prevHeadYaw;
    }
}
