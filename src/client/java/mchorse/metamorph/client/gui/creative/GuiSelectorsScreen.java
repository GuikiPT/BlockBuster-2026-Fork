package mchorse.metamorph.client.gui.creative;

import mchorse.mclib.client.gui.framework.GuiBase;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.metamorph.Metamorph;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.creative.sections.MorphSection;
import mchorse.metamorph.api.creative.sections.UserSection;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.capabilities.render.EntitySelector;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;

/**
 * Entity selectors screen (roadmap P58 / P54.1) — what the
 * {@code key.metamorph.selector_menu} keybind (default {@code -}) opens.
 *
 * <p>1:1 port of Metamorph 1.4's
 * {@code mchorse.metamorph.client.gui.creative.GuiSelectorsScreen}: a
 * {@link GuiSelectorEditor} in <b>menu mode</b> on the left 140 px and a
 * permanently visible {@link GuiCreativeMorphsMenu} filling the rest, so
 * clicking any morph assigns it to the selected selector immediately (no
 * "Pick morph" arming step — that is the standalone editor's flow, reached
 * from the creative screen's {@code S} toggle).</p>
 *
 * <p>The one non-obvious behaviour is {@link #setMorph}: the selector's
 * <b>previous</b> morph is read before the overwrite and, if it decodes, copied
 * into Recent. Reassigning a selector would otherwise silently discard whatever
 * was configured there, with no undo — this is the escape hatch, and it is why
 * the read has to happen before {@code editor.setMorph} runs.</p>
 *
 * <p>Boundary changes: {@code Minecraft} &rarr; {@code MinecraftClient};
 * {@code doesGuiPauseGame} &rarr; {@link #shouldPause()}; {@code drawScreen}
 * &rarr; {@link #render(DrawContext, int, int, float)}, which has to bind the
 * {@link DrawContext} into the framework before anything draws (the GuiBase
 * bridge contract).</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/client/gui/creative/GuiSelectorsScreen.java
 */
public class GuiSelectorsScreen extends GuiBase
{
    public GuiSelectorEditor editor;
    public GuiCreativeMorphsMenu menu;

    public GuiSelectorsScreen(MinecraftClient mc)
    {
        this.editor = new GuiSelectorEditor(mc, true);
        this.menu = new GuiCreativeMorphsMenu(mc, true, this::setMorph);
        this.menu.setVisible(true);

        this.editor.flex().relative(this.viewport).wTo(this.menu.flex()).h(1F);
        this.menu.flex().relative(this.viewport).x(140).h(1F).wTo(this.root.flex(), 1F);

        this.root.add(this.menu, this.editor);
    }

    private void setMorph(AbstractMorph morph)
    {
        EntitySelector selector = this.editor.getSelector();
        NbtCompound tag = null;

        if (selector != null)
        {
            tag = selector.morph;
        }

        this.editor.setMorph(morph);

        if (tag != null)
        {
            AbstractMorph oldMorph = MorphManager.INSTANCE.morphFromNBT(tag);

            if (oldMorph != null)
            {
                addToRecent(oldMorph);
            }
        }
    }

    /**
     * Copy the displaced morph into the first user section's Recent category,
     * skipping it when an equal morph is already there. Legacy returned out of
     * the loop on the <b>first</b> user section it added to, so with several
     * user sections registered only one receives the copy — preserved.
     */
    public static void addToRecent(AbstractMorph oldMorph)
    {
        for (MorphSection section : MorphManager.INSTANCE.list.sections)
        {
            if (section instanceof UserSection)
            {
                UserSection user = (UserSection) section;

                if (user.recent.getEqual(oldMorph) == null)
                {
                    user.recent.add(oldMorph);

                    return;
                }
            }
        }
    }

    @Override
    public boolean shouldPause()
    {
        return Metamorph.pauseGUIInSP.get();
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float partialTicks)
    {
        this.context.drawContext = drawContext;
        GuiDraw.bindDrawContext(drawContext);

        GuiDraw.drawCustomBackground(0, 0, this.width, this.height);

        super.render(drawContext, mouseX, mouseY, partialTicks);
    }
}
