package mchorse.blockbuster.client.render;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The {@code actorAlwaysRender} out-of-frustum sweep of the render-last tail
 * pass (roadmap P80.3), as a pure set operation.
 *
 * <p>1.12.2 {@code RenderingHandler.renderLastEntities} lines 350-363:</p>
 * <pre>List&lt;EntityActor&gt; actors = mc.world.getEntities(EntityActor.class, IS_ALIVE);
 * actors.removeAll(renderedEntityActors);
 * actors.removeAll(renderLasts);
 * renderLasts.addAll(actors);</pre>
 *
 * <p>with the legacy comment "because renderLast entities that are out of range
 * won't be added to renderLast". The two removals are what stops an actor being
 * drawn twice: once by the normal pass and again by the tail.</p>
 *
 * <h2>Why 1.20.4 makes this a safety net rather than the load-bearing path</h2>
 * 1.12.2's {@code RenderGlobal.renderEntities} walks entities <b>per visible
 * chunk render-section</b>, so an actor sitting in a frustum-culled section is
 * never visited at all and its renderer's {@code shouldRender} — where the
 * enqueue lives — is never asked. 1.20.4's {@code WorldRenderer.render} instead
 * iterates {@code world.getEntities()} (every client-loaded entity) and asks
 * {@code entityRenderDispatcher.shouldRender} about each one, so the enqueue in
 * {@code RenderActor.shouldRender} already sees every loaded actor and this
 * sweep will normally find nothing. It is kept because it is cheap, because it
 * is what legacy did, and because it is the backstop if a future
 * culling change (Sodium, S21) reintroduces a per-section walk.
 *
 * <p>Identity, not equality, is the intended semantics on both versions:
 * {@code Entity.equals} is reference equality, so legacy's {@code removeAll}
 * was already an identity operation.</p>
 */
public final class RenderLastSweep
{
    private RenderLastSweep()
    {}

    /**
     * @param alive    every live actor in the client world
     * @param rendered actors the normal pass already drew this frame
     * @param queued   everything already sitting in the render-last queue
     * @return the actors that would otherwise not be drawn at all this frame
     */
    public static <T> List<T> missing(Collection<? extends T> alive, Collection<?> rendered, Collection<?> queued)
    {
        List<T> result = new ArrayList<T>();

        if (alive == null || alive.isEmpty())
        {
            return result;
        }

        for (T actor : alive)
        {
            if (contains(rendered, actor) || contains(queued, actor))
            {
                continue;
            }

            result.add(actor);
        }

        return result;
    }

    private static boolean contains(Collection<?> collection, Object item)
    {
        if (collection == null || collection.isEmpty())
        {
            return false;
        }

        for (Object element : collection)
        {
            if (element == item)
            {
                return true;
            }
        }

        return false;
    }
}
