package mchorse.blockbuster.client.model.parsing;

/**
 * Self-contained box geometry generator (P74).
 *
 * <p>1.12.2's {@link mchorse.blockbuster.client.model.parsing.ModelExporterOBJ}
 * generated its OBJ geometry from vanilla {@code net.minecraft.client.model.ModelBox}
 * / {@code TexturedQuad} / {@code PositionTextureVertex}, then reflected the
 * private {@code TexturedQuad[]} field out of the box. Those classes no longer
 * exist in yarn 1.20.4 with the same shape (and the reflection strategy is not
 * portable), so — per the S5/P74 plan — this class re-implements the exact
 * 8-vertex / 6-quad cube generation from the 1.12.2 {@code ModelBox(ModelRenderer,
 * int, int, float, float, float, int, int, int, float, boolean)} constructor and
 * the {@code TexturedQuad(vertices, u1, v1, u2, v2, tw, th)} UV assignment,
 * verbatim, so the exporter stays fully headless and byte-golden-testable without
 * Knot or any Minecraft rendering class.</p>
 *
 * <p>Vertex ordering, quad face order, mirror handling (swap far/near X then
 * reverse each quad's vertices) and the UV math all match the vanilla source
 * 1:1; do not "fix" anything here — the OBJ byte output is the contract.</p>
 */
public class BoxGeometry
{
    public final Quad[] quads;

    /**
     * Build box geometry.
     *
     * @param texU      texture U offset (limb.texture[0])
     * @param texV      texture V offset (limb.texture[1])
     * @param x         lower-corner X (usually {@code -w * ox})
     * @param y         lower-corner Y (usually {@code -h * oy})
     * @param z         lower-corner Z (usually {@code -d * oz})
     * @param dx        width in pixels
     * @param dy        height in pixels
     * @param dz        depth in pixels
     * @param delta     inflate/size offset (limb.sizeOffset)
     * @param mirror    mirror flag (limb.mirror)
     * @param textureWidth  model texture width (data.texture[0])
     * @param textureHeight model texture height (data.texture[1])
     */
    public BoxGeometry(int texU, int texV, float x, float y, float z, int dx, int dy, int dz, float delta, boolean mirror, float textureWidth, float textureHeight)
    {
        Vertex[] v = new Vertex[8];
        this.quads = new Quad[6];

        float f = x + (float) dx;
        float f1 = y + (float) dy;
        float f2 = z + (float) dz;
        x = x - delta;
        y = y - delta;
        z = z - delta;
        f = f + delta;
        f1 = f1 + delta;
        f2 = f2 + delta;

        if (mirror)
        {
            float f3 = f;
            f = x;
            x = f3;
        }

        Vertex p7 = new Vertex(x, y, z, 0.0F, 0.0F);
        Vertex p = new Vertex(f, y, z, 0.0F, 8.0F);
        Vertex p1 = new Vertex(f, f1, z, 8.0F, 8.0F);
        Vertex p2 = new Vertex(x, f1, z, 8.0F, 0.0F);
        Vertex p3 = new Vertex(x, y, f2, 0.0F, 0.0F);
        Vertex p4 = new Vertex(f, y, f2, 0.0F, 8.0F);
        Vertex p5 = new Vertex(f, f1, f2, 8.0F, 8.0F);
        Vertex p6 = new Vertex(x, f1, f2, 8.0F, 0.0F);

        v[0] = p7;
        v[1] = p;
        v[2] = p1;
        v[3] = p2;
        v[4] = p3;
        v[5] = p4;
        v[6] = p5;
        v[7] = p6;

        this.quads[0] = new Quad(new Vertex[] {p4, p, p1, p5}, texU + dz + dx, texV + dz, texU + dz + dx + dz, texV + dz + dy, textureWidth, textureHeight);
        this.quads[1] = new Quad(new Vertex[] {p7, p3, p6, p2}, texU, texV + dz, texU + dz, texV + dz + dy, textureWidth, textureHeight);
        this.quads[2] = new Quad(new Vertex[] {p4, p3, p7, p}, texU + dz, texV, texU + dz + dx, texV + dz, textureWidth, textureHeight);
        this.quads[3] = new Quad(new Vertex[] {p1, p2, p6, p5}, texU + dz + dx, texV + dz, texU + dz + dx + dx, texV, textureWidth, textureHeight);
        this.quads[4] = new Quad(new Vertex[] {p, p7, p2, p1}, texU + dz, texV + dz, texU + dz + dx, texV + dz + dy, textureWidth, textureHeight);
        this.quads[5] = new Quad(new Vertex[] {p3, p4, p5, p6}, texU + dz + dx + dz, texV + dz, texU + dz + dx + dz + dx, texV + dz + dy, textureWidth, textureHeight);

        if (mirror)
        {
            for (Quad quad : this.quads)
            {
                quad.flipFace();
            }
        }
    }

    /**
     * A single cube face: four textured vertices. Faithful port of
     * {@code net.minecraft.client.model.TexturedQuad}.
     */
    public static class Quad
    {
        public Vertex[] vertexPositions;
        public int nVertices;

        public Quad(Vertex[] vertices, int u1, int v1, int u2, int v2, float textureWidth, float textureHeight)
        {
            this.vertexPositions = vertices;
            this.nVertices = vertices.length;

            float f = 0.0F / textureWidth;
            float f1 = 0.0F / textureHeight;

            vertices[0] = vertices[0].setTexturePosition((float) u2 / textureWidth - f, (float) v1 / textureHeight + f1);
            vertices[1] = vertices[1].setTexturePosition((float) u1 / textureWidth + f, (float) v1 / textureHeight + f1);
            vertices[2] = vertices[2].setTexturePosition((float) u1 / textureWidth + f, (float) v2 / textureHeight - f1);
            vertices[3] = vertices[3].setTexturePosition((float) u2 / textureWidth - f, (float) v2 / textureHeight - f1);
        }

        public void flipFace()
        {
            Vertex[] copy = new Vertex[this.vertexPositions.length];

            for (int i = 0; i < this.vertexPositions.length; ++i)
            {
                copy[i] = this.vertexPositions[this.vertexPositions.length - i - 1];
            }

            this.vertexPositions = copy;
        }
    }

    /**
     * A single textured vertex. Faithful port of
     * {@code net.minecraft.client.model.PositionTextureVertex}: the position
     * ({@link #x}/{@link #y}/{@link #z}) is shared/immutable, and
     * {@link #setTexturePosition(float, float)} returns a <b>new</b> vertex
     * sharing the same position but with fresh UVs (never mutating in place).
     */
    public static class Vertex
    {
        public final double x;
        public final double y;
        public final double z;
        public float texturePositionX;
        public float texturePositionY;

        public Vertex(float x, float y, float z, float u, float v)
        {
            this.x = x;
            this.y = y;
            this.z = z;
            this.texturePositionX = u;
            this.texturePositionY = v;
        }

        private Vertex(Vertex other, float u, float v)
        {
            this.x = other.x;
            this.y = other.y;
            this.z = other.z;
            this.texturePositionX = u;
            this.texturePositionY = v;
        }

        public Vertex setTexturePosition(float u, float v)
        {
            return new Vertex(this, u, v);
        }
    }
}
