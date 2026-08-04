package mchorse.metamorph.client.render;

import mchorse.metamorph.Metamorph;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;

/**
 * Player morph renderer delegate (roadmap P54).
 *
 * <p>Modern replacement for the legacy {@code RenderSubPlayer} skinMap hack +
 * {@code Morphing.renderPlayer}. The {@code PlayerEntityRenderer.render} mixin
 * delegates here; when the player has a current morph (or is mid morph-
 * transition), the morph is drawn instead of the vanilla player and vanilla
 * rendering is cancelled.</p>
 *
 * <p><b>Seams.</b> The per-player morph state lives on the P52 morphing
 * component, which is not in this tree; it is supplied through the {@link
 * #provider} functional seam (null → the mixin is inert and vanilla rendering
 * proceeds). The actual morph draw ({@code MorphUtils.render} in legacy) is the
 * S6 client render pipeline, also a seam — until it lands nothing is drawn, so
 * {@link #shouldReplace} still governs the decision (unit-tested) but {@link
 * #renderPlayer} does not cancel vanilla when there is no draw target.</p>
 *
 * <p>The two-phase transition animation math ({@link #newMorphTransform},
 * {@link #previousMorphTransform}) is copied verbatim from {@code
 * Morphing.renderPlayer} and is unit-tested.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/capabilities/morphing/Morphing.java (renderPlayer)
 */
public final class MorphRenderer
{
    /** Idle sentinel for {@link MorphState#animation}. */
    public static final int IDLE = -1;

    /**
     * Seam supplying a player's morph state. Wired by P52 (the morphing
     * component). Null → no morph rendering (inert).
     */
    public static MorphProvider provider;

    /**
     * Seam performing the actual morph draw. Wired by the S6 client render
     * pipeline. Null → nothing to draw.
     */
    public static MorphDrawer drawer;

    private MorphRenderer()
    {}

    public interface MorphProvider
    {
        /** Morph state for the player, or null when there is no component. */
        MorphState get(PlayerEntity player);
    }

    public interface MorphDrawer
    {
        /**
         * Draw a morph for the player at the current matrix position. The
         * matrices already carry the transition transform.
         *
         * <p>Returns whether anything was actually drawn. Legacy always had a
         * render body for every morph type, so {@code Morphing.renderPlayer}
         * could cancel vanilla unconditionally; in the port a morph type whose
         * client renderer has not landed yet would otherwise turn the player
         * invisible. A {@code false} return keeps the vanilla player — a
         * deliberate deviation, and the only one, scoped to unported render
         * bodies.</p>
         */
        boolean draw(AbstractMorph morph, PlayerEntity player, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, float tickDelta);
    }

    /**
     * Immutable snapshot of the player's morphing state used by the renderer.
     */
    public static final class MorphState
    {
        public final AbstractMorph morph;
        public final AbstractMorph previousMorph;
        public final int animation;

        public MorphState(AbstractMorph morph, AbstractMorph previousMorph, int animation)
        {
            this.morph = morph;
            this.previousMorph = previousMorph;
            this.animation = animation;
        }
    }

    /**
     * Transition transform for a single morph render pass. Mirrors the legacy
     * {@code GlStateManager} translate/rotate/scale ops so it is independently
     * testable.
     */
    public static final class Transform
    {
        public final float offsetY;
        /** Rotation about the X axis, in degrees. */
        public final float rotationDegX;
        public final float scale;
        /** Legacy only applied rotation+scale when {@code anim >= 0}. */
        public final boolean applyRotationScale;

        public Transform(float offsetY, float rotationDegX, float scale, boolean applyRotationScale)
        {
            this.offsetY = offsetY;
            this.rotationDegX = rotationDegX;
            this.scale = scale;
            this.applyRotationScale = applyRotationScale;
        }
    }

    /**
     * Whether the morph animation is currently playing. False when {@code
     * disable_morph_animation} is set (legacy {@code Morphing.isAnimating}).
     */
    public static boolean isAnimating(MorphState state)
    {
        if (state == null || Metamorph.disableMorphAnimation.get())
        {
            return false;
        }

        return state.animation != IDLE;
    }

