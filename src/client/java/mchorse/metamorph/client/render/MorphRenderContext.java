package mchorse.metamorph.client.render;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Ambient render target for a morph draw (roadmap P54).
 *
 * <p>1.12.2's {@code AbstractMorph.render(entity, x, y, z, yaw, partialTicks)}
 * carried no render target: the matrix was the ambient GL model-view and the
 * geometry went straight to the fixed-function pipeline. On 1.20.4 a draw needs
 * a {@link MatrixStack}, a {@link VertexConsumerProvider} and a packed light
 * value, and the legacy signature is a wire-level contract we keep (it is what
 * every ported call site — body parts, sequencer sub-morphs, particle morphs,
 * gun projectiles — invokes recursively).</p>
 *
 * <p>So the render target becomes ambient again, but explicitly: whoever owns
 * the frame ({@code PlayerEntityRendererMixin} via {@link MorphRenderer}, the
 * actor renderer, the gun renderers, the GUI preview paths) {@link #push}es a
 * context, and the per-morph renderers read {@link #current()}. The stack —
 * rather than a single field — is what makes nested morph rendering work: a
 * body part that draws a sub-morph at a different matrix pushes its own frame
 * and pops it, restoring the parent's.</p>
 *
 * <p>Null {@link #current()} means "no render target installed" and every
 * renderer treats it as a no-op — the totality rule, so a stray
 * {@code morph.render(...)} outside a frame can never NPE.</p>
 */
public final class MorphRenderContext
{
    /**
     * Full-bright packed lightmap coordinate, used by the on-screen (GUI)
     * paths where there is no world light to sample.
     */
    public static final int FULL_BRIGHT = 0xf000f0;

    private static final Deque<MorphRenderContext> STACK = new ArrayDeque<MorphRenderContext>();

    public final MatrixStack matrices;
    public final VertexConsumerProvider consumers;
    public final int light;
    public final int overlay;
    public final float partialTicks;

    public MorphRenderContext(MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay, float partialTicks)
    {
        this.matrices = matrices;
        this.consumers = consumers;
        this.light = light;
        this.overlay = overlay;
        this.partialTicks = partialTicks;
    }

    /** The frame currently being drawn into, or null outside a render frame. */
    public static MorphRenderContext current()
    {
        return STACK.peek();
    }

    public static MorphRenderContext push(MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay, float partialTicks)
    {
        MorphRenderContext context = new MorphRenderContext(matrices, consumers, light, overlay, partialTicks);

        STACK.push(context);

        return context;
    }

    /**
     * Pop the innermost frame. Callers must do this in a {@code finally} —
     * an escaping exception would otherwise leave the stack unbalanced and
     * every later morph draw would render into a dead matrix.
     */
    public static void pop()
    {
        STACK.poll();
    }

    /** Current nesting depth (0 outside any frame). Test/diagnostic hook. */
    public static int depth()
    {
        return STACK.size();
    }

    /**
     * Drop every frame. Only for the error paths that cannot know how many
     * frames a throwing renderer left behind, and for test isolation.
     */
    public static void clear()
    {
        STACK.clear();
    }
}
