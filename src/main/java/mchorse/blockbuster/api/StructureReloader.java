package mchorse.blockbuster.api;

/**
 * Seam for the {@code /model clear_structures} command (P74).
 *
 * <p>1.12.2's {@code SubCommandModelClearStructures} calls
 * {@code StructureMorph.reloadStructures()} directly. {@code StructureMorph} is a
 * blockbuster-pack morph that lands in a later stage, so — as the S5/P74 plan
 * prescribes — the command delegates through this indirection instead of a hard
 * reference. Until the morph exists the registered handler is a no-op; when the
 * structure morph ships it installs its {@code reloadStructures} implementation
 * here, and the {@code /model} Brigadier tree (P73) invokes {@link #reload()}.</p>
 */
public final class StructureReloader
{
    /**
     * Reloads cached structure-morph geometry.
     */
    public interface IStructureReloader
    {
        void reloadStructures();
    }

    private static IStructureReloader handler;

    private StructureReloader()
    {}

    /**
     * Install the reload handler (called by the structure morph when it lands).
     */
    public static void register(IStructureReloader reloader)
    {
        handler = reloader;
    }

    /**
     * Invoke {@code /model clear_structures}. No-op until a handler is
     * registered — the command never crashes when structures are absent.
     */
    public static void reload()
    {
        if (handler != null)
        {
            handler.reloadStructures();
        }
    }

    /**
     * @return whether a structure reloader has been installed yet
     */
    public static boolean hasHandler()
    {
        return handler != null;
    }
}
