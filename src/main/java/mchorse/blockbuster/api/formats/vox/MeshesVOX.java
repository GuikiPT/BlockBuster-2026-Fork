package mchorse.blockbuster.api.formats.vox;

import mchorse.blockbuster.api.formats.IMeshes;
import mchorse.blockbuster.api.formats.Mesh;
import mchorse.blockbuster.api.formats.vox.data.Vox;

import org.joml.Matrix3f;

import javax.vecmath.Vector3f;

/**
 * VOX limb meshes (roadmap P66 data-side / P78 client hook).
 *
 * <p>Port of Blockbuster 2.7.2's {@code api/formats/vox/MeshesVOX}. The legacy
 * class implemented {@code createRenderer} directly (returning a
 * {@code ModelVoxRenderer}); on the S5/S6 source-set split that client hook
 * moves to {@code MeshRendererFactory} and this data-side class exposes only
 * {@link #build()} (lazy {@link VoxBuilder} meshing) plus the occupied-voxel
 * bounding-box scans.</p>
 *
 * <p>{@code javax.vecmath.Vector3f} is used for the {@link IMeshes} min/max
 * return type (matching the OBJ side); the meshing math uses JOML like
 * {@link VoxBuilder}.</p>
 */
public class MeshesVOX implements IMeshes
{
    public Mesh mesh;
    public VoxDocument document;
    public Vox vox;
    public Matrix3f rotation;

    public MeshesVOX(VoxDocument document, VoxDocument.LimbNode node)
    {
        this.document = document;
        this.vox = node.chunk;
        this.rotation = node.rotation;
    }

    /**
     * Lazily mesh the voxel chunk (client mesher, GL-free). Idempotent.
     */
    public Mesh build()
    {
        if (this.mesh == null)
        {
            this.mesh = new VoxBuilder(this.rotation).build(this.vox);
        }

        return this.mesh;
    }

    @Override
    public Vector3f getMin()
    {
        Vector3f min = new Vector3f(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);

        for (int x = 0; x < this.vox.x; x++)
        {
            for (int y = 0; y < this.vox.y; y++)
            {
                for (int z = 0; z < this.vox.z; z++)
                {
                    if (this.vox.has(x, y, z))
                    {
                        min.x = Math.min(x, min.x);
                        min.y = Math.min(y, min.y);
                        min.z = Math.min(z, min.z);
                    }
                }
            }
        }

        return min;
    }

    @Override
    public Vector3f getMax()
    {
        Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);

        for (int x = 0; x < this.vox.x; x++)
        {
            for (int y = 0; y < this.vox.y; y++)
            {
                for (int z = 0; z < this.vox.z; z++)
                {
                    if (this.vox.has(x, y, z))
                    {
                        max.x = Math.max(x, max.x);
                        max.y = Math.max(y, max.y);
                        max.z = Math.max(z, max.z);
                    }
                }
            }
        }

        return max;
    }
}
