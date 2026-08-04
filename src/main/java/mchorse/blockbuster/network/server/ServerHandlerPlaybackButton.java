package mchorse.blockbuster.network.server;

import mchorse.blockbuster.common.item.ItemPlayback;
import mchorse.blockbuster.network.common.PacketPlaybackButton;
import mchorse.mclib.network.ServerMessageHandler;
import mchorse.mclib.utils.OpHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server handler for {@link PacketPlaybackButton} (roadmap P129).
 *
 * <p>OP-gated. Locates the {@link ItemPlayback} in the player's main hand (else
 * offhand, else bails), then rewrites its NBT <b>destructively</b>: the three
 * config keys {@code CameraPlay}, {@code CameraProfile} and {@code Scene} are
 * always removed first, so {@code mode == 0} clears all camera config. The
 * {@code Scene} key is re-written when the location is a scene; {@code CameraPlay
 * = true} when {@code mode == 1}; {@code CameraProfile = profile} when
 * {@code mode == 2}. Key names/casing are a save-format contract kept exact.</p>
 *
 * <p>Legacy source:
 * {@code blockbuster-1.12/src/main/java/mchorse/blockbuster/network/server/ServerHandlerPlaybackButton.java}.</p>
 */
public class ServerHandlerPlaybackButton extends ServerMessageHandler<PacketPlaybackButton>
{
    @Override
    public void run(ServerPlayerEntity player, PacketPlaybackButton message)
    {
        if (!OpHelper.isPlayerOp(player))
        {
            return;
        }

        ItemStack stack = player.getMainHandStack();

        if (!(stack.getItem() instanceof ItemPlayback))
        {
            stack = player.getOffHandStack();
        }

        if (!(stack.getItem() instanceof ItemPlayback))
        {
            return;
        }

        rewrite(stack, message);
    }

    /**
     * The pure NBT-rewrite matrix, extracted so the mode 0/1/2 × had-keys cases
     * can be unit-tested against a bare {@link ItemStack} without a live player.
     */
    public static void rewrite(ItemStack stack, PacketPlaybackButton message)
    {
        NbtCompound compound = stack.getOrCreateNbt();

        compound.remove("CameraPlay");
        compound.remove("CameraProfile");
        compound.remove("Scene");

        if (message.location.isScene())
        {
            compound.putString("Scene", message.location.getFilename());
        }

        if (message.mode == 1)
        {
            compound.putBoolean("CameraPlay", true);
        }
        else if (message.mode == 2)
        {
            compound.putString("CameraProfile", message.profile);
        }
    }
}
