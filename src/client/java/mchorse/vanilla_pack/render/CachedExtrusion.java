package mchorse.vanilla_pack.render;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import java.util.Arrays;

/**
 * A baked 2D-image extrusion — the mesh {@link ItemExtruder} builds out of a
 * texture's opaque pixels (roadmap P54).
 *
 * <p>Port of Metamorph 1.4's {@code mchorse.vanilla_pack.render.CachedExtrusion}.
 * Legacy was a thin wrapper over one OpenGL VBO: {@link #addVertex} appended
 * interleaved {@code position/normal/uv} floats into a {@code ByteBuffer},
 * {@link #flush()} uploaded it with {@code glBufferData}, and {@code render()}
 * bound the buffer, pointed the fixed-function {@code glVertexPointer} /
 * {@code glNormalPointer} / {@code glTexCoordPointer} client arrays at it and
 * issued one {@code glDrawArrays(GL_TRIANGLES, …)} with culling off.</p>
 *
 * <p><b>What changed and why.</b> None of that survives the core profile: there
 * are no client-state arrays, the texture bind is a {@code RenderLayer} rather
 * than an imperative call, and lighting/overlay are per-vertex attributes. So
 * the mesh stays on the CPU and is re-emitted through the frame's
 * {@link VertexConsumer} each draw ({@link #emit}), exactly as P79's
 * {@code ModelExtrudedLayer} does with its own voxel mesh — same vertices, same
 * winding, same normals; only the transport differs. The texture the mesh was
 * built from is carried on {@link #texture} so the caller can pick the layer
 * (legacy bound it here, inside {@code render()}).</p>
 *
 * <p><b>Triangles became quads.</b> Legacy emitted each face as two
 * {@code GL_TRIANGLES} sharing an edge — six vertices in the order
 * {@code (2,1,4)}, {@code (2,4,3)}. The vertex formats that carry a lightmap on
 * 1.20.4 are drawn as {@code QUADS}, so a face is stored as the four corners
 * {@code 2, 1, 4, 3} instead. That is the exact quad those two triangles
 * tessellate, so both the geometry and the winding are unchanged.</p>
 *
 * <p><b>The buffer no longer overflows.</b> Legacy sized its {@code ByteBuffer}
 * at {@code w * h} faces, but a single pixel can contribute up to four side
 * faces on top of the two full-sprite faces — so a sufficiently perforated
 * texture (a checkerboard being the worst case) threw
 * {@code BufferOverflowException} out of {@code addVertex} and killed the render
 * call. The backing array grows instead, which is the "every reader is total"
 * rule: a hostile texture now draws its extrusion rather than latching
 * {@code errorRendering}.</p>
 *
 * @see ItemExtruder
 */
public class CachedExtrusion
{
    /** Legacy {@code BYTES_PER_VERTEX / 4}: 3 position + 3 normal + 2 uv. */
    public static final int FLOATS_PER_VERTEX = 3 + 3 + 2;

    /** Four corners per face — see the class note on triangles vs. quads. */
    public static final int VERTICES_PER_FACE = 4;

    /**
     * The texture this mesh was built from. Legacy bound it inside
     * {@code render()}; here it selects the caller's {@code RenderLayer}.
     */
    public final Identifier texture;

    private float[] data;
    private int vertices;

    public CachedExtrusion(Identifier texture, int w, int h)
    {
        this.texture = texture;

        /* Legacy's initial capacity, in floats: one face per pixel. Unlike
         * legacy this is a starting size, not a hard ceiling. */
        int faces = Math.max(1, w * h);

        this.data = new float[faces * VERTICES_PER_FACE * FLOATS_PER_VERTEX];
    }

    public void addVertex(float x, float y, float z, float nx, float ny, float nz, float u, float v)
    {
        int offset = this.vertices * FLOATS_PER_VERTEX;

        if (offset + FLOATS_PER_VERTEX > this.data.length)
        {
            this.data = Arrays.copyOf(this.data, Math.max(this.data.length * 2, offset + FLOATS_PER_VERTEX));
        }

        this.data[offset] = x;
        this.data[offset + 1] = y;
        this.data[offset + 2] = z;

        this.data[offset + 3] = nx;
        this.data[offset + 4] = ny;
        this.data[offset + 5] = nz;

        this.data[offset + 6] = u;
        this.data[offset + 7] = v;

        this.vertices += 1;
    }

