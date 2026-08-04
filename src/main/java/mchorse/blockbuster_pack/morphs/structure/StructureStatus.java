package mchorse.blockbuster_pack.morphs.structure;

/**
 * Load-state of a baked structure renderer (roadmap P162). 1:1 with 2.7.2.
 *
 * <p>The {@code UNLOADED -> LOADING -> LOADED} machine dedupes template
 * re-requests: the first render of an {@code UNLOADED} renderer flips it to
 * {@code LOADING} and fires one {@code PacketStructureRequest}; the arriving
 * {@code PacketStructure} rebuilds it as {@code LOADED}. A server hot-reload
 * push (null tag) deletes the renderer back to {@code UNLOADED}.</p>
 */
public enum StructureStatus
{
    UNLOADED, LOADING, LOADED;
}
