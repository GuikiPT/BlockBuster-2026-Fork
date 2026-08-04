package mchorse.metamorph.client;

import mchorse.mclib.client.gui.framework.elements.GuiModelRenderer;
import mchorse.mclib.utils.OptifineHelper;
import mchorse.metamorph.api.MorphSettings;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Client-side half of Metamorph 1.4's {@code MorphUtils} render trap (roadmap
 * P58). {@code mchorse.metamorph.api.MorphUtils} lives in the common source set
 * and cannot reference client render types, so the error-trapped draw helpers
 * ({@code render} / {@code renderDirect} / {@code renderOnScreen}) and the
 * {@code isRenderingOnScreen} flag they toggle live here.
 *
 * <p>Legacy body, preserved 1:1:</p>
 * <ul>
 *   <li>null / {@code errorRendering} early-out returning {@code false};</li>
 *   <li>the shadow-pass filter — {@link #isFilteredByShadowPass(AbstractMorph)},
 *       wired in P217 once there was an Iris-backed
 *       {@code OptifineHelper.isOptifineShadowPass()} to ask;</li>
 *   <li>{@code isRenderingOnScreen = true} around the draw, so morphs that
 *       branch on "am I being drawn in a GUI?" ({@code SnowstormClient},
 *       {@code ParticleMorph}) can see the 2D pass;</li>
 *   <li>{@code catch (Exception)} → {@code printStackTrace()} +
 *       {@code errorRendering = true} latch, so a broken morph is drawn once
 *       and then skipped forever;</li>
 *   <li>a {@code finally} that recovers the <b>shared</b> tessellator buffer.
 *       Legacy called {@code Tessellator.getInstance().getBuffer().finishDrawing()}
 *       — a morph renderer that threw between {@code begin()} and {@code end()}
 *       would otherwise leave the process-wide {@link BufferBuilder} in the
 *       building state and every subsequent {@code GuiDraw} call would throw
 *       "Already building!" for the rest of the session. The 1.20.4 equivalent
 *       is {@code isBuilding()} + {@code clear()}.</li>
 * </ul>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/api/MorphUtils.java (render/renderOnScreen)
 */
public final class MorphRenderUtils
{
    /**
     * Legacy {@code MorphUtils.isRenderingOnScreen} — true while a morph is
     * being drawn into a GUI through {@link #renderOnScreen}.
     */
    public static boolean isRenderingOnScreen = false;

    private MorphRenderUtils()
    {}

    /**
     * Error-trapped 2D morph draw (legacy {@code MorphUtils.renderOnScreen}).
     * Returns whether the morph actually drew.
     */
    public static boolean renderOnScreen(AbstractMorph morph, PlayerEntity player, int x, int y, float scale, float alpha)
    {
        if (morph == null || morph.errorRendering)
        {
            return false;
        }

        try
        {
            isRenderingOnScreen = true;
            morph.renderOnScreen(player, x, y, scale, alpha);

            return true;
        }
        catch (Exception e)
        {
            e.printStackTrace();
            morph.errorRendering = true;
        }
        finally
        {
            isRenderingOnScreen = false;
            recoverSharedBuffer(morph);
        }

        return false;
    }

    /**
     * Error-trapped world morph draw (legacy {@code MorphUtils.render}).
     * Returns whether the morph actually drew.
     */
    public static boolean render(AbstractMorph morph, LivingEntity entity, double x, double y, double z, float yaw, float partialTicks)
    {
        if (morph == null || morph.errorRendering)
        {
            return false;
        }

        if (isFilteredByShadowPass(morph))
        {
            return false;
        }

        return renderDirect(morph, entity, x, y, z, yaw, partialTicks);
    }

    /**
     * Error-trapped world morph draw <b>without</b> the shadow-pass check
     * (legacy {@code MorphUtils.renderDirect}, whose own comment was simply
     * "Without shadow pass check").
     *
     * <p>Legacy used it for draws that are not a world pass at all and must
     * therefore never be filtered: the body-part editor's gray onion-skin ghost
     * ({@code GuiBodyPartEditor.TransformedOnionSkinMorph#render}) and the
     * throwaway limb-matrix recording pass. Those run with
     * {@code GuiModelRenderer.isRendering()} off, so {@link #render} would
     * consult {@code shadowOption} and could drop an {@code ONLYSHADOW} morph
     * out of its own editor.</p>
     */
    public static boolean renderDirect(AbstractMorph morph, LivingEntity entity, double x, double y, double z, float yaw, float partialTicks)
    {
        if (morph == null || morph.errorRendering)
        {
            return false;
        }

        try
        {
            morph.render(entity, x, y, z, yaw, partialTicks);

            return true;
        }
        catch (Exception e)
        {
            e.printStackTrace();
            morph.errorRendering = true;
        }
        finally
        {
            recoverSharedBuffer(morph);
        }

        return false;
    }

    /**
     * Legacy {@code MorphUtils.render}'s shadow-pass filter (roadmap P217).
     *
     * <p>Two passes are distinguished, and each has its own per-morph opt-in via
     * {@code shadowOption}: 0 = ALL, 1 = NOSHADOW (normal pass only), 2 =
     * ONLYSHADOW (shadow pass only). The whole filter is bypassed while the model
     * editor is drawing — {@code GuiModelRenderer.isRendering()} — because a
     * morph set to ONLYSHADOW would otherwise be invisible in its own editor.</p>
     *
     * <p>Until P217 this filter was absent from the port, which made
     * {@code shadowOption} a dead switch: it is parsed from morph-settings JSON,
     * round-trips through NBT and is user-editable in {@code GuiSettingsPanel},
     * but nothing read it at draw time. With Iris installed it now behaves as it
     * did under Optifine; without it {@code isOptifineShadowPass()} is
     * permanently false, so only the "normal pass" arm can fire — exactly
     * 1.12.2-without-Optifine.</p>
     *
     * @return true when this morph must <b>not</b> draw on the current pass
     */
    public static boolean isFilteredByShadowPass(AbstractMorph morph)
    {
        boolean optifineShadowPass = OptifineHelper.isOptifineShadowPass();
        boolean normalPass = !optifineShadowPass;

        MorphSettings settings = morph.getSettings();

        boolean drawsOnNormalPass = settings.shadowOption == 0 || settings.shadowOption == 1;
        boolean drawsOnOptifineShadows = settings.shadowOption == 0 || settings.shadowOption == 2;

        return !GuiModelRenderer.isRendering()
            && ((normalPass && !drawsOnNormalPass)
            || (optifineShadowPass && !drawsOnOptifineShadows));
    }

    /**
     * Legacy's {@code Tessellator.getInstance().getBuffer().finishDrawing()}
     * recovery, in 1.20.4 terms. A morph renderer that threw mid-{@code begin()}
     * leaves the shared buffer building; {@code clear()} puts it back so the
     * next {@code GuiDraw} call does not throw "Already building!".
     */
    public static void recoverSharedBuffer(AbstractMorph morph)
    {
        try
        {
            BufferBuilder buffer = Tessellator.getInstance().getBuffer();

            if (buffer.isBuilding())
            {
                buffer.clear();

                System.err.println("Unfinished builder comes from class: " + (morph == null ? "null" : morph.getClass().getName()));
            }
        }
        catch (Throwable t)
        {
            /* No GL/tessellator (headless) — nothing to recover. */
        }
    }
}