    /**
     * Legacy uploaded the buffer to the GPU here. There is nothing to upload —
     * the mesh is emitted per frame — so this trims the over-allocated tail,
     * which is the only part of {@code buffer.flip()} that still means
     * something.
     */
    public void flush()
    {
        int used = this.vertices * FLOATS_PER_VERTEX;

        if (used != this.data.length)
        {
            this.data = Arrays.copyOf(this.data, used);
        }
    }

    public int getVertexCount()
    {
        return this.vertices;
    }

    public boolean isEmpty()
    {
        return this.vertices == 0;
    }

    /**
     * The eight interleaved floats of one vertex
     * ({@code x, y, z, nx, ny, nz, u, v}) — the layout legacy wrote into its
     * VBO. Used by the geometry tests.
     */
    public float[] getVertex(int index)
    {
        if (index < 0 || index >= this.vertices)
        {
            throw new IndexOutOfBoundsException("No vertex " + index + " in an extrusion of " + this.vertices);
        }

        return Arrays.copyOfRange(this.data, index * FLOATS_PER_VERTEX, (index + 1) * FLOATS_PER_VERTEX);
    }

    /**
     * Draw the mesh into the frame's vertex consumer — legacy's
     * {@code render()}, minus the texture bind (now the layer's job) and the
     * cull toggle (ditto; see {@code McLibRenderLayers.extrusion}).
     *
     * <p>Normals go through the stack's normal matrix, which is what the
     * fixed-function pipeline did with {@code glNormalPointer} data. Legacy had
     * no per-vertex colour at all — the mesh drew with whatever
     * {@code glColor4f} was current, and both call sites set it to opaque
     * white — so the colour is a parameter here rather than vertex data.</p>
     */
    public void emit(VertexConsumer consumer, MatrixStack.Entry entry, int light, int overlay, float r, float g, float b, float a)
    {
        this.emit(consumer, entry, light, overlay, r, g, b, a, null);
    }

    /**
     * The same draw with legacy's {@code GL_TEXTURE} matrix folded into the UVs.
     * 1.12.2 could push a matrix onto the texture stack and have it apply to
     * whatever was drawn next — which is how {@code ImageMorph}'s UV offset and
     * rotation reached the extrusion as well as the flat quad. The core profile
     * has no texture matrix, so the transform is applied per vertex on the CPU,
     * the same way {@code ImageMorphRenderer} already does it for the quad.
     *
     * @param uv {@code null} for untransformed UVs (legacy's "no texture matrix
     *           pushed" case)
     */
    public void emit(VertexConsumer consumer, MatrixStack.Entry entry, int light, int overlay, float r, float g, float b, float a, IUVTransform uv)
    {
        if (consumer == null || entry == null)
        {
            return;
        }

        float[] texture = uv == null ? null : new float[2];

        for (int i = 0; i < this.vertices; i++)
        {
            int offset = i * FLOATS_PER_VERTEX;
            float u = this.data[offset + 6];
            float v = this.data[offset + 7];

            if (uv != null)
            {
                uv.transform(u, v, texture);

                u = texture[0];
                v = texture[1];
            }

            consumer.vertex(entry.getPositionMatrix(), this.data[offset], this.data[offset + 1], this.data[offset + 2])
                .color(r, g, b, a)
                .texture(u, v)
                .overlay(overlay)
                .light(light)
                .normal(entry.getNormalMatrix(), this.data[offset + 3], this.data[offset + 4], this.data[offset + 5])
                .next();
        }
    }

    /** See {@link #emit(VertexConsumer, MatrixStack.Entry, int, int, float, float, float, float, IUVTransform)}. */
    public interface IUVTransform
    {
        /** Writes the transformed {@code (u, v)} into {@code out}. */
        void transform(float u, float v, float[] out);
    }
}
