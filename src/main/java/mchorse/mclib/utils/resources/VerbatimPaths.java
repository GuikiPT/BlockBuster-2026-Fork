package mchorse.mclib.utils.resources;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The verbatim-path side table S07 open question 1 asked for (roadmap P88 /
 * P249).
 *
 * <p>1.20.4's {@link net.minecraft.util.Identifier} accepts only
 * {@code [a-z0-9_.-]} (plus {@code /} in the path), so
 * {@link ResourceLocation#toIdentifier()} lowercases and substitutes. That
 * projection is <b>lossy and irreversible</b>: a skin at
 * {@code config/blockbuster/models/steve/skins/MySkin.png} is minted as
 * {@code b.a:steve/skins/myskin.png}, and {@code ActorsPack} — which resolves by
 * {@code new File(folder, path)} — then misses on any case-sensitive filesystem.
 * Legacy had no such problem: mclib's {@code TextureLocation} reflectively wrote
 * the original-case string back into vanilla's {@code ResourceLocation} and
 * legacy {@code ActorsPack.getInputStream} used {@code getResourcePath()}
 * verbatim.</p>
 *
 * <p>This table restores that: every time a {@link ResourceLocation} is projected
 * to an {@code Identifier} and the projection <i>changes</i> the string, the
 * original spelling is recorded under the sanitized key. Resolvers consult
 * {@link #candidates(String, String)} first and fall back to the sanitized
 * spelling, so nothing that works today stops working.</p>
 *
 * <h3>Why a candidate <em>list</em> rather than the injective escape grammar the
 * open question floated</h3>
 *
 * <p>An injective grammar would have to change the identifier alphabet itself,
 * and that alphabet is load-bearing in three places that all project through
 * {@link ResourceLocation#sanitizePath}: the vanilla texture-map keys that
 * {@code /model report} / {@code /model clear} filter, the reverse index in
 * {@link MultiResourceLocationManager#byChild}, and
 * {@code ActorsPack.unsanitizeGifPath}'s {@code .gif_/} sentinel. Changing it
 * would be a wide, format-adjacent edit for a case that only needs the lookup to
 * succeed. Keying a small ordered set of verbatim spellings by the sanitized
 * identifier keeps the alphabet exactly as it is and makes the resolver total:
 * where the sanitized projection collides (a folder holding both
 * {@code MySkin.png} and {@code myskin.png}) every recorded spelling is tried in
 * registration order, so both files still resolve to themselves as long as they
 * were minted through {@link ResourceLocation#toIdentifier()}.</p>
 *
 * <p>Bounded: at most {@link #LIMIT} keys are retained; past that new keys are
 * dropped and lookups simply fall back to the sanitized path (the pre-P249
 * behaviour). Skin sets are orders of magnitude smaller than this in practice.</p>
 */
public class VerbatimPaths
{
    /** Hard cap on distinct sanitized keys held (see class javadoc). */
    public static final int LIMIT = 8192;

    private static final Map<String, Set<String>> TABLE = new ConcurrentHashMap<String, Set<String>>();

    private static String key(String namespace, String sanitizedPath)
    {
        return namespace + ":" + sanitizedPath;
    }

    /**
     * Record that {@code verbatimPath} sanitizes to {@code sanitizedPath} under
     * {@code namespace}. A no-op when the two are equal (the overwhelmingly
     * common case — this is the fast path on the per-frame
     * {@code toIdentifier()} calls).
     */
    public static void record(String namespace, String sanitizedPath, String verbatimPath)
    {
        if (namespace == null || sanitizedPath == null || verbatimPath == null
            || sanitizedPath.equals(verbatimPath))
        {
            return;
        }

        String key = key(namespace, sanitizedPath);
        Set<String> set = TABLE.get(key);

        if (set != null)
        {
            /* Fast path: already known. */
            if (set.contains(verbatimPath))
            {
                return;
            }
        }
        else if (TABLE.size() >= LIMIT)
        {
            return;
        }
        else
        {
            set = TABLE.computeIfAbsent(key,
                (k) -> Collections.synchronizedSet(new LinkedHashSet<String>()));
        }

        set.add(verbatimPath);
    }

    /**
     * The verbatim spellings recorded for a sanitized {@code namespace:path}, in
     * registration order. Never null; never contains {@code sanitizedPath}
     * itself — callers are expected to try these <i>and then</i> the sanitized
     * path.
     */
    public static List<String> candidates(String namespace, String sanitizedPath)
    {
        Set<String> set = namespace == null || sanitizedPath == null
            ? null : TABLE.get(key(namespace, sanitizedPath));

        if (set == null)
        {
            return Collections.emptyList();
        }

        synchronized (set)
        {
            return new ArrayList<String>(set);
        }
    }

    /** Test/reload hook. */
    public static void clear()
    {
        TABLE.clear();
    }

    /** Number of distinct sanitized keys currently held. */
    public static int size()
    {
        return TABLE.size();
    }
}
