package mchorse.metamorph.client.gui.creative;

import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.context.GuiContextMenu;
import mchorse.mclib.client.gui.framework.elements.context.GuiSimpleContextMenu;
import mchorse.mclib.client.gui.framework.elements.modals.GuiConfirmModal;
import mchorse.mclib.client.gui.framework.elements.modals.GuiModal;
import mchorse.mclib.client.gui.framework.elements.modals.GuiPromptModal;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.utils.GuiUtils;
import mchorse.mclib.client.gui.utils.Icons;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.creative.categories.AcquiredCategory;
import mchorse.metamorph.api.creative.categories.MorphCategory;
import mchorse.metamorph.api.creative.categories.RecentCategory;
import mchorse.metamorph.api.creative.categories.UserCategory;
import mchorse.metamorph.api.creative.sections.MorphSection;
import mchorse.metamorph.api.creative.sections.UserSection;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.StringNbtReader;

import java.util.function.Consumer;

/**
 * User morph section GUI (port of Metamorph 1.4's {@code GuiUserSection},
 * roadmap P58). Extends {@link GuiMorphSection} with the user-category
 * management context menu: paste NBT (plain, or the Ctrl-held prompt modal
 * pre-filled from the clipboard), copy-to-recent, add/rename/remove category,
 * clear category and remove morph.
 *
 * <p>Legacy dispatched to this class through {@code MorphSection.getGUI(...)},
 * which {@code UserSection} overrode. In this port {@code MorphSection} lives in
 * the common source set (no client GUI types), so the dispatch happens in
 * {@link GuiCreativeMorphs#setupSections} on {@code section instanceof
 * UserSection} instead — same result, no common → client dependency.</p>
 *
 * <p>Legacy relies on {@link GuiMorphSection#createContextMenu} gating only on
 * {@code this.parent == null}: whenever the section has a picker parent, super
 * returns a {@link GuiSimpleContextMenu} that this class appends to.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/client/gui/creative/GuiUserSection.java
 */
public class GuiUserSection extends GuiMorphSection
{
    public GuiUserSection(MinecraftClient mc, GuiElement parent, MorphSection section, Consumer<GuiMorphSection> callback)
    {
        super(mc, parent, section, callback);
    }

    @Override
    public GuiContextMenu createContextMenu(GuiContext context)
    {
        GuiContextMenu menu = super.createContextMenu(context);

        if (this.parent == null || !(menu instanceof GuiSimpleContextMenu))
        {
            return menu;
        }

        GuiSimpleContextMenu contextMenu = (GuiSimpleContextMenu) menu;

        MorphCategory category = this.hoverCategory;
        AbstractMorph morph = this.hoverMorph;

        boolean user = category instanceof UserCategory;
        boolean acquired = category instanceof AcquiredCategory;
        boolean recent = category instanceof RecentCategory;

        if (category != null)
        {
            contextMenu.action(Icons.PASTE, IKey.lang("metamorph.gui.creative.context.paste"), () -> this.pasteMorph(category));
        }

        if (morph != null && (user || acquired))
        {
            contextMenu.action(Icons.REFRESH, IKey.lang("metamorph.gui.creative.context.to_recent"), () -> this.copyToRecent(morph));
        }

        contextMenu.action(Icons.ADD, IKey.lang("metamorph.gui.creative.context.add_category"), () -> this.section.add(new UserCategory(this.section, "User category")));

        if (user)
        {
            contextMenu.action(Icons.EDIT, IKey.lang("metamorph.gui.creative.context.rename_category"), () -> this.renameCategory(category));
            contextMenu.action(Icons.CLOSE, IKey.lang("metamorph.gui.creative.context.remove_category"), () -> this.removeCategory(category));
        }

        if (recent || acquired)
        {
            contextMenu.action(Icons.CLOSE, IKey.lang("metamorph.gui.creative.context.clear_category"), () -> this.clearCategory(category));
        }

        if (morph != null && category != null)
        {
            contextMenu.action(Icons.CLOSE, IKey.lang("metamorph.gui.creative.context.remove_morph"), () -> this.removeMorph(category, morph));
        }

        return contextMenu;
    }

    void pasteMorph(MorphCategory category)
    {
        String clipboard = GuiUtils.getClipboardString();

        if (!GuiUtils.isCtrlKeyDown())
        {
            try
            {
                category.add(MorphManager.INSTANCE.morphFromNBT(StringNbtReader.parse(clipboard)));

                return;
            }
            catch (Exception e)
            {}
        }

        GuiModal.addFullModal(this.parent, () ->
        {
            GuiPromptModal modal = new GuiPromptModal(this.mc, IKey.lang("metamorph.gui.creative.context.paste_modal"), (string) ->
            {
                try
                {
                    category.add(MorphManager.INSTANCE.morphFromNBT(StringNbtReader.parse(string)));
                }
                catch (Exception e)
                {}
            });

            modal.text.field.setMaxStringLength(100000);
            modal.setValue(clipboard);

            return modal;
        });
    }

    void renameCategory(MorphCategory category)
    {
        GuiModal.addModal(this.parent, () ->
        {
            GuiPromptModal modal = new GuiPromptModal(this.mc, IKey.lang("metamorph.gui.creative.context.rename_category_modal"), (string) ->
            {
                category.title = string;

                if (this.section instanceof UserSection)
                {
                    ((UserSection) this.section).save();
                }
            });

            modal.setValue(category.getTitle());
            modal.flex().relative(this.parent).xy(0.5F, 0.5F).wh(160, 180).anchor(0.5F, 0.5F);

            return modal;
        });
    }

    void removeCategory(MorphCategory category)
    {
        GuiModal.addModal(this.parent, () ->
        {
            return GuiConfirmModal.createTemplate(this.mc, this.parent, IKey.lang("metamorph.gui.creative.context.remove_category_modal"), (value) ->
            {
                if (value)
                {
                    this.section.remove(category);
                }
            });
        });
    }

    void removeMorph(MorphCategory category, AbstractMorph morph)
    {
        GuiModal.addModal(this.parent, () ->
        {
            return GuiConfirmModal.createTemplate(this.mc, this.parent, IKey.lang("metamorph.gui.creative.context.remove_morph_modal"), (value) ->
            {
                if (value)
                {
                    category.remove(morph);
                }
            });
        });
    }

    void clearCategory(MorphCategory category)
    {
        GuiModal.addModal(this.parent, () ->
        {
            GuiConfirmModal modal = new GuiConfirmModal(this.mc, IKey.lang("metamorph.gui.creative.context.clear_category_modal"), (value) ->
            {
                if (value)
                {
                    category.clear();
                }
            });

            modal.flex().relative(this.parent).xy(0.5F, 0.5F).wh(160, 180).anchor(0.5F, 0.5F);

            return modal;
        });
    }

    void copyToRecent(AbstractMorph morph)
    {
        if (this.section instanceof UserSection)
        {
            ((UserSection) this.section).recent.add(morph.copy());
        }
    }
}
