package mchorse.mclib.client.gui.framework.elements.modals;

import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.utils.Area;
import mchorse.mclib.client.gui.utils.LegacyKeyCodes;
import mchorse.mclib.client.gui.utils.keys.IKey;
import net.minecraft.client.MinecraftClient;

import java.util.function.Consumer;

/**
 * Port of McLib 2.4.3's {@code GuiConfirmModal} (roadmap P36).
 * Enter = OK / ESC = Cancel → boolean callback.
 *
 * S2 seam note: {@code ClientHandlerConfirm} (the server-requested confirm
 * packet) still opens its placeholder — wiring it to this modal is a later
 * phase's one-liner now that the modal exists.
 */
public class GuiConfirmModal extends GuiModal
{
    public GuiButtonElement confirm;
    public GuiButtonElement cancel;

    public Consumer<Boolean> callback;

    public GuiConfirmModal(MinecraftClient mc, IKey label, Consumer<Boolean> callback)
    {
        super(mc, label);

        this.callback = callback;

        this.confirm = new GuiButtonElement(mc, IKey.lang("mclib.gui.ok"), (b) -> this.close(true));
        this.cancel = new GuiButtonElement(mc, IKey.lang("mclib.gui.cancel"), (b) -> this.close(false));

        this.bar.add(this.confirm, this.cancel);
    }

    public static GuiConfirmModal createTemplate(MinecraftClient mc, GuiElement parent, IKey label, Consumer<Boolean> callback)
    {
        GuiConfirmModal modal = new GuiConfirmModal(mc, label, callback);

        modal.flex().relative(parent).xy(0.5F, 0.5F).wh(160, 180).anchor(0.5F, 0.5F);

        return modal;
    }

    public static GuiConfirmModal createTemplate(MinecraftClient mc, Area area, IKey label, Consumer<Boolean> callback)
    {
        GuiConfirmModal modal = new GuiConfirmModal(mc, label, callback);

        modal.flex().relative(area).xy(0.5F, 0.5F).wh(160, 180).anchor(0.5F, 0.5F);

        return modal;
    }

    public void close(boolean confirmed)
    {
        if (this.callback != null)
        {
            this.callback.accept(confirmed);
        }

        this.removeFromParent();
    }

    @Override
    public boolean keyTyped(GuiContext context)
    {
        if (super.keyTyped(context))
        {
            return true;
        }

        if (context.keyCode == LegacyKeyCodes.KEY_RETURN || context.keyCode == LegacyKeyCodes.KEY_ESCAPE)
        {
            (context.keyCode == LegacyKeyCodes.KEY_RETURN ? this.confirm : this.cancel).clickItself(context);

            return true;
        }

        return false;
    }
}
