package mchorse.blockbuster.client.gui.dashboard.panels.snowstorm;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.blockbuster.client.particles.BedrockScheme;
import mchorse.blockbuster.client.particles.components.expiration.BedrockComponentKillPlane;
import mchorse.blockbuster.client.particles.emitter.BedrockEmitter;
import mchorse.mclib.client.gui.framework.elements.GuiModelRenderer;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;

import javax.vecmath.Vector3f;

/**
 * Port of Blockbuster 2.7.2's {@code GuiSnowstormRenderer} (roadmap P155) — the
 * orbit-camera viewport the Snowstorm editor previews an emitter in.
 *
 * <p>Legacy source: blockbuster-1.12/src/main/java/mchorse/blockbuster/client/gui/dashboard/panels/snowstorm/GuiSnowstormRenderer.java</p>
 *
 * <p>Reuses the {@link GuiModelRenderer} orbit camera and owns a
 * {@link BedrockEmitter} whose simulation is ticked ({@link BedrockEmitter#update()})
 * each frame while {@link #playing}. {@link #drawUserModel(GuiContext)} draws the
 * RGB axis (depth test off, 3px lines), the emitter (frozen at partialTicks 1 when
 * paused), and — when the {@link BedrockComponentKillPlane} is non-trivial — a
 * translucent green (0,1,0,0.5) 10×10 quad solving {@code ax + by + cz + d = 0}
 * (solve axis chosen by the first nonzero of <b>b, then a, then c</b>). Legacy
 * immediate-mode GL becomes {@link RenderSystem}/{@link Tessellator} draws.</p>
 */
public class GuiSnowstormRenderer extends GuiModelRenderer
{
    public BedrockEmitter emitter;

    public boolean playing = true;

    private Vector3f vector = new Vector3f(0, 0, 0);

    public GuiSnowstormRenderer(MinecraftClient mc)
    {
        super(mc);

        this.emitter = new BedrockEmitter();
    }

    public void setScheme(BedrockScheme scheme)
    {
        this.emitter = new BedrockEmitter();
        this.emitter.setScheme(scheme);
        this.playing = true;
    }

    @Override
    protected void update()
    {
        super.update();

        if (this.playing && this.emitter != null)
        {
            this.emitter.rotation.setIdentity();
            this.emitter.update();
        }
    }

    @Override
    protected void drawUserModel(GuiContext context)
    {
        if (this.emitter == null || this.emitter.scheme == null)
        {
            return;
        }

        this.emitter.cYaw = this.yaw;
        this.emitter.cPitch = this.pitch;
        this.emitter.cX = this.temp.x;
        this.emitter.cY = this.temp.y;
        this.emitter.cZ = this.temp.z;
        this.emitter.perspective = 100;
        this.emitter.rotation.setIdentity();

        /* Axis — legacy Draw.axis(1F) bracketed by disableDepth + glLineWidth(3) */
        RenderSystem.disableDepthTest();
        this.drawAxis();
        RenderSystem.enableDepthTest();

        this.emitter.render(this.playing ? context.partialTicks : 1);

        BedrockComponentKillPlane plane = this.emitter.scheme.get(BedrockComponentKillPlane.class);

        if (plane != null && (plane.a != 0 || plane.b != 0 || plane.c != 0))
        {
            this.drawKillPlane(plane.a, plane.b, plane.c, plane.d);
        }
    }

    private void drawAxis()
    {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(3);
        buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        buffer.vertex(0, 0, 0).color(1F, 0F, 0F, 1F).next();
        buffer.vertex(1, 0, 0).color(1F, 0F, 0F, 1F).next();
        buffer.vertex(0, 0, 0).color(0F, 1F, 0F, 1F).next();
        buffer.vertex(0, 1, 0).color(0F, 1F, 0F, 1F).next();
        buffer.vertex(0, 0, 0).color(0F, 0F, 1F, 1F).next();
        buffer.vertex(0, 0, 1).color(0F, 0F, 1F, 1F).next();

        tessellator.draw();
        RenderSystem.lineWidth(1);
    }

    private void drawKillPlane(float a, float b, float c, float d)
    {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        this.calculate(0, 0, a, b, c, d);
        buffer.vertex(this.vector.x, this.vector.y, this.vector.z).color(0F, 1F, 0F, 0.5F).next();
        this.calculate(1, 0, a, b, c, d);
        buffer.vertex(this.vector.x, this.vector.y, this.vector.z).color(0F, 1F, 0F, 0.5F).next();
        this.calculate(1, 1, a, b, c, d);
        buffer.vertex(this.vector.x, this.vector.y, this.vector.z).color(0F, 1F, 0F, 0.5F).next();
        this.calculate(0, 1, a, b, c, d);
        buffer.vertex(this.vector.x, this.vector.y, this.vector.z).color(0F, 1F, 0F, 0.5F).next();

        tessellator.draw();
    }

    private void calculate(float i, float j, float a, float b, float c, float d)
    {
        final float radius = 5;

        if (b != 0)
        {
            this.vector.x = -radius + radius * 2 * i;
            this.vector.z = -radius + radius * 2 * j;
            this.vector.y = (a * this.vector.x + c * this.vector.z + d) / -b;
        }
        else if (a != 0)
        {
            this.vector.y = -radius + radius * 2 * i;
            this.vector.z = -radius + radius * 2 * j;
            this.vector.x = (b * this.vector.y + c * this.vector.z + d) / -a;
        }
        else if (c != 0)
        {
            this.vector.x = -radius + radius * 2 * i;
            this.vector.y = -radius + radius * 2 * j;
            this.vector.z = (b * this.vector.y + a * this.vector.x + d) / -c;
        }
    }
}
