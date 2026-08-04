package mchorse.mclib.utils.resources;

import java.util.Locale;
import net.minecraft.util.Identifier;

/**
 * Bundled resource location
 *
 * This class reproduces the surface of 1.12.2's
 * {@code net.minecraft.util.ResourceLocation} that mclib and Blockbuster
 * compile against. It exists because 1.20.4's {@code net.minecraft.util.Identifier}
 * is final and validates its charset (lowercase only), which makes the legacy
 * subclassing tricks ({@link MultiResourceLocation}, {@link TextureLocation})
 * impossible and would crash on legacy skins with mixed-case paths.
 *
 * Semantics mirror the effective legacy behavior for mclib locations: case is
 * preserved as given (legacy {@link TextureLocation} used reflection to undo
 * vanilla's lowercasing), the single-string constructor splits on the first
 * {@code ':'} with default domain {@code "minecraft"}, equality is by value
 * over domain + path, and {@code compareTo} compares path first, then domain
 * (as 1.12.2 did). No validation is performed — this is a total reader;
 * conversion/sanitization to {@code Identifier} happens only at the texture
 * manager boundary (S7), see {@link #toIdentifier()}.
 */
public class ResourceLocation implements Comparable<ResourceLocation>
{
    protected String domain;
    protected String path;

    protected ResourceLocation(int unused, String... resourceName)
    {
        this.domain = resourceName[0] == null || resourceName[0].isEmpty() ? "minecraft" : resourceName[0];
        this.path = resourceName[1] == null ? "" : resourceName[1];
    }

    public ResourceLocation(String resourceName)
    {
        this(0, splitObjectName(resourceName));
    }

    public ResourceLocation(String resourceDomainIn, String resourcePathIn)
    {
        this(0, resourceDomainIn, resourcePathIn);
    }

    /**
     * Splits on the first {@code ':'} — {@code "a:b:c"} becomes domain
     * {@code "a"}, path {@code "b:c"}; a leading colon keeps the default
     * domain (mirrors 1.12.2's splitObjectName)
     */
    protected static String[] splitObjectName(String toSplit)
    {
        String[] result = new String[] {"minecraft", toSplit};
        int i = toSplit.indexOf(':');

        if (i >= 0)
        {
            result[1] = toSplit.substring(i + 1);

            if (i >= 1)
            {
                result[0] = toSplit.substring(0, i);
            }
        }

        return result;
    }

    public String getResourceDomain()
    {
        return this.domain;
    }

    public String getResourcePath()
    {
        return this.path;
    }

    @Override
    public String toString()
    {
        return this.domain + ":" + this.path;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }

        if (!(obj instanceof ResourceLocation))
        {
            return false;
        }

        ResourceLocation location = (ResourceLocation) obj;

        return this.domain.equals(location.domain) && this.path.equals(location.path);
    }

    @Override
    public int hashCode()
    {
        return 31 * this.domain.hashCode() + this.path.hashCode();
    }

    /**
     * 1.12.2 order: path first, then domain
     */
    @Override
    public int compareTo(ResourceLocation location)
    {
        int i = this.path.compareTo(location.path);

        if (i == 0)
        {
            i = this.domain.compareTo(location.domain);
        }

        return i;
    }

    /**
     * Convert to a modern {@link net.minecraft.util.Identifier} with minimal
     * sanitization (lowercase, illegal characters replaced with {@code '_'}).
     *
     * This is a lossy convenience for the S7 texture-manager boundary — the
     * data model itself never validates, so legacy mixed-case paths survive
     * (de)serialization untouched.
     *
     * <p>Two port-only steps happen here and <b>nowhere else</b>, so that no
     * serializer ever sees them (roadmap P249):</p>
     * <ul>
     * <li>{@link VanillaTextureMoves} redirects the vanilla paths 1.20.4 moved
     * or renamed (legacy files still name the 1.12.2 spellings) — the handful of
     * exact moves from P249 plus, since P252, the two directories the 1.13
     * flattening renamed. <b>This is the port's universal legacy-texture-path
     * seam:</b> every user- and legacy-data-supplied texture path is modelled as
     * a {@code ResourceLocation} and reaches the texture manager through here,
     * so a rule added to that table applies to model skins, multiskin layers,
     * morph textures, particle schemes and GUI previews alike;</li>
     * <li>{@link VerbatimPaths} records the original spelling whenever
     * sanitization changed it, so {@code ActorsPack} can undo the projection
     * when it walks the skins folders (uppercase / spaced filenames).</li>
     * </ul>
     *
     * <p>The redirect is looked up under the <i>sanitized</i> domain, so a
     * location that only spells {@code minecraft} after sanitization is still
     * translated. (1.12.2's own {@code ResourceLocation} lowercased the domain
     * on construction, so no legacy file on disk can carry a mixed-case one —
     * this is belt and braces for locations the port mints itself.)</p>
     */
    public Identifier toIdentifier()
    {
        String domain = sanitizeDomain(this.domain);
        String verbatim = VanillaTextureMoves.translate(domain, this.path);
        String path = sanitizePath(verbatim);

        VerbatimPaths.record(domain, path, verbatim);

        return new Identifier(domain, path);
    }

    /**
     * The domain half of {@link #toIdentifier}'s sanitization, exposed because
     * consumers that match against the <em>vanilla</em> texture map's keys have
     * to speak the sanitized alphabet (e.g. the legacy {@code /model clear}
     * domain list contains {@code "s&b"}, which is illegal in an
     * {@code Identifier} namespace and lives in the map as {@code "s_b"}).
     */
    public static String sanitizeDomain(String domain)
    {
        return sanitize(domain, false);
    }

    /**
     * The path half of {@link #toIdentifier}'s sanitization. Used by
     * {@code /model clear} to bring a user-typed path prefix into the same
     * (lowercased, charset-legal) alphabet as the map keys it filters.
     */
    public static String sanitizePath(String path)
    {
        return sanitize(path, true);
    }

    private static String sanitize(String string, boolean isPath)
    {
        StringBuilder builder = new StringBuilder(string.length());

        for (char c : string.toLowerCase(Locale.ROOT).toCharArray())
        {
            boolean valid = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '-' || (isPath && c == '/');

            builder.append(valid ? c : '_');
        }

        if (!isPath && builder.length() == 0)
        {
            return "minecraft";
        }

        return builder.toString();
    }
}
