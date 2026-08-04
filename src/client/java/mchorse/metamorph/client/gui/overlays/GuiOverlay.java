package mchorse.metamorph.client.gui.overlays;

import mchorse.metamorph.api.morphs.AbstractMorph;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Acquired-morph toast overlay (roadmap P61).
 *
 * <p>1:1 port of Metamorph 1.4's {@code GuiOverlay}: when the player acquires a
 * morph, a small preview slides in at {@code x=15} above the hotbar with the
 * {@code metamorph.gui.acquired} label, lives 240 ticks, and over the last
 * {@link #cap} (60) ticks fades out while sliding down 40&nbsp;px.</p>
 *
 * <p>The actual pixel drawing ({@code MorphUtils.renderOnScreen} + font) lives
 * in {@code MetamorphHudWiring.renderOverlay} (roadmap P225) so this class stays
 * headless: it owns the load-bearing <b>timeline</b> (add / tick / fade / the
 * fast-forward-on-new quirk) and the per-frame render geometry as pure
 * functions. The wiring's {@code HudRenderCallback} reads
 * {@link #alpha(int)}/{@link #y(int, int)}/{@link #color(int)} to paint each
 * toast and then calls {@link #tick()} — <b>once per rendered frame</b>, not per
 * client tick, because legacy decremented inside {@code render()} on
 * {@code RenderGameOverlayEvent}. The 240-"tick" lifetime is therefore 240
 * frames, i.e. framerate-dependent; that is the 1.12.2 behaviour.</p>
 *
 * Legacy source:
 * .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/client/gui/overlays/GuiOverlay.java
 */
public class GuiOverlay
{
    /**
     * List of acquired morphs currently animating.
     */
    public List<AcquiredMorph> morphs = new ArrayList<AcquiredMorph>();

    /**
     * Disappearing cap: a toast whose timer has dropped to this value (or
     * below) is in its fade-out phase.
     */
    public final int cap = 60;

    /**
     * Add an acquired morph to this overlay.
     *
     * <p>Legacy quirk (load-bearing): adding a new toast fast-forwards every
     * existing toast whose timer is still above {@link #cap} down to
     * {@code cap}, so a freshly acquired morph shoves the older ones straight
     * into their fade-out.</p>
     */
    public void add(AbstractMorph acquired)
    {
        for (AcquiredMorph morph : this.morphs)
        {
            if (morph.timer > this.cap)
            {
                morph.timer = this.cap;
            }
        }

        this.morphs.add(new AcquiredMorph(acquired));
    }

    /**
     * Advance every toast by one tick, removing those whose timer has run out.
     *
     * <p>In the legacy code the decrement + removal happened inside
     * {@code render()}; it is split out here so the timeline can be exercised
     * without a render context. Call once per rendered <em>frame</em> (that is
     * what legacy did), after reading this frame's render geometry.</p>
     */
    public void tick()
    {
        Iterator<AcquiredMorph> iterator = this.morphs.iterator();

        while (iterator.hasNext())
        {
            AcquiredMorph morph = iterator.next();

            morph.timer--;

            if (morph.timer <= 0)
            {
                iterator.remove();
            }
        }
    }

    /**
     * Whether the toast with the given timer is in its fade-out phase.
     */
    public boolean disappearing(int timer)
    {
        return timer <= this.cap;
    }

    /**
     * Alpha byte (0–255) for the given timer. Matches legacy
     * {@code 255 * timer / cap}; note the value <em>exceeds</em> 255 while the
     * toast is still in its steady phase ({@code timer > cap}) — legacy passed
     * this straight through to {@code renderOnScreen} and the opaque font
     * colour, so the quirk is preserved rather than clamped.
     */
    public int alpha(int timer)
    {
        return 255 * timer / this.cap;
    }

    /**
     * Morph preview alpha ({@code alpha / 255f}) passed to the render seam.
     */
    public float renderAlpha(int timer)
    {
        return (float) this.alpha(timer) / 255F;
    }

    /**
     * Vertical draw position for the given timer, on a screen of the given
     * height. During the fade phase the toast slides down up to 40&nbsp;px.
     */
    public int y(int timer, int height)
    {
        boolean disappear = this.disappearing(timer);
        int progress = this.cap - timer;

        return height - 10 + (disappear ? (int) (40 * (float) progress / this.cap) : 0);
    }

    /**
     * ARGB label colour for the given timer: opaque white while steady, fading
     * to transparent white during the fade phase.
     */
    public int color(int timer)
    {
        return this.disappearing(timer) ? 0x00ffffff + (this.alpha(timer) << 24) : 0xffffffff;
    }

    /**
     * Acquired morph toast entry — a morph plus its remaining lifetime timer.
     */
    public static class AcquiredMorph
    {
        public AbstractMorph morph;
        public int timer = 240;

        public AcquiredMorph(AbstractMorph morph)
        {
            this.morph = morph;
        }
    }
}
