package mchorse.blockbuster.client.gui.dashboard.panels.recording_editor.actions;

import mchorse.blockbuster.client.gui.dashboard.panels.recording_editor.GuiRecordingEditorPanel;
import mchorse.blockbuster.recording.actions.ItemUseBlockAction;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiCirculateElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTrackpadElement;
import mchorse.mclib.client.gui.utils.keys.IKey;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * P139 — editor for {@link ItemUseBlockAction} (hand circulate from
 * {@link GuiItemUseActionPanel}, plus facing, block position, and hit vector).
 *
 * <p><b>Legacy-parity decision (recorded):</b> in Blockbuster 2.7.2 this panel
 * was <em>broken</em> — {@code GuiItemUseBlockActionPanel} declared its
 * {@code facing}/{@code block}/{@code hit} fields but the constructor never
 * instantiated them, so {@code fill()} dereferenced {@code null} and selecting
 * an {@code ItemUseBlockAction} in the recording editor threw an NPE (dead
 * panel). Per the "reader is total, never crash" ground rule this port takes
 * the plan's default and <b>fixes</b> the bug by actually creating the widgets;
 * crash-parity would be worthless UX. The legacy behaviour is noted in the P146
 * checklist.</p>
 *
 * <p>The facing byte is the legacy {@code EnumFacing} ordinal
 * (DOWN,UP,NORTH,SOUTH,WEST,EAST); yarn's {@link Direction} declares the same
 * order, so the circulate index maps 1:1 to {@code facing.ordinal()}.</p>
 */
public class GuiItemUseBlockActionPanel extends GuiItemUseActionPanel<ItemUseBlockAction>
{
    public GuiCirculateElement facing;
    public GuiTrackpadElement blockX;
    public GuiTrackpadElement blockY;
    public GuiTrackpadElement blockZ;
    public GuiTrackpadElement hitX;
    public GuiTrackpadElement hitY;
    public GuiTrackpadElement hitZ;

    public GuiItemUseBlockActionPanel(MinecraftClient mc, GuiRecordingEditorPanel panel)
    {
        super(mc, panel);

        this.facing = new GuiCirculateElement(mc, (b) -> this.action.facing = Direction.values()[b.getValue()]);
        this.facing.addLabel(IKey.lang("blockbuster.gui.record_editor.actions.use_item_block.down"));
        this.facing.addLabel(IKey.lang("blockbuster.gui.record_editor.actions.use_item_block.up"));
        this.facing.addLabel(IKey.lang("blockbuster.gui.record_editor.actions.use_item_block.north"));
        this.facing.addLabel(IKey.lang("blockbuster.gui.record_editor.actions.use_item_block.south"));
        this.facing.addLabel(IKey.lang("blockbuster.gui.record_editor.actions.use_item_block.west"));
        this.facing.addLabel(IKey.lang("blockbuster.gui.record_editor.actions.use_item_block.east"));
        this.facing.flex().set(10, 0, 80, 20).relative(this.hand.resizer()).y(0, -25);

        this.blockX = new GuiTrackpadElement(mc, (v) -> this.action.pos = new BlockPos(v.intValue(), this.action.pos.getY(), this.action.pos.getZ()));
        this.blockX.integer().tooltip(IKey.lang("blockbuster.gui.model_block.x"));
        this.blockY = new GuiTrackpadElement(mc, (v) -> this.action.pos = new BlockPos(this.action.pos.getX(), v.intValue(), this.action.pos.getZ()));
        this.blockY.integer().tooltip(IKey.lang("blockbuster.gui.model_block.y"));
        this.blockZ = new GuiTrackpadElement(mc, (v) -> this.action.pos = new BlockPos(this.action.pos.getX(), this.action.pos.getY(), v.intValue()));
        this.blockZ.integer().tooltip(IKey.lang("blockbuster.gui.model_block.z"));

        this.blockX.flex().set(100, 0, 80, 20).relative(this.area).y(1, -30);
        this.blockY.flex().set(0, 25, 80, 20).relative(this.blockX.resizer());
        this.blockZ.flex().set(0, 25, 80, 20).relative(this.blockY.resizer());

        this.hitX = new GuiTrackpadElement(mc, (v) -> this.action.hitX = v.floatValue());
        this.hitX.limit(0, 1).tooltip(IKey.lang("blockbuster.gui.model_block.x"));
        this.hitY = new GuiTrackpadElement(mc, (v) -> this.action.hitY = v.floatValue());
        this.hitY.limit(0, 1).tooltip(IKey.lang("blockbuster.gui.model_block.y"));
        this.hitZ = new GuiTrackpadElement(mc, (v) -> this.action.hitZ = v.floatValue());
        this.hitZ.limit(0, 1).tooltip(IKey.lang("blockbuster.gui.model_block.z"));

        this.hitX.flex().set(190, 0, 80, 20).relative(this.area).y(1, -30);
        this.hitY.flex().set(0, 25, 80, 20).relative(this.hitX.resizer());
        this.hitZ.flex().set(0, 25, 80, 20).relative(this.hitY.resizer());

        this.add(this.facing, this.blockX, this.blockY, this.blockZ, this.hitX, this.hitY, this.hitZ);
    }

    @Override
    public void fill(ItemUseBlockAction action)
    {
        super.fill(action);

        this.facing.setValue(action.facing.ordinal());
        this.blockX.setValue(action.pos.getX());
        this.blockY.setValue(action.pos.getY());
        this.blockZ.setValue(action.pos.getZ());
        this.hitX.setValue(action.hitX);
        this.hitY.setValue(action.hitY);
        this.hitZ.setValue(action.hitZ);
    }
}
