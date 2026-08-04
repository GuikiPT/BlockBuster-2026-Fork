package mchorse.blockbuster.utils.mclib;

import mchorse.mclib.utils.resources.IResourceTransformer;
import org.apache.commons.lang3.StringUtils;

/**
 * Rewrites Blockbuster's actor-skin resource locations as they are parsed by
 * {@code RLUtils} (roadmap P88, direct port of 1.12.2's
 * {@code mchorse.blockbuster.utils.mclib.BlockbusterResourceTransformer}).
 *
 * <p>Two load-bearing jobs:</p>
 * <ul>
 * <li>alias the old {@code blockbuster.actors} namespace to the shorthand
 * {@code b.a} (a dot is legal in a namespace, this is not a typo);</li>
 * <li>expand pre-2.x two-segment extensionless references
 * ({@code model/skin} → {@code model/skins/skin.png}) so old model.json /
 * morph skin references from old worlds resolve.</li>
 * </ul>
 *
 * <p>The {@link #fixPath(String)} trigger conditions are load-bearing and must
 * not be "simplified": the rewrite fires <b>only</b> when the path contains no
 * {@code '.'} <b>and</b> splits into exactly two {@code '/'}-segments. So
 * {@code "alex/skins/x.png"} (two slashes) and {@code "alex/skin.png"} (has a
 * dot) are left untouched. The single-string {@link #transform(String)} adds a
 * further guard — the location must have exactly one {@code '/'} — mirroring the
 * legacy {@code StringUtils.countMatches(location, "/") == 1} check.</p>
 */
public class BlockbusterResourceTransformer implements IResourceTransformer
{
    public static final String DOMAIN = "b.a";
    public static final String OLD_DOMAIN = "blockbuster.actors";

    @Override
    public String transformDomain(String domain, String path)
    {
        if (domain.equals(OLD_DOMAIN))
        {
            domain = DOMAIN;
        }

        return domain;
    }

    @Override
    public String transformPath(String domain, String path)
    {
        /* Fix old fashion model/skin resource locations */
        if (domain.equals(DOMAIN) || domain.equals(OLD_DOMAIN))
        {
            path = this.fixPath(path);
        }

        return path;
    }

    @Override
    public String transform(String location)
    {
        if (location.startsWith(OLD_DOMAIN + ":"))
        {
            location = DOMAIN + location.substring(OLD_DOMAIN.length());
        }

        if (location.startsWith(DOMAIN + ":") && StringUtils.countMatches(location, "/") == 1)
        {
            int index = location.indexOf(":");

            String domain = location.substring(0, index + 1);
            String path = this.fixPath(location.substring(index + 1));

            location = domain + path;
        }

        return location;
    }

    private String fixPath(String path)
    {
        if (path.indexOf(".") != -1)
        {
            return path;
        }

        String[] splits = path.split("/");

        if (splits.length != 2)
        {
            return path;
        }

        return splits[0] + "/skins/" + splits[1] + ".png";
    }
}
