package mchorse.blockbuster.api.formats.obj;

import mchorse.mclib.utils.resources.ResourceLocation;

/**
 * OBJ material
 *
 * This class stores information about OBJ material from MTL file.
 *
 * <p>Ported as a plain data DTO here because the model API (P63/P64) references
 * it via {@link mchorse.blockbuster.api.Model#materials} and
 * {@link mchorse.blockbuster.api.Model#hasTexturedMaterials()}. The OBJ/MTL
 * <b>parser</b> that populates these (P65) reconciles trivially at merge — the
 * two ports of this DTO are field-identical by design (legacy spec).</p>
 */
public class OBJMaterial
{
    public String name;

    public float r = 1;
    public float g = 1;
    public float b = 1;

    public boolean useTexture;
    public boolean linear = false;
    public ResourceLocation texture;

    public OBJMaterial(String name)
    {
        this.name = name;
    }
}
