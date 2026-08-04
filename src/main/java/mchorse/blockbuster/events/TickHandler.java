package mchorse.blockbuster.events;

import mchorse.mclib.network.Side;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Port of Blockbuster 1.12.2's {@code events/TickHandler} (roadmap P22.1).
 *
 * The Forge {@code (TickEvent class, phase, side)} key becomes the enum-based
 * {@link TickIdentifier} with the same dimensionality; identity-set semantics
 * (the same Runnable instance cannot be added twice for one event), explicit
 * iterator-based self-removal of {@link IRunnable}s and empty-set pruning are
 * verbatim. Dispatch is wired from Fabric tick events / the player-tick mixin
 * seam (see the mod initializers); {@code WORLD_CLIENT} is synthesized from
 * the local player's client tick, preserving the legacy adapter workaround
 * for Forge's server-only WorldTickEvent.
 */
public class TickHandler
{
    private Map<TickIdentifier, Set<Runnable>> runnables = new HashMap<TickIdentifier, Set<Runnable>>();

    public void addRunnable(TickType type, Side side, Runnable runnable)
    {
        this.addRunnable(type, side, Phase.START, runnable);
    }

    public void addRunnable(TickType type, Side side, Phase phase, Runnable runnable)
    {
        TickIdentifier identifier = new TickIdentifier(type, phase, side);
        Set<Runnable> set = this.runnables.get(identifier);

        if (set == null)
        {
            set = Collections.newSetFromMap(new IdentityHashMap<Runnable, Boolean>());
            this.runnables.put(identifier, set);
        }

        set.add(runnable);
    }

    public void removeRunnable(TickType type, Side side, Runnable runnable)
    {
        this.removeRunnable(type, side, Phase.START, runnable);
    }

    public void removeRunnable(TickType type, Side side, Phase phase, Runnable runnable)
    {
        TickIdentifier identifier = new TickIdentifier(type, phase, side);
        Set<Runnable> set = this.runnables.get(identifier);

        if (set != null)
        {
            set.remove(runnable);

            if (set.isEmpty())
            {
                this.runnables.remove(identifier);
            }
        }
    }

    public void runRunnables(TickType type, Side side, Phase phase)
    {
        TickIdentifier identifier = new TickIdentifier(type, phase, side);
        Set<Runnable> set = this.runnables.get(identifier);

        if (set == null)
        {
            return;
        }

        Iterator<Runnable> it = set.iterator();

        while (it.hasNext())
        {
            Runnable runnable = it.next();

            runnable.run();

            if (runnable instanceof IRunnable && ((IRunnable) runnable).shouldRemove())
            {
                it.remove();
            }
        }

        if (set.isEmpty())
        {
            this.runnables.remove(identifier);
        }
    }

    public boolean isEmpty()
    {
        return this.runnables.isEmpty();
    }

    /**
     * Runnables that remove themselves once done
     */
    public interface IRunnable extends Runnable
    {
        boolean shouldRemove();
    }

    public enum TickType
    {
        WORLD, WORLD_CLIENT, PLAYER, CLIENT;
    }

    public enum Phase
    {
        START, END;
    }

    public static class TickIdentifier
    {
        public final TickType type;
        public final Phase phase;
        public final Side side;

        public TickIdentifier(TickType type, Phase phase, Side side)
        {
            this.type = type;
            this.phase = phase;
            this.side = side;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (obj instanceof TickIdentifier)
            {
                TickIdentifier other = (TickIdentifier) obj;

                return this.type == other.type && this.phase == other.phase && this.side == other.side;
            }

            return false;
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(this.type, this.phase, this.side);
        }
    }
}
