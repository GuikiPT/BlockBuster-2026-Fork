package mchorse.blockbuster_pack.client.gui;

import mchorse.blockbuster.ClientProxy;
import mchorse.blockbuster.client.particles.BedrockScheme;
import mchorse.blockbuster.utils.mclib.BBIcons;
import mchorse.blockbuster_pack.morphs.SnowstormClient;
import mchorse.blockbuster_pack.morphs.SnowstormMorph;
import mchorse.mclib.client.gui.framework.elements.input.GuiTextElement;
import mchorse.mclib.client.gui.framework.elements.list.GuiStringListElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.Label;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.client.gui.editor.GuiAbstractMorph;
import mchorse.metamorph.client.gui.editor.GuiMorphPanel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.nbt.NbtCompound;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Snowstorm morph editor (roadmap P164).
 *
 * <p>A presets list (one entry per loaded Bedrock scheme, each setting the
 * {@code Scheme} NBT tag) plus a single variables panel listing the scheme's
 * MoLang variables with a 1000-char expression field.</p>
 *
 * <p>Quirk preserved: {@link GuiSnowstormVariablesMorphPanel#fillData} prunes
 * stored variables that the current scheme no longer declares — mutating the
 * morph during GUI fill.</p>
 *
 * Legacy source: blockbuster-1.12/src/main/java/mchorse/blockbuster_pack/client/gui/GuiSnowstormMorph.java
 */
public class GuiSnowstormMorph extends GuiAbstractMorph<SnowstormMorph>
{
    public GuiSnowstormMorph(MinecraftClient mc)
    {
        super(mc);

        this.defaultPanel = new GuiSnowstormVariablesMorphPanel(mc, this);
        this.registerPanel(this.defaultPanel, IKey.lang("blockbuster.gui.snowstorm.variables"), BBIcons.PARTICLE);
    }

    @Override
    public boolean canEdit(AbstractMorph morph)
    {
        return morph instanceof SnowstormMorph;
    }

    @Override
    public List<Label<NbtCompound>> getPresets(SnowstormMorph morph)
    {
        List<Label<NbtCompound>> labels = new ArrayList<Label<NbtCompound>>();

        if (ClientProxy.particles == null)
        {
            return labels;
        }

        for (String preset : ClientProxy.particles.presets.keySet())
        {
            NbtCompound tag = new NbtCompound();

            tag.putString("Scheme", preset);
            this.addPreset(morph, labels, preset, tag);
        }

        return labels;
    }

    public static class GuiSnowstormVariablesMorphPanel extends GuiMorphPanel<SnowstormMorph, GuiSnowstormMorph>
    {
        public GuiStringListElement variables;
        public GuiTextElement expression;

        private String variable;

        public GuiSnowstormVariablesMorphPanel(MinecraftClient mc, GuiSnowstormMorph editor)
        {
            super(mc, editor);

            this.variables = new GuiStringListElement(mc, (list) -> this.pickVariable(list.get(0)));
            this.variables.background();
            this.expression = new GuiTextElement(mc, 1000, this::replaceVariable);

            this.variables.flex().relative(this).xy(10, 22).w(110).hTo(this.expression.area, -17);
            this.expression.flex().relative(this).x(10).y(1F, -30).w(1F, -20).h(20);

            this.add(this.expression, this.variables);
        }

        @Override
        public void fillData(SnowstormMorph morph)
        {
            super.fillData(morph);

            Set<String> keys = new HashSet<String>();
            BedrockScheme scheme = SnowstormClient.getEmitter(this.morph).scheme;

            for (String key : this.morph.variables.keySet())
            {
                if (scheme != null && !scheme.parser.variables.containsKey(key))
                {
                    keys.add(key);
                }
            }

            for (String key : keys)
            {
                this.morph.variables.remove(key);
            }

            this.variables.clear();

            if (scheme != null)
            {
                this.variables.add(scheme.parser.variables.keySet());
                this.variables.sort();
            }

            String first = this.variables.getList().isEmpty() ? "" : this.variables.getList().get(0);

            this.pickVariable(first);
            this.expression.setEnabled(!first.isEmpty());
            this.variables.setCurrent(first);
        }

        private void pickVariable(String variable)
        {
            this.variable = variable;

            String expression = this.morph.variables.get(variable);

            this.expression.setEnabled(true);
            this.expression.setText(expression == null ? "" : expression);
        }

        private void replaceVariable(String expression)
        {
            if (this.variable.isEmpty())
            {
                return;
            }

            this.morph.replaceVariable(this.variable, expression);
        }

        @Override
        public void draw(GuiContext context)
        {
            super.draw(context);

            GuiDraw.drawStringWithShadow(this.font, I18n.translate("blockbuster.gui.snowstorm.variables"), this.variables.area.x, this.variables.area.y - 12, 0xffffff);
            GuiDraw.drawStringWithShadow(this.font, I18n.translate("blockbuster.gui.snowstorm.expression"), this.expression.area.x, this.expression.area.y - 12, 0xffffff);
        }
    }
}
