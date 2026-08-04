package mchorse.metamorph.client.render;

import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;

/**
 * Client-side render body for one morph class (roadmap P54).
 *
 * <p>This is the half of every legacy morph that was {@code @SideOnly(CLIENT)}:
 * {@code render} (in-world) and {@code renderOnScreen} (GUI). It is a separate
 * class per morph type rather than an override on the morph, because the morph
 * classes live in the common source set — see
 * {@link AbstractMorph.IRenderDispatcher}.</p>
 *
 * @param <T> the morph class this renderer is registered for
 */
public interface IMorphRenderer<T extends AbstractMorph>
{
    /**
     * In-world draw. {@code x/y/z} are the legacy render offsets (relative to
     * the interpolated entity position, which the caller has already applied to
     * the context matrices); the render target comes from
     * {@link MorphRenderContext#current()}.
     */
    void render(T morph, LivingEntity entity, double x, double y, double z, float entityYaw, float partialTicks, MorphRenderContext context);

    /**
     * GUI draw at screen pixel {@code (x, y)}. Implementations run inside
     * {@code MorphRenderUtils.renderOnScreen}'s trap, so
     * {@code isRenderingOnScreen} is already set.
     */
    void renderOnScreen(T morph, PlayerEntity player, int x, int y, float scale, float alpha);

    /**
     * The first-person arm, for a player morphed into {@code morph}. Returns
     * legacy's "I claimed this hand" boolean — see
     * {@link AbstractMorph#renderHand}.
     *
     * <p>The render target is the {@link MorphRenderContext} the mixin pushed,
     * as it is for {@link #render}: the legacy signature carries no matrices.</p>
     *
     * <p>Default is legacy's {@code AbstractMorph} body, so a morph type whose
     * renderer has no arm of its own keeps the base behaviour — the hand is
     * claimed (and left blank) exactly when the morph's settings say it has no
     * hands, and otherwise the player's own arm is drawn.</p>
     */
    default boolean renderHand(T morph, PlayerEntity player, Hand hand)
    {
        return morph.claimsHandByDefault();
    }
}
