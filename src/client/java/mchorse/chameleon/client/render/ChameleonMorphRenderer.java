package mchorse.chameleon.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.chameleon.lib.ChameleonAnimator;
import mchorse.chameleon.lib.ChameleonModel;
import mchorse.chameleon.lib.data.model.Model;
import mchorse.chameleon.lib.render.ChameleonRenderer;
import mchorse.chameleon.metamorph.ChameleonMorph;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.metamorph.bodypart.BodyPart;
import mchorse.metamorph.bodypart.BodyPartRenderer;
import mchorse.metamorph.client.render.IMorphRenderer;
import mchorse.metamorph.client.render.MorphRenderContext;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import mchorse.mclib.utils.Interpolations;
import mchorse.mclib.utils.MatrixUtils;
import mchorse.mclib.client.render.RenderingUtilsClient;

/**
 * Client render body for {@link ChameleonMorph} (the Chameleon port's half of
 * roadmap P54).
 *
 * <p>Ports the two {@code @SideOnly(CLIENT)} bodies legacy declared on
 * {@code ChameleonMorph} — {@code render} (in-world) and {@code renderOnScreen}
 * (GUI) — plus their shared private {@code renderModel}. See
 * {@link ChameleonMorph}'s javadoc for why they cannot live on the morph.</p>
 *
 * <p><b>Draw order is load-bearing</b> and matches legacy exactly: reset the
 * bone tree to its rest pose, let the animator apply the running actions, then
 * stamp the morph's pose over the result, and only then walk the tree emitting
 * geometry. Pose-before-animation would make {@code fixed} bones meaningless.</p>
 *
 * <p>Port notes:</p>
 * <ul>
 *   <li>Legacy's {@code RenderLightmap.set(target, partialTicks)} hurt flash is
 *       1.20.4's <b>overlay</b> UV, so it becomes
 *       {@link LivingEntityRenderer#getOverlay}. McLib's {@code RenderLightmap}
 *       has no port (it poked the fixed-function lightmap directly).</li>
 *   <li>The blend/cull/alpha GL state legacy set by hand is the render layer
 *       ({@code RenderLayer.getEntityTranslucent} is translucent + no culling,
 *       which is exactly what it configured).</li>
 *   <li>A morph with no skin draws <b>nothing</b> rather than drawing with
 *       whatever texture happened to be bound — there is no such thing as an
 *       ambient bound texture once it is part of the render layer. Same
 *       resolution {@code CustomMorphRenderer.renderHand} documents.</li>
 * </ul>
 *
 * Legacy source: chameleon/.../metamorph/ChameleonMorph.java (render/renderOnScreen/renderModel)
 */
public class ChameleonMorphRenderer implements IMorphRenderer<ChameleonMorph>
{
    /**
     * Key/fill pair for the GUI preview, in <b>model</b> space (BBS
     * {@code UIModelRenderer}'s): both tilted up, one from the front and one
     * from behind. Rotated into the preview chain's space at draw time — see
     * {@link #renderOnScreen}.
     */
    private static final Vector3f GUI_LIGHT_0 = new Vector3f(0F, 0.85F, -1F).normalize();
    private static final Vector3f GUI_LIGHT_1 = new Vector3f(0F, 0.85F, 1F).normalize();

    /* --------------------------------------------------------------------- */
    /* In-world                                                              */
    /* --------------------------------------------------------------------- */

    @Override
    public void render(ChameleonMorph morph, LivingEntity entity, double x, double y, double z, float entityYaw, float partialTicks, MorphRenderContext context)
    {
        if (context == null || context.matrices == null || context.consumers == null)
        {
            return;
        }

        float scale = morph.getScale(partialTicks);
        MatrixStack matrices = context.matrices;
        boolean captured = false;

        matrices.push();

        /* Both the pop and the matrix release are in the finally, for the same
         * reason RenderCustomModel's pop is (P278): MorphRenderUtils swallows a
         * throwing morph (the errorRendering latch), so an escape here would
         * leave the frame one level deep — displacing every later draw — and,
         * worse, leave MatrixUtils.matrix permanently captured, which silently
         * breaks the world transform every body part and particle morph derives
         * from it for the rest of the session. Legacy released it inline; on
         * 1.12.2 that was the same latent bug, but there the whole GL stack was
         * ambient and a stale capture was less reachable. */
        try
        {
            matrices.translate(x, y, z);

            captured = MatrixUtils.captureMatrix(
                RenderingUtilsClient.toVecmath(matrices.peek().getPositionMatrix()));

            matrices.scale(scale, scale, scale);

            float bodyYaw = entity == null
                ? 0F
                : Interpolations.lerp(entity.prevBodyYaw, entity.bodyYaw, partialTicks);

            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bodyYaw + 180));

            int overlay = entity == null
                ? OverlayTexture.DEFAULT_UV
                : LivingEntityRenderer.getOverlay(entity, 0F);

