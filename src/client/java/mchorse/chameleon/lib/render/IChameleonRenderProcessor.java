package mchorse.chameleon.lib.render;

import mchorse.chameleon.lib.data.model.ModelBone;
import mchorse.chameleon.lib.utils.MatrixStack;
import net.minecraft.client.render.VertexConsumer;

/**
 * A visitor over a Chameleon model's bone tree.
 *
 * <p>{@link ChameleonRenderer} walks the tree depth-first, pushing each bone's
 * transform onto {@code stack} before the call and popping it after; returning
 * {@code true} halts the whole walk (that is how {@link ChameleonPostRenderer}
 * stops at the bone it was looking for).</p>
 *
 * <p>Port note: legacy's parameter was a {@code BufferBuilder}; on 1.20.4 it is
 * the {@link VertexConsumer} the caller pulled from a {@code RenderLayer}. The
 * vanilla {@code MatrixStack}, packed light and overlay a 1.20.4 draw also needs
 * are carried as fields on the individual processors, mirroring how legacy
 * already kept per-draw state on them (bone name, colour) rather than widening
 * this signature.</p>
 *
 * Legacy source: chameleon/.../lib/render/IChameleonRenderProcessor.java
 */
public interface IChameleonRenderProcessor
{
    public boolean renderBone(VertexConsumer builder, MatrixStack stack, ModelBone bone);
}
