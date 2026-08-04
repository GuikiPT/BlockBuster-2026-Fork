package mchorse.blockbuster.client.model;

/**
 * Headless, GL-free reproduction of vanilla 1.12.2 {@code net.minecraft.client.model.ModelBox}
 * geometry (roadmap P75).
 *
 * <p>1.12.2 Blockbuster built every JSON box limb by extending
 * {@code ModelRenderer}/{@code ModelBox}; the exact cube vertex layout and UV
 * unwrap are a load-bearing contract — every legacy {@code model.json} was
 * authored against it, so this class reproduces the vanilla
 * {@code ModelBox(renderer, texU, texV, x, y, z, dx, dy, dz, delta, mirror)}
 * constructor and the per-face {@code TexturedQuad} UV assignment /
 * cross-product face normal <b>bit-for-bit</b> (verified against the Optifine
 * 1.12.2 sources).</p>
 *
 * <p>The output is pure geometry data: 6 quads × 4 vertices, each vertex
 * {@code [x, y, z, u, v, nx, ny, nz]}. Positions are in <em>pixel</em> units
 * (the legacy per-vertex {@code * scale} 1/16 factor is applied later, at emit
 * time, exactly as vanilla {@code TexturedQuad.draw} did). This separation lets
 * the P85 geometry probes assert vertex math with no GL context.</p>
 */
public final class ModelBoxBaker
{
    private ModelBoxBaker()
    {
    }

    /**
     * Bake a box into 24 vertices (6 faces × 4). Each returned {@code float[]}
     * is {@code [x, y, z, u, v, nx, ny, nz]}. Quads are laid out in the vanilla
     * face order.
     *
     * @param texU    texture offset X (limb.texture[0])
     * @param texV    texture offset Y (limb.texture[1])
     * @param x       box origin X (pixel units; typically {@code -ax * w})
     * @param y       box origin Y
     * @param z       box origin Z
     * @param dx      box width
     * @param dy      box height
     * @param dz      box depth
     * @param delta   box inflation ({@code limb.sizeOffset})
     * @param mirror  mirror flag ({@code limb.mirror})
     * @param texW    texture width (model.texture[0])
     * @param texH    texture height (model.texture[1])
     */
    public static float[][] bake(int texU, int texV, float x, float y, float z, int dx, int dy, int dz, float delta, boolean mirror, float texW, float texH)
    {
        float f = x + dx;
        float f1 = y + dy;
        float f2 = z + dz;

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

        /* The eight cube corners, indexed exactly as vanilla's
         * vertexPositions[] (pt7=0, pt=1, pt1=2, pt2=3, pt3=4, pt4=5, pt5=6,
         * pt6=7). */
        float[][] pos = {
            {x, y, z},   // 0 pt7
            {f, y, z},   // 1 pt
            {f, f1, z},  // 2 pt1
            {x, f1, z},  // 3 pt2
            {x, y, f2},  // 4 pt3
            {f, y, f2},  // 5 pt4
            {f, f1, f2}, // 6 pt5
            {x, f1, f2}, // 7 pt6
        };

        /* Per-quad: the four corner indices then u1,v1,u2,v2 (in texel units). */
        int[][] idx = {
            {5, 1, 2, 6},
            {0, 4, 7, 3},
            {5, 4, 0, 1},
            {2, 3, 7, 6},
            {1, 0, 3, 2},
            {4, 5, 6, 7},
        };

        float[][] uv = {
            {texU + dz + dx, texV + dz, texU + dz + dx + dz, texV + dz + dy},
            {texU, texV + dz, texU + dz, texV + dz + dy},
            {texU + dz, texV, texU + dz + dx, texV + dz},
            {texU + dz + dx, texV + dz, texU + dz + dx + dx, texV},
            {texU + dz, texV + dz, texU + dz + dx, texV + dz + dy},
            {texU + dz + dx + dz, texV + dz, texU + dz + dx + dz + dx, texV + dz + dy},
        };

        float[][] out = new float[24][];

        for (int q = 0; q < 6; q++)
        {
            int[] v = idx[q];
            float u1 = uv[q][0], v1 = uv[q][1], u2 = uv[q][2], v2 = uv[q][3];

            /* Vanilla TexturedQuad(...) per-vertex UV assignment (f=f1=0). */
            float[][] vu = {
                {u2 / texW, v1 / texH},
                {u1 / texW, v1 / texH},
                {u1 / texW, v2 / texH},
                {u2 / texW, v2 / texH},
            };

            /* Assemble the four vertices [x,y,z,u,v]. */
            float[][] verts = new float[4][];

            for (int i = 0; i < 4; i++)
            {
                float[] p = pos[v[i]];

                verts[i] = new float[] {p[0], p[1], p[2], vu[i][0], vu[i][1], 0, 0, 0};
            }

            /* Mirror reverses the vertex array (flipFace) before the normal is
             * derived from vertex order. */
            if (mirror)
            {
                float[][] flipped = new float[4][];

                for (int i = 0; i < 4; i++)
                {
                    flipped[i] = verts[3 - i];
                }

                verts = flipped;
            }

            /* Face normal, vanilla TexturedQuad.draw:
             *   a = v[0] - v[1]  (v[1].subtractReverse(v[0]))
             *   b = v[2] - v[1]  (v[1].subtractReverse(v[2]))
             *   n = normalize(b x a) */
            double ax = verts[0][0] - verts[1][0];
            double ay = verts[0][1] - verts[1][1];
            double az = verts[0][2] - verts[1][2];
            double bx = verts[2][0] - verts[1][0];
            double by = verts[2][1] - verts[1][1];
            double bz = verts[2][2] - verts[1][2];

            double nx = by * az - bz * ay;
            double ny = bz * ax - bx * az;
            double nz = bx * ay - by * ax;
            double len = Math.sqrt(nx * nx + ny * ny + nz * nz);

            if (len != 0)
            {
                nx /= len;
                ny /= len;
                nz /= len;
            }

            for (int i = 0; i < 4; i++)
            {
                verts[i][5] = (float) nx;
                verts[i][6] = (float) ny;
                verts[i][7] = (float) nz;
                out[q * 4 + i] = verts[i];
            }
        }

        return out;
    }
}