    /**
     * The pure decision from {@code Morphing.renderPlayer}: whether the morph
     * render replaces (and cancels) the vanilla player render. Spectators never
     * render morphs; an empty morph outside the transition window falls
     * through to vanilla.
     */
    public static boolean shouldReplace(PlayerEntity player, MorphState state)
    {
        return shouldReplace(player != null && player.isSpectator(), state);
    }

    /**
     * Pure core of {@link #shouldReplace(PlayerEntity, MorphState)} (extracted
     * for headless testing so the spectator flag can be supplied directly).
     */
    public static boolean shouldReplace(boolean spectator, MorphState state)
    {
        if (state == null || spectator)
        {
            return false;
        }

        boolean animating = isAnimating(state);
        boolean morphEmpty = state.morph == null;

        if (morphEmpty && !animating)
        {
            return false;
        }

        if ((morphEmpty && state.animation <= 10) || (state.previousMorph == null && state.animation > 10))
        {
            return false;
        }

        return true;
    }

    /**
     * Transform for the incoming ("new") morph, rendered in the second half of
     * the transition ({@code animation <= 10}). Copied verbatim from legacy.
     */
    public static Transform newMorphTransform(int animation, float partialTick)
    {
        float anim = (animation - partialTick) / 10.0F;
        boolean apply = anim >= 0;
        float offset = apply ? -anim * anim * 2F : 0F;
        float rotation = apply ? anim * -90.0F : 0F;
        float scale = apply ? 1F - anim : 1F;

        return new Transform(offset, rotation, scale, apply);
    }

    /**
     * Transform for the outgoing ("previous") morph, rendered in the first half
     * of the transition ({@code animation > 10}). Copied verbatim from legacy.
     */
    public static Transform previousMorphTransform(int animation, float partialTick)
    {
        float anim = (animation - 10 - partialTick) / 10.0F;
        boolean apply = anim >= 0;
        float offset = apply ? (1F - anim) : 0F;
        float rotation = apply ? (1F - anim) * 90.0F : 0F;
        float scale = apply ? anim : 1F;

        return new Transform(offset, rotation, scale, apply);
    }

    /* --------------------------------------------------------------------- */
    /* Vanilla-model fade (legacy RenderingHandler.onPlayerRender's            */
    /* `else if (capability.isAnimating())` branch)                            */
    /* --------------------------------------------------------------------- */

    /**
     * "No fade this frame" sentinel. {@code NaN} rather than a magic number so
     * a caller that forgets the check gets a visibly wrong result instead of a
     * plausible one.
     */
    public static final float NO_FADE = Float.NaN;

    /**
     * The fade alpha for the vanilla player model in the frame currently being
     * drawn, or {@link #NO_FADE}. Set from the player-render mixin's HEAD hook
     * and consumed by {@code LivingEntityRendererFadeMixin}, which is the modern
     * stand-in for legacy's ambient {@code GlStateManager.color(1, 1, 1, anim)}.
     */
    private static float vanillaFade = NO_FADE;

    /**
     * Legacy {@code onPlayerRender}'s fade alpha — the <b>demorph</b> half of
     * the transition, where {@code renderPlayer} declined to draw a morph
     * because there is none to draw and the vanilla player is showing instead.
     * Copied verbatim, including the {@code anim = 0} initialiser that no
     * reachable input can leave in place (the two branches are exactly the two
     * cases {@link #shouldReplace} rejects while animating).
     */
    public static float vanillaFadeAlpha(MorphState state, float partialTick)
    {
        if (!isAnimating(state))
        {
            return NO_FADE;
        }

        float anim = 0;

        if (state.morph == null && state.animation <= 10)
        {
            anim = 1 - (state.animation - partialTick) / 10.0F;
        }
        else if (state.previousMorph == null && state.animation > 10)
        {
            anim = (state.animation - 10 - partialTick) / 10.0F;
        }

        return anim;
    }

