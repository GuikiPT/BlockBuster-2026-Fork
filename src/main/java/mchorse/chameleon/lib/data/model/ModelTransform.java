package mchorse.chameleon.lib.data.model;

import javax.vecmath.Vector3f;

/**
 * A bone's translate/scale/rotation triple. Every {@link ModelBone} carries two:
 * {@code initial} (parsed from the model file, the rest pose) and {@code current}
 * (the per-frame animated value the renderer reads).
 *
 * Legacy source: chameleon/.../lib/data/model/ModelTransform.java
 */
public class ModelTransform
{
    public Vector3f translate = new Vector3f();
    public Vector3f scale = new Vector3f(1, 1, 1);
    public Vector3f rotation = new Vector3f();
}
