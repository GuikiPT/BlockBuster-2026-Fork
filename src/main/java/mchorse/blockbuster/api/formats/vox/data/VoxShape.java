package mchorse.blockbuster.api.formats.vox.data;

import mchorse.blockbuster.api.formats.vox.VoxReader;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Direct port of Blockbuster 2.7.2's
 * {@code api/formats/vox/data/VoxShape.java} — an {@code nSHP} node. Only
 * {@code modelAttrs.get(0).id} is honored downstream (multi-model shapes are
 * truncated — 1.12.2 behavior, kept).
 */
public class VoxShape extends VoxBaseNode
{
    public List<Attribute> modelAttrs;

    public VoxShape(InputStream stream, VoxReader reader) throws Exception
    {
        this.id = reader.readInt(stream);
        this.attrs = reader.readDictionary(stream);
        this.num = reader.readInt(stream);
        this.modelAttrs = new ArrayList<Attribute>();

        for (int i = 0; i < this.num; i ++)
        {
            this.modelAttrs.add(new Attribute(reader.readInt(stream), reader.readDictionary(stream)));
        }
    }

    public static class Attribute
    {
        public final int id;
        public final Map<String, String> attrs;

        public Attribute(int id, Map<String, String> attrs)
        {
            this.id = id;
            this.attrs = attrs;
        }
    }
}
