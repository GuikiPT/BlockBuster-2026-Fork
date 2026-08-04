package mchorse.blockbuster.legacy;

import mchorse.mclib.utils.resources.ResourceLocation;
import mchorse.mclib.utils.resources.VanillaTextureMoves;
import net.minecraft.util.Identifier;

/**
 * P71 sibling — the pre-flattening <b>texture path</b> translation shim.
 *
 * <p>{@link LegacyIdMap} translates embedded block/item/entity <i>ids</i>. This
 * class translates the other kind of 1.12.2 string a legacy file can carry: a
 * {@code minecraft:} <b>resource path</b> pointing at a vanilla texture. The
 * 1.13 flattening renamed the two directories every 1.12.2 author typed —
 * {@code textures/blocks/} &rarr; {@code textures/block/} and
 * {@code textures/items/} &rarr; {@code textures/item/} — and renamed a handful
 * of files inside them. Neither vanilla's {@code DataFixerUpper} nor our id shim
 * touches a resource path, so without this the path simply misses.</p>
 *
 * <h2>Why a miss is worse than it sounds</h2>
 *
 * <p>A missing texture is <b>not</b> a crash and <b>not</b> obviously wrong on
 * screen: {@code RenderSystem.setShaderTexture} logs one
 * {@code Failed to load texture} line and leaves sampler 0 pointing at an
 * incomplete texture object, which GL samples as {@code (0, 0, 0, 1)}. The quad
 * draws at the right place, the right size, with the right alpha — and is
 * <b>solid black</b>. That is exactly how the shipped {@code default_fire}
 * Snowstorm preset behaved in the port: its
 * {@code minecraft:textures/blocks/fire_layer_1.png} (valid in 1.12.2, gone in
 * 1.20.4, where the same 16&times;512 sheet is
 * {@code minecraft:textures/block/fire_1.png}) rendered a black billboard in the
 * world and in the dashboard viewport alike.</p>
 *
 * <h2>Shape of the table</h2>
 *
 * <p>Deliberately <b>not</b> the full 1.13 rename manifest (hundreds of
 * entries). Following {@link LegacyIdTable}'s house rule — explicit lists, never
 * a global formula — it carries:</p>
 * <ul>
 *   <li>the two <b>directory</b> renames, which are mechanical and apply to
 *       every texture that kept its file name (the large majority), and</li>
 *   <li>an explicit <b>file</b> rename table, seeded only with names we have
 *       actual evidence for. Add an entry when a real legacy file surfaces
 *       carrying it; do not bulk-import the manifest speculatively.</li>
 * </ul>
 *
 * <p><b>Total by contract:</b> null, a non-{@code minecraft} namespace, an
 * already-modern path or an unrecognised one are all returned unchanged. The
 * translation is a pure string rewrite — it never asks whether the result
 * exists, because the resource manager is not available to every caller and a
 * wrong-but-modern path fails no worse than the legacy one did.</p>
 *
 * <p>Applied at <b>bind time</b>, not at parse time, so DTOs keep their raw
 * legacy strings and a user's file round-trips byte for byte — the same
 * discipline {@link LegacyIdMap} documents for ids.</p>
 *
 * <h2>Where the table actually lives (P252, batch V-I)</h2>
 *
 * <p>P251 kept the rules here and consulted them from exactly one seam,
 * {@code GifTexture.resolveFrame}, leaving model skins, multiskin layers and
 * every other 1.12.2-authored path untranslated. The inventory that followed
 * found something better than a list of seams to patch: the port already funnels
 * <b>every</b> user- and legacy-data-supplied texture path through
 * {@link ResourceLocation#toIdentifier()}, and P249 had already installed a
 * translation hook there ({@link VanillaTextureMoves}) for the vanilla paths that
 * <i>moved</i>. Two tables, one chokepoint, different contents.</p>
 *
 * <p>So the rules moved into {@link VanillaTextureMoves} and this class is now a
 * thin {@link Identifier}-level façade over it. Nothing about the contract
 * changed; what changed is that there is one table instead of two, it is
 * consulted everywhere instead of once, and the two entry points cannot drift.
 * This class stays because {@code GifTexture.resolveFrame} takes an
 * {@code Identifier} that a caller may have minted without going through
 * {@code ResourceLocation}, and because it is the name the Blockbuster-side
 * lints and docs refer to.</p>
 */
public final class LegacyTexturePath
{
    /** The only namespace whose paths the flattening moved. */
    public static final String VANILLA = VanillaTextureMoves.VANILLA;

    private LegacyTexturePath()
    {}

    /**
     * Translate a (possibly legacy) texture identifier into its 1.20.4 form.
     * Returns the argument itself when nothing applies, so callers can use the
     * result unconditionally and the per-frame bind path allocates nothing.
     */
    public static Identifier translate(Identifier location)
    {
        if (location == null)
        {
            return null;
        }

        String path = translate(location.getNamespace(), location.getPath());

        return path.equals(location.getPath()) ? location : new Identifier(location.getNamespace(), path);
    }

    /**
     * The pure half: {@code (namespace, path)} &rarr; translated path. Split out
     * so the table is testable without constructing an {@link Identifier} and
     * without a Minecraft bootstrap.
     */
    public static String translate(String namespace, String path)
    {
        return VanillaTextureMoves.translate(namespace, path);
    }

    /**
     * Whether this identifier is a pre-flattening vanilla texture path, i.e.
     * whether {@link #translate(Identifier)} would change it. Used by the
     * shipped-asset lints.
     */
    public static boolean isLegacy(String namespace, String path)
    {
        return VanillaTextureMoves.isLegacy(namespace, path);
    }
}