    /**
     * Mixin entry: the fade for this player this frame, or {@link #NO_FADE}.
     *
     * <p>Legacy applied the fade in the {@code else} of {@code
     * capability.renderPlayer(...)}, so it is gated on {@link #shouldReplace}
     * — <b>not</b> on what {@link #renderPlayer} returns. The port's extra
     * "no drawer / unported morph type → keep the vanilla player" deviation
     * also makes {@code renderPlayer} return false, and legacy would have
     * cancelled in those cases, so a fade there would be an invention.</p>
     */
    public static float vanillaFade(PlayerEntity player, float tickDelta)
    {
        if (provider == null)
        {
            return NO_FADE;
        }

        MorphState state = provider.get(player);

        if (shouldReplace(player, state))
        {
            return NO_FADE;
        }

        return vanillaFadeAlpha(state, tickDelta);
    }

    /** Arm the fade for the frame the player-render mixin is about to run. */
    public static void beginVanillaFade(float alpha)
    {
        vanillaFade = alpha;
    }

    /** Disarm it. Called from the mixin's RETURN hook. */
    public static void endVanillaFade()
    {
        vanillaFade = NO_FADE;
    }

    /** The armed fade alpha, or {@link #NO_FADE}. */
    public static float currentVanillaFade()
    {
        return vanillaFade;
    }

    /** Whether a fade is armed (the render-layer swap needs to know). */
    public static boolean isVanillaFading()
    {
        return !Float.isNaN(vanillaFade);
    }

    /**
     * The alpha the vanilla model render should use, given the one vanilla
     * picked. Pure so the precedence rule is testable without a mixin.
     *
     * <p>Vanilla only ever passes something other than {@code 1.0F} for the
     * invisible-but-visible-to-you player ({@code 0.15F}), and on 1.12.2 that
     * assignment happened <b>after</b> the {@code Pre} event's ambient colour
     * and therefore won. Same order here.</p>
     */
    public static float fadeAlphaOver(float vanillaAlpha)
    {
        if (!isVanillaFading() || vanillaAlpha != 1.0F)
        {
            return vanillaAlpha;
        }

        return vanillaFade;
    }

    /**
     * Delegate for the {@code PlayerEntityRenderer.render} mixin. Returns
     * whether vanilla rendering must be cancelled.
     */
    public static boolean renderPlayer(PlayerEntity player, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light)
    {
        if (provider == null)
        {
            return false;
        }

        MorphState state = provider.get(player);

        if (!shouldReplace(player, state))
        {
            return false;
        }

        /* Nothing can be drawn until the S6 render pipeline wires the drawer;
         * do not cancel vanilla in that case (total: no invisible players). */
        if (drawer == null)
        {
            return false;
        }

        boolean animating = isAnimating(state);
        boolean drawn = false;

        matrices.push();

        try
        {
            undoVanillaSneakOffset(matrices, player);

            if (!animating)
            {
                return drawer.draw(state.morph, player, matrices, vertexConsumers, light, tickDelta);
            }

            if (state.animation <= 10)
            {
                applyTransform(matrices, newMorphTransform(state.animation, tickDelta));
                drawn = drawer.draw(state.morph, player, matrices, vertexConsumers, light, tickDelta);
            }
            else if (state.previousMorph != null)
            {
                applyTransform(matrices, previousMorphTransform(state.animation, tickDelta));
                drawn = drawer.draw(state.previousMorph, player, matrices, vertexConsumers, light, tickDelta);
            }
        }
        finally
        {
            matrices.pop();
        }

        return drawn;
    }

    /**
     * The Y offset {@code PlayerEntityRenderer.getPositionOffset} applies to a
     * crouching player: {@code isInSneakingPose() ? new Vec3d(0, -0.125, 0) :
     * super} (javap, 1.20.4).
     */
    private static final double VANILLA_SNEAK_OFFSET = -0.125D;

