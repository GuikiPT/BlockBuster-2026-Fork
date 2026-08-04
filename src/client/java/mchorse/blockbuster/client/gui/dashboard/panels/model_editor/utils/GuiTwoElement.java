package mchorse.blockbuster.client.gui.dashboard.panels.model_editor.utils;

import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTrackpadElement;
import net.minecraft.client.MinecraftClient;

import java.util.function.Consumer;

/**
 * Paired trackpad editor (roadmap P137) — two horizontally-laid trackpads whose
 * values feed a single {@code Double[2]} callback (used for the pose size/hitbox
 * editor in {@code GuiModelPoses}).
 *
 * <p>Legacy source (ported 1:1):
 * {@code blockbuster-1.12/.../model_editor/utils/GuiTwoElement.java}
 * ({@code Minecraft} &rarr; {@code MinecraftClient}; the unused legacy
 * {@code Flex} import is dropped).</p>
 */
public class GuiTwoElement extends GuiElement
{
    public GuiTrackpadElement a;
    public GuiTrackpadElement b;
    public Double[] array;

    public GuiTwoElement(MinecraftClient mc, Consumer<Double[]> callback)
    {
        super(mc);

        this.array = new Double[] {0D, 0D};
        this.a = new GuiTrackpadElement(mc, (value) ->
        {
            this.array[0] = value;

            if (callback != null)
            {
                callback.accept(this.array);
            }
        });
        this.b = new GuiTrackpadElement(mc, (value) ->
        {
            this.array[1] = value;

            if (callback != null)
            {
                callback.accept(this.array);
            }
        });

        this.flex().h(20).row(5);
        this.add(this.a, this.b);
    }

    public void setLimit(int min, int max)
    {
        this.a.limit(min, max);
        this.b.limit(min, max);
    }

    public void setLimit(int min, int max, boolean integer)
    {
        this.a.limit(min, max, integer);
        this.b.limit(min, max, integer);
    }

    public void setValues(double a, double b)
    {
        this.a.setValue(a);
        this.b.setValue(b);

        this.array[0] = a;
        this.array[1] = b;
    }
}
