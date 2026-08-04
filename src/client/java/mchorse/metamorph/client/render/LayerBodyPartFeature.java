package mchorse.metamorph.client.render;

import mchorse.metamorph.api.morphs.EntityMorph;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;

/**
 * The body-part feature layer of a vanilla mob renderer (roadmap P80.2).
 *
 * <p>Port of legacy {@code EntityMorph.LayerBodyPart}, a {@code LayerRenderer}
 * that Metamorph attached to each {@code RenderLivingBase} it ever morphed into
 * (once, cached in {@code EntityMorph.bodyPartMap}) and armed per draw by setting
 * {@code layer.morph}. A {@link FeatureRenderer} is the 1.20.4 counterpart and
 * runs in the same place — inside {@code LivingEntityRenderer.render}'s pushed
 * frame, after the model, with the matrix already in model space — which is why
 * this route was chosen over replicating {@code setupTransforms}/{@code scale}/
 * {@code translate(0, -1.501, 0)} by hand outside the vanilla render.</p>
 *
 * <p><b>Two legacy details kept.</b> The sneak nudge
 * ({@code translate(0, 0.2, 0)} when the wearer is sneaking) and the hard-coded
 * {@code 1F} partial ticks legacy passed to {@code renderBodyParts} — the layer
 * received real partials and threw them away, which pins body-part sub-morph
 * animation to the tick boundary rather than the frame.</p>
 *
 * <p><b>One legacy detail not kept.</b> Legacy never cleared {@code layer.morph}
 * after a draw, so the layer stayed armed on a renderer shared with every real
 * mob of that type — a passing cow could wear a morph's body parts until the next
 * morph draw overwrote the field. {@link EntityMorphBodyPartPass} restores the
 * previous value in a {@code finally} instead. That also makes nesting work: an
 * entity morph worn <i>as</i> a body part of another entity morph arms and
 * disarms the same feature reentrantly.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../api/morphs/EntityMorph.java ({@code LayerBodyPart})
 */
public class LayerBodyPartFeature extends FeatureRenderer<LivingEntity, EntityModel<LivingEntity>>
{
    /** Legacy hard-coded this at the {@code renderBodyParts} call site. */
    public static final float PARTIAL_TICKS = 1F;

    /** Legacy's sneak nudge, applied before the parts are drawn. */
    public static final double SNEAK_OFFSET = 0.2D;

    /**
     * The morph whose parts this layer draws on its next invocation, or null for
     * "this is a plain mob, draw nothing". Written by
     * {@link EntityMorphBodyPartPass} around each morph draw.
     */
    public EntityMorph morph;

    public LayerBodyPartFeature(FeatureRendererContext<LivingEntity, EntityModel<LivingEntity>> context)
    {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, LivingEntity entity, float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch)
    {
        EntityMorph morph = this.morph;

        if (morph == null || matrices == null || vertexConsumers == null)
        {
            return;
        }

        boolean was = EntityMorphBodyPartPass.inPass;

        matrices.push();
        EntityMorphBodyPartPass.inPass = true;

        try
        {
            if (entity != null && entity.isSneaking())
            {
                matrices.translate(0D, SNEAK_OFFSET, 0D);
            }

            EntityMorphBodyParts.render(morph, entity, this.getContextModel(), matrices, vertexConsumers, light, PARTIAL_TICKS);
        }
        finally
        {
            /* Vanilla is walking this renderer's feature list right now; a nested
             * morph must not append to it. See EntityMorphBodyPartPass.inPass. */
            EntityMorphBodyPartPass.inPass = was;
            /* Legacy leaked the sneak translate into whatever layer ran next
             * (fixed-function GL kept it); an unbalanced MatrixStack corrupts
             * the rest of the frame, so it is scoped. */
            matrices.pop();
        }
    }
}
