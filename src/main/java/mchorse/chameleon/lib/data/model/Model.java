package mchorse.chameleon.lib.data.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A parsed Bedrock ({@code .geo.json}) model — the root of the bone tree
 * {@link mchorse.chameleon.lib.parsing.ModelParser} builds.
 *
 * <p>Verbatim port of Chameleon 1.2's {@code lib/data/model/Model} (no MC
 * types; lives in the common source set so headless tests can parse a
 * geometry file).</p>
 *
 * Legacy source: chameleon/src/main/java/mchorse/chameleon/lib/data/model/Model.java
 */
public class Model
{
    public String id;
    public int textureWidth;
    public int textureHeight;

    public List<ModelBone> bones = new ArrayList<ModelBone>();
}
