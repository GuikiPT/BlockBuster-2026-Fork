package mchorse.blockbuster_pack.client.render;

import mchorse.blockbuster.recording.data.Frame;
import mchorse.blockbuster_pack.morphs.RecordMorph;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.framework.elements.utils.GuiInventoryElement;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.client.MorphRenderUtils;
import mchorse.metamorph.client.render.IMorphRenderer;
import mchorse.metamorph.client.render.MorphRenderContext;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Client render body for {@link RecordMorph} (roadmap P54/P161).
 *
 * <p>A record morph draws its ghost {@link mchorse.blockbuster.common.entity.EntityActor}
 * actor's <i>current</i> morph, displaced by however far the actor has walked
 * from frame 0 — so a recording that runs in a circle makes the morph run in a
 * circle around wherever the record morph itself is. The displacement is
 * interpolated per axis by {@link RecordMorph#renderOffset}, and the very first
 * draw seeds the actor onto frame 0's position (that seeding lives in the render
 * body because legacy put it there: a record morph that is never drawn never
 * initiates).</p>
 *
 * <p><b>Preview.</b> The GUI cell shows a music disc over the initial morph
 * rather than the playback, which is what makes a record morph identifiable in
 * the picker without running a whole recording per cell. Legacy drew the disc
 * with depth off so it always sat in front of the morph behind it; on 1.20.4
 * {@code DrawContext} draws GUI items at their own z (150) above the morph
 * preview's, so the ordering holds without touching the depth state — and
 * disabling depth here would instead break the disc's own 3D item model.</p>
 *
 * Legacy source: blockbuster-1.12/.../blockbuster_pack/morphs/RecordMorph.java (render/renderOnScreen)
 */
public class RecordMorphRenderer implements IMorphRenderer<RecordMorph>
{
    /** Legacy {@code ICON}: {@code new ItemStack(Items.RECORD_13)}. */
    public static final ItemStack ICON = new ItemStack(Items.MUSIC_DISC_13);

    /* --------------------------------------------------------------------- */
    /* In-world                                                              */
    /* --------------------------------------------------------------------- */

    @Override
    public void render(RecordMorph morph, LivingEntity entity, double x, double y, double z, float entityYaw, float partialTicks, MorphRenderContext context)
    {
        morph.initiateActor(entity.getWorld());

        AbstractMorph actorMorph = morph.getActorMorph();

        if (actorMorph == null)
        {
            return;
        }

        if (morph.actor.playback.record != null)
        {
            Frame first = morph.actor.playback.record.getFrame(0);

            if (first != null)
            {
                if (!morph.isInitiated())
                {
                    morph.actor.setPosition(first.x, first.y, first.z);
                    morph.actor.prevX = first.x;
                    morph.actor.prevY = first.y;
                    morph.actor.prevZ = first.z;
                    morph.setInitiated(true);
                }

                x += RecordMorph.renderOffset(morph.actor.prevX, morph.actor.getX(), partialTicks, first.x);
                y += RecordMorph.renderOffset(morph.actor.prevY, morph.actor.getY(), partialTicks, first.y);
                z += RecordMorph.renderOffset(morph.actor.prevZ, morph.actor.getZ(), partialTicks, first.z);
            }
        }

        /* The actor, not the rendered entity, is the draw's entity: the inner
         * morph poses against the recorded body, not against whoever wears the
         * record morph. */
        MorphRenderUtils.render(actorMorph, morph.actor, x, y, z, entityYaw, partialTicks);
    }

    /* --------------------------------------------------------------------- */
    /* On screen (GUI)                                                       */
    /* --------------------------------------------------------------------- */

    @Override
    public void renderOnScreen(RecordMorph morph, PlayerEntity player, int x, int y, float scale, float alpha)
    {
        /* Legacy passed player.world unconditionally; the null guard is for the
         * previews that have no wearer at all (headless probes, and the picker
         * cells drawn before the client player exists) — without a world there
         * is no ghost actor to initiate, and the disc + initial morph below are
         * all a preview shows anyway. */
        if (player != null)
        {
            morph.initiateActor(player.getWorld());
        }

        this.drawDisc(x, y, scale);

        if (morph.initial != null)
        {
            MorphRenderUtils.renderOnScreen(morph.initial, player, x, y, scale, alpha);
        }
    }

    /**
     * Legacy: {@code translate(x + 1, y - 9, 0)}, {@code scale(ceil(scale / 16))}
     * on X/Y only, disc centred on {@code (-8, -8)} — one disc per 16 px of cell
     * scale, so it grows in whole steps with the cell instead of blurring.
     */
    /**
     * Legacy {@code (float) Math.ceil(scale / 16)} — one whole disc per 16 px of
     * cell scale, so the icon steps rather than blurring. Note it never falls
     * below 1 for any positive scale, and is 0 for scale 0.
     */
    public static float discScale(float scale)
    {
        return (float) Math.ceil(scale / 16F);
    }

    private void drawDisc(int x, int y, float scale)
    {
        DrawContext dc = GuiDraw.getDrawContext();

        if (dc == null)
        {
            return;
        }

        float disc = discScale(scale);
        MatrixStack matrices = dc.getMatrices();

        matrices.push();

        try
        {
            matrices.translate(x + 1, y - 9, 0);
            matrices.scale(disc, disc, 1F);

            GuiInventoryElement.drawItemStack(ICON, -8, -8, 0, null);
        }
        finally
        {
            matrices.pop();
        }
    }
}
