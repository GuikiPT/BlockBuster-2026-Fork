package mchorse.blockbuster.utils.mclib;

import mchorse.mclib.utils.files.FileTree;
import mchorse.mclib.utils.files.entries.FolderImageEntry;

import java.io.File;

/**
 * Blockbuster custom model system's file tree (roadmap P88.1).
 *
 * <p>Direct port of Blockbuster 2.7.2's {@code mchorse.blockbuster.utils.mclib
 * .BlockbusterTree}: this bad boy looks through the models' skins folders and
 * recursively collects all the stuff.</p>
 *
 * <p>The root title {@code "b.a"} is <b>load-bearing</b> — {@link FolderImageEntry}
 * builds every file's {@link mchorse.mclib.utils.resources.ResourceLocation} as
 * {@code prefix + (prefix.contains(":") ? "/" : ":") + name}, so the root's title
 * becomes the RL <em>domain</em>, and that domain is exactly the one
 * {@link mchorse.blockbuster.client.ActorsPack} serves
 * ({@code ActorsPack.DOMAIN}). Renaming the root breaks every skin RL the picker
 * hands out.</p>
 *
 * <p>The {@code folder} passed here is legacy's {@code pack.folders.get(0)} — the
 * config models directory, wrapped in an {@link ImageFolder} so every {@code .gif}
 * is listed both as a raw file and as a {@link GifFolder} pseudo-directory of
 * frames (the wrapping propagates: {@code ImageFolder.listFiles()} re-wraps
 * sub-directories, which is what {@code FolderImageEntry} stores in its child
 * entries).</p>
 *
 * <p>Legacy source:
 * blockbuster-1.12/src/main/java/mchorse/blockbuster/utils/mclib/BlockbusterTree.java</p>
 */
public class BlockbusterTree extends FileTree
{
    public BlockbusterTree(File folder)
    {
        this.root = new FolderImageEntry("b.a", folder, null);
    }
}
