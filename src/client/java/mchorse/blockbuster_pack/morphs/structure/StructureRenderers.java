package mchorse.blockbuster_pack.morphs.structure;

import java.util.HashMap;
import java.util.Map;

import mchorse.blockbuster.api.StructureReloader;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.structure.PacketStructureRequest;
import mchorse.blockbuster_pack.morphs.StructureMorph;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Client registry + seam implementation for {@link StructureMorph} (roadmap
 * P162). Holds the {@code STRUCTURES} cache of baked renderers (the field lived
 * on {@code StructureMorph} in 2.7.2; the source-set split moves it to the
 * client here), owns the UNLOADED-&gt;LOADING re-request dedupe, and installs
 * both the morph's cache seam and the {@code /model clear_structures}
 * ({@link StructureReloader}) reload handler.
 *
 * <p>The draw itself is <b>not</b> here: it is
 * {@code mchorse.blockbuster_pack.client.render.StructureMorphRenderer}, reached
 * through P54's morph render dispatcher like every other morph type. What stays
 * is the cache and the load-state machine — {@link #ensureLoaded} is what the
 * renderer calls per frame, so geometry starts loading the moment a structure
 * morph is first seen.</p>
 */
@Environment(EnvType.CLIENT)
public class StructureRenderers implements StructureMorph.IStructureClient
{
    /** Baked structure renderers keyed by structure name. */
    public static final Map<String, StructureRenderer> STRUCTURES = new HashMap<String, StructureRenderer>();

    private static boolean installed;

    /** Wire the seam + reload handler (idempotent). */
    public static void install()
    {
        if (installed)
        {
            return;
        }

        installed = true;

        StructureRenderers instance = new StructureRenderers();

        StructureMorph.client = instance;
        StructureReloader.register(instance::reloadStructures);
    }

    @Override
    public void request()
    {
        if (STRUCTURES.isEmpty())
        {
            Dispatcher.sendToServer(new PacketStructureRequest());
        }
    }

    @Override
    public void reloadStructures()
    {
        this.cleanUp();
        this.request();
    }

    @Override
    public void cleanUp()
    {
        for (StructureRenderer renderer : STRUCTURES.values())
        {
            renderer.delete();
        }

        STRUCTURES.clear();
    }

    /**
     * Look up the renderer for the morph's structure and drive the load-state
     * machine: an UNLOADED renderer flips to LOADING and fires exactly one
     * template request (dedupe). Returns the renderer only when LOADED and
     * ready to draw.
     *
     * <p>Static because the draw lives in {@code StructureMorphRenderer} (P54)
     * while the cache lives here — legacy had both on the morph class.</p>
     */
    public static StructureRenderer ensureLoaded(StructureMorph morph)
    {
        StructureRenderer renderer = STRUCTURES.get(morph.structure);

        if (renderer == null)
        {
            return null;
        }

        if (renderer.status != StructureStatus.LOADED)
        {
            if (renderer.status == StructureStatus.UNLOADED)
            {
                renderer.status = StructureStatus.LOADING;
                Dispatcher.sendToServer(new PacketStructureRequest(morph.structure));
            }

            return null;
        }

        return renderer;
    }
}
