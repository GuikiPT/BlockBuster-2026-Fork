package mchorse.blockbuster_pack.client;

import mchorse.blockbuster.ClientProxy;
import mchorse.blockbuster.api.Model;
import mchorse.blockbuster_pack.BlockbusterSection;
import mchorse.mclib.utils.files.entries.AbstractEntry;
import mchorse.mclib.utils.files.entries.FileEntry;
import mchorse.mclib.utils.files.entries.FolderEntry;
import mchorse.mclib.utils.resources.ResourceLocation;

/**
 * Client half of {@link BlockbusterSection#getSkin} (roadmap P88.1) — the skin
 * tree walk that gives a remote model with no {@code defaultTexture} a picker
 * icon.
 *
 * <p>Verbatim port of the body legacy carried inline (behind
 * {@code @SideOnly(Side.CLIENT)}) in {@code BlockbusterSection.getSkin}, moved
 * to the client source set because {@code BlockbusterSection} is common. Legacy
 * behaviour kept: the first {@code FileEntry} of the model's own {@code skins}
 * folder wins; the borrowed {@code model.skins} folder is consulted whenever
 * that produced nothing (a missing folder and a folder holding only
 * sub-folders both fall through); the search is one level deep — unlike
 * {@code GuiCustomMorph.addSkins} it does not recurse; and back entries, being
 * {@code FolderEntry}s, are skipped by the {@code instanceof} test.</p>
 */
public class BlockbusterSectionSkins
{
    public static void install()
    {
        BlockbusterSection.skinResolver = BlockbusterSectionSkins::getSkin;
    }

    private static ResourceLocation getSkin(String key, Model model)
    {
        ResourceLocation skin = firstSkin(ClientProxy.skins(key + "/skins"));

        return skin != null ? skin : firstSkin(ClientProxy.skins(model.skins + "/skins"));
    }

    private static ResourceLocation firstSkin(FolderEntry folder)
    {
        if (folder == null)
        {
            return null;
        }

        for (AbstractEntry skinEntry : folder.getEntries())
        {
            if (skinEntry instanceof FileEntry)
            {
                return ((FileEntry) skinEntry).resource;
            }
        }

        return null;
    }
}
