package mchorse.blockbuster.client.compat.iris;

import java.util.Arrays;

import mchorse.blockbuster.api.formats.vox.data.VoxTexture;

/**
 * PBR companion maps for a VOX limb's palette texture (roadmap P217.3).
 *
 * <p>This is the VOX half of what {@link PbrFramePacker} does for animated GIF
 * skins, and it closes S21 open question 10 — "the VOX palette's shader specular
 * row has no owner".</p>
 *
 * <h2>There were never three rows</h2>
 *
 * <p>The port carried a {@code ROWS_SHADER = 3} allocation switch on
 * {@link VoxTexture} because 2.7.2's {@code updatePalette()} ends in</p>
 *
 * <pre>
 *   if (tex.length == 3 * this.palette.length)
 *       Arrays.fill(tex, 2 * this.palette.length, tex.length, specular);
 * </pre>
 *
 * <p>which reads like a taller texture. It is not. Legacy always allocated
 * {@code DynamicTexture(max(palette.length, 1), 1)} and never touched the height;
 * what tripled the buffer was <b>Optifine itself</b>. Optifine's patched
 * {@code net.minecraft.client.renderer.texture.DynamicTexture} allocates
 * {@code new int[width * height * 3]} — one array holding base, normal and
 * specular back to back — and {@code ShadersTex.initDynamicTexture} pre-fills the
 * middle third with {@code -8421377} and the last third with {@code 0}, then
 * {@code updateDynamicTexture} uploads the three thirds into
 * {@code MultiTexID.base/norm/spec} as three separate GL textures
 * ({@code updateDynTexSubImage1(src, w, h, 0, 0, page)}).</p>
 *
 * <p>So {@code tex.length == 3 * palette.length} is not a row check at all: with
 * height 1 it is exactly an <b>"is Optifine installed"</b> probe, and the branch
 * was live on every 1.12.2 install that had it. It is the same packed triple as
 * {@code GifFrameTexture.tryLoadMultiTex}'s {@code new int[width * height * 3]},
 * confirmed against {@code ShadersTex.setupTexture}, which slices {@code src} at
 * {@code [0, k) [k, 2k) [2k, 3k)} for base/norm/spec.</p>
 *
 * <p>Two consequences the port has to honour:</p>
 *
 * <ol>
 *   <li><b>{@link VoxTexture#rows} must stay {@link VoxTexture#ROWS_LEGACY}.</b>
 *       Raising it to {@code ROWS_SHADER} would build a {@code width × 3} image
 *       — three times the pixels, and the VOX mesher's V coordinate is
 *       {@code 0.5 ± 0.5} (i.e. the full height of whatever texture is bound,
 *       see {@code VoxBuilder.add}), so every limb would sample straight across
 *       the normal and specular thirds. Iris has no packed-triple convention, so
 *       nothing would read them as maps. That is a visual regression in exchange
 *       for nothing, and is why this phase changes no palette pixel.</li>
 *   <li><b>The maps become their own textures.</b> Iris takes normal/specular as
 *       ordinary {@code AbstractTexture}s through a {@code PBRTextureLoader}, so
 *       the two thirds legacy handed Optifine inside one array are built here as
 *       two {@code width × height} images with the same fills legacy ended up
 *       with.</li>
 * </ol>
 *
 * <h2>The fills are legacy's, byte for byte</h2>
 *
 * <ul>
 *   <li><b>Normal</b> — {@link PbrFramePacker#NORMAL_FILL}. Blockbuster never
 *       wrote the normal third of a VOX palette; it kept whatever
 *       {@code initDynamicTexture} left there, which is {@code -8421377} —
 *       bit-identical to {@code 0xFF7F7FFF}, the flat tangent-space normal
 *       {@code tryLoadMultiTex} fills explicitly. The two legacy routes agree,
 *       and so do Iris' own {@code PBRType.NORMAL} defaults.</li>
 *   <li><b>Specular</b> — {@link VoxTexture#specular}, i.e. the limb's
 *       {@code ModelLimb.specular} colour, which is what the legacy branch
 *       filled the last third with. Its default is {@code 0x00000000}, which is
 *       also {@link PbrFramePacker#SPECULAR_FILL}, so a model that never set
 *       a specular colour serves Iris exactly its own default and renders
 *       identically — the integration only becomes visible for the models that
 *       used the feature, which is the 1.12.2 behaviour.</li>
 * </ul>
 *
 * <p>Legacy source:
 * blockbuster-1.12/src/main/java/mchorse/blockbuster/api/formats/vox/data/VoxTexture.java;
 * Optifine SRC {@code net/minecraft/client/renderer/texture/DynamicTexture.java}
 * and {@code shadersmod/client/ShadersTex.java}.</p>
 */
public final class VoxPbrPacker
{
    /**
     * Optifine's {@code ShadersTex.initDynamicTexture} normal-third fill, in the
     * spelling the decompiled source uses. Pinned as a constant (and by test)
     * because it is the only evidence that the VOX palette's untouched normal
     * third and the GIF path's explicit {@link PbrFramePacker#NORMAL_FILL} are
     * the same value.
     */
    public static final int OPTIFINE_NORMAL_FILL = -8421377;

    private VoxPbrPacker()
    {}

    /**
     * The flat normal map for a palette texture: {@code width × height} pixels of
     * {@link PbrFramePacker#NORMAL_FILL}.
     *
     * @return {@code null} for a {@code null} palette — the caller degrades to
     *         "no PBR maps", never throws.
     */
    public static int[] normalMap(VoxTexture texture)
    {
        return texture == null ? null : fill(pixels(texture), PbrFramePacker.NORMAL_FILL);
    }

    /**
     * The specular map for a palette texture: {@code width × height} pixels of
     * {@link VoxTexture#specular} — legacy's
     * {@code Arrays.fill(tex, 2 * palette.length, tex.length, specular)}, lifted
     * out of the packed triple into its own image.
     *
     * @return {@code null} for a {@code null} palette.
     */
    public static int[] specularMap(VoxTexture texture)
    {
        return texture == null ? null : fill(pixels(texture), texture.specular);
    }

    /**
     * Pixel count of the map for {@code texture} — always exactly the palette
     * texture's own {@code width × height}, whatever {@link VoxTexture#rows}
     * says, because Iris samples a PBR map with the base texture's UVs.
     */
    public static int pixels(VoxTexture texture)
    {
        return texture == null ? 0 : texture.getWidth() * texture.getHeight();
    }

    private static int[] fill(int size, int value)
    {
        int[] out = new int[Math.max(size, 0)];

        Arrays.fill(out, value);

        return out;
    }
}