    /**
     * Take vanilla's crouch position offset back off the stack for a morph draw.
     *
     * <p><b>Why a morph must not have it.</b> 1.12.2 applied the same constant
     * <i>inside</i> {@code RenderPlayer.doRender} ({@code d0 = y - 0.125D}), and
     * legacy's {@code RenderSubPlayer} returned before that line whenever the
     * morph took over — so a morphed player never received it. 1.20.4 moved the
     * constant out into {@code PlayerEntityRenderer.getPositionOffset}, which
     * {@code EntityRenderDispatcher.render} folds into the matrix <b>before</b>
     * it calls {@code PlayerEntityRenderer.render} — that is, before the mixin
     * that hands the draw to us. Every morph therefore inherited a two-pixel
     * drop while sneaking that legacy never had, sinking a model's feet through
     * the floor.</p>
     *
     * <p><b>Why it is undone here rather than suppressed at the source.</b>
     * Vanilla still needs the offset on the frames where the morph does
     * <i>not</i> take over — an unported morph type falls back to the vanilla
     * player, and that player must keep its own crouch offset. Undoing it inside
     * the morph branch scopes the correction to exactly the draws that replace
     * vanilla.</p>
     *
     * <p><b>The one-tick lag comes with it.</b> The gate vanilla uses,
     * {@code isInSneakingPose()}, is {@code ClientPlayerEntity.inSneakingPose},
     * recomputed in {@code tickMovement} <i>before</i> that method's
     * {@code input.tick(…)} call — so it trails the sneak key by a tick, while
     * the pose Blockbuster picks ({@code EntityUtils.getPose} →
     * {@code isSneaking()} → {@code input.sneaking}) is current. Leaving the
     * offset in place made the model snap into the sneaking pose and then drop
     * a tick later, which is the visible "not instant" switch. Removing it
     * removes the second step.</p>
     */
    private static void undoVanillaSneakOffset(MatrixStack matrices, PlayerEntity player)
    {
        if (player != null && player.isInSneakingPose())
        {
            matrices.translate(0.0D, -VANILLA_SNEAK_OFFSET, 0.0D);
        }
    }

    /**
     * Whether the vanilla first-person arm should be suppressed (the morph
     * draws its own hand instead). Mirrors {@code RenderSubPlayer.renderHand}:
     * {@code disable_first_person_hand} always keeps the vanilla arm; otherwise
     * a morphed player whose morph {@code renderHand} claims the hand (and is
     * not erroring) suppresses it. Errors flip {@code errorRendering} and keep
     * the vanilla arm.
     */
    public static boolean shouldSuppressVanillaHand(PlayerEntity player, MorphState state, Hand hand)
    {
        if (Metamorph.disableFirstPersonHand.get())
        {
            return false;
        }

        if (state == null || state.morph == null)
        {
            return false;
        }

        AbstractMorph morph = state.morph;

        try
        {
            if (!morph.errorRendering && morph.renderHand(player, hand))
            {
                return true;
            }
        }
        catch (Exception e)
        {
            morph.errorRendering = true;
        }

        return false;
    }

    /**
     * Mixin entry for the first-person arm. Returns whether the vanilla arm
     * render must be cancelled. Gated on the draw seam so no arm goes missing
     * before the S6 render pipeline is wired.
     *
     * <p>The legacy {@code renderHand} signature carries no matrices — 1.12.2's
     * arm drew straight into the ambient GL state the hand renderer had set up.
     * So the caller's frame is put on the {@link MorphRenderContext} stack for
     * the duration of the call, the same way {@code drawEntity} does it for the
     * in-world draw, and the morph's renderer reads it back from there.</p>
     */
    public static boolean suppressVanillaHand(PlayerEntity player, Hand hand,
        MatrixStack matrices, VertexConsumerProvider consumers, int light, float tickDelta)
    {
        if (provider == null || drawer == null)
        {
            return false;
        }

        MorphRenderContext.push(matrices, consumers, light, OverlayTexture.DEFAULT_UV, tickDelta);

        try
        {
            return shouldSuppressVanillaHand(player, provider.get(player), hand);
        }
        finally
        {
            MorphRenderContext.pop();
        }
    }

    private static void applyTransform(MatrixStack matrices, Transform transform)
    {
        matrices.translate(0.0D, transform.offsetY, 0.0D);

        if (transform.applyRotationScale)
        {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(transform.rotationDegX));
            matrices.scale(transform.scale, transform.scale, transform.scale);
        }
    }
}
