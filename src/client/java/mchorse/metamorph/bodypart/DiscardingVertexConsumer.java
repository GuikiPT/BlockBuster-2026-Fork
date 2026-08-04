package mchorse.metamorph.bodypart;

import net.minecraft.client.render.VertexConsumer;

/**
 * A {@link VertexConsumer} that accepts every call and emits nothing
 * (roadmap P54).
 *
 * <p>Used by {@link BodyPartRenderer#recordMatrix}, whose render pass exists
 * only to walk the model and let each body part stamp its limb matrix. Legacy
 * relied on {@code BodyPart.recording} making every part return before it drew,
 * with the pass bracketed by {@code glPushMatrix/glLoadIdentity} so anything
 * that <i>did</i> slip through landed off-screen. Feeding the pass a null sink
 * instead is the 1.20.4 equivalent and is stricter: a morph type that ignores
 * the recording flag cannot paint over the frame the editor is showing.</p>
 */
public final class DiscardingVertexConsumer implements VertexConsumer
{
    public static final DiscardingVertexConsumer INSTANCE = new DiscardingVertexConsumer();

    private DiscardingVertexConsumer()
    {
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z)
    {
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha)
    {
        return this;
    }

    @Override
    public VertexConsumer texture(float u, float v)
    {
        return this;
    }

    @Override
    public VertexConsumer overlay(int u, int v)
    {
        return this;
    }

    @Override
    public VertexConsumer light(int u, int v)
    {
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z)
    {
        return this;
    }

    @Override
    public void next()
    {
    }

    @Override
    public void fixedColor(int red, int green, int blue, int alpha)
    {
    }

    @Override
    public void unfixColor()
    {
    }
}
