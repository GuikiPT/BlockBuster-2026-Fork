package mchorse.blockbuster.client.gui.dashboard.panels.recording_editor.actions;

import mchorse.blockbuster.client.gui.dashboard.panels.recording_editor.GuiRecordingEditorPanel;
import mchorse.blockbuster.recording.actions.PlaceBlockAction;
import mchorse.mclib.client.gui.framework.elements.input.GuiTextElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTrackpadElement;
import mchorse.mclib.client.gui.utils.keys.IKey;
import net.minecraft.client.MinecraftClient;

/**
 * P139 — {@link GuiBlockActionPanel} plus a free-text block-id field and a
 * metadata trackpad (0..15) stored as a <b>byte</b>. These edit the raw
 * pre-flattening {@code (block id, meta)} wire pair on disk; the P71 id shim
 * translates them only at apply time (in {@link PlaceBlockAction}), never here.
 *
 * <p>S22 P296 adds a third free-text field for the optional {@code State}
 * carrier ({@code minecraft:oak_stairs[facing=east,…]}), which is what makes
 * the panel round-trip a modern capture instead of silently showing only its
 * block. Two deliberate behaviours:</p>
 * <ul>
 * <li>Editing the <b>state</b> field is authoritative — that is the whole point
 * of having it, and {@code PlaceBlockAction.resolveState()} prefers it.</li>
 * <li>Editing the <b>block</b> field therefore has to clear the state, or the
 * user's edit would be silently ignored at apply time (the stale state would
 * still win). The clear is <i>visible</i>: the state field empties in the same
 * keystroke, in front of the user, and typing a state back in restores full
 * control. Not clearing is the one option that is genuinely silent.</li>
 * </ul>
 * <p>{@code fill()} writes both fields through {@code GuiTextElement.setText},
 * which does not fire the responder, so loading an action never trips the
 * clear.</p>
 */
public class GuiPlaceBlockActionPanel extends GuiBlockActionPanel<PlaceBlockAction>
{
    public GuiTextElement block;
    public GuiTextElement state;
    public GuiTrackpadElement meta;

    public GuiPlaceBlockActionPanel(MinecraftClient mc, GuiRecordingEditorPanel panel)
    {
        super(mc, panel);

        /* 1000, not McLib's default 32: a full state string is routinely longer
         * than that (redstone wire is 170+ characters) and GuiTextField.setText
         * silently truncates past the limit, which would show the user a state
         * that is not the one on disk — and make it the one on disk the moment
         * they typed in the field. */
        this.state = new GuiTextElement(mc, 1000, (str) -> this.action.state = str);
        this.state.tooltip(IKey.lang("blockbuster.gui.record_editor.state"));

        this.block = new GuiTextElement(mc, (str) ->
        {
            this.action.block = str;

            /* See the class doc: a stale state would override the block edit. */
            this.action.state = "";
            this.state.setText("");
        });

        this.meta = new GuiTrackpadElement(mc, (value) -> this.action.metadata = value.byteValue());
        this.meta.tooltip(IKey.lang("blockbuster.gui.record_editor.meta"));

        this.block.flex().set(0, -30, 100, 20).relative(this.meta.resizer());
        this.state.flex().set(0, -30, 100, 20).relative(this.block.resizer());
        this.meta.flex().set(0, -30, 100, 20).relative(this.x.resizer());
        this.meta.limit(0, 15, true);

        this.add(this.block, this.state, this.meta);
    }

    @Override
    public void fill(PlaceBlockAction action)
    {
        super.fill(action);

        this.block.setText(action.block);
        this.state.setText(action.state);
        this.meta.setValue(action.metadata);
    }
}
