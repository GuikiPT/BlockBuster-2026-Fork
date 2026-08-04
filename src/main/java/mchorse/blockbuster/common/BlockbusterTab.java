package mchorse.blockbuster.common;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.common.block.BlockGreen;
import mchorse.blockbuster.common.block.ChromaColor;
import mchorse.blockbuster.common.item.BlockbusterItems;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.block.Block;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Blockbuster creative tab (P99).
 *
 * <p>Ports the 1.12.2 {@code mchorse.blockbuster.common.BlockbusterTab} item
 * group: label {@code "blockbuster"} ({@code itemGroup.blockbuster}), the
 * director block as its icon, and the legacy display ordering.</p>
 *
 * <h2>Ordering</h2>
 * <p>The tab is 9 columns wide, and the two chroma runs are 8 wide, so each
 * leaves its row's last cell free. Those two cells hold the model block and,
 * directly beneath it, the director block:</p>
 *
 * <ul>
 *   <li>row 1 — 8 chroma colors, then the model block;</li>
 *   <li>row 2 — the same 8 colors in dim form, then the director block;</li>
 *   <li>row 3 — register, playback, actor config, gun, the actor spawn egg, and
 *       (when {@code add_utility_blocks}) command / structure / barrier.</li>
 * </ul>
 *
 * <p>1.12.2 reached the same two-row shape by emitting {@code ItemStack.EMPTY}
 * spacers after each chroma run, plus 9 empty fillers, to push the main items
 * onto a fresh row. 1.20.4's {@link ItemGroup.Entries} rejects empty stacks, so
 * the rows are ended with real items instead.</p>
 *
 * <h2>Chroma sub-blocks</h2>
 * <p>1.12's 8 metadata sub-items per chroma block become 8 {@code BlockStateTag}
 * stacks ({@link BlockGreen#colorStack}); {@code dim_green} emits the same 8
 * colors, exactly like the legacy {@code getSubBlocks} run. Both chroma
 * {@code BlockItem}s stay <b>out</b> of the generic item run — legacy skipped
 * {@code ItemBlockGreen} instances there so the colors appear only once.</p>
 *
 * <p>Rendering those stacks in their own color is a client concern: one item id
 * resolves to one item model, so the color is pulled back out of the
 * {@code BlockStateTag} by the {@code blockbuster:chroma} model predicate
 * ({@code BlockbusterClient.registerChromaItemModels}) and mapped to a per-color
 * override in {@code models/item/green.json} / {@code dim_green.json}.</p>
 *
 * <h2>Actor spawn egg</h2>
 * <p>1.12 shipped no egg item; the tab conjured a vanilla {@code Items.SPAWN_EGG}
 * stack with {@code blockbuster:actor} written into its NBT. The port adds the
 * registered {@link Blockbuster#ACTOR_SPAWN_EGG} in the same position (after the
 * generic run, before the utility blocks) and deliberately keeps it out of the
 * generic run.</p>
 */
public final class BlockbusterTab
{
    /** The registered {@code blockbuster:blockbuster} item group. */
    public static ItemGroup GROUP;

    private BlockbusterTab()
    {}

    /**
     * Idempotent registration — safe from the mod initializer and headless
     * tests alike. Requires {@link BlockbusterItems#register()} to have run
     * first (the icon and the generic item run reference the registered items).
     */
    public static synchronized void register()
    {
        if (GROUP != null)
        {
            return;
        }

        GROUP = FabricItemGroup.builder()
            .icon(BlockbusterTab::icon)
            .displayName(Text.translatable("itemGroup.blockbuster"))
            .entries(BlockbusterTab::collect)
            .build();

        Registry.register(Registries.ITEM_GROUP, new Identifier(Blockbuster.MOD_ID, "blockbuster"), GROUP);
    }

    /**
     * Legacy {@code getTabIconItem} returned the director block item (P94).
     * Falls back to the playback item if the director block is somehow not
     * registered yet (defensive — registerContent registers it first).
     */
    private static ItemStack icon()
    {
        if (Blockbuster.DIRECTOR_BLOCK != null)
        {
            return new ItemStack(Blockbuster.DIRECTOR_BLOCK);
        }

        return new ItemStack(BlockbusterItems.PLAYBACK);
    }

    /**
     * Legacy {@code displayAllRelevantItems}, translated to a 1.20.4 entries
     * callback. Package-visible so the P99 headless ordering test can invoke it
     * with a stub {@link ItemGroup.Entries}.
     */
    public static void collect(ItemGroup.DisplayContext context, ItemGroup.Entries entries)
    {
        /*
         * Row 1 — the 8 chroma colors (legacy greenBlock.getSubItems, metadata
         * 0..7), then the model block in the 9th cell.
         */
        addChromaColors(entries, Blockbuster.greenBlock);

        /* P95: the model block item (legacy modelBlockItems[0], an ItemBlock
         * sharing the block's id). model1..15 are excluded — legacy only gave
         * the tab to the lightValue == 0 variant. */
        if (Blockbuster.modelBlockItems[0] != null)
        {
            entries.add(Blockbuster.modelBlockItems[0]);
        }

        /*
         * Row 2 — the same 8 colors in dim form, then the director block in the
         * 9th cell, directly under the model block.
         */
        addChromaColors(entries, Blockbuster.dimGreenBlock);

        /* P94: the director block item (plain BlockItem). */
        if (Blockbuster.DIRECTOR_BLOCK != null)
        {
            entries.add(Blockbuster.DIRECTOR_BLOCK);
        }

        /*
         * Row 3 — the generic item run. Legacy iterated the item registry and
         * added every item whose creativeTab was this one, in registration
         * order, skipping ItemBlockGreen. In the port there is no per-item tab
         * attribute, so the order is spelled out explicitly, matching the legacy
         * CommonProxy registration order.
         */
        entries.add(BlockbusterItems.REGISTER);
        entries.add(BlockbusterItems.PLAYBACK);
        entries.add(BlockbusterItems.ACTOR_CONFIG);
        entries.add(BlockbusterItems.GUN);

        /* P93: the actor spawn egg, last before the utility blocks — added
         * manually, never through a tab attribute, exactly like the 1.12 tab's
         * hand-built vanilla egg stack. */
        if (Blockbuster.ACTOR_SPAWN_EGG != null)
        {
            entries.add(Blockbuster.ACTOR_SPAWN_EGG);
        }

        /*
         * Utility blocks — gated on the client-side add_utility_blocks config
         * (default false). Adding vanilla OP_BLOCKS to our group bypasses the
         * operator-tab gate exactly like 1.12 did; kept faithfully.
         */
        if (Blockbuster.addUtilityBlocks != null && Blockbuster.addUtilityBlocks.get())
        {
            entries.add(Items.COMMAND_BLOCK);
            entries.add(Items.STRUCTURE_BLOCK);
            entries.add(Items.BARRIER);
        }
    }

    /**
     * Emits one {@code BlockStateTag} stack per {@link ChromaColor}, in ordinal
     * (= legacy metadata) order — the port's stand-in for 1.12's
     * {@code getSubBlocks} metadata sub-items. No-op when the block is not
     * registered yet (headless tests that only boot part of the content).
     */
    private static void addChromaColors(ItemGroup.Entries entries, Block block)
    {
        if (block == null)
        {
            return;
        }

        for (ChromaColor color : ChromaColor.values())
        {
            entries.add(BlockGreen.colorStack(block, color));
        }
    }
}
