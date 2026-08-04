package mchorse.vanilla_pack.editors.panels;

import mchorse.mclib.client.gui.framework.elements.buttons.GuiToggleElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTextElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.metamorph.api.morphs.EntityMorph;
import mchorse.metamorph.client.gui.editor.GuiMorphPanel;
import mchorse.vanilla_pack.editors.GuiPlayerMorph;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.language.I18n;

/**
 * Username morph panel (roadmap P59.2) — edits the username of a player morph.
 *
 * <p>Port of Metamorph 1.4's {@code GuiUsernamePanel}, including its debounce:
 * every keystroke re-arms a 15-frame counter, and the profile lookup
 * ({@code resetEntity} + {@code setProfile}) only fires on the frame the
 * counter hits zero, with an "Updating..." indicator drawn above the field
 * while it counts down. Typing "Notch" therefore costs one lookup, not five.
 * The counter lives in {@code draw} exactly as legacy had it — the panel has no
 * tick of its own.</p>
 *
 * <p>Legacy's counter is decremented <b>after</b> the zero check, so it lands on
 * −1 and stops; an empty field re-arms nothing ({@code editUsername} returns
 * early) and an empty field at zero performs no lookup, which is what keeps a
 * cleared field from resolving the empty profile.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/vanilla_pack/editors/panels/GuiUsernamePanel.java
 */
public class GuiUsernamePanel extends GuiMorphPanel<EntityMorph, GuiPlayerMorph>
{
    public GuiTextElement username;

    /**
     * Arm-model override. Only has a visible effect when the profile is not
     * deciding the skin — see {@code EntityMorph.skinTypeOverride()}.
     */
    public GuiToggleElement slim;

    /** Frames left before the profile lookup fires; −1 = idle. */
    public int counter = -1;

    public GuiUsernamePanel(MinecraftClient mc, GuiPlayerMorph editor)
    {
        super(mc, editor);

        this.username = new GuiTextElement(mc, 120, (str) -> this.editUsername(str));
        this.username.flex().relative(this).set(10, 10, 0, 20).w(1, -20).y(1, -30);

        this.slim = new GuiToggleElement(mc, IKey.lang("metamorph.gui.panels.slim"), false, (b) -> this.editSlim(b.isToggled()));
        this.slim.flex().relative(this.username).set(0, -30, 0, 20).w(1F);

        this.add(this.username, this.slim);
    }

    /**
     * The inner entity is built with the arm model baked in, so the toggle has
     * to drop it — same reason the username field calls {@code resetEntity}.
     */
    private void editSlim(boolean value)
    {
        this.morph.slim = value;
        this.morph.resetEntity();
    }

    private void editUsername(String str)
    {
        if (str.isEmpty())
        {
            return;
        }

        this.counter = 15;
    }

    @Override
    public void fillData(EntityMorph morph)
    {
        super.fillData(morph);

        /* A morph whose profile never resolved has no name to show (legacy's
         * profile was never null here — the port allows it and falls back to
         * EntityMorph.DEFAULT_PLAYER_PROFILE). */
        this.username.setText(morph.profile == null ? "" : morph.profile.getName());
        this.slim.toggled(morph.slim);
    }

    /**
     * The debounce half of legacy's {@code draw}, split out so it can be driven
     * without a font: advances the counter one frame, firing the profile lookup
     * on the frame it reaches zero, and answers whether the "Updating..."
     * indicator belongs on screen this frame. Legacy decremented after drawing
     * the indicator; the indicator's text does not depend on the counter, so
     * doing it here is the same frame-for-frame.
     */
    public boolean updateCounter()
    {
        if (this.counter < 0)
        {
            return false;
        }

        if (this.counter == 0 && !this.username.field.getText().isEmpty())
        {
            EntityMorph morph = this.morph;

            morph.resetEntity();
            morph.setProfile(this.username.field.getText());
        }

        this.counter--;

        return true;
    }

    @Override
    public void draw(GuiContext context)
    {
        if (this.updateCounter())
        {
            String updating = I18n.translate("metamorph.gui.panels.updating");
            int w = this.font.getWidth(updating);

            GuiDraw.drawStringWithShadow(this.font, updating, this.username.area.ex() - w, this.username.area.y - 12, 0xaaaaaa);
        }

        super.draw(context);

        if (this.username.isVisible())
        {
            GuiDraw.drawStringWithShadow(this.font, I18n.translate("metamorph.gui.panels.username"), this.username.area.x, this.username.area.y - 12, 0xffffff);
        }
    }
}
