package mchorse.blockbuster.api.formats.vox.data;

/**
 * Direct port of Blockbuster 2.7.2's {@code api/formats/vox/data/Vox.java}.
 *
 * <p>A single voxel grid. The flat {@link #voxels} array is indexed
 * {@code x + y*this.x + z*this.x*this.y}. {@link #set} silently ignores
 * out-of-range indices (load-bearing totality) and maintains the running
 * {@link #blocks} counter used by {@link mchorse.blockbuster.api.formats.vox.VoxBuilder}
 * for mesh allocation.</p>
 */
public class Vox
{
    public int x;
    public int y;
    public int z;
    public int blocks;

    public int[] voxels;

    public int toIndex(int x, int y, int z)
    {
        return x + y * this.x + z * this.x * this.y;
    }

    public boolean has(int x, int y, int z)
    {
        return x >= 0 && y >= 0 && z >= 0 && x < this.x && y < this.y && z < this.z && this.voxels[this.toIndex(x, y, z)] != 0;
    }

    public void set(int x, int y, int z, int block)
    {
        int index = this.toIndex(x, y, z);

        if (index < 0 || index >= this.x * this.y * this.z)
        {
            return;
        }

        int last = this.voxels[index];

        this.voxels[index] = block;

        if (last == 0 && block != 0)
        {
            this.blocks += 1;
        }
        else if (last != 0 && block == 0)
        {
            this.blocks -= 1;
        }
    }
}
