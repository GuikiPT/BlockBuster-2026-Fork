package mchorse.blockbuster_pack.client.render;

import mchorse.blockbuster.client.RenderingHandler;
import mchorse.blockbuster.client.particles.emitter.BedrockEmitter;
import mchorse.blockbuster_pack.morphs.SnowstormClient;
import mchorse.blockbuster_pack.morphs.SnowstormClient.State;
import mchorse.blockbuster_pack.morphs.SnowstormMorph;
import mchorse.mclib.client.gui.framework.elements.GuiModelRenderer;
import mchorse.mclib.client.render.RenderingUtilsClient;
import mchorse.mclib.utils.Interpolations;
import mchorse.mclib.utils.MatrixUtils;
import mchorse.metamorph.client.MorphRenderUtils;
import mchorse.metamorph.client.render.IMorphRenderer;
import mchorse.metamorph.client.render.MorphRenderContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

import javax.vecmath.Matrix4f;
import javax.vecmath.Vector4f;

/**
 * Client render body for {@link SnowstormMorph} (roadmap P54/P164).
 *
 * <p>Like the other emitter-shaped morphs, this one draws no geometry itself:
 * the Bedrock engine owns the particles, and a "render" is really <b>a frame's
 * worth of state handed to a live emitter</b> — where it is in the world, how it
 * is rotated and scaled, and a registration with
 * {@link RenderingHandler#addEmitter} so the engine draws and ticks it. Skipping
 * a frame is therefore not a missing puff of smoke but an emitter that stops
 * following its morph, which is why the fallback branch below matters as much as
 * the matrix one.</p>
 *
 * <p>The body was already ported (it lived on {@code SnowstormClient}, reached
 * through a {@code render} override on the morph). Moving it here is what puts
 * the morph on the P54 dispatcher — an override would have bypassed it, so the
 * pipeline's {@code canDraw} gate answered "no renderer" and a player morphed
 * into a snowstorm kept the vanilla player <i>and</i> got the emitter. Two things
 * change with the move:</p>
 * <ul>
 *   <li>the model-view is the frame's own matrix rather than the
 *       {@code SnowstormClient.modelView} static that nothing ever set — legacy
 *       read GL, and the {@link MorphRenderContext} is where that lives now, so
 *       the body-part branch is reachable for the first time;</li>
 *   <li>the guard regains its {@code isRenderingOnScreen} half (the flag landed
 *       with {@link MorphRenderUtils}), so a picker cell no longer drags the
 *       world emitter to wherever the GUI frame happens to sit.</li>
 * </ul>
 *
 * Legacy source: blockbuster-1.12/.../blockbuster_pack/morphs/SnowstormMorph.java (render/renderOnScreen)
 */
public class SnowstormMorphRenderer implements IMorphRenderer<SnowstormMorph>
{
    /* --------------------------------------------------------------------- */
    /* In-world                                                              */
    /* --------------------------------------------------------------------- */

    @Override
    public void render(SnowstormMorph morph, LivingEntity target, double x, double y, double z, float entityYaw, float partialTicks, MorphRenderContext context)
    {
        SnowstormClient client = client();

        if (client == null || target == null)
        {
            return;
        }

        if (GuiModelRenderer.isRendering() || MorphRenderUtils.isRenderingOnScreen)
        {
            return;
        }

        State state = client.state(morph);

        client.reloadIfStale(morph, state);

        BedrockEmitter emitter = client.emitter(morph);
        MatrixStack matrices = context == null ? null : context.matrices;
        Matrix4f captured = MatrixUtils.matrix;

        if (captured != null && matrices != null)
        {
            /* COLUMN major: the basis axes of a model→camera matrix are its
             * columns. (ParticleMorph reads the rows instead — that transpose is
             * a legacy quirk of its own, not a disagreement to reconcile.) */
            MatrixUtils.Transformation transformation = MatrixUtils.extractTransformations(
                captured, RenderingUtilsClient.toVecmath(matrices.peek().getPositionMatrix()), MatrixUtils.MatrixMajor.COLUMN);

            Vector4f zero = SnowstormMorph.calculateGlobal(transformation.translation, target, 0, 0, 0, partialTicks);

            /* Once per tick, not once per frame: prev-state is what the engine
             * interpolates against, so advancing it every frame would collapse
             * the interpolation to nothing. */
            if (state.lastAge != morph.age)
            {
                emitter.prevRotation.set(emitter.rotation);
                emitter.prevGlobal.set(emitter.lastGlobal);
            }

            state.lastAge = morph.age;

            emitter.lastGlobal.x = zero.x;
            emitter.lastGlobal.y = zero.y;
            emitter.lastGlobal.z = zero.z;

            emitter.translation.set(morph.cachedTranslation);

            emitter.rotation.set(transformation.getRotation3f());

            emitter.scale[0] = transformation.scale.m00;
            emitter.scale[1] = transformation.scale.m11;
            emitter.scale[2] = transformation.scale.m22;
        }
        else
        {
            /* No parent capture — the morph is worn directly rather than hung
             * off a body part. Legacy anchored to the entity itself and left the
             * rotation identity (and did not touch prev-state here). */
            emitter.lastGlobal.x = Interpolations.lerp(target.prevX, target.getX(), partialTicks);
            emitter.lastGlobal.y = Interpolations.lerp(target.prevY, target.getY(), partialTicks);
            emitter.lastGlobal.z = Interpolations.lerp(target.prevZ, target.getZ(), partialTicks);
            emitter.rotation.setIdentity();
        }

        client.mirrorLastEmitters(state, emitter);

        RenderingHandler.addEmitter(emitter, target);
        client.update(morph);
    }

    /* --------------------------------------------------------------------- */
    /* On screen (GUI)                                                       */
    /* --------------------------------------------------------------------- */

    @Override
    public void renderOnScreen(SnowstormMorph morph, PlayerEntity player, int x, int y, float scale, float alpha)
    {
        SnowstormClient client = client();

        if (client == null)
        {
            return;
        }

        client.emitter(morph).renderOnScreen(x, y, scale);
    }

    /**
     * The installed seam, or null when it has not been installed (headless).
     * Every emitter operation goes through it because the per-morph state it
     * owns — the live emitter, the retired ones, the reload stamp — has to be
     * shared with the data path.
     */
    private static SnowstormClient client()
    {
        return SnowstormMorph.CLIENT instanceof SnowstormClient ? (SnowstormClient) SnowstormMorph.CLIENT : null;
    }
}
