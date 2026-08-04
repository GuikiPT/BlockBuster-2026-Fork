package mchorse.metamorph.client.gui.creative;

import mchorse.mclib.client.gui.framework.GuiBase;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.utils.LegacyKeyCodes;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.client.MinecraftClient;

import java.util.function.Consumer;

/**
 * Nested morph edit widget (port of Metamorph 1.4's
 * {@code client/gui/creative/GuiNestedEdit}, roadmap P58).
 *
 * <p>A 20 px tall row of two buttons — <em>Pick</em> and <em>Edit</em> — which
 * Blockbuster reuses everywhere a morph is embedded in another editor (body
 * parts, sequencer frames, scene replays, the actor/director panels…). Both
 * buttons funnel into a single {@code Consumer<Boolean>}: {@code true} means
 * "edit the nested morph", {@code false} means "pick a different morph". The
 * host — {@code GuiCreativeMorphsList} (SEAM P58) — decides what that means; on
 * "edit" it pushes a frame onto its own {@code nestedEdits} stack
 * ({@link GuiCreativeMorphsList#nestEdit}) and dives in, and ESC/exit
 * ({@link GuiCreativeMorphsList#restoreEdit}) pops it back. This widget
 * deliberately owns <b>no</b> stack state of its own, exactly like legacy.</p>
 *
 * <p>Layout is verbatim legacy: {@code flex().h(20).row(0)} on the container
 * with both children {@code relative(this).h(1F)}, so the two buttons split the
 * container's width evenly with no gutter. Children are added
 * {@code pick, edit} (pick first, i.e. left) while the fields are constructed
 * {@code edit} first — keep the order, the row resizer lays out in child
 * order.</p>
 *
 * <p>Optional keybinds (on by default): {@code P} clicks Pick, {@code E} clicks
 * Edit. Legacy dispatched them through {@code clickItself(GuiBase.getCurrent())}
 * so the buttons run their normal click path (including the disabled check) —
 * kept as-is; LWJGL2 {@code Keyboard.KEY_P/KEY_E} map to
 * {@link LegacyKeyCodes#KEY_P}/{@link LegacyKeyCodes#KEY_E} (the framework
 * translates GLFW keycodes back to the legacy codes in {@code GuiBase}).</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/client/gui/creative/GuiNestedEdit.java
 */
public class GuiNestedEdit extends GuiElement
{
    public GuiButtonElement pick;
    public GuiButtonElement edit;

    public GuiNestedEdit(MinecraftClient mc, Consumer<Boolean> callback)
    {
        this(mc, true, callback);
    }

    public GuiNestedEdit(MinecraftClient mc, boolean keybinds, Consumer<Boolean> callback)
    {
        super(mc);

        this.edit = new GuiButtonElement(mc, IKey.lang("metamorph.gui.creative.edit"), (b) -> callback.accept(true));
        this.pick = new GuiButtonElement(mc, IKey.lang("metamorph.gui.creative.pick"), (b) -> callback.accept(false));

        this.edit.flex().relative(this).h(1F);
        this.pick.flex().relative(this).h(1F);

        this.flex().h(20).row(0);
        this.add(this.pick, this.edit);

        if (keybinds)
        {
            this.keys().register(this.pick.label, LegacyKeyCodes.KEY_P, () -> this.pick.clickItself(GuiBase.getCurrent()));
            this.keys().register(this.edit.label, LegacyKeyCodes.KEY_E, () -> this.edit.clickItself(GuiBase.getCurrent()));
        }
    }

    /**
     * Edit is only clickable when there is a nested morph to edit; Pick stays
     * enabled so an empty slot can be filled.
     */
    public void setMorph(AbstractMorph morph)
    {
        this.edit.setEnabled(morph != null);
    }
}
