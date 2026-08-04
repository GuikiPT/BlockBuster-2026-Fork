package mchorse.chameleon.metamorph.editor;

import mchorse.chameleon.Chameleon;
import mchorse.chameleon.lib.ChameleonModel;
import mchorse.chameleon.metamorph.ChameleonMorph;
import mchorse.mclib.client.gui.framework.GuiBase;
import mchorse.mclib.client.gui.framework.elements.GuiModelRenderer;
import mchorse.mclib.client.gui.utils.Icons;
import mchorse.mclib.client.gui.utils.Label;
import mchorse.mclib.client.gui.utils.LegacyKeyCodes;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.utils.files.entries.AbstractEntry;
import mchorse.mclib.utils.files.entries.FileEntry;
import mchorse.mclib.utils.files.entries.FolderEntry;
import mchorse.mclib.utils.resources.ResourceLocation;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.client.gui.editor.GuiAbstractMorph;
import mchorse.metamorph.client.gui.editor.GuiMorphPanel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Chameleon morph editor: three panels over a bone-pickable 3D preview.
 *
 * <ul>
 *   <li>{@link GuiChameleonMainPanel} — the <b>default</b> panel: skin picking,
 *       the per-bone pose editor (transform + fixate + glow/colour), global and
 *       GUI scale, and the morph-transition settings.</li>
 *   <li>{@link GuiCustomBodyPartEditor} — Metamorph's body-part editor, wired to
 *       cross-highlight the picked bone.</li>
 *   <li>{@link GuiActionsPanel} — which animation plays for each of the 21
 *       action slots, and how.</li>
 * </ul>
 *
 * <p>Ctrl+click picking in the preview routes to whichever panel is shown, if it
 * is an {@link IBonePicker}. {@code Shift+P} jumps to the main panel and opens
 * the skin picker.</p>
 *
 * <p>{@link #getPresets} turns every texture under the model's {@code skins/}
 * folder into a one-click preset.</p>
 *
 * Legacy source: chameleon/.../metamorph/editor/GuiChameleonMorph.java
 */
public class GuiChameleonMorph extends GuiAbstractMorph<ChameleonMorph>
{
    public GuiActionsPanel actionsPanel;
    public GuiCustomBodyPartEditor bodyPart;
    public GuiChameleonMainPanel mainPanel;
    public GuiChameleonModelRenderer chameleonModelRenderer;

    public GuiChameleonMorph(MinecraftClient mc)
    {
        super(mc);

        this.mainPanel = new GuiChameleonMainPanel(mc, this);
        this.bodyPart = new GuiCustomBodyPartEditor(mc, this);
        this.actionsPanel = new GuiActionsPanel(mc, this);
        this.defaultPanel = this.mainPanel;

        this.registerPanel(this.actionsPanel, IKey.lang("chameleon.gui.editor.actions.actions"), Icons.MORE);
        this.registerPanel(this.bodyPart, IKey.lang("metamorph.gui.body_parts.parts"), Icons.LIMB);
        this.registerPanel(this.mainPanel, IKey.lang("chameleon.gui.editor.main"), Icons.GEAR);

        this.keys().register(IKey.lang("chameleon.gui.editor.pick_skin"), LegacyKeyCodes.KEY_P, () ->
        {
            this.setPanel(this.mainPanel);

            this.mainPanel.skin.clickItself(GuiBase.getCurrent());
        }).held(LegacyKeyCodes.KEY_LSHIFT);
    }

    @Override
    protected GuiModelRenderer createMorphRenderer(MinecraftClient mc)
    {
        /* Called from the super constructor (virtual dispatch) — assigns the
         * field before this class's constructor body runs. */
        this.chameleonModelRenderer = new GuiChameleonModelRenderer(mc);
        this.chameleonModelRenderer.picker(this::pickLimb);

        return this.chameleonModelRenderer;
    }

    private void pickLimb(String limb)
    {
        if (this.view.delegate instanceof IBonePicker)
        {
            ((IBonePicker) this.view.delegate).pickBone(limb);
        }
    }

    @Override
    public boolean canEdit(AbstractMorph morph)
    {
        return morph instanceof ChameleonMorph;
    }

    @Override
    public void startEdit(ChameleonMorph morph)
    {
        super.startEdit(morph);

        ChameleonModel model = morph.getModel();

        morph.parts.reinitBodyParts();

        if (model == null)
        {
            this.bodyPart.setLimbs(Collections.emptyList());
        }
        else
        {
            this.bodyPart.setLimbs(model.getBoneNames());
        }
    }

    @Override
    public void setPanel(GuiMorphPanel panel)
    {
        this.chameleonModelRenderer.boneName = "";

        super.setPanel(panel);
    }

    @Override
    public List<Label<NbtCompound>> getPresets(ChameleonMorph morph)
    {
        List<Label<NbtCompound>> list = new ArrayList<Label<NbtCompound>>();
        String key = morph.getKey();

        this.addSkins(morph, list, "Skin", Chameleon.tree == null ? null : Chameleon.tree.getByPath(key + "/skins", null));

        return list;
    }

    /**
     * One preset per texture file under the model's skins folder, labelled by the
     * path <i>after</i> the {@code "/skins/"} segment.
     *
     * <p>The {@code isTop()} check keeps the recursion out of {@code "../"}
     * entries, which delegate their listing to the parent and would loop.</p>
     */
    public void addSkins(AbstractMorph morph, List<Label<NbtCompound>> list, String name, FolderEntry entry)
    {
        if (entry == null)
        {
            return;
        }

        for (AbstractEntry childEntry : entry.getEntries())
        {
            if (childEntry instanceof FileEntry)
            {
                ResourceLocation location = ((FileEntry) childEntry).resource;
                String label = location.getResourcePath();
                int index = label.indexOf("/skins/");

                if (index != -1)
                {
                    label = label.substring(index + 7);
                }

                this.addPreset(morph, list, name, label, location);
            }
            else if (childEntry instanceof FolderEntry)
            {
                FolderEntry childFolder = (FolderEntry) childEntry;

                if (!childFolder.isTop())
                {
                    this.addSkins(morph, list, name, childFolder);
                }
            }
        }
    }

    public void addPreset(AbstractMorph morph, List<Label<NbtCompound>> list, String name, String label, ResourceLocation skin)
    {
        try
        {
            NbtCompound tag = morph.toNBT();

            tag.putString(name, skin.toString());
            list.add(new Label<NbtCompound>(IKey.str(label), tag));
        }
        catch (Exception e)
        {}
    }
}
