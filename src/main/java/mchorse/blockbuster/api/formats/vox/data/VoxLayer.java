package mchorse.blockbuster.api.formats.vox.data;

import mchorse.blockbuster.api.formats.vox.VoxReader;

import java.io.InputStream;

/**
 * Direct port of Blockbuster 2.7.2's
 * {@code api/formats/vox/data/VoxLayer.java} — a {@code LAYR} node. A layer is
 * hidden when its {@code _hidden} attribute equals the string {@code "1"}.
 */
public class VoxLayer extends VoxBaseNode
{
    public VoxLayer(InputStream stream, VoxReader reader) throws Exception
    {
        this.id = reader.readInt(stream);
        this.attrs = reader.readDictionary(stream);
        this.num = reader.readInt(stream);
    }

    public boolean isHidden()
    {
        return this.attrs.containsKey("_hidden") && this.attrs.get("_hidden").equals("1");
    }
}
