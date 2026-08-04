package mchorse.blockbuster.client.gui.dashboard.panels.snowstorm.sections;

import mchorse.blockbuster.client.gui.dashboard.panels.snowstorm.GuiSnowstorm;
import mchorse.blockbuster.client.particles.BedrockScheme;
import mchorse.blockbuster.client.particles.components.appearance.BedrockComponentParticleMorph;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiToggleElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.client.MinecraftClient;

/**
 * Port of Blockbuster 2.7.2's {@code GuiSnowstormParticleMorphSection} (roadmap
 * P155) — the <b>dormant</b> particle-morph editor.
 *
 * <p>Legacy source: blockbuster-1.12/src/main/java/mchorse/blockbuster/client/gui/dashboard/panels/snowstorm/sections/GuiSnowstormParticleMorphSection.java</p>
 *
 * <p><b>Dormant by design:</b> in legacy this section is constructed but never
 * added to the editor column (constructor line 113 + commented line 124), so its
 * component-edit UI is unreachable. The port keeps it dormant: {@link GuiSnowstorm}
 * does <b>not</b> register it (see {@code SECTION_ORDER}).</p>
 *
 * <p><b>batch-4 integration:</b> the legacy morph picker is
 * {@code mchorse.metamorph.client.gui.creative.GuiNestedEdit} wired through
 * {@code ClientProxy.panels.addMorphs(...)} / the shared morph picker in
 * {@code GuiSnowstorm.appear()}. Neither the Metamorph creative GUI
 * ({@code GuiNestedEdit}) nor the dashboard morph-picker plumbing
 * ({@code ClientProxy.panels}) exists yet in the port, so the picker field and
 * its callbacks are omitted here and must be reinstated when the Metamorph GUI
 * stage lands. The enabled / renderTexture toggles and the in-section morph
 * preview (which only needs {@link AbstractMorph#renderOnScreen}) are ported
 * live so the dormant section is otherwise complete.</p>
 */
public class GuiSnowstormParticleMorphSection extends GuiSnowstormComponentSection<BedrockComponentParticleMorph>
{
    public GuiToggleElement enabled;
    public GuiToggleElement renderTexture;
    /* batch-4 integration: public GuiNestedEdit pickMorph; — Metamorph creative GUI not ported yet. */

    public GuiSnowstormParticleMorphSection(MinecraftClient mc, GuiSnowstorm parent)
    {
        super(mc, parent);

        this.enabled = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.snowstorm.particle_morph.enabled"), (b) ->
        {
            this.component.enabled = b.isToggled();

            this.parent.dirty();
        });

        this.renderTexture = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.snowstorm.particle_morph.render_texture"), (b) ->
        {
            this.component.renderTexture = b.isToggled();

            this.parent.dirty();
        });

        /* batch-4 integration: the GuiNestedEdit morph picker (marginTop 80,
         * callback -> ClientProxy.panels.addMorphs(this.parent, editing,
         * this.component.morph.get())) is reinstated with the Metamorph creative
         * GUI. Legacy added it between enabled and renderTexture:
         *   this.fields.add(this.enabled, this.pickMorph, this.renderTexture); */
        this.fields.add(this.enabled, this.renderTexture);
    }

    public void setMorph(AbstractMorph morph)
    {
        this.component.morph.set(morph);

        /* batch-4 integration: this.pickMorph.setMorph(morph); */
    }

    @Override
    public String getTitle()
    {
        return "blockbuster.gui.snowstorm.particle_morph.title";
    }

    @Override
    public void draw(GuiContext context)
    {
        if (!this.component.morph.isEmpty())
        {
            AbstractMorph morph = this.component.morph.get();
            int x = this.area.mx();
            int y = this.enabled.area.y(1F) + 50;

            GuiDraw.scissor(this.area.x, this.area.y, this.area.w, this.area.h, context);
            morph.renderOnScreen(this.mc.player, x, y, this.area.h / 3.5F, 1.0F);
            GuiDraw.unscissor(context);
        }

        super.draw(context);
    }

    @Override
    protected BedrockComponentParticleMorph getComponent(BedrockScheme scheme)
    {
        return this.scheme.getOrCreate(BedrockComponentParticleMorph.class);
    }

    @Override
    protected void fillData()
    {
        this.enabled.toggled(this.component.enabled);
        this.renderTexture.toggled(this.component.renderTexture);
        /* batch-4 integration: this.pickMorph.setMorph(this.component.morph.get()); */
    }
}
