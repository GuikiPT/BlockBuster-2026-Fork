package mchorse.blockbuster.api.formats.vox.data;

import mchorse.mclib.utils.binary.BinaryChunk;

/**
 * This represents a data chunk information in the VOX file
 * (not used anywhere outside of vox reader class)
 *
 * <p>Direct port of Blockbuster 2.7.2's
 * {@code api/formats/vox/data/VoxChunk.java}.</p>
 */
public class VoxChunk extends BinaryChunk
{
    public int chunks;

    public VoxChunk(String id, int size, int chunks)
    {
        super(id, size);

        this.chunks = chunks;
    }

    @Override
    public String toString()
    {
        return this.id;
    }
}
