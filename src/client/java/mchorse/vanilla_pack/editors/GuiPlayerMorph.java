package mchorse.vanilla_pack.editors;

import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import mchorse.metamorph.util.MMIcons;
import mchorse.vanilla_pack.editors.panels.GuiUsernamePanel;
import net.minecraft.client.MinecraftClient;

/**
 * Player morph editor (roadmap P59.2) — {@link GuiUsernamePanel} on top of the
 * entity editor.
 *
 * <p>Port of Metamorph 1.4's {@code GuiPlayerMorph}. Legacy's player morph was
 * its own class with its own one-panel editor; a player disguise is an ordinary
 * {@link EntityMorph} now (named {@link EntityMorph#PLAYER_ID}), so this editor
 * <b>extends</b> {@link GuiEntityMorph} rather than {@code GuiAbstractMorph} and
 * adds the username panel to the panels every entity morph already gets. That is
 * what puts the body-part editor and the entity panel's texture picker — i.e.
 * the {@code userTexture} skin override — within reach of a player disguise,
 * which legacy had no way to express.</p>
 *
 * <p>Still registered <b>before</b> {@link GuiEntityMorph} in
 * {@code MetamorphMorphEditors}: the editor is chosen by the first
 * {@code canEdit} that answers, and the entity editor accepts every
 * {@link EntityMorph}, this one included.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/vanilla_pack/editors/GuiPlayerMorph.java
 */
public class GuiPlayerMorph extends GuiEntityMorph
{
    public GuiUsernamePanel username;

    public GuiPlayerMorph(MinecraftClient mc)
    {
        super(mc);

        this.username = new GuiUsernamePanel(mc, this);
        this.registerPanel(this.username, IKey.lang("metamorph.gui.panels.username"), MMIcons.USER);

        /* Legacy opened a player morph on its username field. */
        this.defaultPanel = this.username;
    }

    @Override
    public boolean canEdit(AbstractMorph morph)
    {
        return morph instanceof EntityMorph entity && entity.isPlayer();
    }
}
