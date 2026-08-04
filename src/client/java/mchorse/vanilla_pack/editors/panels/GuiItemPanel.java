package mchorse.vanilla_pack.editors.panels;

import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiCirculateElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiSlotElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiToggleElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTexturePicker;
import mchorse.mclib.client.gui.utils.Elements;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.utils.resources.RLUtils;
import mchorse.metamorph.client.gui.editor.GuiAbstractMorph;
import mchorse.metamorph.client.gui.editor.GuiMorphPanel;
import mchorse.vanilla_pack.morphs.ItemMorph;
import mchorse.vanilla_pack.render.ItemMorphRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.entity.EquipmentSlot;

/**
 * Item morph panel (roadmap P59.2) — stack slot, lighting, camera-transform
 * circulate, extruded-texture picker, animation, and the
 * {@code itemFromEquipment} + equipment-slot pair.
 *
 * <p>Port of Metamorph 1.4's {@code GuiItemPanel}. Two enum orders are load
 * bearing and both survived the version jump unchanged, which is why the
 * ordinal-indexed circulate elements port verbatim:</p>
 *
 * <ul>
 *   <li>1.12's {@code ItemCameraTransforms.TransformType} → yarn's
 *       {@link ModelTransformationMode}: {@code NONE, THIRD_PERSON_LEFT_HAND,
 *       THIRD_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND,
 *       FIRST_PERSON_RIGHT_HAND, HEAD, GUI, GROUND, FIXED} — same nine
 *       constants in the same order.</li>
 *   <li>1.12's {@code EntityEquipmentSlot} → yarn's {@link EquipmentSlot}:
 *       {@code MAINHAND, OFFHAND, FEET, LEGS, CHEST, HEAD} — likewise.</li>
 * </ul>
 *
 * <p>Legacy read the transform key through {@code ItemMorph.getTransformTypes()},
 * a Guava {@code BiMap} it kept solely so this panel could do the reverse
 * lookup. The port keeps the frozen key set on the client renderer instead
 * ({@link ItemMorphRenderer#getTransformTypes()}), with
 * {@link ItemMorphRenderer#transformName} as the reverse direction — the disk
 * contract is the same nine strings either way.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/vanilla_pack/editors/panels/GuiItemPanel.java
 */
public class GuiItemPanel extends GuiMorphPanel<ItemMorph, GuiAbstractMorph<? extends ItemMorph>>
{
    public GuiSlotElement slot;

    public GuiToggleElement lighting;
    public GuiCirculateElement transform;
    public GuiButtonElement texture;
    public GuiToggleElement animation;
    public GuiToggleElement itemFromEquipment;
    public GuiCirculateElement equipmentSlot;

    public GuiTexturePicker picker;

    public GuiItemPanel(MinecraftClient mc, GuiAbstractMorph<? extends ItemMorph> editor)
    {
        super(mc, editor);

        this.slot = new GuiSlotElement(mc, 0, (stack) -> this.morph.setStack(stack));
        this.lighting = new GuiToggleElement(mc, IKey.lang("metamorph.gui.label.lighting"), (b) -> this.morph.lighting = b.isToggled());
        this.transform = new GuiCirculateElement(mc, (b) ->
        {
            ModelTransformationMode type = ModelTransformationMode.values()[b.getValue()];

            this.morph.transform = ItemMorphRenderer.transformName(type);
        });
        this.animation = new GuiToggleElement(mc, IKey.lang("metamorph.gui.item.animation"), (b) -> this.morph.animation = b.isToggled());
        this.itemFromEquipment = new GuiToggleElement(mc, IKey.lang("metamorph.gui.item.item_from_equipment"), (b) -> this.morph.itemFromEquipment = b.isToggled());
        this.equipmentSlot = new GuiCirculateElement(mc, (b) -> this.morph.equipmentSlot = EquipmentSlot.values()[b.getValue()]);

        this.slot.flex().relative(this).x(0.5F, 0).y(1, -10).wh(32, 32).anchor(0.5F, 1);

        for (ModelTransformationMode transform : ModelTransformationMode.values())
        {
            this.transform.addLabel(IKey.lang("metamorph.gui.item.transform." + ItemMorphRenderer.transformName(transform)));
        }

        for (EquipmentSlot slot : EquipmentSlot.values())
        {
            this.equipmentSlot.addLabel(IKey.lang("metamorph.gui.item.equipment_slot." + slot.getName()));
        }

        this.texture = new GuiButtonElement(mc, IKey.lang("metamorph.gui.editor.texture"), (b) ->
        {
            this.picker.refresh();
            this.picker.fill(this.morph.texture);
            this.add(this.picker);
            this.picker.resize();
        });
        this.picker = new GuiTexturePicker(mc, (rl) -> this.morph.texture = RLUtils.clone(rl));
        this.picker.flex().relative(this).wh(1F, 1F);

        GuiElement column = Elements.column(mc, 5, this.lighting, this.transform, this.texture, this.animation, this.itemFromEquipment.marginTop(12), this.equipmentSlot);

        column.flex().relative(this).xy(10, 10).w(110);

        this.add(this.slot, column);
    }

    @Override
    public void fillData(ItemMorph morph)
    {
        super.fillData(morph);

        ModelTransformationMode type = ItemMorphRenderer.transformType(morph.transform);

        this.slot.setStack(morph.getStack());
        this.lighting.toggled(morph.lighting);
        this.transform.setValue(type.ordinal());
        this.animation.toggled(morph.animation);
        this.itemFromEquipment.toggled(morph.itemFromEquipment);
        this.equipmentSlot.setValue(morph.equipmentSlot.ordinal());
    }
}
