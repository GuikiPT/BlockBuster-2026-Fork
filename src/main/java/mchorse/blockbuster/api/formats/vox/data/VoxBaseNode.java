package mchorse.blockbuster.api.formats.vox.data;

import java.util.Map;

/**
 * Direct port of Blockbuster 2.7.2's
 * {@code api/formats/vox/data/VoxBaseNode.java}.
 */
public abstract class VoxBaseNode
{
    public int id;
    public Map<String, String> attrs;
    public int num;
}
