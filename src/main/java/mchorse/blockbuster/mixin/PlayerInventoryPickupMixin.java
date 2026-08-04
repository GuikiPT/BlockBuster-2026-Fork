package mchorse.blockbuster.mixin;

import mchorse.blockbuster.events.PlayerHandler;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * P119.3 — item-pickup suppression while a scene puppeteers a real player.
 *
 * <p>Replaces legacy's ASM coremod
 * {@code mchorse/blockbuster/core/transformers/InventoryPlayerTransformer.java},
 * which rewrote {@code InventoryPlayer.addItemStackToInventory(ItemStack)} into
 * exactly:</p>
 *
 * <pre>
 * public boolean addItemStackToInventory(ItemStack stack)
 * {
 *     PlayerHandler.beforeItemStackAdd(this);
 *     boolean result = this.add(-1, stack);
 *     PlayerHandler.afterItemStackAdd(this);
 *     return result;
 * }
 * </pre>
 *
 * <p>A pure wrap — it guards nothing and the result passes through untouched,
 * so HEAD + RETURN injections are the exact equivalent. The yarn 1.20.4 target
 * is {@code PlayerInventory.insertStack(ItemStack)}, whose whole body
 * disassembles to {@code aload_0; iconst_m1; aload_1; invokevirtual
 * insertStack(ILnet/minecraft/item/ItemStack;)Z; ireturn} — structurally the
 * same one-liner legacy transformed (verified with javap against the loom
 * named jar).</p>
 *
 * <p><b>Only the 1-arg overload is targeted.</b> The 2-arg
 * {@code insertStack(int, ItemStack)} carries all the logic and is reached from
 * non-pickup paths (and recursively from this one); wrapping it would over-fire
 * relative to 1.12.2.</p>
 *
 * <p>Legacy's second transformer ({@code EntityItemTransformer}, calling
 * {@code PlayerHandler.beforePlayerItemPickUp}) is intentionally <b>not</b>
 * ported: that method's 2.7.2 body is empty, and its ASM anchor was a local
 * added by Forge's {@code EntityItemPickupEvent} patch that vanilla 1.20.4 has
 * no counterpart for.</p>
 */
@Mixin(PlayerInventory.class)
public abstract class PlayerInventoryPickupMixin
{
    @Inject(method = "insertStack(Lnet/minecraft/item/ItemStack;)Z", at = @At("HEAD"))
    private void blockbuster$beforeItemStackAdd(ItemStack stack, CallbackInfoReturnable<Boolean> info)
    {
        PlayerHandler.beforeItemStackAdd((PlayerInventory) (Object) this);
    }

    @Inject(method = "insertStack(Lnet/minecraft/item/ItemStack;)Z", at = @At("RETURN"))
    private void blockbuster$afterItemStackAdd(ItemStack stack, CallbackInfoReturnable<Boolean> info)
    {
        PlayerHandler.afterItemStackAdd((PlayerInventory) (Object) this);
    }
}
