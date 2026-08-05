from pathlib import Path

root = Path.cwd()

# NeoForge production registries do not always populate Block#asItem for content
# registered through the Fabric compatibility path. Resolve chroma BlockItems by
# registry id so their component-bearing stacks are real items.
block_green = root / "src/main/java/mchorse/blockbuster/common/block/BlockGreen.java"
block_green_text = block_green.read_text(encoding="utf-8")

item_import = "import net.minecraft.item.Item;\n"
extra_imports = (
    "import net.minecraft.item.Item;\n"
    "import net.minecraft.item.Items;\n"
    "import net.minecraft.registry.Registries;\n"
)

if "import net.minecraft.registry.Registries;" not in block_green_text:
    if item_import not in block_green_text:
        raise SystemExit("Expected BlockGreen Item import was not found")
    block_green_text = block_green_text.replace(item_import, extra_imports, 1)

old_stack = """        ItemStack stack = new ItemStack(block);

        if (color != ChromaColor.GREEN)
"""
new_stack = """        Item item = Registries.ITEM.get(Registries.BLOCK.getId(block));

        if (item == Items.AIR)
        {
            item = block.asItem();
        }

        if (item == Items.AIR)
        {
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(item, 1);

        if (color != ChromaColor.GREEN)
"""

if old_stack in block_green_text:
    block_green_text = block_green_text.replace(old_stack, new_stack, 1)
elif "Item item = Registries.ITEM.get(Registries.BLOCK.getId(block));" not in block_green_text:
    raise SystemExit("Expected BlockGreen colorStack construction was not found")

block_green.write_text(block_green_text, encoding="utf-8")

# Build the complete creative tab as one validated list. This is deliberately
# shared by the real inventory callback and the startup validator, preventing a
# narrow test from missing a later invalid entry (e.g. the director block).
blockbuster_tab = root / "src/main/java/mchorse/blockbuster/common/BlockbusterTab.java"
tab_text = blockbuster_tab.read_text(encoding="utf-8")

if "import java.util.ArrayList;" not in tab_text:
    tab_text = tab_text.replace(
        "package mchorse.blockbuster.common;\n\n",
        "package mchorse.blockbuster.common;\n\nimport java.util.ArrayList;\nimport java.util.List;\n\n",
        1,
    )

if "import net.minecraft.item.Item;" not in tab_text:
    tab_text = tab_text.replace(
        "import net.minecraft.item.ItemGroup;\n",
        "import net.minecraft.item.Item;\nimport net.minecraft.item.ItemGroup;\n",
        1,
    )

icon_start = tab_text.index("    private static ItemStack icon()\n")
collect_comment = tab_text.index("    /**\n     * Legacy {@code displayAllRelevantItems}", icon_start)
new_icon = """    private static ItemStack icon()
    {
        if (Blockbuster.DIRECTOR_BLOCK != null)
        {
            return stackForBlock(Blockbuster.DIRECTOR_BLOCK, "director tab icon");
        }

        return stackForItem(BlockbusterItems.PLAYBACK, "playback tab icon");
    }

"""
tab_text = tab_text[:icon_start] + new_icon + tab_text[collect_comment:]

collect_start = tab_text.index("    public static void collect(ItemGroup.DisplayContext context, ItemGroup.Entries entries)\n")
class_end = tab_text.rfind("}\n")
if class_end < collect_start:
    raise SystemExit("Could not locate BlockbusterTab class end")

