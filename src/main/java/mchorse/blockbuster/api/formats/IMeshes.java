package mchorse.blockbuster.api.formats;

import javax.vecmath.Vector3f;

/**
 * Compiled mesh data (OBJ / VOX) exposed as a headless, render-free surface.
 *
 * <p>In 1.12.2 this interface also declared
 * {@code createRenderer(Model, ModelCustom, ModelLimb, ModelTransform)}, which
 * built a {@code ModelCustomRenderer} on the client. That method drags in the
 * {@code Model} DTO stack (P63) and the whole client renderer stack
 * ({@code mchorse.blockbuster.client.model.*}). Per the S5/S6 split it moved
 * out: the data-side {@code IMeshes} keeps only the bounding-box scans, and
 * P77's client-side {@code MeshRendererFactory} does the dispatch, mapping
 * {@code MeshesOBJ} to {@code ModelOBJRenderer}.</p>
 *
 * <p>The {@code javax.vecmath.Vector3f} return type is intentional — the project
 * bundles {@code javax.vecmath} (build.gradle) for diff-ability against the
 * legacy sources; JOML conversion happens only at the render boundary.</p>
 */
public interface IMeshes
{
    Vector3f getMin();

    Vector3f getMax();
}
