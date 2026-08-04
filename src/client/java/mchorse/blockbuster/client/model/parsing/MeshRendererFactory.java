package mchorse.blockbuster.client.model.parsing;

import mchorse.blockbuster.api.Model;
import mchorse.blockbuster.api.ModelLimb;
import mchorse.blockbuster.api.ModelTransform;
import mchorse.blockbuster.api.formats.IMeshes;
import mchorse.blockbuster.api.formats.obj.MeshesOBJ;
import mchorse.blockbuster.api.formats.vox.MeshesVOX;
import mchorse.blockbuster.client.model.ModelCustom;
import mchorse.blockbuster.client.model.ModelCustomRenderer;
import mchorse.blockbuster.client.model.ModelOBJRenderer;
import mchorse.blockbuster.client.model.ModelVoxRenderer;

/**
 * Client-side render factory mapping data-side {@link IMeshes} to their
 * concrete {@link ModelCustomRenderer} subclasses (roadmap P77/P78).
 *
 * <p>In 1.12.2 this dispatch lived on {@code IMeshes.createRenderer(...)}
 * directly, but that method returns a client renderer and drags the whole
 * {@code client.model.*} stack into the data-side classes. With the S5/S6
 * source-set split ({@code IMeshes} is main/data-side, renderers are
 * client-side) the dispatch moves here so {@link ModelParser} can build the
 * right renderer without the data classes referencing client types.</p>
 */
public final class MeshRendererFactory
{
    private MeshRendererFactory()
    {
    }

    /**
     * Build the renderer for a limb's meshes, or {@code null} to fall back to
     * JSON box rendering. Mirrors legacy {@code createRenderer} semantics:
     * OBJ meshes are only used when {@code data.providesObj} (the silent
     * box-vs-obj toggle), VOX meshes are lazily meshed on first request.
     */
    public static ModelCustomRenderer create(IMeshes meshes, Model data, ModelCustom model, ModelLimb limb, ModelTransform transform)
    {
        if (meshes instanceof MeshesOBJ)
        {
            /* providesObj=false silently disables OBJ meshes even when the
             * files exist — a feature (box-vs-obj toggle), not a bug. */
            if (!data.providesObj)
            {
                return null;
            }

            return new ModelOBJRenderer(model, limb, transform, (MeshesOBJ) meshes);
        }

        if (meshes instanceof MeshesVOX)
        {
            MeshesVOX vox = (MeshesVOX) meshes;

            vox.build();

            return new ModelVoxRenderer(model, limb, transform, vox);
        }

        return null;
    }
}
