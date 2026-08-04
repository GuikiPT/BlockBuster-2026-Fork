package mchorse.mclib.utils.resources;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Vanilla texture paths that <b>moved</b> between 1.12.2 and 1.20.4 (roadmap
 * P249).
 *
 * <p>This class has no legacy counterpart — it is a port artefact of the same
 * family as the P71 id-translation shim: a legacy Blockbuster file may name a
 * vanilla resource by its 1.12.2 spelling, and 1.20.4 no longer ships that
 * spelling. Without a translation the request resolves to nothing and 1.20.4's
 * {@code TextureManager.registerTexture} swallows the {@code IOException}, logs
 * <i>"Failed to load texture"</i> and caches the missing-texture checkerboard
 * under that identifier until the next resource reload — i.e. a silent,
 * user-visible purple/black model.</p>
 *
 * <p>The two entries below are the ones that actually bite: the default player
 * skins were moved from {@code textures/entity/{steve,alex}.png} into
 * {@code textures/entity/player/{wide,slim}/…} in 1.19.3, and Blockbuster's own
 * bundled {@code alex}/{@code alex_3d}/{@code fred}/{@code fred_3d}/{@code eyes}
 * models named them by the old path. The bundled JSONs are now written with the
 * modern spelling directly; this map is what keeps <b>user-authored and
 * user-captured</b> 1.12.2 {@code model.json} files (and morph NBT, and scene
 * files) working — they carry the old string and must never be rewritten on
 * disk.</p>
 *
 * <h3>Why the translation lives at {@link ResourceLocation#toIdentifier()} and
 * not in {@link RLUtils}'s transformer chain</h3>
 *
 * <p>{@code RLUtils.create} transformers rewrite the stored string, which would
 * leak into every save path ({@code model.json}, NBT, JSON, clipboard) and break
 * the byte-identical round-trip the format goldens pin. Translating at the
 * {@code Identifier} boundary instead leaves {@link ResourceLocation#toString()}
 * and every serializer untouched: only the <i>lookup</i> is redirected, exactly
 * like the sanitization that already happens there.</p>
 *
 * <p>Deliberately tiny. This is not a general vanilla-rename database; add an
 * entry only when a legacy Blockbuster file provably names a vanilla path
 * 1.20.4 dropped.</p>
 *
 * <h3>The 1.13 flattening (P252, batch V-I)</h3>
 *
 * <p>P251 shipped a second copy of this idea —
 * {@code mchorse.blockbuster.legacy.LegacyTexturePath} — for the <b>other</b>
 * kind of pre-1.13 path: the two directories the flattening renamed,
 * {@code textures/blocks/} &rarr; {@code textures/block/} and
 * {@code textures/items/} &rarr; {@code textures/item/}. It was consulted from a
 * single bind seam ({@code GifTexture.resolveFrame}) because widening it looked
 * like a bigger change than one batch should make.</p>
 *
 * <p>It is not: <b>this class already sits at the universal chokepoint.</b>
 * Every user- and legacy-data-supplied texture path in the port is modelled as
 * an mclib {@link ResourceLocation} and reaches the texture manager through
 * {@link ResourceLocation#toIdentifier()} — so the widening is not a new hook,
 * it is two more rows in a table that was already global. {@code
 * LegacyTexturePath} now delegates here, so the two entry points cannot drift
 * apart.</p>
 *
 * <p><b>Why the directory rules are safe to apply globally</b>, where an
 * arbitrary rename table would not be: 1.20.4 ships <b>zero</b> files under
 * {@code assets/minecraft/textures/blocks/} or {@code .../textures/items/} (and
 * 1626 under the flattened spellings) — verified against the loom-cache client
 * jar and asserted by {@code LegacyTexturePathReachTest}. Every request under
 * the legacy directories is therefore a <i>guaranteed</i> miss today, and a miss
 * is silent: the texture manager logs one line and leaves sampler 0 incomplete,
 * which GL reads as {@code (0, 0, 0, 1)}. Translating can only turn a certain
 * miss into a possible hit; it cannot break a path that works.</p>
 *
 * <p>The per-file rename table is held to the same evidence bar as {@link #MOVES}
 * — it carries only names a real legacy file was seen to use.</p>
 */
public class VanillaTextureMoves
{
    public static final String VANILLA = "minecraft";

    private static final String LEGACY_BLOCKS = "textures/blocks/";
    private static final String MODERN_BLOCKS = "textures/block/";
    private static final String LEGACY_ITEMS = "textures/items/";
    private static final String MODERN_ITEMS = "textures/item/";

    private static final Map<String, String> MOVES;
    private static final Map<String, String> RENAMES;

    static
    {
        Map<String, String> moves = new HashMap<String, String>();

        /* Moved in 1.19.3 (the "player skin variants" change). Verified present
         * in the loom-cache named 1.20.4 client jar. */
        moves.put("textures/entity/steve.png", "textures/entity/player/wide/steve.png");
        moves.put("textures/entity/alex.png", "textures/entity/player/slim/alex.png");

        MOVES = Collections.unmodifiableMap(moves);

        Map<String, String> renames = new HashMap<String, String>();

        /* 1.12.2 file stem -> 1.20.4 file stem, for files the flattening renamed
         * as well as moved. Seeded from our own shipped
         * assets/blockbuster/particles/default_fire.json, copied verbatim from
         * 2.7.2 (P251). Same 16x512 animated sheet either side of the rename. */
        renames.put("fire_layer_0", "fire_0");
        renames.put("fire_layer_1", "fire_1");

        RENAMES = Collections.unmodifiableMap(renames);
    }

    /**
     * @return the 1.20.4 path for a {@code minecraft}-namespace path that moved,
     *         or {@code path} itself for everything else (total — never null,
     *         never throws)
     */
    public static String translate(String namespace, String path)
    {
        if (namespace == null || path == null || !VANILLA.equals(namespace))
        {
            return path;
        }

        String moved = MOVES.get(path);

        if (moved != null)
        {
            return moved;
        }

        if (path.startsWith(LEGACY_BLOCKS))
        {
            return MODERN_BLOCKS + rename(path.substring(LEGACY_BLOCKS.length()));
        }

        if (path.startsWith(LEGACY_ITEMS))
        {
            return MODERN_ITEMS + rename(path.substring(LEGACY_ITEMS.length()));
        }

        return path;
    }

    /**
     * Whether {@link #translate} would change this {@code namespace:path}, i.e.
     * whether it is a pre-1.20.4 spelling. Used by the shipped-asset lints.
     */
    public static boolean isLegacy(String namespace, String path)
    {
        return path != null && !path.equals(translate(namespace, path));
    }

    /**
     * Apply the file rename table to the part after the flattened directory,
     * preserving any extension ({@code fire_layer_1.png} &rarr;
     * {@code fire_1.png}) and any suffix a caller appended
     * ({@code .png.mcmeta}).
     */
    private static String rename(String file)
    {
        int dot = file.indexOf('.');
        String stem = dot < 0 ? file : file.substring(0, dot);
        String rest = dot < 0 ? "" : file.substring(dot);
        String renamed = RENAMES.get(stem);

        return (renamed == null ? stem : renamed) + rest;
    }

    /** The exact-path move table, for tests and diagnostics. */
    public static Map<String, String> moves()
    {
        return MOVES;
    }

    /** The flattening file-stem rename table, for tests and diagnostics. */
    public static Map<String, String> renames()
    {
        return RENAMES;
    }
}
