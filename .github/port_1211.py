from pathlib import Path

ROOT = Path('.')


def replace(path: str, old: str, new: str) -> None:
    file = ROOT / path
    text = file.read_text()
    if old not in text:
        raise RuntimeError(f'Expected text not found in {path}: {old[:100]!r}')
    file.write_text(text.replace(old, new))


def replace_optional(path: str, old: str, new: str) -> None:
    file = ROOT / path
    text = file.read_text()
    if old in text:
        file.write_text(text.replace(old, new))


# NBT size tracker rename in 1.21.
for file in list((ROOT / 'src/main/java').rglob('*.java')) + list((ROOT / 'src/client/java').rglob('*.java')):
    text = file.read_text()
    if 'NbtTagSizeTracker' in text:
        file.write_text(text.replace('NbtTagSizeTracker', 'NbtSizeTracker'))

# Item tooltip API: World/TooltipContext -> Item.TooltipContext/TooltipType.
for path in [
    'src/main/java/mchorse/blockbuster/common/item/ItemPlayback.java',
    'src/main/java/mchorse/blockbuster/common/item/ItemRegister.java',
    'src/main/java/mchorse/blockbuster/common/item/ItemActorConfig.java',
    'src/main/java/mchorse/blockbuster/common/item/ItemBlockModel.java',
]:
    replace_optional(path, 'import net.minecraft.client.item.TooltipContext;\n', '')
    replace_optional(path, 'import net.minecraft.item.ItemStack;\n',
                     'import net.minecraft.item.ItemStack;\nimport net.minecraft.item.tooltip.TooltipType;\n')
    replace_optional(path,
                     'public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context)',
                     'public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type)')

replace('src/main/java/mchorse/blockbuster/common/item/ItemBlockModel.java',
        'this.block.appendTooltip(stack, world, tooltip, context);',
        'this.block.appendTooltip(stack, context, tooltip, type);')

# Block tooltip API.
for path in [
    'src/main/java/mchorse/blockbuster/common/block/BlockGreen.java',
    'src/main/java/mchorse/blockbuster/common/block/BlockModel.java',
    'src/main/java/mchorse/blockbuster/common/block/BlockDimGreen.java',
]:
    replace_optional(path, 'import net.minecraft.client.item.TooltipContext;\n', '')
    text = (ROOT / path).read_text()
    if 'import net.minecraft.item.Item;\n' not in text:
        replace(path, 'import net.minecraft.item.ItemStack;\n',
                'import net.minecraft.item.Item;\nimport net.minecraft.item.ItemStack;\n')
    text = (ROOT / path).read_text()
    if 'import net.minecraft.item.tooltip.TooltipType;\n' not in text:
        replace(path, 'import net.minecraft.item.ItemStack;\n',
                'import net.minecraft.item.ItemStack;\nimport net.minecraft.item.tooltip.TooltipType;\n')
    replace_optional(path,
                     'public void appendTooltip(ItemStack stack, BlockView world, List<Text> tooltip, TooltipContext options)',
                     'public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options)')

replace('src/main/java/mchorse/blockbuster/common/block/BlockDimGreen.java',
        'super.appendTooltip(stack, world, tooltip, options);',
        'super.appendTooltip(stack, context, tooltip, options);')

# Client tooltip rendering.
path = 'src/client/java/mchorse/mclib/client/gui/framework/elements/utils/GuiInventoryElement.java'
replace_optional(path, 'import net.minecraft.client.item.TooltipContext;\n', '')
text = (ROOT / path).read_text()
if 'import net.minecraft.item.Item;\n' not in text:
    replace(path, 'import net.minecraft.item.ItemStack;\n',
            'import net.minecraft.item.Item;\nimport net.minecraft.item.ItemStack;\n')
text = (ROOT / path).read_text()
if 'import net.minecraft.item.tooltip.TooltipType;\n' not in text:
    replace(path, 'import net.minecraft.item.ItemStack;\n',
            'import net.minecraft.item.ItemStack;\nimport net.minecraft.item.tooltip.TooltipType;\n')
replace(path,
        'TooltipContext flag = mc.options.advancedItemTooltips ? TooltipContext.ADVANCED : TooltipContext.BASIC;',
        'TooltipType flag = mc.options.advancedItemTooltips ? TooltipType.ADVANCED : TooltipType.BASIC;')
replace(path,
        'stack.getTooltip(player, flag)',
        'stack.getTooltip(Item.TooltipContext.create(mc.world), player, flag)')

# Potions are data components and potion constants are registry entries in 1.21.
path = 'src/main/java/mchorse/vanilla_pack/actions/Potions.java'
replace(path, 'import net.minecraft.item.ItemStack;\n',
        'import net.minecraft.component.type.PotionContentsComponent;\nimport net.minecraft.item.ItemStack;\n')
replace(path, 'import net.minecraft.potion.Potion;\n',
        'import net.minecraft.potion.Potion;\nimport net.minecraft.registry.entry.RegistryEntry;\n')
replace(path, 'import net.minecraft.potion.PotionUtil;\n', '')
replace(path, 'public static Potion pickPotion(Random random)',
        'public static RegistryEntry<Potion> pickPotion(Random random)')
replace(path, 'Potion effect = net.minecraft.potion.Potions.HARMING;',
        'RegistryEntry<Potion> effect = net.minecraft.potion.Potions.HARMING;')
replace(path, 'Potion effect = pickPotion(target.getRandom());',
        'RegistryEntry<Potion> effect = pickPotion(target.getRandom());')
replace(path, 'ItemStack stack = PotionUtil.setPotion(new ItemStack(Items.SPLASH_POTION), effect);',
        'ItemStack stack = PotionContentsComponent.createStack(Items.SPLASH_POTION, effect);')
replace_optional(path,
                 '{@code PotionUtils.addPotionToItemStack} → {@link PotionUtil#setPotion};',
                 '{@code PotionUtils.addPotionToItemStack} → {@link PotionContentsComponent#createStack};')

# Skull profile lookup was renamed in 1.21.
path = 'src/main/java/mchorse/metamorph/mixin/SkullBlockEntityProfileInvoker.java'
replace(path, '@Invoker("fetchProfile")', '@Invoker("fetchProfileByName")')
replace_optional(path, 'SkullBlockEntity.fetchProfile(String)', 'SkullBlockEntity.fetchProfileByName(String)')

# Compile mixins as Java 21 bytecode.
for path in [
    'src/main/resources/blockbuster.client.mixins.json',
    'src/main/resources/blockbuster.iris.mixins.json',
    'src/main/resources/blockbuster.mixins.json',
    'src/main/resources/metamorph.client.mixins.json',
    'src/main/resources/metamorph.mixins.json',
]:
    replace(path, '"compatibilityLevel": "JAVA_17"', '"compatibilityLevel": "JAVA_21"')
