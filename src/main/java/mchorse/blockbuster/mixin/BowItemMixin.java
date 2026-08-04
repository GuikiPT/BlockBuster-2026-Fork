package mchorse.blockbuster.mixin;

import mchorse.blockbuster.recording.capturing.ActionHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Legacy {@code ArrowLooseEvent} replacement — charge is
 * {@code getMaxUseTime - remainingUseTicks}, the exact value Forge passed,
 * captured at HEAD (the legacy event fired before ammo checks too).
 */
@Mixin(BowItem.class)
public abstract class BowItemMixin
{
    @Inject(method = "onStoppedUsing", at = @At("HEAD"))
    private void blockbuster$onArrowLoose(ItemStack stack, World world, LivingEntity user, int remainingUseTicks, CallbackInfo info)
    {
        if (!world.isClient() && user instanceof PlayerEntity player)
        {
            int charge = ((BowItem) (Object) this).getMaxUseTime(stack) - remainingUseTicks;

            ActionHandler.onArrowLoose(player, charge);
        }
    }
}
