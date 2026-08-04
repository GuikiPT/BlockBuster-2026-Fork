package mchorse.metamorph.client.render;

import mchorse.metamorph.api.morphs.EntityMorph;
import mchorse.metamorph.bodypart.BodyPart;
import mchorse.metamorph.bodypart.BodyPartRenderer;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;

import java.util.List;

/**
 * The entity-morph body-part draw (roadmap P80.2) — the port of legacy
 * {@code EntityMorph.renderBodyParts}.
 *
 * <p>Legacy body, one for one:</p>
 * <pre>
 * for (BodyPart part : this.parts.parts)
 *     for (Map.Entry&lt;String, ModelRenderer&gt; entry : this.limbs.entrySet())
 *         if (entry.getKey().equals(part.limb)) {
 *             pushMatrix(); entry.getValue().postRender(1/16F);
 *             part.render(this, target, partialTicks); popMatrix(); break; }
 * </pre>
 *
 * <p>The {@code limbs} map came from {@code setupLimbs}, which reflected over the
 * {@code ModelBase}'s fields. That reflection cannot be ported — 1.20.4 field
 * names are obfuscated in a production jar — so the lookup goes through
 * {@link EntityMorphLimbParts} instead, which addresses limbs by the stable yarn
 * part names that {@code EntityMorphLimbs} translates old saved strings into.
 * {@code ModelRenderer.postRender(1/16F)} is {@link ModelPart#rotate(MatrixStack)}
 * verbatim: pivot over sixteen, then Z-Y-X rotations.</p>
 *
 * <p>The one structural addition is the ancestor walk. 1.12's vanilla models were
 * flat, so one {@code postRender} was the whole transform; on 1.20.4 a limb can
 * be nested ({@code left_wing} under {@code body}) and the path has to be applied
 * from the root down.</p>
 */
public final class EntityMorphBodyParts
{
    private EntityMorphBodyParts()
    {}

    /**
     * Draw every body part of {@code morph} onto {@code model}'s limbs, in the
     * frame the caller has already moved to the model's own space (a
     * {@code FeatureRenderer} frame, which is exactly where legacy's
     * {@code LayerRenderer} ran).
     *
     * @param morph the entity morph owning the parts — also the animation source
     * @param target the entity being drawn (the morph's dummy), which is what a
     *        {@code useTarget} part animates against
     * @param model the vanilla model whose limbs the parts name
     * @param partialTicks legacy hard-codes {@code 1F} at the call site; see
     *        {@link LayerBodyPartFeature}
     */
    public static void render(EntityMorph morph, LivingEntity target, EntityModel<?> model, MatrixStack matrices, VertexConsumerProvider consumers, int light, float partialTicks)
    {
        if (morph == null || model == null || matrices == null || consumers == null)
        {
            return;
        }

        List<BodyPart> parts = morph.parts.parts;

        if (parts.isEmpty())
        {
            return;
        }

        int overlay = target == null ? OverlayTexture.DEFAULT_UV : LivingEntityRenderer.getOverlay(target, 0F);

        matrices.push();
        MorphRenderContext.push(matrices, consumers, light, overlay, partialTicks);

        try
        {
            for (BodyPart part : parts)
            {
                List<ModelPart> path = EntityMorphLimbParts.path(model, part.limb);

                /* Legacy: "no point to render here since if a limb wasn't found
                 * then it wouldn't be transformed correctly". */
                if (path == null)
                {
                    continue;
                }

                matrices.push();

                try
                {
                    for (int i = 0, c = path.size(); i < c; i++)
                    {
                        path.get(i).rotate(matrices);
                    }

                    BodyPartRenderer.render(part, morph, target, partialTicks);
                }
                finally
                {
                    matrices.pop();
                }
            }
        }
        finally
        {
            MorphRenderContext.pop();
            matrices.pop();
        }
    }
}
