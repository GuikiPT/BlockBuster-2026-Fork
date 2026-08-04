package mchorse.blockbuster_pack.morphs.structure;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;

/**
 * A {@link VertexConsumer} that transforms incoming positions and normals by a
 * captured {@link MatrixStack.Entry} before delegating (roadmap P162, fluid
 * parity).
 *
 * <p>Exists for {@code FluidRenderer}: block models transform through the
 * matrix stack handed to {@code BlockModelRenderer}, but fluid geometry is
 * emitted straight into the consumer at chunk-section-local coordinates — in a
 * chunk rebuild the section origin is applied at draw time, which a morph
 * render can't do. Wrapping the buffer applies the current pose per vertex
 * instead.</p>
 *
 * <p>Every fluent method returns {@code this}, never the delegate — the fluid
 * vertex chain ends {@code ...light(n).normal(x,y,z).next()}, and the normal
 * must still be caught and rotated at the end of the chain.</p>
 */
@Environment(EnvType.CLIENT)
public class MatrixVertexConsumer implements VertexConsumer
{
    private final VertexConsumer delegate;
    private final MatrixStack.Entry entry;

    public MatrixVertexConsumer(VertexConsumer delegate, MatrixStack.Entry entry)
    {
        this.delegate = delegate;
        this.entry = entry;
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z)
    {
        this.delegate.vertex(this.entry.getPositionMatrix(), (float) x, (float) y, (float) z);

        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha)
    {
        this.delegate.color(red, green, blue, alpha);

        return this;
    }

    @Override
    public VertexConsumer texture(float u, float v)
    {
        this.delegate.texture(u, v);

        return this;
    }

    @Override
    public VertexConsumer overlay(int u, int v)
    {
        this.delegate.overlay(u, v);

        return this;
    }

    @Override
    public VertexConsumer light(int u, int v)
    {
        this.delegate.light(u, v);

        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z)
    {
        this.delegate.normal(this.entry.getNormalMatrix(), x, y, z);

        return this;
    }

    @Override
    public void next()
    {
        this.delegate.next();
    }

    @Override
    public void fixedColor(int red, int green, int blue, int alpha)
    {
        this.delegate.fixedColor(red, green, blue, alpha);
    }

    @Override
    public void unfixColor()
    {
        this.delegate.unfixColor();
    }
}
