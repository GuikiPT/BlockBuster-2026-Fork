package mchorse.aperture.client.gui.panels.modifiers.widgets;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.aperture.camera.CameraProfile;
import mchorse.aperture.camera.fixtures.AbstractFixture;
import mchorse.aperture.camera.smooth.Envelope;
import mchorse.aperture.client.gui.panels.modifiers.GuiAbstractModifierPanel;
import mchorse.aperture.client.gui.utils.GuiCameraEditorKeyframesGraphEditor;
import mchorse.aperture.client.gui.utils.undo.ModifierValueChangeUndo;
import mchorse.aperture.utils.TimeUtils;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiIconElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiToggleElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTrackpadElement;
import mchorse.mclib.client.gui.framework.elements.list.GuiInterpolationList;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.framework.tooltips.InterpolationTooltip;
import mchorse.mclib.client.gui.utils.Area;
import mchorse.mclib.client.gui.utils.Elements;
import mchorse.mclib.client.gui.utils.Icons;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.config.values.Value;
import mchorse.mclib.utils.Color;
import mchorse.mclib.utils.ColorUtils;
import mchorse.mclib.utils.Direction;
import mchorse.mclib.utils.Interpolation;
import mchorse.mclib.utils.Interpolations;
import mchorse.mclib.utils.undo.CompoundUndo;
import mchorse.mclib.utils.undo.IUndo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Envelope editor widget (P185).
 *
 * <p>Enabled/relative toggles, interpolation picker, start/end X + duration
 * trackpads (time-unit aware). Keyframes mode swaps the four trackpads for a
 * 200 px channel graph. Draws a live envelope curve (legacy
 * {@code GL_LINE_STRIP}; ported to the fabric {@code DEBUG_LINES} pair
 * technique — the strip is accumulated then emitted as consecutive segments
 * so the per-vertex gradient is preserved) with hover-highlighted segments and
 * a green factor cursor.</p>
 *
 * <p>Quirk (load-bearing): toggling {@code relative} rewrites
 * {@code endX = duration - endX} as part of a compound undo.</p>
 *
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/client/gui/panels/modifiers/widgets/GuiEnvelope.java
 */
public class GuiEnvelope extends GuiElement
{
    public static final int regular = 0xff000000;
    public static final int selected = 0xffff0000;

    public GuiAbstractModifierPanel panel;

    public GuiElement row;
    public GuiToggleElement enabled;
    public GuiToggleElement relative;
    public GuiIconElement pickInterp;
    public GuiTrackpadElement startX;
    public GuiTrackpadElement startD;
    public GuiTrackpadElement endX;
    public GuiTrackpadElement endD;
    public GuiInterpolationList interps;

    public GuiToggleElement keyframes;
    public GuiCameraEditorKeyframesGraphEditor channel;

    private Color color = new Color();

    /** Accumulated strip vertices for the curve, emitted as line pairs. */
    private final List<float[]> verts = new ArrayList<float[]>();

    public GuiEnvelope(MinecraftClient mc, GuiAbstractModifierPanel panel)
    {
        super(mc);

        this.panel = panel;

        InterpolationTooltip tooltip = new InterpolationTooltip(0F, 0F, () -> this.get().interpolation.get(), null);

        this.enabled = new GuiToggleElement(mc, IKey.lang("aperture.gui.modifiers.enabled"), (b) ->
        {
            this.panel.modifiers.editor.postUndo(this.undo(this.get().enabled, b.isToggled()));
        });
        this.relative = new GuiToggleElement(mc, IKey.lang("aperture.gui.modifiers.panels.relative"), (b) -> this.toggleRelative(b.isToggled()));
        this.pickInterp = new GuiIconElement(mc, Icons.GEAR, (b) -> this.interps.toggleVisible());
        this.pickInterp.tooltip(IKey.lang("aperture.gui.modifiers.envelopes.interp"));

        this.startX = new GuiTrackpadElement(mc, (value) ->
        {
            this.panel.modifiers.editor.postUndo(this.undo(this.get().startX, (float) TimeUtils.fromTime(value.floatValue())));
        });
        this.startX.tooltip(IKey.lang("aperture.gui.modifiers.envelopes.start_x"), Direction.TOP);
        this.startD = new GuiTrackpadElement(mc, (value) ->
        {
            this.panel.modifiers.editor.postUndo(this.undo(this.get().startDuration, (float) TimeUtils.fromTime(value.floatValue())));
        });
        this.startD.tooltip(IKey.lang("aperture.gui.modifiers.envelopes.start_d"), Direction.TOP);
        this.endX = new GuiTrackpadElement(mc, (value) ->
        {
            this.panel.modifiers.editor.postUndo(this.undo(this.get().endX, (float) TimeUtils.fromTime(value.floatValue())));
        });
        this.endX.tooltip(IKey.lang("aperture.gui.modifiers.envelopes.end_x"), Direction.TOP);
        this.endD = new GuiTrackpadElement(mc, (value) ->
        {
            this.panel.modifiers.editor.postUndo(this.undo(this.get().endDuration, (float) TimeUtils.fromTime(value.floatValue())));
        });
        this.endD.tooltip(IKey.lang("aperture.gui.modifiers.envelopes.end_d"), Direction.TOP);
        this.interps = new GuiInterpolationList(mc, (l) ->
        {
            this.panel.modifiers.editor.postUndo(this.undo(this.get().interpolation, l.get(0)));
        });
        this.interps.tooltip(tooltip).setVisible(false);

        this.keyframes = new GuiToggleElement(mc, IKey.lang("aperture.gui.modifiers.panels.keyframes"), (b) ->
        {
            this.panel.modifiers.editor.postUndo(this.undo(this.get().keyframes, b.isToggled()));
            this.toggleKeyframes(b.isToggled());
        });
        this.channel = new GuiCameraEditorKeyframesGraphEditor(mc, panel.modifiers.editor);

        this.enabled.flex().reset();
        this.relative.flex().reset();

        this.row = Elements.row(mc, 5, 0, 20, this.enabled, this.relative, this.pickInterp);
        this.row.flex().relative(this).xy(10, 10).w(1F, -20);

        this.startX.flex().relative(this.enabled).xy(0, 20).w(1F);
        this.startD.flex().relative(this.startX).xy(0, 25).w(1F);
        this.endX.flex().relative(this.relative).xy(0, 20).w(1F);
        this.endD.flex().relative(this.endX).xy(0, 25).w(1F);
        this.interps.flex().relative(this.pickInterp).xy(1F, 1F).w(110).hTo(this.area, 1F).anchor(1F, 0F);

        this.channel.flex().relative(this.enabled).y(20).wTo(this.row.area, 1F).h(200);
    }

