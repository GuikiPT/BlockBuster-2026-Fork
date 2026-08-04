package mchorse.metamorph.bodypart;

import java.util.List;
import java.util.function.Consumer;

import mchorse.mclib.client.gui.framework.GuiBase;
import mchorse.mclib.client.gui.framework.elements.list.GuiListElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.client.MorphRenderUtils;
import net.minecraft.client.MinecraftClient;

/**
 * Body part list which displays body parts (roadmap P59.1).
 *
 * <p>Verbatim port of Metamorph 1.4's
 * {@code mchorse/metamorph/bodypart/GuiBodyPartListElement.java} (56 lines).
 * Only the {@code Minecraft} → {@code MinecraftClient} boundary rename
 * differs; every pixel offset, magic number and label rule is preserved:</p>
 *
 * <ul>
 *   <li>{@code scroll.scrollItemSize = 24} — rows are 24px tall (taller than
 *       the {@code GuiListElement} default of 20) to fit the morph preview.</li>
 *   <li>The morph preview is drawn <b>before</b> {@code super.drawElementPart}
 *       (i.e. underneath the row's label), scissored to
 *       {@code (x, y, scroll.w, scroll.scrollItemSize)}. Note the scissor box
 *       is <b>not</b> clamped to the scroll viewport here (unlike Blockbuster's
 *       {@code GuiModelBlockList}) — legacy relies on {@code GuiDraw.scissor}
 *       nesting inside the list's own viewport scissor to clamp it. Kept as-is.</li>
 *   <li>Preview anchor {@code (x + scroll.w - 16, y + 30)} with scale 20 and
 *       alpha 1: the preview is drawn at the row's right edge and 30px
 *       <i>below</i> the row's top — i.e. the morph's feet sit 6px past the
 *       24px row, so the row shows the upper body only. Load-bearing.</li>
 *   <li>Label: {@code "_"} when the limb name is empty, and
 *       {@code " - " + morph.getDisplayName()} appended <b>only</b> when the
 *       morph has a custom name ({@code hasCustomName()}), never for the
 *       morph's own subclass display name.</li>
 * </ul>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/bodypart/GuiBodyPartListElement.java
 */
public class GuiBodyPartListElement extends GuiListElement<BodyPart>
{
    public GuiBodyPartListElement(MinecraftClient mc, Consumer<List<BodyPart>> callback)
    {
        super(mc, callback);

        this.scroll.scrollItemSize = 24;
    }

    @Override
    protected void drawElementPart(BodyPart element, int i, int x, int y, boolean hover, boolean selected)
    {
        GuiContext context = GuiBase.getCurrent();

        if (context != null && !element.morph.isEmpty())
        {
            GuiDraw.scissor(x, y, this.scroll.w, this.scroll.scrollItemSize, context);

            try
            {
                /* Legacy called renderOnScreen bare; routed through the shared
                 * error trap so one broken body-part morph latches
                 * errorRendering instead of taking the whole list down (and so
                 * the scissor stack always unwinds). */
                MorphRenderUtils.renderOnScreen(element.morph.get(), this.mc == null ? null : this.mc.player, x + this.scroll.w - 16, y + 30, 20, 1);
            }
            finally
            {
                GuiDraw.unscissor(context);
            }
        }

        super.drawElementPart(element, i, x, y, hover, selected);
    }

    @Override
    protected String elementToString(BodyPart element)
    {
        String label = element.limb.isEmpty() ? "_" : element.limb;

        if (!element.morph.isEmpty())
        {
            AbstractMorph morph = element.morph.get();

            if (morph.hasCustomName())
            {
                label += " - " + element.morph.get().getDisplayName();
            }
        }

        return label;
    }
}
