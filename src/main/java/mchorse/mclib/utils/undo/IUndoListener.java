package mchorse.mclib.utils.undo;

/**
 * Full port of McLib 2.4.3's IUndoListener (roadmap P16).
 *
 * Legacy source: .tools/legacy-src/mclib/src/main/java/mchorse/mclib/utils/undo/IUndoListener.java
 */
public interface IUndoListener<T>
{
    public void handleUndo(IUndo<T> undo, boolean redo);
}
