package mchorse.mclib.utils.undo;

/**
 * Full port of McLib 2.4.3's IUndo (roadmap P16).
 *
 * Legacy source: .tools/legacy-src/mclib/src/main/java/mchorse/mclib/utils/undo/IUndo.java
 *
 * An undo entry in the UndoManager
 *
 * This represents a single operation that can be either undone or redone.
 * The generic represents context upon which this undo acts upon.
 */
public interface IUndo <T>
{
    /**
     * Mark undo as unmergable
     */
    public IUndo<T> noMerging();

    /**
     * Check whether a given undo is compatible with whatever
     * data it might be storing
     */
    public boolean isMergeable(IUndo<T> undo);

    /**
     * Merge the data with given undo
     */
    public void merge(IUndo<T> undo);

    /**
     * Undo changes made to given context
     */
    public void undo(T context);

    /**
     * Redo changes made to given context
     */
    public void redo(T context);
}