    public IUndo<CameraProfile> undo(Value value, Object newValue)
    {
        CameraProfile profile = this.panel.modifiers.editor.getProfile();
        AbstractFixture fixture = this.panel.modifiers.fixture;
        int index = -1;

        if (fixture != null)
        {
            index = profile.fixtures.indexOf(fixture);
        }

        return new ModifierValueChangeUndo(index, this.panel.modifiers.panels.scroll.scroll, value.getPath(), value.getValue(), newValue).view(this.panel.modifiers.editor.timeline);
    }

    private void toggleKeyframes(boolean toggled)
    {
        this.removeAll();
        this.row.removeAll();

        if (toggled)
        {
            this.row.add(this.enabled);
            this.add(this.row, this.channel, this.keyframes);

            this.flex().h(255);
            this.keyframes.flex().reset().relative(this.channel).y(1F).w(1F).h(20);
        }
        else
        {
            this.row.add(this.enabled, this.relative, this.pickInterp);
            this.add(this.row, this.startX, this.startD, this.endX, this.endD, this.keyframes, this.interps);

            this.flex().h(110);
            this.keyframes.flex().reset().relative(this.startD).y(1F).wTo(this.endD.area, 1F).h(20);
        }

        if (this.getParent() != null)
        {
            this.getParent().getParent().resize();

            if (toggled)
            {
                this.initiate();
            }
        }
    }

    public void initiate()
    {
        this.updateDuration();
        this.channel.graph.resetView();
        this.channel.updateConverter();

        TimeUtils.configure(this.startX, Integer.MIN_VALUE);
        TimeUtils.configure(this.startD, 0);
        TimeUtils.configure(this.endX, Integer.MIN_VALUE);
        TimeUtils.configure(this.endD, 0);

        this.fillIntervals();
    }

    public void wasToggled()
    {
        this.channel.graph.resetView();
    }

    public void fillData()
    {
        Envelope envelope = this.get();

        this.enabled.toggled(envelope.enabled.get());
        this.relative.toggled(envelope.relative.get());
        this.fillIntervals();
        this.interps.setCurrentScroll(envelope.interpolation.get());
        this.keyframes.toggled(envelope.keyframes.get());
        this.channel.setChannel(envelope.channel, 0x0088ff);

        this.toggleKeyframes(envelope.keyframes.get());
    }

    private void fillIntervals()
    {
        Envelope envelope = this.get();

        this.startX.setValue(TimeUtils.toTime(envelope.startX.get().intValue()));
        this.startD.setValue(TimeUtils.toTime(envelope.startDuration.get().intValue()));
        this.endX.setValue(TimeUtils.toTime(envelope.endX.get().intValue()));
        this.endD.setValue(TimeUtils.toTime(envelope.endDuration.get().intValue()));
    }

    public void updateDuration()
    {
        this.channel.graph.duration = (int) this.getDuration();
    }

    private void toggleRelative(boolean toggled)
    {
        Envelope envelope = this.get();

        this.panel.modifiers.editor.postUndo(new CompoundUndo<CameraProfile>(
            this.undo(envelope.relative, toggled),
            this.undo(envelope.endX, this.getDuration() - envelope.endX.get())
        ));

        this.fillIntervals();
    }

