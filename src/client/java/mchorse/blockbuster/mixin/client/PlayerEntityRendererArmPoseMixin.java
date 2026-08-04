package mchorse.blockbuster.mixin.client;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.client.KeyboardHandler;
import mchorse.blockbuster.client.render.GunArmPose;
import mchorse.blockbuster.common.GunProps;
import mchorse.blockbuster.utils.NBTUtils;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gun arm shooting-pose (P197).
 *
 * <p>Replaces the legacy ASM {@code RenderPlayerTransformer} (which appended
 * {@code RenderingHandler.changePlayerHand} to {@code setModelVisibilities}):
 * when the hand being posed holds a gun that requests the shooting pose
 * (always, or while the shoot key is held), force {@code BOW_AND_ARROW}. The
 * decision is the pure {@link GunArmPose}.</p>
 *
 * <p>Signature verified via javap against the loom-cache named jar:
 * {@code private static BipedEntityModel.ArmPose getArmPose(AbstractClientPlayerEntity, Hand)}.</p>
 */
@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererArmPoseMixin
{
    @Inject(method = "getArmPose", at = @At("RETURN"), cancellable = true)
    private static void blockbuster$onGetArmPose(AbstractClientPlayerEntity player, Hand hand, CallbackInfoReturnable<BipedEntityModel.ArmPose> info)
    {
        ItemStack stack = player.getStackInHand(hand);

        if (stack.getItem() != Blockbuster.GUN)
        {
            return;
        }

        GunProps props = NBTUtils.getGunProps(stack);

        if (props == null)
        {
            return;
        }

        boolean shootKeyDown = KeyboardHandler.gunShoot != null && KeyboardHandler.gunShoot.isPressed();

        if (GunArmPose.shouldUseShootingPose(props.alwaysArmsShootingPose, props.enableArmsShootingPose, shootKeyDown))
        {
            info.setReturnValue(BipedEntityModel.ArmPose.BOW_AND_ARROW);
        }
    }
}
