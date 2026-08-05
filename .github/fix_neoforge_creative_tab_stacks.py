from pathlib import Path

root = Path.cwd()

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
new_stack = """        /*
         * Blockbuster registers its Fabric-style content while NeoForge's
         * vanilla registries are temporarily unfrozen. In that compatibility
         * path Block#asItem can remain AIR even though the matching BlockItem
         * is correctly present in the item registry. Resolve the item by the
         * block's registry id so creative-tab stacks are real, count-one stacks.
         */
        Item item = Registries.ITEM.get(Registries.BLOCK.getId(block));

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

blockbuster_tab = root / "src/main/java/mchorse/blockbuster/common/BlockbusterTab.java"
tab_text = blockbuster_tab.read_text(encoding="utf-8")
old_loop = """        for (ChromaColor color : ChromaColor.values())
        {
            entries.add(BlockGreen.colorStack(block, color));
        }
"""
new_loop = """        for (ChromaColor color : ChromaColor.values())
        {
            ItemStack stack = BlockGreen.colorStack(block, color);

            if (stack.isEmpty())
            {
                continue;
            }

            stack.setCount(1);
            entries.add(stack);
        }
"""

if old_loop in tab_text:
    tab_text = tab_text.replace(old_loop, new_loop, 1)
elif "stack.setCount(1);" not in tab_text:
    raise SystemExit("Expected BlockbusterTab chroma loop was not found")

blockbuster_tab.write_text(tab_text, encoding="utf-8")

entrypoint = root / "src/main/java/mchorse/blockbuster/neoforge/BlockbusterNeoForge.java"
entry_text = entrypoint.read_text(encoding="utf-8")

blockbuster_import = "import mchorse.blockbuster.Blockbuster;\n"
validation_imports = (
    "import mchorse.blockbuster.Blockbuster;\n"
    "import mchorse.blockbuster.common.block.BlockGreen;\n"
    "import mchorse.blockbuster.common.block.ChromaColor;\n"
)
if "import mchorse.blockbuster.common.block.BlockGreen;" not in entry_text:
    if blockbuster_import not in entry_text:
        raise SystemExit("Expected BlockbusterNeoForge Blockbuster import was not found")
    entry_text = entry_text.replace(blockbuster_import, validation_imports, 1)

living_import = "import net.minecraft.entity.LivingEntity;\n"
validation_mc_imports = (
    "import net.minecraft.block.Block;\n"
    "import net.minecraft.entity.LivingEntity;\n"
    "import net.minecraft.item.ItemStack;\n"
)
if "import net.minecraft.item.ItemStack;" not in entry_text:
    if living_import not in entry_text:
        raise SystemExit("Expected BlockbusterNeoForge LivingEntity import was not found")
    entry_text = entry_text.replace(living_import, validation_mc_imports, 1)

client_anchor = """        if (dist == Dist.CLIENT)
        {
            NeoForgeClientBootstrap.initialize();
        }
"""
client_with_validation = """        validateCreativeTabStacks();

        if (dist == Dist.CLIENT)
        {
            NeoForgeClientBootstrap.initialize();
        }
"""
if client_anchor in entry_text:
    entry_text = entry_text.replace(client_anchor, client_with_validation, 1)
elif "validateCreativeTabStacks();" not in entry_text:
    raise SystemExit("Expected BlockbusterNeoForge client initialization anchor was not found")

method_anchor = """    @SubscribeEvent
    public void onRegisterAttributes(EntityAttributeCreationEvent event)
"""
validation_method = """    private static void validateCreativeTabStacks()
    {
        for (Block block : new Block[] {Blockbuster.greenBlock, Blockbuster.dimGreenBlock})
        {
            if (block == null)
            {
                continue;
            }

            for (ChromaColor color : ChromaColor.values())
            {
                ItemStack stack = BlockGreen.colorStack(block, color);

                if (stack.isEmpty() || stack.getCount() != 1)
                {
                    throw new IllegalStateException(
                        "Invalid Blockbuster creative stack for " + color + ": " + stack
                    );
                }
            }
        }
    }

    @SubscribeEvent
    public void onRegisterAttributes(EntityAttributeCreationEvent event)
"""
if method_anchor in entry_text:
    entry_text = entry_text.replace(method_anchor, validation_method, 1)
elif "private static void validateCreativeTabStacks()" not in entry_text:
    raise SystemExit("Expected BlockbusterNeoForge attribute event anchor was not found")

entrypoint.write_text(entry_text, encoding="utf-8")

print("Applied NeoForge creative-tab stack compatibility fix and startup validation.")
