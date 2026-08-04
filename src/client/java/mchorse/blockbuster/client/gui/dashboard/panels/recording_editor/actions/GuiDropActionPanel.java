package mchorse.blockbuster.client.gui.dashboard.panels.recording_editor.actions;

import mchorse.blockbuster.client.gui.dashboard.panels.recording_editor.GuiRecordingEditorPanel;
import mchorse.blockbuster.recording.actions.DropAction;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiSlotElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

/**
 * P139 — a centered item-slot picker. The picked stack is serialized to NBT
 * into {@link DropAction#itemData}, or {@code null} when the slot is emptied
 * (legacy null-on-empty behavior kept).
 */
public class GuiDropActionPanel extends GuiActionPanel<DropAction>
{
    public GuiSlotElement slot;

    public GuiDropActionPanel(MinecraftClient mc, GuiRecordingEditorPanel panel)
    {
        super(mc, panel);

        this.slot = new GuiSlotElement(mc, 0, this::pickItem);
        this.slot.flex().relative(this.area).xy(0.5F, 0.5F).anchor(0.5F, 0.5F);

        this.add(this.slot);
    }

    public void pickItem(ItemStack stack)
    {
        this.action.itemData = stack.isEmpty() ? null : stack.writeNbt(new NbtCompound());
        this.slot.setStack(stack);
    }

    @Override
    public void fill(DropAction action)
    {
        super.fill(action);

        this.slot.setStack(action.itemData == null ? ItemStack.EMPTY : ItemStack.fromNbt(action.itemData));
    }
}