            this.renderModel(morph, entity, partialTicks, matrices, context.consumers, context.light, overlay);
        }
        finally
        {
            if (captured)
            {
                MatrixUtils.releaseMatrix();
            }

            matrices.pop();
        }
    }

    /* --------------------------------------------------------------------- */
    /* Shared draw                                                           */
    /* --------------------------------------------------------------------- */

    /**
     * Legacy private {@code ChameleonMorph.renderModel}: pose the model, draw
     * it, then hang the body parts off their bones.
     */
    private void renderModel(ChameleonMorph morph, LivingEntity target, float partialTicks, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay)
    {
        ChameleonModel chameleonModel = morph.getModel();

        if (chameleonModel == null)
        {
            return;
        }

        morph.checkAnimator();

        Model model = chameleonModel.model;

        ChameleonAnimator.resetPose(model);

        if (!chameleonModel.isStatic())
        {
            morph.getAnimator().applyActions(target, model, partialTicks);
        }

        morph.applyPose(model, partialTicks);

        /* Render the model */
        Identifier texture = morph.skin == null ? null : morph.skin.toIdentifier();

        if (texture != null)
        {
            ChameleonRenderer.render(model, matrices,
                consumers.getBuffer(RenderLayer.getEntityTranslucent(texture)), light, overlay);
        }

        /* Render body parts */
        morph.parts.initBodyParts();

        for (BodyPart part : morph.parts.parts)
        {
            matrices.push();

            try
            {
                if (ChameleonRenderer.postRender(model, part.limb, matrices))
                {
                    BodyPartRenderer.render(part, morph, target, partialTicks);
                }
            }
            finally
            {
                matrices.pop();
            }
        }
    }

    /* --------------------------------------------------------------------- */
    /* On screen (GUI)                                                       */
    /* --------------------------------------------------------------------- */

    /**
     * <p><b>{@code alpha} is deliberately unread</b> — legacy's
     * {@code renderOnScreen} took it and never used it either. A Chameleon
     * morph's transparency comes from its per-bone colour, not from the
     * picker's fade.</p>
     *
     * <p>The transform chain is legacy's, op for op:</p>
     *
     * <pre>
     * translate(x, y, 0)      scale(scale, scale, scale)
     * rotate(45, -1, 0, 0)    rotate(135, 0, -1, 0)    rotate(180, 0, 0, 1)
     * </pre>
     *
     * <p>There is no mirroring {@code scale(-1, -1, 1)} the way Blockbuster's
     * custom-model preview has one — the Z-180 turn does that job here.</p>
     */
    @Override
    public void renderOnScreen(ChameleonMorph morph, PlayerEntity player, int x, int y, float scale, float alpha)
    {
        DrawContext dc = GuiDraw.getDrawContext();

        if (dc == null)
        {
            return;
        }

        scale *= morph.scaleGui;

        MatrixStack matrices = dc.getMatrices();
        VertexConsumerProvider.Immediate consumers = dc.getVertexConsumers();

        matrices.push();

        try
        {
            matrices.translate(x, y, 0);
            matrices.scale(scale, scale, scale);
            matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(45.0F));
            matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees(135.0F));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0F));

            /* Lighting. MorphRendererRegistry's dispatcher arms the flat GUI
             * preset for every on-screen morph, which suits the preview chains
             * whose normals are mirrored; this one's are not (the scale above is
             * uniform and positive, so it never touches the normal matrix), and
             * under the flat preset its side face burns to 1.0 while the top —
             * the face you mostly see — drops to 0.73.
             *
             * Rather than hand-fitting another light pair to this chain, give
             * setupLevelDiffuseLighting the pair in *model* space (BBS
             * UIModelRenderer's: up, one from the front, one from behind) plus
             * the normal matrix the chain above just built, and let it rotate
             * them. That yields the plain top-lit reading — top 1.00, front and
             * back 0.86, sides 0.40 — and, being symmetric in Z, it holds
             * whichever way a model is authored to face.
             *
             * Read off the stack rather than rebuilt from the same angles on
             * purpose: edit the chain and the lighting follows instead of
             * silently drifting out of sync. The dispatcher restores the ambient
             * GUI preset afterwards. */
            RenderSystem.setupLevelDiffuseLighting(GUI_LIGHT_0, GUI_LIGHT_1,
                new Matrix4f(matrices.peek().getNormalMatrix()));

            /* The parts draw into the DrawContext's matrices, so a context frame
             * has to be installed around them (the world path already runs
             * inside one). Legacy's RenderHelper.enableStandardItemLighting is
             * the GUI shader's own diffuse setup on 1.20.4; full-bright light is
             * what every other in-GUI morph preview uses. */
            MorphRenderContext.push(matrices, consumers, MorphRenderContext.FULL_BRIGHT, OverlayTexture.DEFAULT_UV, 0F);

            try
            {
                this.renderModel(morph, player, 0F, matrices, consumers,
                    MorphRenderContext.FULL_BRIGHT, OverlayTexture.DEFAULT_UV);
            }
            finally
            {
                MorphRenderContext.pop();
            }

            consumers.draw();
        }
        finally
        {
            matrices.pop();
        }
    }
}