replacement = """    public static void collect(ItemGroup.DisplayContext context, ItemGroup.Entries entries)
    {
        for (ItemStack stack : buildEntries())
        {
            entries.add(stack);
        }
    }

    /**
     * Builds the exact stack list used by the real creative inventory. Every
     * entry is resolved through the item registry and normalized to count one,
     * matching NeoForge's creative-tab contract.
     */
    private static List<ItemStack> buildEntries()
    {
        List<ItemStack> stacks = new ArrayList<>(26);

        addChromaColors(stacks, Blockbuster.greenBlock, "green chroma");
        stacks.add(stackForItem(Blockbuster.modelBlockItems[0], "model block"));

        addChromaColors(stacks, Blockbuster.dimGreenBlock, "dim green chroma");
        stacks.add(stackForBlock(Blockbuster.DIRECTOR_BLOCK, "director block"));

        stacks.add(stackForItem(BlockbusterItems.REGISTER, "register item"));
        stacks.add(stackForItem(BlockbusterItems.PLAYBACK, "playback item"));
        stacks.add(stackForItem(BlockbusterItems.ACTOR_CONFIG, "actor config item"));
        stacks.add(stackForItem(BlockbusterItems.GUN, "gun item"));
        stacks.add(stackForItem(Blockbuster.ACTOR_SPAWN_EGG, "actor spawn egg"));

        if (utilityBlocksEnabled())
        {
            stacks.add(stackForItem(Items.COMMAND_BLOCK, "command block"));
            stacks.add(stackForItem(Items.STRUCTURE_BLOCK, "structure block"));
            stacks.add(stackForItem(Items.BARRIER, "barrier"));
        }

        return stacks;
    }

    /** Called by the NeoForge entrypoint after registration has completed. */
    public static void validateEntries()
    {
        List<ItemStack> stacks = buildEntries();
        int expected = utilityBlocksEnabled() ? 26 : 23;

        if (stacks.size() != expected)
        {
            throw new IllegalStateException(
                "Blockbuster creative tab built " + stacks.size() +
                " stacks, expected " + expected
            );
        }

        for (int index = 0; index < stacks.size(); index++)
        {
            ItemStack stack = stacks.get(index);

            if (stack.isEmpty() || stack.getCount() != 1)
            {
                throw new IllegalStateException(
                    "Invalid Blockbuster creative stack at index " + index + ": " + stack
                );
            }
        }

        ItemStack icon = icon();

        if (icon.isEmpty() || icon.getCount() != 1)
        {
            throw new IllegalStateException("Invalid Blockbuster creative tab icon: " + icon);
        }
    }

    private static boolean utilityBlocksEnabled()
    {
        return Blockbuster.addUtilityBlocks != null && Blockbuster.addUtilityBlocks.get();
    }

    private static void addChromaColors(List<ItemStack> stacks, Block block, String label)
    {
        if (block == null)
        {
            throw new IllegalStateException("Missing Blockbuster block for " + label);
        }

        for (ChromaColor color : ChromaColor.values())
        {
            stacks.add(normalize(BlockGreen.colorStack(block, color), label + " / " + color));
        }
    }

    private static ItemStack stackForBlock(Block block, String label)
    {
        if (block == null)
        {
            throw new IllegalStateException("Missing Blockbuster block: " + label);
        }

        Identifier id = Registries.BLOCK.getId(block);
        Item item = Registries.ITEM.get(id);

        if (item == Items.AIR)
        {
            item = block.asItem();
        }

        return stackForItem(item, label + " [" + id + "]");
    }

    private static ItemStack stackForItem(Item item, String label)
    {
        if (item == null || item == Items.AIR)
        {
            throw new IllegalStateException("Missing Blockbuster creative item: " + label);
        }

        return normalize(new ItemStack(item, 1), label);
    }

    private static ItemStack normalize(ItemStack stack, String label)
    {
        if (stack == null || stack.isEmpty())
        {
            throw new IllegalStateException("Empty Blockbuster creative stack: " + label);
        }

        ItemStack copy = stack.copy();
        copy.setCount(1);

        if (copy.isEmpty() || copy.getCount() != 1)
        {
            throw new IllegalStateException("Could not normalize Blockbuster creative stack: " + label);
        }

        return copy;
    }
"""

tab_text = tab_text[:collect_start] + replacement + "}\n"
blockbuster_tab.write_text(tab_text, encoding="utf-8")

# Validate the exact list after Blockbuster's compatibility registration and
# before the client bootstrap continues.
entrypoint = root / "src/main/java/mchorse/blockbuster/neoforge/BlockbusterNeoForge.java"
entry_text = entrypoint.read_text(encoding="utf-8")

if "import mchorse.blockbuster.common.BlockbusterTab;" not in entry_text:
    entry_text = entry_text.replace(
        "import mchorse.blockbuster.Blockbuster;\n",
        "import mchorse.blockbuster.Blockbuster;\nimport mchorse.blockbuster.common.BlockbusterTab;\n",
        1,
    )

client_anchor = """        if (dist == Dist.CLIENT)
        {
            NeoForgeClientBootstrap.initialize();
        }
"""
client_with_validation = """        BlockbusterTab.validateEntries();

        if (dist == Dist.CLIENT)
        {
            NeoForgeClientBootstrap.initialize();
        }
"""
if client_anchor in entry_text:
    entry_text = entry_text.replace(client_anchor, client_with_validation, 1)
elif "BlockbusterTab.validateEntries();" not in entry_text:
    raise SystemExit("Expected BlockbusterNeoForge client initialization anchor was not found")

entrypoint.write_text(entry_text, encoding="utf-8")

# Uppercase resource paths are invalid in modern Minecraft resource packs and
# produced a persistent Blockbuster error during every reload.
readme = root / "src/main/resources/assets/blockbuster/models/user/README.txt"
if readme.exists():
    readme.unlink()

print("Applied complete NeoForge creative-tab validation and resource cleanup.")
