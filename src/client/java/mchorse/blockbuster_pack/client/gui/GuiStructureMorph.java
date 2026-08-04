package mchorse.blockbuster_pack.client.gui;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import mchorse.blockbuster.client.gui.dashboard.panels.model_editor.utils.GuiPoseTransformations;
import mchorse.blockbuster_pack.morphs.StructureMorph;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiToggleElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTrackpadElement;
import mchorse.mclib.client.gui.framework.elements.list.GuiListElement;
import mchorse.mclib.client.gui.framework.elements.list.GuiSearchListElement;
import mchorse.mclib.client.gui.utils.Icons;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.client.gui.editor.GuiAbstractMorph;
import mchorse.metamorph.client.gui.editor.GuiAnimation;
import mchorse.metamorph.client.gui.editor.GuiMorphPanel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;

/**
 * Structure morph editor (roadmap P162) — port of 2.7.2's {@code GuiStructureMorph}.
 *
 * <p>Exposes the world-lighting toggle, the rotation-anchor trackpads, the
 * transition {@link GuiAnimation}, and a searchable biome list (sourced from the
 * client world's dynamic biome registry on 1.20.4, sorted by localized name).
 * The structure itself is chosen from the creative picker's
 * {@code blockbuster_structures} category, not this panel.</p>
 *
 * <p>The pose-transform widget ({@link GuiPoseTransformations}) landed with S22
 * P226 — mounted at the panel bottom ({@code 256×70}, centred, 75&nbsp;px above
 * the panel bottom) and bound to the live {@code morph.pose} in
 * {@code fillData}, exactly as 1.12.2. This editor is registered by
 * {@code BlockbusterFactory.registerMorphEditors} (P157, parallel).</p>
 */
public class GuiStructureMorph extends GuiAbstractMorph<StructureMorph>
{
    public GuiStructureMorphPanel general;

    public GuiStructureMorph(MinecraftClient mc)
    {
        super(mc);

        this.defaultPanel = this.general = new GuiStructureMorphPanel(mc, this);
        this.registerPanel(this.general, IKey.lang("blockbuster.morph.structure"), Icons.GEAR);
    }

    @Override
    public boolean canEdit(AbstractMorph morph)
    {
        return morph instanceof StructureMorph;
    }

    public static class GuiStructureMorphPanel extends GuiMorphPanel<StructureMorph, GuiStructureMorph>
    {
        public GuiPoseTransformations pose;
        public GuiAnimation animation;
        public GuiToggleElement lighting;
        public GuiSearchBiomeList biomes;
        public GuiTrackpadElement anchorX;
        public GuiTrackpadElement anchorY;
        public GuiTrackpadElement anchorZ;

        public GuiStructureMorphPanel(MinecraftClient mc, GuiStructureMorph editor)
        {
            super(mc, editor);

            this.pose = new GuiPoseTransformations(mc);
            this.pose.flex().relative(this.area).set(0, 0, 256, 70).x(0.5F, -128).y(1, -75);

            this.animation = new GuiAnimation(mc, true);
            this.animation.flex().relative(this).x(1F, -130).w(130);

            this.lighting = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.structure_morph.lighting"), (b) -> this.morph.lighting = b.isToggled());
            this.lighting.flex().relative(this).x(1F, -10).y(1F, -10).w(110).anchor(1F, 1F);

            this.anchorX = new GuiTrackpadElement(mc, (v) -> this.morph.anchorX = v.floatValue());
            this.anchorY = new GuiTrackpadElement(mc, (v) -> this.morph.anchorY = v.floatValue());
            this.anchorZ = new GuiTrackpadElement(mc, (v) -> this.morph.anchorZ = v.floatValue());
            this.anchorX.flex().relative(this.anchorY).y(-5).w(1F).anchorY(1);
            this.anchorY.flex().relative(this.anchorZ).y(-5).w(1F).anchorY(1);
            this.anchorZ.flex().relative(this.lighting).y(-5).w(1F).anchorY(1);

            this.biomes = new GuiSearchBiomeList(mc, this::accept);
            this.biomes.list.sorting();
            this.biomes.flex().relative(this).x(0F).w(150).h(1F).anchorX(0F);
            this.biomes.list.background(0x80000000);

            this.add(this.pose, this.animation, this.lighting, this.anchorZ, this.anchorY, this.anchorX, this.biomes);
        }

        @Override
        public void fillData(StructureMorph morph)
        {
            super.fillData(morph);

            this.pose.set(morph.pose);
            this.animation.fill(morph.animation);
            this.lighting.toggled(morph.lighting);
            this.biomes.filter("", true);
            this.biomes.list.setCurrent(morph.biome);
            this.anchorX.setValue(morph.anchorX);
            this.anchorY.setValue(morph.anchorY);
            this.anchorZ.setValue(morph.anchorZ);
        }

        private void accept(List<Identifier> sel)
        {
            if (!sel.isEmpty())
            {
                this.morph.biome = sel.get(0);
            }
        }
    }

    public static class GuiSearchBiomeList extends GuiSearchListElement<Identifier>
    {
        public GuiSearchBiomeList(MinecraftClient mc, Consumer<List<Identifier>> callback)
        {
            super(mc, callback);
        }

        @Override
        protected GuiListElement<Identifier> createList(MinecraftClient mc, Consumer<List<Identifier>> callback)
        {
            return new GuiBiomeList(mc, callback);
        }
    }

    public static class GuiBiomeList extends GuiListElement<Identifier>
    {
        public GuiBiomeList(MinecraftClient mc, Consumer<List<Identifier>> callback)
        {
            super(mc, callback);

            Registry<Biome> registry = biomeRegistry(mc);

            if (registry != null)
            {
                for (Identifier id : registry.getIds())
                {
                    this.add(id);
                }
            }

            this.sort();
        }

        private static Registry<Biome> biomeRegistry(MinecraftClient mc)
        {
            /* `mc` itself is null headless (and briefly at boot) — the port's
             * readers-are-total rule, not a legacy behaviour change: 1.12.2 read
             * the static Biome REGISTRY, which needed no client at all. */
            return mc != null && mc.world != null ? mc.world.getRegistryManager().get(RegistryKeys.BIOME) : null;
        }

        @Override
        protected boolean sortElements()
        {
            this.list.sort(Comparator.comparing(this::elementToString));

            return true;
        }

        @Override
        protected String elementToString(Identifier element)
        {
            return Text.translatable(element.toTranslationKey("biome")).getString();
        }
    }
}