    public long getDuration()
    {
        if (this.panel.modifiers.fixture == null)
        {
            return this.panel.modifiers.editor.getProfile().getDuration();
        }

        return this.panel.modifiers.fixture.getDuration();
    }

    public Envelope get()
    {
        return this.panel.modifier.envelope.get();
    }

    @Override
    public void draw(GuiContext context)
    {
        if (this.interps.isVisible())
        {
            this.pickInterp.area.draw(ColorUtils.HALF_BLACK);
        }

        if (!this.get().keyframes.get())
        {
            /* Draw an approximate visualisation of the envelope. Legacy used a
             * raw GL_LINE_STRIP; here we accumulate the strip and emit it as
             * consecutive DEBUG_LINES pairs (headless: skipped, geometry only
             * exists on a real frame). */
            this.verts.clear();
            this.drawGraph(context, this.get());
            this.flushCurve();

            /* Draw cursor */
            int x = this.panel.modifiers.editor.timeline.value;

            if (this.panel.modifiers.fixture != null)
            {
                x -= this.panel.modifiers.editor.getProfile().calculateOffset(this.panel.modifiers.fixture);
            }

            float factor = this.get().factor(this.getDuration(), x);

            x = this.getX(x);

            if (x >= this.area.x && x < this.area.ex())
            {
                GuiDraw.drawRect(x, this.area.ey() - 3 - (int) (9 * factor), x + 2, this.area.ey(), 0xff57f52a);
            }
        }

        super.draw(context);
    }

    /**
     * Emit the accumulated strip vertices as line pairs (fabric technique;
     * legacy drew them as a single {@code GL_LINE_STRIP}).
     */
    private void flushCurve()
    {
        if (GuiDraw.getDrawContext() == null || this.verts.size() < 2)
        {
            return;
        }

        Matrix4f m = GuiDraw.getDrawContext().getMatrices().peek().getPositionMatrix();

        RenderSystem.lineWidth(2);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        BufferBuilder buffer = Tessellator.getInstance().getBuffer();

        buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i + 1 < this.verts.size(); i++)
        {
            float[] a = this.verts.get(i);
            float[] b = this.verts.get(i + 1);

            buffer.vertex(m, a[0], a[1], 0F).color(a[2], a[3], a[4], a[5]).next();
            buffer.vertex(m, b[0], b[1], 0F).color(b[2], b[3], b[4], b[5]).next();
        }

        Tessellator.getInstance().draw();
        RenderSystem.lineWidth(1);
    }

    private void vertex(float x, float y)
    {
        this.verts.add(new float[] {x, y, this.color.r, this.color.g, this.color.b, this.color.a});
    }

    /**
     * Accumulate the graph strip at the bottom of the envelope panel.
     */
    private void drawGraph(GuiContext context, Envelope envelope)
    {
        long duration = this.getDuration();
        int startX = this.getX(envelope.getStartX(duration));
        int startD = this.getX(envelope.getStartDuration(duration));
        int endX = this.getX(envelope.getEndX(duration));
        int endD = this.getX(envelope.getEndDuration(duration));
        int sy = this.area.ey() - 1;
        int ey = sy - 10;

        if (startD > this.area.x)
        {
            this.color.set(this.startX.area.isInside(context) ? selected : regular, true);
            this.vertex(this.area.x, sy);
            this.vertex(startX, sy);
            this.drawFades(context, envelope.interpolation.get(), this.startD.area, startX, startD, sy, ey);
        }

        this.color.set(this.startD.area.isInside(context) ? selected : regular, true);
        this.vertex(startD, ey);
        this.color.set(regular, true);
        this.vertex(Interpolations.lerp(startD, endD, 0.5F), ey);
        this.color.set(this.endD.area.isInside(context) ? selected : regular, true);
        this.vertex(endD, ey);

        if (endD < this.area.ex())
        {
            this.drawFades(context, envelope.interpolation.get(), this.endX.area, endD, endX, ey, sy);
            this.color.set(this.endX.area.isInside(context) ? selected : regular, true);
            this.vertex(endX, sy);
            this.vertex(this.area.ex(), sy);
        }
    }

    /**
     * Accumulate fading lines for the graph
     */
    private void drawFades(GuiContext context, Interpolation interp, Area area, int startX, int startD, int a, int b)
    {
        for (int i = 1; i < 10; i++)
        {
            float x = Interpolations.lerp(startX, startD, i / 10F);
            float y = interp.interpolate(a, b, i / 10F);

            if (i == 6)
            {
                this.color.set(area.isInside(context) ? selected : regular, true);
            }

            this.vertex(x, y);
        }
    }

    private int getX(float value)
    {
        long duration = this.getDuration();
        float factor = value / (float) duration;

        return this.area.x + (int) (factor * this.area.w);
    }

    public void updateVisibility()
    {
        this.panel.modifiers.editor.postUndo(this.undo(this.get().visible, !this.get().visible.get()), false, false);
        this.get().visible.set(!this.get().visible.get());
    }
}
