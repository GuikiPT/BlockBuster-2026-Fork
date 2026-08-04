package mchorse.mclib.client.gui.framework.elements;

import mchorse.mclib.client.gui.framework.GuiBase;
import mchorse.mclib.client.gui.framework.elements.modals.GuiConfirmModal;
import mchorse.mclib.client.gui.utils.keys.IKey;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.function.Consumer;

/**
 * Port of McLib 2.4.3's {@code GuiConfirmationScreen} (roadmap P36) — a
 * standalone screen firing a boolean callback on ANY close (defaults to
 * false when dismissed without picking).
 */
public class GuiConfirmationScreen extends GuiBase
{
    private Consumer<Boolean> callback;
    private boolean value;

    public GuiConfirmationScreen(IKey label, Consumer<Boolean> callback)
    {
        super();

        this.callback = callback;

        this.root.add(GuiConfirmModal.createTemplate(MinecraftClient.getInstance(), this.viewport, label, (value) ->
        {
            this.value = value;
            this.closeScreen();
        }));
    }

    @Override
    public boolean shouldPause()
    {
        /* Legacy doesGuiPauseGame() = false */
        return false;
    }

    @Override
    protected void closeScreen()
    {
        this.callback.accept(this.value);
        super.closeScreen();
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float partialTicks)
    {
        /* Legacy drawDefaultBackground() */
        this.renderBackground(drawContext);

        super.render(drawContext, mouseX, mouseY, partialTicks);
    }
}
