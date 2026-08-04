package mchorse.blockbuster.utils.mclib;

import mchorse.blockbuster.utils.ResourcePackUtils;
import mchorse.mclib.utils.files.FileTree;
import mchorse.mclib.utils.files.entries.AbstractEntry;
import mchorse.mclib.utils.files.entries.FileEntry;
import mchorse.mclib.utils.files.entries.FolderEntry;
import mchorse.mclib.utils.resources.ResourceLocation;
import net.minecraft.client.MinecraftClient;

import java.util.List;

/**
 * Blockbuster jar file tree (roadmap P88.1) — every PNG the mod's own resource
 * pack ships, presented to the texture picker as a browsable folder tree rooted
 * {@code "blockbuster"} (which doubles as the RL domain of the entries, since
 * they carry their {@link ResourceLocation} directly rather than deriving it
 * from a {@code File}).
 *
 * <p>Port of Blockbuster 2.7.2's {@code BlockbusterJarTree}. Legacy built the
 * whole thing in its constructor, once, at {@code ClientProxy.load}. The port
 * splits construction from population ({@link #populate(List)}) for two
 * reasons: the enumeration source is only meaningful <em>after</em> a resource
 * reload has run, and 1.20.4 lets packs change at runtime, so the tree is
 * rebuilt in place on every client-resource reload. Rebuilding in place matters
 * because {@link mchorse.mclib.utils.files.GlobalTree#register} reparents the
 * root once — re-registering a fresh tree would duplicate the branch.</p>
 *
 * <p>Legacy source:
 * blockbuster-1.12/src/main/java/mchorse/blockbuster/utils/mclib/BlockbusterJarTree.java</p>
 */
public class BlockbusterJarTree extends FileTree
{
    public BlockbusterJarTree()
    {
        this.root = new FolderEntry("blockbuster", null, null);
    }

    public BlockbusterJarTree(List<ResourceLocation> pictures)
    {
        this();

        this.populate(pictures);
    }

    /**
     * Rebuild from the live client resource manager (the no-arg legacy
     * constructor's body). Safe to call repeatedly — see {@link #populate}.
     */
    public void reload()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        this.populate(ResourcePackUtils.getAllPictures(mc == null ? null : mc.getResourceManager()));
    }

    /**
     * Fill the tree from a path-sorted picture list (legacy sorted with
     * {@code Comparator.comparing(ResourceLocation::getResourcePath)} right
     * here; {@link ResourcePackUtils#getAllPictures} already does).
     *
     * <p>Unlike legacy this clears first, so a reload replaces rather than
     * appends, and it re-inserts the root's own {@code "../"} back entry
     * afterwards when the tree is already registered under
     * {@link mchorse.mclib.utils.files.GlobalTree} (legacy's
     * {@code FileTree.addBackEntry(jarTree.root)} in {@code ClientProxy}).</p>
     */
    public void populate(List<ResourceLocation> pictures)
    {
        this.root.getRawEntries().clear();

        for (ResourceLocation location : pictures)
        {
            this.add(location);
        }

        this.recursiveSort(this.root);

        if (this.root.parent != null)
        {
            FileTree.addBackEntry(this.root);
        }
    }

    private void recursiveSort(FolderEntry folder)
    {
        folder.getRawEntries().sort(FileTree.SORTER);

        for (AbstractEntry entry : folder.getRawEntries())
        {
            if (entry instanceof FolderEntry)
            {
                this.recursiveSort((FolderEntry) entry);
            }
        }
    }

    protected void add(ResourceLocation location)
    {
        String[] splits = location.getResourcePath().split("/");
        FolderEntry entry = this.root;

        main:
        for (int i = 0; i < splits.length - 1; i++)
        {
            for (AbstractEntry entryChild : entry.getRawEntries())
            {
                /* Total-reader guard: legacy tested the (always true)
                 * entry.isFolder() and cast entryChild regardless, so a pack
                 * shipping both "a/b.png" and "a/b.png/c.png" — impossible on
                 * disk, possible in a zip — was a ClassCastException. */
                if (entryChild.isFolder() && entryChild.title.equals(splits[i]))
                {
                    entry = (FolderEntry) entryChild;

                    continue main;
                }
            }

            FolderEntry folder = new FolderEntry(splits[i], null, entry);

            this.addBackEntryTo(folder);
            entry.getRawEntries().add(folder);
            entry = folder;
        }

        FileEntry file = new FileEntry(splits[splits.length - 1], null, location);

        entry.getRawEntries().add(file);
    }

    private void addBackEntryTo(FolderEntry entry)
    {
        entry.getRawEntries().sort(FileTree.SORTER);

        FileTree.addBackEntry(entry);
    }
}
