package mchorse.mclib.utils.binary;

/**
 * Full port of McLib 2.4.3's BinaryChunk (roadmap P14).
 *
 * Legacy source: .tools/legacy-src/mclib/src/main/java/mchorse/mclib/utils/binary/BinaryChunk.java
 */
public class BinaryChunk
{
    public String id;
    public int size;

    public BinaryChunk(String id, int size)
    {
        this.id = id;
        this.size = size;
    }
}
