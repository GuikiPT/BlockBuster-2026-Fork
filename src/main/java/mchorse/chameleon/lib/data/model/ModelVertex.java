package mchorse.chameleon.lib.data.model;

import javax.vecmath.Vector2f;
import javax.vecmath.Vector3f;

/**
 * One baked cube-face vertex: model-space position plus its normalized UV.
 *
 * Legacy source: chameleon/.../lib/data/model/ModelVertex.java
 */
public class ModelVertex
{
    public Vector2f uv = new Vector2f();
    public Vector3f position = new Vector3f();
}
