package mchorse.mclib.client;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;

/**
 * McLib's immediate-mode debug primitives (roadmap P54 / P75.2).
 *
 * <p>Port of {@code mchorse.mclib.client.Draw} — the {@code cube} /
 * {@code point} / {@code axis} helpers that mchorse mods draw gizmos with. They
 * were originally written against fixed-function GL inside
 * {@code GuiBBModelRenderer}; this class is where they belong (both that
 * viewport and {@code BodyPartRenderer}'s body-part gizmo call them), so the
 * viewport now delegates here rather than owning the geometry.</p>
 *
 * <p>Every method is a no-op without a GL context — the {@code catch} is what
 * lets headless tests construct and drive gizmo-drawing code paths.</p>
 *
 * Legacy source: .tools/legacy-src/mclib/src/main/java/mchorse/mclib/client/Draw.java
 */
public final class Draw
{
    private Draw()
    {
    }

    /**
     * Legacy {@code Draw.point(x, y, z)}: a 12px black point with a 10px white
     * point on top, at the current matrix origin offset by (x, y, z). Core
     * profile has no point draw mode, so two nested cubes keep the 12:10 ratio.
     */
    public static void point(MatrixStack matrices)
    {
        final float outer = 0.012F;
        final float inner = 0.010F;

        cube(matrices, -outer, -outer, -outer, outer, outer, outer, 0F, 0F, 0F, 1F);
        cube(matrices, -inner, -inner, -inner, inner, inner, inner, 1F, 1F, 1F, 1F);
    }

    /**
     * Legacy {@code Draw.axis(length)}: a black 5px underlay, the RGB 3px axis
     * on top, then the origin marker.
     */
    public static void axis(MatrixStack matrices, float length)
    {
        try
        {
            Matrix4f matrix = matrices.peek().getPositionMatrix();
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();

            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            RenderSystem.lineWidth(5);
            buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            buffer.vertex(matrix, 0, 0, 0).color(0F, 0F, 0F, 1F).next();
            buffer.vertex(matrix, length, 0, 0).color(0F, 0F, 0F, 1F).next();
            buffer.vertex(matrix, 0, 0, 0).color(0F, 0F, 0F, 1F).next();
            buffer.vertex(matrix, 0, length, 0).color(0F, 0F, 0F, 1F).next();
            buffer.vertex(matrix, 0, 0, 0).color(0F, 0F, 0F, 1F).next();
            buffer.vertex(matrix, 0, 0, length).color(0F, 0F, 0F, 1F).next();
            tessellator.draw();

            RenderSystem.lineWidth(3);
            buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            buffer.vertex(matrix, 0, 0, 0).color(1F, 0F, 0F, 1F).next();
            buffer.vertex(matrix, length, 0, 0).color(1F, 0F, 0F, 1F).next();
            buffer.vertex(matrix, 0, 0, 0).color(0F, 1F, 0F, 1F).next();
            buffer.vertex(matrix, 0, length, 0).color(0F, 1F, 0F, 1F).next();
            buffer.vertex(matrix, 0, 0, 0).color(0F, 0F, 1F, 1F).next();
            buffer.vertex(matrix, 0, 0, length).color(0F, 0F, 1F, 1F).next();
            tessellator.draw();

            RenderSystem.lineWidth(1);

            point(matrices);
        }
        catch (Exception e)
        {
            /* No GL context — the axis simply doesn't render. */
        }
    }

    /**
     * Port of McLib's {@code Draw.cube} — a filled quad cube in the legacy face
     * order (top, bottom, left, right, front, back) and vertex winding, emitted
     * through the {@link VertexFormats#POSITION_COLOR} shader (blend on, so the
     * legacy {@code enableAlpha}/{@code enableBlend} bracket is preserved).
     */
    public static void cube(MatrixStack matrices, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, float red, float green, float blue, float alpha)
    {
        try
        {
            Matrix4f m = matrices.peek().getPositionMatrix();
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();

            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

            /* Top */
            buffer.vertex(m, minX, maxY, minZ).color(red, green, blue, alpha).next();
            buffer.vertex(m, minX, maxY, maxZ).color(red, green, blue, alpha).next();
            buffer.vertex(m, maxX, maxY, maxZ).color(red, green, blue, alpha).next();
            buffer.vertex(m, maxX, maxY, minZ).color(red, green, blue, alpha).next();

            /* Bottom */
            buffer.vertex(m, minX, minY, minZ).color(red, green, blue, alpha).next();
            buffer.vertex(m, maxX, minY, minZ).color(red, green, blue, alpha).next();
            buffer.vertex(m, maxX, minY, maxZ).color(red, green, blue, alpha).next();
            buffer.vertex(m, minX, minY, maxZ).color(red, green, blue, alpha).next();

            /* Left */
            buffer.vertex(m, minX, maxY, minZ).color(red, green, blue, alpha).next();
            buffer.vertex(m, minX, minY, minZ).color(red, green, blue, alpha).next();
            buffer.vertex(m, minX, minY, maxZ).color(red, green, blue, alpha).next();
            buffer.vertex(m, minX, maxY, maxZ).color(red, green, blue, alpha).next();

            /* Right */
            buffer.vertex(m, maxX, maxY, minZ).color(red, green, blue, alpha).next();
            buffer.vertex(m, maxX, maxY, maxZ).color(red, green, blue, alpha).next();
            buffer.vertex(m, maxX, minY, maxZ).color(red, green, blue, alpha).next();
            buffer.vertex(m, maxX, minY, minZ).color(red, green, blue, alpha).next();

            /* Front */
            buffer.vertex(m, minX, maxY, minZ).color(red, green, blue, alpha).next();
            buffer.vertex(m, maxX, maxY, minZ).color(red, green, blue, alpha).next();
            buffer.vertex(m, maxX, minY, minZ).color(red, green, blue, alpha).next();
            buffer.vertex(m, minX, minY, minZ).color(red, green, blue, alpha).next();

            /* Back */
            buffer.vertex(m, minX, maxY, maxZ).color(red, green, blue, alpha).next();
            buffer.vertex(m, minX, minY, maxZ).color(red, green, blue, alpha).next();
            buffer.vertex(m, maxX, minY, maxZ).color(red, green, blue, alpha).next();
            buffer.vertex(m, maxX, maxY, maxZ).color(red, green, blue, alpha).next();

            tessellator.draw();
        }
        catch (Exception e)
        {
            /* No GL context — the highlight simply doesn't render. */
        }
    }
}
