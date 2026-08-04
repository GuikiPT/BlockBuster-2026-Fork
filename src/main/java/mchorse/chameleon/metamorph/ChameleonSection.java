package mchorse.chameleon.metamorph;

import mchorse.chameleon.Chameleon;
import mchorse.mclib.utils.files.entries.AbstractEntry;
import mchorse.mclib.utils.files.entries.FileEntry;
import mchorse.mclib.utils.files.entries.FolderEntry;
import mchorse.metamorph.api.creative.categories.MorphCategory;
import mchorse.metamorph.api.creative.sections.MorphSection;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/**
 * Chameleon's creative-picker section: one morph per loaded model folder,
 * grouped into a category per parent folder.
 *
 * <p>{@link #update} reloads the models first, so dropping a new model folder in
 * and reopening the picker is enough to see it — that is the only reload trigger
 * legacy had, and the only one here.</p>
 *
 * <p>Each morph is pre-filled with the <b>first</b> skin found in the model's
 * {@code skins/} folder, so the picker shows something textured rather than a
 * missing-texture model.</p>
 *
 * <p>Port note: the file-tree lookup goes through {@link Chameleon#tree}, the
 * client-installed {@code ChameleonTree} seam — legacy read
 * {@code ClientProxy.tree} directly, which a common-source class cannot. It is
 * {@code null} on a dedicated server, where the whole section is empty anyway.</p>
 *
 * Legacy source: chameleon/.../metamorph/ChameleonSection.java
 */
public class ChameleonSection extends MorphSection
{
    public ChameleonSection(String title)
    {
        super(title);
    }

    @Override
    public void update(World world)
    {
        /* Reload models */
        Chameleon.reloadModels();

        this.categories.clear();

        Map<String, ChameleonCategory> categories = new HashMap<String, ChameleonCategory>();

        for (String key : Chameleon.getModelKeys())
        {
            ChameleonMorph morph = new ChameleonMorph();
            FolderEntry skins = Chameleon.tree == null ? null : Chameleon.tree.getByPath(key + "/skins/", null);

            if (skins != null)
            {
                for (AbstractEntry entry : skins.getEntries())
                {
                    if (entry instanceof FileEntry)
                    {
                        morph.skin = ((FileEntry) entry).resource;

                        break;
                    }
                }
            }

            morph.name = Chameleon.MORPH_PREFIX + key;

            String categoryKey = key.contains("/") ? key.substring(0, key.lastIndexOf("/")) : "";
            ChameleonCategory category = categories.get(categoryKey);

            if (category == null)
            {
                category = new ChameleonCategory(this, "chameleon", categoryKey);
                categories.put(categoryKey, category);
            }

            category.add(morph);
        }

        for (ChameleonCategory category : categories.values())
        {
            category.sort();
            this.categories.add(category);
        }

        this.categories.sort((a, b) -> a.getTitle().compareTo(b.getTitle()));
    }

    /**
     * A category whose title is the shared {@code morph.category.chameleon}
     * string with the sub-folder path appended in brackets, so nested model
     * packs are distinguishable in the picker.
     */
    public static class ChameleonCategory extends MorphCategory
    {
        private String subtitle;

        public ChameleonCategory(MorphSection parent, String title, String subtitle)
        {
            super(parent, title);

            this.subtitle = subtitle;
        }

        @Override
        public String getTitle()
        {
            return super.getTitle() + (this.subtitle == null || this.subtitle.isEmpty() ? "" : " (" + this.subtitle + ")");
        }
    }
}
