package mchorse.blockbuster.client.gui.dashboard.panels.snowstorm.sections;

import mchorse.blockbuster.client.gui.dashboard.panels.snowstorm.GuiSnowstorm;
import mchorse.blockbuster.client.particles.BedrockScheme;
import mchorse.blockbuster.client.particles.components.BedrockComponentBase;
import net.minecraft.client.MinecraftClient;

/**
 * Port of Blockbuster 2.7.2's abstract {@code GuiSnowstormComponentSection}
 * (roadmap P155). A section bound to a single {@link BedrockComponentBase}
 * subtype {@code T}; {@link #setScheme(BedrockScheme)} resolves the component
 * via {@link #getComponent(BedrockScheme)} and repopulates the widgets through
 * {@link #fillData()}.
 *
 * <p>Legacy source: blockbuster-1.12/src/main/java/mchorse/blockbuster/client/gui/dashboard/panels/snowstorm/sections/GuiSnowstormComponentSection.java</p>
 */
public abstract class GuiSnowstormComponentSection <T extends BedrockComponentBase> extends GuiSnowstormSection
{
    protected T component;

    public GuiSnowstormComponentSection(MinecraftClient mc, GuiSnowstorm parent)
    {
        super(mc, parent);
    }

    @Override
    public void setScheme(BedrockScheme scheme)
    {
        super.setScheme(scheme);

        this.component = this.getComponent(scheme);
        this.fillData();
    }

    protected abstract T getComponent(BedrockScheme scheme);

    protected void fillData()
    {}
}
