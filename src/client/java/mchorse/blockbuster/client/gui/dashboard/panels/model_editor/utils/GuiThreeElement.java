package mchorse.blockbuster.client.gui.dashboard.panels.model_editor.utils;

import mchorse.mclib.client.gui.framework.elements.input.GuiTrackpadElement;
import net.minecraft.client.MinecraftClient;

import java.util.function.Consumer;

/**
 * Triple trackpad editor (roadmap P137) — {@link GuiTwoElement} plus a third
 * trackpad, all three feeding one {@code Double[3]} callback (limb size, anchor,
 * origin, model scale and the anchor modal's vector).
 *
 * <p>Legacy source (ported 1:1):
 * {@code blockbuster-1.12/.../model_editor/utils/GuiThreeElement.java}
 * ({@code Minecraft} &rarr; {@code MinecraftClient}; the unused legacy
 * {@code Flex} import is dropped).</p>
 *
 * <p><b>Legacy oddity, copied as-is.</b> The two-argument
 * {@link #setLimit(int, int)} assigns {@code c.min}/{@code c.max} <b>directly</b>
 * instead of calling {@code c.limit(min, max)} the way {@link GuiTwoElement}
 * does for its own two trackpads. In McLib 2.4.3 {@code limit(min, max)} does
 * nothing but assign those same two fields — verified against
 * {@code .tools/legacy-src} — so the bypass is <b>inert</b>: both paths install
 * the bound and neither re-clamps the trackpad's current value. It is kept
 * verbatim for diff-ability, not because it changes behaviour. (The
 * three-argument overload does route through {@code limit()}, since it must also
 * set the integer flag.)</p>
 */
public class GuiThreeElement extends GuiTwoElement
{
    public GuiTrackpadElement c;

    public GuiThreeElement(MinecraftClient mc, Consumer<Double[]> callback)
    {
        super(mc, callback);

        this.array = new Double[] {0D, 0D, 0D};
        this.c = new GuiTrackpadElement(mc, (value) ->
        {
            this.array[2] = value;

            if (callback != null)
            {
                callback.accept(this.array);
            }
        });
        this.add(this.c);
    }

    @Override
    public void setLimit(int min, int max)
    {
        super.setLimit(min, max);

        /* Legacy bypass of limit() — see the class javadoc. */
        this.c.min = min;
        this.c.max = max;
    }

    @Override
    public void setLimit(int min, int max, boolean integer)
    {
        super.setLimit(min, max, integer);
        this.c.limit(min, max, integer);
    }

    public void setValues(double a, double b, double c)
    {
        this.setValues(a, b);
        this.c.setValue(c);

        this.array[2] = c;
    }
}
