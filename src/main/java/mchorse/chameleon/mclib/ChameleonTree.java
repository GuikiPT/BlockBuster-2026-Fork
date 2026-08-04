package mchorse.chameleon.mclib;

import mchorse.mclib.utils.files.FileTree;
import mchorse.mclib.utils.files.entries.FolderImageEntry;

import java.io.File;

/**
 * Chameleon's skin file tree — the models folder browsed for textures by the
 * morph editor's texture picker and by {@code ChameleonSection}.
 *
 * <p>The root title {@code "c.s"} is <b>load-bearing</b>, exactly as
 * {@code "b.a"} is for {@link mchorse.blockbuster.utils.mclib.BlockbusterTree}:
 * {@link FolderImageEntry} builds each file's
 * {@link mchorse.mclib.utils.resources.ResourceLocation} as
 * {@code prefix + (prefix.contains(":") ? "/" : ":") + name}, so the root title
 * becomes the RL <b>domain</b> — and that domain is the one
 * {@code ChameleonPack} serves. Rename it and every skin a Chameleon morph
 * points at stops resolving.</p>
 *
 * <p>Lives in the common source set (unlike legacy's {@code @SideOnly(CLIENT)})
 * because both {@link FileTree} and {@link FolderImageEntry} do; only its
 * <i>registration</i> into {@code GlobalTree.TREE} is client-side.</p>
 *
 * Legacy source: chameleon/.../mclib/ChameleonTree.java
 */
public class ChameleonTree extends FileTree
{
    /** The resource domain Chameleon skins live under. */
    public static final String DOMAIN = "c.s";

    public ChameleonTree(File folder)
    {
        this.root = new FolderImageEntry(DOMAIN, folder, null);
    }
}
