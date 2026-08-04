package mchorse.blockbuster.client.gui.dashboard.panels.model_editor.tabs;

import mchorse.blockbuster.client.gui.dashboard.panels.model_editor.utils.GuiThreeElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.framework.elements.modals.GuiModal;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.utils.LegacyKeyCodes;
import mchorse.mclib.client.gui.utils.keys.IKey;
import net.minecraft.client.MinecraftClient;

import javax.vecmath.Vector3f;
import java.util.function.Consumer;

/**
 * Anchor-point picker modal (roadmap P137) — three 0..1 trackpads with a
 * {@code 0.1} increment, an OK button, and Enter-to-confirm.
 *
 * <p>Legacy source (ported 1:1):
 * {@code blockbuster-1.12/.../model_editor/tabs/GuiAnchorModal.java}
 * ({@code Minecraft} &rarr; {@code MinecraftClient};
 * {@code Keyboard.KEY_RETURN} &rarr; {@link LegacyKeyCodes#KEY_RETURN}, matching
 * the ported {@code GuiPromptModal}).</p>
 *
 * <p><b>Live preview.</b> While the modal is open the model viewport draws the
 * anchor point at the modal's current vector. Legacy stored the modal itself in
 * {@code GuiBBModelRenderer.anchorPreview} and read
 * {@code anchorPreview.vector.a.value} back on every frame; the ported renderer
 * holds a plain {@code float[3]} instead (it landed before this tab did), so the
 * same liveness is achieved by having the trackpad callback mirror the values
 * into {@link #preview} — the array the renderer is handed. A trackpad only
 * changes through user interaction, which always fires the callback, so the two
 * are equivalent.</p>
 */
public class GuiAnchorModal extends GuiModal
{
    public Consumer<Vector3f> callback;

    public GuiThreeElement vector;
    public GuiButtonElement confirm;

    /**
     * The live vector, mirrored out of the trackpads for
     * {@code GuiBBModelRenderer.anchorPreview}. See the class javadoc.
     */
    public final float[] preview = new float[] {0F, 0F, 0F};

    public GuiAnchorModal(MinecraftClient mc, IKey label, Consumer<Vector3f> callback)
    {
        super(mc, label);

        this.callback = callback;
        this.vector = new GuiThreeElement(mc, (values) ->
        {
            this.preview[0] = values[0].floatValue();
            this.preview[1] = values[1].floatValue();
            this.preview[2] = values[2].floatValue();
        });
        this.vector.setLimit(0, 1, false);
        this.vector.a.increment(0.1).values(0.05, 0.01, 0.1);
        this.vector.b.increment(0.1).values(0.05, 0.01, 0.1);
        this.vector.c.increment(0.1).values(0.05, 0.01, 0.1);
        this.vector.flex().relative(this).set(10, 0, 0, 20).y(1, -55).w(1, -20);

        this.confirm = new GuiButtonElement(mc, IKey.lang("mclib.gui.ok"), (b) -> this.send());

        this.bar.add(this.confirm);
        this.add(this.vector);
    }

    public void send()
    {
        this.removeFromParent();

        if (this.callback != null)
        {
            this.callback.accept(new Vector3f((float) this.vector.a.value, (float) this.vector.b.value, (float) this.vector.c.value));
        }
    }

    @Override
    public boolean keyTyped(GuiContext context)
    {
        if (super.keyTyped(context))
        {
            return true;
        }

        if (context.keyCode == LegacyKeyCodes.KEY_RETURN)
        {
            this.confirm.clickItself(context);

            return true;
        }

        return false;
    }
}
