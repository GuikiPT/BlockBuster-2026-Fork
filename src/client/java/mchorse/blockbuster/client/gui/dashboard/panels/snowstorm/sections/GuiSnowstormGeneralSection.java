package mchorse.blockbuster.client.gui.dashboard.panels.snowstorm.sections;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.blockbuster.client.gui.dashboard.panels.snowstorm.GuiSectionManager;
import mchorse.blockbuster.client.gui.dashboard.panels.snowstorm.GuiSnowstorm;
import mchorse.blockbuster.client.particles.BedrockMaterial;
import mchorse.blockbuster.client.particles.BedrockScheme;
import mchorse.blockbuster.client.particles.components.appearance.BedrockComponentAppearanceBillboard;
import mchorse.blockbuster.client.textures.GifTexture;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiCirculateElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTextElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTexturePicker;
import mchorse.mclib.client.gui.utils.Elements;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.utils.resources.ResourceLocation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;

/**
 * Port of Blockbuster 2.7.2's {@code GuiSnowstormGeneralSection} (roadmap P155).
 *
 * <p>Legacy source: blockbuster-1.12/src/main/java/mchorse/blockbuster/client/gui/dashboard/panels/snowstorm/sections/GuiSnowstormGeneralSection.java</p>
 */
public class GuiSnowstormGeneralSection extends GuiSnowstormSection
{
    public GuiTextElement identifier;
    public GuiButtonElement pick;
    public GuiCirculateElement material;
    public GuiCirculateElement play;
    public GuiTexturePicker texture;

    public GuiSnowstormGeneralSection(MinecraftClient mc, GuiSnowstorm parent)
    {
        super(mc, parent);

        this.identifier = new GuiTextElement(mc, 100, (str) ->
        {
            this.scheme.identifier = str;
            this.parent.dirty();
        });
        this.identifier.tooltip(IKey.lang("blockbuster.gui.snowstorm.general.identifier"));

        this.pick = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.snowstorm.general.pick"), (b) ->
        {
            GuiElement container = this.getParentContainer();

            this.texture.fill(this.scheme.texture);
            this.texture.flex().relative(container).wh(1F, 1F);
            this.texture.resize();
            container.add(this.texture);
        });

        this.material = new GuiCirculateElement(mc, (b) ->
        {
            this.scheme.material = BedrockMaterial.values()[this.material.getValue()];
            this.parent.dirty();
        });
        this.material.addLabel(IKey.lang("blockbuster.gui.snowstorm.general.particles_opaque"));
        this.material.addLabel(IKey.lang("blockbuster.gui.snowstorm.general.particles_alpha"));
        this.material.addLabel(IKey.lang("blockbuster.gui.snowstorm.general.particles_blend"));
        this.material.addLabel(IKey.lang("blockbuster.gui.snowstorm.general.particles_additive"));

        this.texture = new GuiTexturePicker(mc, (rl) ->
        {
            if (rl == null)
            {
                rl = BedrockScheme.DEFAULT_TEXTURE;
            }

            this.setTextureSize(rl);
            this.scheme.texture = rl;
            this.parent.dirty();
        });

        this.play = new GuiCirculateElement(mc, (b) ->
        {
            this.parent.renderer.playing = this.play.getValue() == 0;
        });
        this.play.addLabel(IKey.lang("blockbuster.gui.snowstorm.general.play_playing"));
        this.play.addLabel(IKey.lang("blockbuster.gui.snowstorm.general.play_paused"));

        this.fields.add(this.identifier, Elements.row(mc, 5, 0, 20, this.pick, this.material), this.play);
    }

    @Override
    protected void collapseState()
    {
        GuiSectionManager.setDefaultState(this.getClass().getSimpleName(), false);

        super.collapseState();
    }

    /**
     * Bind the picked texture and stamp its live GL dimensions onto the
     * billboard component. Legacy used {@code mc.renderEngine.bindTexture(rl)};
     * on 1.20.4 the equivalent is pulling the {@link AbstractTexture} out of the
     * texture manager and binding its GL id, then querying
     * {@code GL_TEXTURE_WIDTH/HEIGHT} (same idiom as {@code GuiTextureManagerPanel}).
     *
     * <p><b>P252.</b> The identifier goes through {@link GifTexture#resolveFrame}
     * — the same seam the render path binds through — rather than straight to
     * {@code toIdentifier()}. Two reasons, and both are silent when wrong:
     * a pre-flattening {@code minecraft:textures/blocks/…} path would otherwise
     * measure the 16&times;16 missing-texture placeholder and stamp <i>that</i>
     * into the user's scheme, permanently corrupting its UV divisors; and for an
     * animated GIF this measures the frame rather than the proxy.</p>
     */
    private void setTextureSize(ResourceLocation rl)
    {
        BedrockComponentAppearanceBillboard component = this.scheme.get(BedrockComponentAppearanceBillboard.class);

        if (component == null)
        {
            return;
        }

        Identifier id = GifTexture.resolveFrame(rl.toIdentifier(), 0, 0F);
        AbstractTexture texture = this.mc.getTextureManager().getTexture(id);

        if (texture == null)
        {
            /* Total: keep whatever the component already carries rather than
             * stamping a zero the billboard's UV math would divide by. */
            return;
        }

        RenderSystem.setShaderTexture(0, id);
        RenderSystem.bindTexture(texture.getGlId());

        component.textureWidth = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        component.textureHeight = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
    }

    @Override
    public String getTitle()
    {
        return "blockbuster.gui.snowstorm.general.title";
    }

    @Override
    public void setScheme(BedrockScheme scheme)
    {
        super.setScheme(scheme);

        this.identifier.setText(scheme.identifier);
        this.material.setValue(scheme.material.ordinal());
        this.play.setValue(0);
    }
}
