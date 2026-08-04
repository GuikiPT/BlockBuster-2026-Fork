package mchorse.blockbuster.commands.model;

import mchorse.mclib.utils.files.GlobalTree;
import mchorse.mclib.utils.files.entries.AbstractEntry;
import mchorse.mclib.utils.files.entries.FileEntry;
import mchorse.mclib.utils.files.entries.FolderEntry;
import mchorse.mclib.utils.resources.FilteredResourceLocation;
import mchorse.mclib.utils.resources.MultiResourceLocation;
import mchorse.mclib.utils.resources.RLUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Headless core of {@code /model combine} (roadmap P73).
 *
 * <p>Resolves each argument path through the McLib global file tree ({@code b.a}
 * skin domain, roadmap P17) and cartesian-combines the file entries across the
 * resolved folders into {@link MultiResourceLocation}s — exactly 1.12.2's
 * {@code SubCommandModelCombine.generate}/{@code generateRLs}. The actual image
 * compositing ({@code TextureProcessor.process} + PNG export) is a client-side
 * background job; only the combination enumeration lands here and is tested
 * headlessly (image compositing is covered by the P15 {@code TextureProcessor}
 * tests).</p>
 *
 * <p>The {@code "!"} join/split is the legacy internal encoding: each combo is
 * built as a {@code "!"}-joined string of child resource strings, then split
 * back into {@link FilteredResourceLocation} children. Safe as long as paths
 * contain no {@code '!'} — preserved verbatim.</p>
 *
 * <p>Legacy source:
 * {@code blockbuster-1.12/.../commands/model/SubCommandModelCombine.java}.</p>
 */
public final class CombineJob
{
    private CombineJob()
    {}

    /**
     * Resolve the argument paths to {@code b.a} folder entries, dropping any
     * that do not exist — legacy
     * {@code GlobalTree.TREE.getByPath("b.a/" + path, null)} with the nulls
     * skipped.
     */
    public static List<FolderEntry> resolve(String[] paths)
    {
        List<FolderEntry> entries = new ArrayList<FolderEntry>();

        for (String path : paths)
        {
            FolderEntry entry = GlobalTree.TREE.getByPath("b.a/" + path, null);

            if (entry != null)
            {
                entries.add(entry);
            }
        }

        return entries;
    }

    /**
     * Cartesian-combine the file entries of the resolved folders into
     * {@link MultiResourceLocation}s (legacy {@code generate}).
     */
    public static List<MultiResourceLocation> generate(List<FolderEntry> entries)
    {
        List<MultiResourceLocation> toExport = new ArrayList<MultiResourceLocation>();

        if (entries.isEmpty())
        {
            return toExport;
        }

        generateRLs(entries, entries.get(0), 0, "", (string) ->
        {
            String[] splits = string.substring(1).split("!");
            MultiResourceLocation location = new MultiResourceLocation();

            for (String split : splits)
            {
                location.children.add(new FilteredResourceLocation(RLUtils.create(split)));
            }

            toExport.add(location);
        });

        return toExport;
    }

    private static void generateRLs(List<FolderEntry> entries, FolderEntry folder, int index, String prefix, Consumer<String> callback)
    {
        for (AbstractEntry entry : folder.getEntries())
        {
            if (entry instanceof FileEntry)
            {
                FileEntry file = (FileEntry) entry;

                if (index == entries.size() - 1)
                {
                    callback.accept(prefix + "!" + file.resource);
                }
                else
                {
                    generateRLs(entries, entries.get(index + 1), index + 1, prefix + "!" + file.resource, callback);
                }
            }
        }
    }
}
