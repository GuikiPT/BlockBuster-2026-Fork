package mchorse.blockbuster.api.formats;

/**
 * Holds the mesh data
 *
 * <p>Direct port of Blockbuster 2.7.2's {@code api/formats/Mesh.java}. The
 * {@code triangles} field is a legacy misnomer — it is really a running vertex
 * count — do not rename (roadmap P65 quirk).</p>
 *
 * <p><b>Ownership note:</b> this class is shared by the OBJ stack (P65) and the
 * VOX stack (P66). It was landed here by P66 as a verbatim port so
 * {@link mchorse.blockbuster.api.formats.vox.VoxBuilder} can compile and be
 * tested headlessly. If P65 also lands it, the two are byte-identical.</p>
 */
public class Mesh
{
    public float[] posData;
    public float[] texData;
    public float[] normData;
    public int triangles;

    public Mesh(int triangles)
    {
        this(new float[triangles * 9], new float[triangles * 6], new float[triangles * 9]);
    }

    public Mesh(float[] posData, float[] texData, float[] normData)
    {
        this.posData = posData;
        this.texData = texData;
        this.normData = normData;

        this.triangles = posData.length / 3;
    }
}
