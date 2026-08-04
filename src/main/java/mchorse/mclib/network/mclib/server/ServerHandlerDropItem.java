package mchorse.mclib.network.mclib.server;

import mchorse.mclib.McLib;
import mchorse.mclib.network.ServerMessageHandler;
import mchorse.mclib.network.mclib.common.PacketDropItem;
import mchorse.mclib.utils.OpHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import java.util.function.Supplier;

/**
 * Full port of McLib 2.4.3's ServerHandlerDropItem (roadmap P26) — the
 * creative-GUI "give me this stack" helper. Op gate lands with the handler
 * (security-relevant, not deferred).
 *
 * <p>Yarn mapping (javap-verified): {@code inventory.addItemStackToInventory}
 * → {@code getInventory().insertStack(ItemStack)};
 * {@code inventoryContainer.detectAndSendChanges} →
 * {@code playerScreenHandler.sendContentUpdates()}; the pickup-sound
 * {@code world.playSound(null, x, y, z, event, category, volume, pitch)}
 * overload exists unchanged.</p>
 *
 * <p>Legacy source: .tools/legacy-src/mclib/build/sources/main/java/mchorse/mclib/network/mclib/server/ServerHandlerDropItem.java</p>
 */
public class ServerHandlerDropItem extends ServerMessageHandler<PacketDropItem>
{
    /**
     * Legacy {@code McLib.opDropItems} — the {@code op_access} config module's
     * {@code mclib.drop_items} boolean (default true). Kept as a supplier seam
     * so headless tests can drive the gate without a config tree, but the
     * default reads the real registered value, exactly like legacy's direct
     * {@code McLib.opDropItems.get()}.
     */
    public static Supplier<Boolean> opDropItems = () -> McLib.opDropItems.get();

    @Override
    public void run(ServerPlayerEntity player, PacketDropItem message)
    {
        if (player.isCreative() && opDropItems.get() || OpHelper.isPlayerOp(player))
        {
            ItemStack stack = message.stack;

            player.getInventory().insertStack(stack);
            player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.2F, 1F);
            player.playerScreenHandler.sendContentUpdates();
        }
    }
}
