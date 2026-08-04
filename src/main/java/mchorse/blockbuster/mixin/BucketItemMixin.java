package mchorse.blockbuster.mixin;

import mchorse.blockbuster.recording.capturing.ActionHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BucketItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Legacy {@code FillBucketEvent} replacement — bucket placement fires no
 * place event, so water/lava bucket use is captured here with the same
 * raycast the vanilla bucket does for emptying ({@code FluidHandling.NONE},
 * matching 1.12.2's {@code rayTrace(world, player, false)}).
 *
 * <p>Extends {@link Item} only to reach the protected static
 * {@code raycast} helper.</p>
 */
@Mixin(BucketItem.class)
public abstract class BucketItemMixin extends Item
{
    public BucketItemMixin(Settings settings)
    {
        super(settings);
    }

    @Inject(method = "use", at = @At("HEAD"))
    private void blockbuster$onUseBucket(World world, PlayerEntity user, Hand hand, CallbackInfoReturnable<TypedActionResult<ItemStack>> cir)
    {
        if (world.isClient())
        {
            return;
        }

        ItemStack stack = user.getStackInHand(hand);

        if (stack.isOf(Items.WATER_BUCKET) || stack.isOf(Items.LAVA_BUCKET))
        {
            BlockHitResult target = raycast(world, user, RaycastContext.FluidHandling.NONE);

            ActionHandler.onPlayerUseBucket(user, stack, target);
        }
    }
}
