package mchorse.blockbuster.client.gui.dashboard.panels.snowstorm.sections;

import mchorse.blockbuster.client.gui.dashboard.panels.snowstorm.GuiSnowstorm;
import mchorse.blockbuster.client.particles.BedrockScheme;
import mchorse.blockbuster.client.particles.components.appearance.BedrockComponentCollisionAppearance;
import mchorse.blockbuster.client.particles.components.appearance.BedrockComponentCollisionTinting;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiToggleElement;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.math.molang.MolangParser;
import mchorse.mclib.math.molang.expressions.MolangExpression;
import net.minecraft.client.MinecraftClient;

/**
 * Port of Blockbuster 2.7.2's {@code GuiSnowstormCollisionLightingSection}
 * (roadmap P155). A {@link GuiSnowstormLightingSection} subclass bound to
 * {@code BedrockComponentCollisionTinting}.
 *
 * <p>Legacy source: blockbuster-1.12/src/main/java/mchorse/blockbuster/client/gui/dashboard/panels/snowstorm/sections/GuiSnowstormCollisionLightingSection.java</p>
 *
 * <p><b>Parity quirks:</b> {@code enabled} is saved as ONE/ZERO; the "lighting"
 * checkbox is <b>inverted</b> onto {@code appearanceComponent.lit} (checked = lit
 * false); {@link #setScheme} deliberately skips {@code super.setScheme} to avoid
 * binding the wrong component. All preserved.</p>
 */
public class GuiSnowstormCollisionLightingSection extends GuiSnowstormLightingSection
{
    public GuiToggleElement enabled;

    private BedrockComponentCollisionAppearance appearanceComponent;

    public GuiSnowstormCollisionLightingSection(MinecraftClient mc, GuiSnowstorm parent)
    {
        super(mc, parent);

        this.enabled = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.snowstorm.collision.enabled"), (b) -> this.parent.dirty());

        this.lighting.callback = (b) ->
        {
            this.appearanceComponent.lit = !b.isToggled();
            this.parent.dirty();
        };

        this.fields.addBefore(this.lighting, this.enabled);
    }

    private BedrockComponentCollisionTinting getComponent()
    {
        return (BedrockComponentCollisionTinting) this.component;
    }

    @Override
    public String getTitle()
    {
        return "blockbuster.gui.snowstorm.collision.lighting.title";
    }

    @Override
    public void beforeSave(BedrockScheme scheme)
    {
        this.getComponent().enabled = this.enabled.isToggled() ? MolangParser.ONE : MolangParser.ZERO;
    }

    @Override
    public void setScheme(BedrockScheme scheme)
    {
        this.scheme = scheme; //cant call super as it would set the wrong component

        this.component = scheme.getOrCreate(BedrockComponentCollisionTinting.class);
        this.appearanceComponent = scheme.getOrCreate(BedrockComponentCollisionAppearance.class);
        this.lighting.toggled(!this.appearanceComponent.lit);
        this.enabled.toggled(MolangExpression.isOne(this.getComponent().enabled));

        this.setTintsCache();
        this.fillData();
    }
}
