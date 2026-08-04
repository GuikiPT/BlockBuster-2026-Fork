package mchorse.blockbuster.common.item;

import mchorse.blockbuster.utils.EntityUtils;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * Register item (P98).
 *
 * <p>This item used to tie actors to a director block. Director blocks were
 * removed, so the item is now a deprecation relic — but it is <b>not</b>
 * dropped: it still registers, still has a tooltip and still occupies its slot
 * in the creative tab, exactly like Blockbuster 2.7.2. Right-clicking it emits
 * an <b>action-bar</b> status message and returns {@code PASS} (no arm swing).</p>
 *
 * <p>Legacy source:
 * {@code blockbuster-1.12/src/main/java/mchorse/blockbuster/common/item/ItemRegister.java}.</p>
 */
public class ItemRegister extends Item
{
    /* Legacy lang-key parity: the converted lang JSONs keep 1.12.2's keys
     * 1:1 (LangConversionTest), so point the translation key at the legacy id. */
    @Override
    public String getTranslationKey()
    {
        return "item.blockbuster.register.name";
    }

    public ItemRegister()
    {
        super(new Item.Settings().maxCount(1));
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context)
    {
        tooltip.add(Text.translatable("blockbuster.info.register"));
    }

    /**
     * Legacy {@code onItemRightClick}: send the "bye register item" deprecation
     * message to the action bar (not chat — {@link EntityUtils#sendStatusMessage}
     * uses the overlay slot) and return {@code PASS}.
     */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand)
    {
        if (!world.isClient)
        {
            EntityUtils.sendStatusMessage((ServerPlayerEntity) player, Text.translatable("blockbuster.bye_register_item"));
        }

        return TypedActionResult.pass(player.getStackInHand(hand));
    }
}
