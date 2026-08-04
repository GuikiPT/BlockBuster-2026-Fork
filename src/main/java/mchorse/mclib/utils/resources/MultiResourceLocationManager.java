package mchorse.mclib.utils.resources;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.Identifier;

public class MultiResourceLocationManager
{
    private static int id = 0;
    private static Map<ResourceLocation, List<Pair>> map = new HashMap<ResourceLocation, List<Pair>>();

    /**
     * Forward index id &rarr; registered composite (roadmap P249, defect F).
     * {@link MultiResourceLocation#toIdentifier()} names a composite by its id,
     * so the resource seam needs to get from {@code mclib:multiskin/<id>} back
     * to the child list it has to composite.
     */
    private static Map<Integer, MultiResourceLocation> byId = new HashMap<Integer, MultiResourceLocation>();

    /**
     * Every multiskin registered so far whose child list contains
     * {@code child} (S22/P233 — the watchdog needs the reverse index: "which
     * composites does this edited file feed?").
     *
     * <p>Comparison goes through {@link ResourceLocation#toIdentifier()} on
     * both sides, because the caller's key comes from the <em>vanilla texture
     * map</em> (sanitized/lowercased) while a stored child keeps its verbatim,
     * case-preserving legacy spelling — a direct {@code equals} would miss
     * {@code b.a:Anvil/skins/x.png} for a change to {@code anvil/skins/x.png}.
     * Same projection {@code ModelExtrudedLayer.clearByIdentifier} uses.</p>
     *
     * <p>Total: a child whose sanitization cannot form a legal identifier is
     * skipped, never thrown.</p>
     */
    public static synchronized List<MultiResourceLocation> byChild(ResourceLocation child)
    {
        List<MultiResourceLocation> result = new ArrayList<MultiResourceLocation>();

        if (child == null)
        {
            return result;
        }

        Identifier target = identifier(child);

        if (target == null)
        {
            return result;
        }

        for (List<Pair> pairs : map.values())
        {
            for (Pair pair : pairs)
            {
                for (FilteredResourceLocation location : pair.mrl.children)
                {
                    if (location != null && target.equals(identifier(location.path)))
                    {
                        result.add(pair.mrl);

                        break;
                    }
                }
            }
        }

        return result;
    }

    private static Identifier identifier(ResourceLocation location)
    {
        if (location == null)
        {
            return null;
        }

        try
        {
            return location.toIdentifier();
        }
        catch (Exception e)
        {
            return null;
        }
    }

    public static synchronized int getId(MultiResourceLocation location)
    {
        if (location.children.isEmpty())
        {
            return -1;
        }

        ResourceLocation keyRL = location.children.get(0).path;
        List<Pair> pairs = map.get(keyRL);

        if (pairs == null)
        {
            pairs = new ArrayList<Pair>();

            map.put(keyRL, pairs);
        }

        for (Pair pair : pairs)
        {
            if (pair.mrl.equals(location))
            {
                return pair.id;
            }
        }

        int newId = id;
        MultiResourceLocation stored = location.copy();

        pairs.add(new Pair(newId, stored));
        byId.put(newId, stored);
        id += 1;

        return newId;
    }

    /**
     * The composite registered under {@code id}, or {@code null} (roadmap P249).
     * The returned instance is the manager's own defensive copy — treat as
     * read-only.
     */
    public static synchronized MultiResourceLocation byId(int id)
    {
        return byId.get(id);
    }

    private static class Pair
    {
        public int id;
        public MultiResourceLocation mrl;

        public Pair(int id, MultiResourceLocation mrl)
        {
            this.id = id;
            this.mrl = mrl;
        }
    }
}
