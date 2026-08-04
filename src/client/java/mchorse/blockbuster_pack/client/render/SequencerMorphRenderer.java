package mchorse.blockbuster_pack.client.render;

import mchorse.blockbuster.utils.mclib.BBIcons;
import mchorse.blockbuster_pack.morphs.SequencerMorph;
import mchorse.blockbuster_pack.morphs.SequencerMorph.FoundMorph;
import mchorse.mclib.utils.Interpolations;
import mchorse.metamorph.api.MorphUtils;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.utils.Animation;
import mchorse.metamorph.api.morphs.utils.IAnimationProvider;
import mchorse.metamorph.client.MorphRenderUtils;
import mchorse.metamorph.client.render.IMorphRenderer;
import mchorse.metamorph.client.render.MorphRenderContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Client render body for {@link SequencerMorph} (roadmap P54/P160).
 *
 * <p>The sequencer draws nothing of its own — it resolves <i>which</i> entry is
 * current at this instant and hands the draw to that entry's morph. What makes
 * it a render body rather than a pass-through is that the resolution itself is
 * driven from the render call: {@code updateClient} advances the automaton at
 * render rate (legacy did the same — a sequencer that is never drawn never
 * advances), the per-loop XYZ offset is applied to the inner draw position, and
 * {@code morphSetDuration} rewrites the inner morph's animation duration and
 * <b>replaces</b> the partial ticks handed down.</p>
 *
 * <p>All of that arithmetic is a verbatim carry-over of the body that lived on
 * {@code SequencerMorph} itself until this phase (it was parked in
 * {@code src/main} because there was no client render seam to hold it); the two
 * things that could not be expressed there and are restored here are the
 * {@link MorphRenderUtils} routing — so a throwing inner morph latches
 * {@code errorRendering} on <b>itself</b>, not on the sequencer — and the empty
 * list's chicken preview icon.</p>
 *
 * <p><b>Timer ownership.</b> {@code screenTimer} is incremented and wrapped at
 * 2000 by the GUI path, and it is deliberately a field on the morph rather than
 * on this renderer: a sequencer in a picker cell keeps animating across a
 * renderer instance's lifetime, and two cells showing the same morph object
 * share the phase, exactly as on 1.12.2.</p>
 *
 * Legacy source: blockbuster-1.12/.../blockbuster_pack/morphs/SequencerMorph.java (render/renderOnScreen)
 */
public class SequencerMorphRenderer implements IMorphRenderer<SequencerMorph>
{
    /* --------------------------------------------------------------------- */
    /* In-world                                                              */
    /* --------------------------------------------------------------------- */

    @Override
    public void render(SequencerMorph morph, LivingEntity entity, double x, double y, double z, float entityYaw, float partialTicks, MorphRenderContext context)
    {
        float progress = morph.timer + partialTicks;

        if (!morph.isPaused())
        {
            morph.updateClient(entity, progress);

            partialTicks = progress - morph.lastDuration;
            partialTicks -= (int) partialTicks;
        }
        else
        {
            partialTicks = 0;
            progress = morph.timer;
        }

        AbstractMorph current = morph.currentMorph.get();

        if (current == null)
        {
            return;
        }

        if (morph.offsetCount > -1)
        {
            int times = morph.loopCount % (morph.offsetCount + 1);
            double baseMul = 0.0625 * (morph.reverse ? -1 : 1);
            double offsetMul = baseMul * times;
            Vec3d offset = new Vec3d(morph.offset[0] * offsetMul, morph.offset[1] * offsetMul, morph.offset[2] * offsetMul);

            if (morph.isFirstMorph && !morph.currentMorph.isEmpty())
            {
                if (morph.currentMorph.get() instanceof IAnimationProvider)
                {
                    Animation anim = ((IAnimationProvider) morph.currentMorph.get()).getAnimation();

                    if (anim.isInProgress())
                    {
                        double lastMul = baseMul * (times - 1);

                        double lerpX = anim.interp.interpolate(morph.offset[0] * lastMul, morph.offset[0] * offsetMul, anim.getFactor(partialTicks));
                        double lerpY = anim.interp.interpolate(morph.offset[1] * lastMul, morph.offset[1] * offsetMul, anim.getFactor(partialTicks));
                        double lerpZ = anim.interp.interpolate(morph.offset[2] * lastMul, morph.offset[2] * offsetMul, anim.getFactor(partialTicks));

                        offset = new Vec3d(lerpX, lerpY, lerpZ);
                    }
                }
            }

            float yaw = Interpolations.lerpYaw(entity.prevBodyYaw, entity.bodyYaw, partialTicks);
            offset = offset.rotateY((float) Math.toRadians(-yaw));

            x += offset.x;
            y += offset.y;
            z += offset.z;
        }

        float duration = morph.duration - morph.lastDuration;

        if (morph.morphSetDuration)
        {
            if (duration > 0)
            {
                float setDuration = (float) Math.ceil(duration);
                float ticks = (progress - morph.lastDuration) * setDuration / duration;
                int tick = (int) ticks;

                if (morph.updateSetDuration(current, tick, (int) setDuration))
                {
                    partialTicks = ticks - tick;
                }
            }
            else
            {
                morph.updateSetDuration(current, 1, 1);
            }
        }

        MorphRenderUtils.render(current, entity, x, y, z, entityYaw, partialTicks);
    }

    /* --------------------------------------------------------------------- */
    /* On screen (GUI)                                                       */
    /* --------------------------------------------------------------------- */

    /**
     * Legacy's preview walks the same automaton against {@code screenTimer}
     * rather than the world timer, on a <b>copy</b> of the resolved entry (and
     * of the previous entry, so the transition animation shows) — a preview must
     * never advance the morph that the world draw reads.
     */
    @Override
    public void renderOnScreen(SequencerMorph morph, PlayerEntity player, int x, int y, float scale, float alpha)
    {
        if (morph.morphs.isEmpty())
        {
            BBIcons.CHICKEN.render(x - 8, y - 20);

            return;
        }

        morph.screenTimer++;
        morph.screenTimer %= 2000;

        FoundMorph found = morph.getMorphAt(morph.screenTimer);
        AbstractMorph current = MorphUtils.copy(found.getCurrentMorph());
        AbstractMorph previous = MorphUtils.copy(found.getPreviousMorph());

        MorphUtils.pause(current, previous, (int) (morph.screenTimer - found.lastDuration));

        if (current != null)
        {
            MorphRenderUtils.renderOnScreen(current, player, x, y, scale, alpha);
        }
    }
}
