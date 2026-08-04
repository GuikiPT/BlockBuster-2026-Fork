package mchorse.blockbuster.api.formats.vox.data;

import mchorse.blockbuster.api.formats.vox.VoxReader;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Direct port of Blockbuster 2.7.2's
 * {@code api/formats/vox/data/VoxTransform.java} — an {@code nTRN} node.
 *
 * <p><b>Porting notes (javax.vecmath -&gt; JOML):</b></p>
 * <ul>
 *   <li>{@link #unusedId} is a reserved field that is <b>unused</b> but must
 *       still be consumed from the stream to keep alignment (load-bearing).</li>
 *   <li>Per-frame {@code "_t"} translation has its <b>X negated on read</b>
 *       ({@code translate.set(-x, y, z)}, the {@code /* Stupid coordinate
 *       systems... *}{@code /} quirk) — one of the three places where the
 *       VOX-&gt;MC coordinate convention lives.</li>
 *   <li>The per-frame {@link Matrix4f} is assembled by setting the upper-left
 *       3x3 to the rotation (JOML {@link Matrix4f#set(org.joml.Matrix3fc)}
 *       zeroes translation and sets identity elsewhere, matching vecmath's
 *       {@code Matrix4f.set(Matrix3f)}) then applying the translation.</li>
 * </ul>
 */
public class VoxTransform extends VoxBaseNode
{
    public int childId;
    public int unusedId;
    public int layerId;
    public List<Matrix4f> transforms;

    public VoxTransform(InputStream stream, VoxReader reader) throws Exception
    {
        this.id = reader.readInt(stream);
        this.attrs = reader.readDictionary(stream);
        this.childId = reader.readInt(stream);
        this.unusedId = reader.readInt(stream);
        this.layerId = reader.readInt(stream);
        this.num = reader.readInt(stream);
        this.transforms = new ArrayList<Matrix4f>();

        for (int i = 0; i < this.num; i ++)
        {
            Map<String, String> dict = reader.readDictionary(stream);
            Matrix3f rotation = new Matrix3f();
            Vector3f translate = new Vector3f(0, 0, 0);

            rotation.identity();

            if (dict.containsKey("_r"))
            {
                rotation = reader.readRotation(Integer.parseInt(dict.get("_r")));
            }

            if (dict.containsKey("_t"))
            {
                String[] splits = dict.get("_t").split(" ");

                if (splits.length == 3)
                {
                    /* Stupid coordinate systems... */
                    translate.set(-Integer.parseInt(splits[0]), Integer.parseInt(splits[1]), Integer.parseInt(splits[2]));
                }
            }

            /* Assemble the main result */
            Matrix4f transform = new Matrix4f();

            transform.set(rotation);
            transform.setTranslation(translate);

            this.transforms.add(transform);
        }
    }
}
