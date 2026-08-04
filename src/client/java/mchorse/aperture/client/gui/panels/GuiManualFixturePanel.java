package mchorse.aperture.client.gui.panels;

import mchorse.aperture.ClientProxy;
import mchorse.aperture.camera.data.RenderFrame;
import mchorse.aperture.camera.fixtures.ManualFixture;
import mchorse.aperture.client.gui.GuiCameraEditor;
import mchorse.aperture.utils.APIcons;
import mchorse.mclib.client.gui.framework.GuiBase;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTrackpadElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.Elements;
import mchorse.mclib.client.gui.utils.LegacyKeyCodes;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.utils.Timer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;

import java.util.List;

/**
 * Manual fixture panel.
 *
 * Quirk (load-bearing): the recording state machine is all static fields
 * (recording/duration/tick/offset/timer) shared across handlers — recording
 * survives the editor being closed (Ctrl+R closes it) and the recorded
 * frames get committed as an undo on editor REOPEN, not when the recording
 * stops (see {@link #cameraEditorOpened()}).
 *
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/client/gui/panels/GuiManualFixturePanel.java
 * (drawHUD gained a {@link DrawContext} parameter — the 1.20.4 HUD hook
 * draws through a context instead of global GL state.)
 */
public class GuiManualFixturePanel extends GuiAbstractFixturePanel<ManualFixture>
{
    public static boolean recording;
    public static int duration;
    public static int tick;
    public static int offset;
    public static Timer timer = new Timer(3000);

    public GuiTrackpadElement shift;
    public GuiTrackpadElement speed;
    public GuiButtonElement record;

    public static void update()
    {
        if (!recording)
        {
            return;
        }

        if (tick >= duration)
        {
            ClientProxy.openCameraEditor();
        }
        else
        {
            tick ++;
        }
    }

    /**
     * Draw manual fixture related HUD elements, like countdown and
     * recording overlays
     */
    public static void drawHUD(DrawContext context, int w, int h)
    {
        /* Headless seam: mc/context are null in unit tests — the state
         * transition below still runs, only the drawing is skipped */
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer font = mc == null ? null : mc.textRenderer;

        GuiDraw.bindDrawContext(context);

        if (timer.checkReset())
        {
            recording = true;
            ClientProxy.getCameraEditor().postPlayback(offset, true);
        }
        else if (timer.enabled && context != null && font != null)
        {
            long remaining = timer.getRemaining();
            float factor = (remaining % 1000L) / 1000F * 3;

            MatrixStack matrices = context.getMatrices();

            matrices.push();
            matrices.translate(w / 2, h / 2, 0);
            matrices.scale(factor, factor, 1);

            String label = String.valueOf(remaining / 1000L + 1);

            GuiDraw.drawStringWithShadow(font, label, -font.getWidth(label) / 2, -4, 0xffffff);

            matrices.pop();
        }

        if (recording && context != null && font != null)
        {
            String caption = "Recording§r (§l" + tick + "§r)";

            APIcons.RECORD.render(4, 4, 0, 0);
            GuiDraw.drawStringWithShadow(font, caption, 22, 8, 0xffffffff);
        }
    }

    public GuiManualFixturePanel(MinecraftClient mc, GuiCameraEditor editor)
    {
        super(mc, editor);

        this.shift = new GuiTrackpadElement(mc, (v) -> this.editor.postUndo(this.undo(this.fixture.shift, v.intValue())));
        this.shift.integer().tooltip(IKey.lang("aperture.gui.panels.manual.shift"));

        this.speed = new GuiTrackpadElement(mc, (v) -> this.editor.postUndo(this.undo(this.fixture.speed, v.floatValue())));
        this.speed.limit(0).tooltip(IKey.lang("aperture.gui.panels.manual.speed"));

        this.record = new GuiButtonElement(mc, IKey.lang("aperture.gui.record"), this::startRecording);
        this.record.tooltip(IKey.lang("aperture.gui.panels.manual.record"));

        this.left.add(Elements.label(IKey.lang("aperture.gui.panels.manual.title")).background(), this.shift, this.speed, this.record);

        this.keys().register(IKey.lang("aperture.gui.panels.keys.record_manual"), LegacyKeyCodes.KEY_R, () -> this.record.clickItself(GuiBase.getCurrent())).held(LegacyKeyCodes.KEY_LCONTROL).active(editor::isFlightDisabled).category(CATEGORY);
    }

    @Override
    public void select(ManualFixture fixture, long duration)
    {
        super.select(fixture, duration);

        this.shift.setValue(fixture.shift.get());
        this.speed.setValue(fixture.speed.get());
    }

    private void startRecording(GuiButtonElement button)
    {
        offset = (int) this.editor.getProfile().calculateOffset(this.fixture);
        duration = (int) this.fixture.getDuration();
        tick = 0;
        timer.mark();

        this.editor.postRewind(offset);
        this.editor.exit();
    }

    @Override
    public void cameraEditorOpened()
    {
        super.cameraEditorOpened();

        if (recording)
        {
            recording = false;

            if (tick > 0)
            {
                List<List<RenderFrame>> frames = this.fixture.setupRecorded();

                if (frames != null)
                {
                    this.editor.postUndo(this.undo(this.fixture.frames, frames));
                }
            }
        }
        else
        {
            timer.reset();
        }
    }

    public void recordFrame(ClientPlayerEntity player, float partialTicks)
    {
        this.fixture.recordFrame(player, partialTicks);
    }
}
