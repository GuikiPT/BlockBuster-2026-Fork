package mchorse.blockbuster.mixin;

import mchorse.blockbuster.recording.capturing.ActionHandler;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fabric has no block-place event (legacy {@code BlockEvent.PlaceEvent} /
 * {@code MultiPlaceEvent}) — capture successful placements at the return of
 * {@code BlockItem.place}.
 */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin
{
    @Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;", at = @At("RETURN"))
    private void blockbuster$onPlace(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir)
    {
        if (cir.getReturnValue().isAccepted())
        {
            ActionHandler.onBlockPlaced(context);
        }
    }
}
