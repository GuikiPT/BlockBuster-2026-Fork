package mchorse.chameleon.lib.data.model;

import javax.vecmath.Vector3f;
import java.util.ArrayList;
import java.util.List;

/**
 * A single baked quad of a {@link ModelCube} — four {@link ModelVertex}es plus
 * the face normal. Built once at parse time by {@link ModelCube#generateQuads}.
 *
 * Legacy source: chameleon/.../lib/data/model/ModelQuad.java
 */
public class ModelQuad
{
    public List<ModelVertex> vertices = new ArrayList<ModelVertex>();
    public Vector3f normal = new Vector3f();

    public ModelQuad normal(float x, float y, float z)
    {
        this.normal.set(x, y, z);

        return this;
    }

    public ModelQuad vertex(float x, float y, float z, float u, float v)
    {
        ModelVertex vertex = new ModelVertex();

        vertex.position.set(x, y, z);
        vertex.uv.set(u, v);
        this.vertices.add(vertex);

        return this;
    }
}
