package mchorse.blockbuster.client.gui.dashboard.panels.recording_editor.actions;

import mchorse.blockbuster.client.gui.dashboard.panels.recording_editor.GuiRecordingEditorPanel;
import mchorse.blockbuster.recording.actions.EquipAction;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiCirculateElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiSlotElement;
import mchorse.mclib.client.gui.utils.keys.IKey;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

/**
 * P139 — an armor-slot circulate plus an item-slot picker.
 *
 * <p>The circulate cycles {@link EquipAction#armorSlot} as a byte 0..5 in label
 * order {@code main_hand, feet, legs, chest, head, off_hand}. This order is a
 * <b>wire/disk contract</b> matching the legacy {@code EntityEquipmentSlot}
 * armor-index space recorded in 1.12.2 files. On 1.20.4 the action resolves the
 * byte through {@code EquipmentSlot.getArmorStandSlotId()} (main_hand=0, feet=1,
 * legs=2, chest=3, head=4, off_hand=5), which reproduces exactly this label
 * order — so the recorded byte stays valid across the port.</p>
 */
public class GuiEquipActionPanel extends GuiActionPanel<EquipAction>
{
    public GuiCirculateElement armor;
    public GuiSlotElement slot;

    public GuiEquipActionPanel(MinecraftClient mc, GuiRecordingEditorPanel panel)
    {
        super(mc, panel);

        this.armor = new GuiCirculateElement(mc, (b) -> this.action.armorSlot = (byte) b.getValue());
        this.armor.addLabel(IKey.lang("blockbuster.gui.record_editor.actions.equip.main_hand"));
        this.armor.addLabel(IKey.lang("blockbuster.gui.record_editor.actions.equip.feet"));
        this.armor.addLabel(IKey.lang("blockbuster.gui.record_editor.actions.equip.legs"));
        this.armor.addLabel(IKey.lang("blockbuster.gui.record_editor.actions.equip.chest"));
        this.armor.addLabel(IKey.lang("blockbuster.gui.record_editor.actions.equip.head"));
        this.armor.addLabel(IKey.lang("blockbuster.gui.record_editor.actions.equip.off_hand"));
        this.armor.flex().relative(this).w(80).x(10).y(1F, -30);

        this.slot = new GuiSlotElement(mc, 0, this::pickItem);
        this.slot.flex().relative(this).xy(0.5F, 0.5F).anchor(0.5F, 0.5F);

        this.add(this.armor, this.slot);
    }

    public void pickItem(ItemStack stack)
    {
        this.action.itemData = stack.isEmpty() ? null : stack.writeNbt(new NbtCompound());
        this.slot.setStack(stack);
    }

    @Override
    public void fill(EquipAction action)
    {
        super.fill(action);

        this.armor.setValue(action.armorSlot);
        this.slot.setStack(action.itemData == null ? ItemStack.EMPTY : ItemStack.fromNbt(action.itemData));
    }
}
