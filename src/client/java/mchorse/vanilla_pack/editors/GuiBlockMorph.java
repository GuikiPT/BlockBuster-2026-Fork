package mchorse.vanilla_pack.editors;

import mchorse.mclib.client.gui.utils.Icons;
import mchorse.mclib.client.gui.utils.Label;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.client.gui.editor.GuiAbstractMorph;
import mchorse.vanilla_pack.editors.panels.GuiItemStackPanel;
import mchorse.vanilla_pack.morphs.BlockMorph;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;

import java.util.ArrayList;
import java.util.List;

/**
 * Block morph editor (roadmap P59.2) — the stack slot + lighting panel, plus
 * eight one-click block presets.
 *
 * <p>Port of Metamorph 1.4's {@code GuiBlockMorph}. The preset payloads are
 * legacy's <b>verbatim</b>, pre-flattening ids and all: a preset is applied by
 * feeding its NBT back through {@link BlockMorph#fromNBT}, which routes
 * {@code Block} through the P71 id-translation shim. So {@code minecraft:grass}
 * resolves to {@code grass_block}, {@code minecraft:log} to {@code oak_log} and
 * {@code minecraft:deadbush} to {@code dead_bush} without the preset strings
 * having to be rewritten — and the shim, not this list, stays the one place
 * that knows the flattening.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/vanilla_pack/editors/GuiBlockMorph.java
 */
public class GuiBlockMorph extends GuiAbstractMorph<BlockMorph>
{
    public GuiItemStackPanel block;

    public GuiBlockMorph(MinecraftClient mc)
    {
        super(mc);

        this.defaultPanel = this.block = new GuiItemStackPanel(mc, this);
        this.registerPanel(this.block, IKey.lang("metamorph.gui.panels.block"), Icons.BLOCK);
    }

    @Override
    public boolean canEdit(AbstractMorph morph)
    {
        return morph instanceof BlockMorph;
    }

    @Override
    public List<Label<NbtCompound>> getPresets(BlockMorph morph)
    {
        List<Label<NbtCompound>> presets = new ArrayList<Label<NbtCompound>>();

        this.addPreset(morph, presets, "Stone", "{Block:\"minecraft:stone\"}");
        this.addPreset(morph, presets, "Cobblestone", "{Block:\"minecraft:cobblestone\"}");
        this.addPreset(morph, presets, "Grass", "{Block:\"minecraft:grass\"}");
        this.addPreset(morph, presets, "Dirt", "{Block:\"minecraft:dirt\"}");
        this.addPreset(morph, presets, "Log", "{Block:\"minecraft:log\"}");
        this.addPreset(morph, presets, "Diamond block", "{Block:\"minecraft:diamond_block\"}");
        this.addPreset(morph, presets, "Sponge", "{Block:\"minecraft:sponge\"}");
        this.addPreset(morph, presets, "Deadbush", "{Block:\"minecraft:deadbush\"}");

        return presets;
    }
}
