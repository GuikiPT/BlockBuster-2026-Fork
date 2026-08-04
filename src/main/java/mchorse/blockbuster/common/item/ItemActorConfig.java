package mchorse.blockbuster.common.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.List;

/**
 * Actor configuration item (P98).
 *
 * <p>Used to open an actor's configuration GUI. This is a <b>pure item</b> — it
 * has <i>no use logic of its own</i>. The interaction lives on the actor side:
 * {@code EntityActor.interactMob} checks {@code stack.getItem() instanceof
 * ItemActorConfig} and opens the ACTOR GUI with the entity id passed through
 * (P93). Max stack 1, registry name {@code actor_config}.</p>
 *
 * <p>Legacy source:
 * {@code blockbuster-1.12/src/main/java/mchorse/blockbuster/common/item/ItemActorConfig.java}.</p>
 */
public class ItemActorConfig extends Item
{
    /* Legacy lang-key parity: the converted lang JSONs keep 1.12.2's keys
     * 1:1 (LangConversionTest), so point the translation key at the legacy id. */
    @Override
    public String getTranslationKey()
    {
        return "item.blockbuster.actor_config.name";
    }

    public ItemActorConfig()
    {
        super(new Item.Settings().maxCount(1));
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context)
    {
        tooltip.add(Text.translatable("blockbuster.info.actor_config"));
    }
}
