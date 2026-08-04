package mchorse.blockbuster.network.client.guns;

import mchorse.blockbuster.client.render.item.TileEntityGunItemStackRenderer;
import mchorse.blockbuster.common.GunProps;
import mchorse.blockbuster.network.common.guns.PacketGunInfo;
import mchorse.blockbuster.utils.NBTUtils;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;

/**
 * GunInfo client handler (P196). Looks up the owning entity, saves the props tag
 * onto its mainhand stack. Legacy also transplanted <b>only</b> {@code state}
 * into the cached render entry so animation continuity survives the re-parse —
 * that cache lands in P197 (SEAM). Tolerates id-0 lookups returning null
 * (the GUI-save echo uses id 0 until the server substitutes the sender id).
 */
public class ClientHandlerGunInfo extends ClientMessageHandler<PacketGunInfo>
{
    @Override
    public void run(ClientPlayerEntity player, PacketGunInfo message)
    {
        Entity entity = player.getWorld().getEntityById(message.entity);

        if (entity instanceof LivingEntity)
        {
            ItemStack stack = ((LivingEntity) entity).getMainHandStack();

            if (!stack.isEmpty())
            {
                NBTUtils.saveGunProps(stack, message.tag);

                /* If the client render cache holds this stack instance,
                 * transplant only the freshly-parsed state so animation
                 * continuity survives the re-parse (P197). */
                GunProps fresh = NBTUtils.getGunProps(stack);

                if (fresh != null)
                {
                    TileEntityGunItemStackRenderer.transplantState(stack, fresh.state);
                }
            }
        }
    }
}
