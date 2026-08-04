package mchorse.blockbuster.client.render;

import java.util.Arrays;
import java.util.List;
import javax.vecmath.Vector3d;
import mchorse.mclib.utils.Interpolations;

/**
 * The back-to-front depth sort of the render-last tail pass (roadmap P80.3).
 *
 * <p>This is <b>the</b> deliverable of P80.3. 1.20.4's
 * {@code VertexConsumerProvider.Immediate} batches by {@code RenderLayer} and
 * never sorts by camera distance, so nothing vanilla does subsumes it: without
 * this, two semi-transparent morphs at different depths draw in whatever order
 * the entity iteration happened to visit them, and the nearer one erases the
 * farther one's fragments.</p>
 *
 * <h2>The comparator is legacy's hand-rolled sign function, verbatim</h2>
 * 1.12.2 {@code RenderingHandler.renderLastEntities} (lines 366-376):
 * <pre>return dist1 == dist2 ? 0 : (dist2 - dist1 &gt; 0 ? 1 : -1);</pre>
 * <b>Descending on squared distance</b> — farthest first, i.e. back-to-front,
 * which is the painter's order a translucent draw needs. Note this is not
 * {@code Comparator.comparingDouble(...).reversed()}: the explicit
 * {@code dist1 == dist2 → 0} arm is reproduced, and the subtraction is the
 * comparison (so a {@code NaN} distance compares {@code -1} against everything
 * rather than throwing) exactly as legacy did.
 *
 * <h2>Allocation</h2>
 * Legacy called {@code getRenderLastPos()} — a fresh {@code Vector3d} — twice
 * per comparison, i.e. {@code O(n log n)} allocations every frame. This sorts
 * decorated, so {@code getRenderLastPos} is called exactly once per element
 * ({@code O(n)}) and the comparisons run on cached {@code double}s. The result
 * is identical: positions do not change during a sort, so precomputing the keys
 * cannot reorder anything relative to computing them lazily.
 */
public final class RenderLastSort
{
    private RenderLastSort()
    {}

    /**
     * Legacy's sign function over two squared distances.
     *
     * <p>Sign convention, spelled out because it is easy to invert: if {@code a}
     * is <b>nearer</b> than {@code b} then {@code distA < distB}, so
     * {@code distB - distA > 0} and this returns {@code +1} — the nearer item
     * sorts <b>after</b> the farther one. Farthest first.</p>
     */
    public static int compare(double distA, double distB)
    {
        return distA == distB ? 0 : (distB - distA > 0 ? 1 : -1);
    }

    /**
     * The actor's sort position (legacy {@code EntityActor.getRenderLastPos},
     * 1.12.2 lines 148-153) as a pure function, so the arithmetic is assertable
     * without an entity: {@code Interpolations.lerp(prev, current, partialTicks)}
     * on each axis.
     */
    public static Vector3d lerpPos(double prevX, double x, double prevY, double y, double prevZ, double z, float partialTicks)
    {
        return new Vector3d(
            Interpolations.lerp(prevX, x, partialTicks),
            Interpolations.lerp(prevY, y, partialTicks),
            Interpolations.lerp(prevZ, z, partialTicks));
    }

    /**
     * Squared distance from the sort origin to an item's render-last position.
     *
     * <p>Legacy used {@code Entity.getDistanceSq(x, y, z)} against
     * {@code mc.getRenderViewEntity()} — the render-view entity's <b>feet</b>.
     * See {@code RenderingHandler.renderLastOrigin} for the port's deliberate
     * choice of origin.</p>
     */
    public static double distanceSq(IRenderLast item, float partialTicks, double originX, double originY, double originZ)
    {
        Vector3d pos = item.getRenderLastPos(partialTicks);

        double dx = pos.x - originX;
        double dy = pos.y - originY;
        double dz = pos.z - originZ;

        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Full comparator over two items — the legacy expression with the positions
     * resolved. Kept as a public entry point so the sign convention is directly
     * assertable; {@link #sort} does not route through it (it decorates instead)
     * but produces the same order.
     */
    public static int compare(IRenderLast a, IRenderLast b, float partialTicks, double originX, double originY, double originZ)
    {
        return compare(distanceSq(a, partialTicks, originX, originY, originZ),
            distanceSq(b, partialTicks, originX, originY, originZ));
    }

    /**
     * Sort {@code list} in place, farthest from the origin first.
     */
    public static void sort(List<IRenderLast> list, float partialTicks, double originX, double originY, double originZ)
    {
        int size = list.size();

        if (size < 2)
        {
            return;
        }

        Keyed[] keyed = new Keyed[size];

        for (int i = 0; i < size; i++)
        {
            IRenderLast item = list.get(i);

            keyed[i] = new Keyed(item, distanceSq(item, partialTicks, originX, originY, originZ));
        }

        Arrays.sort(keyed, (a, b) -> compare(a.distanceSq, b.distanceSq));

        for (int i = 0; i < size; i++)
        {
            list.set(i, keyed[i].item);
        }
    }

    /** Decoration holder: one per element per frame, instead of two per comparison. */
    private static final class Keyed
    {
        final IRenderLast item;
        final double distanceSq;

        Keyed(IRenderLast item, double distanceSq)
        {
            this.item = item;
            this.distanceSq = distanceSq;
        }
    }
}
