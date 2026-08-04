package mchorse.mclib.permissions;

import mchorse.mclib.network.mclib.Dispatcher;
import mchorse.mclib.network.mclib.client.AbstractClientHandlerAnswer;
import mchorse.mclib.network.mclib.common.PacketRequestPermission;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import java.util.function.Consumer;

/**
 * Port of McLib 2.4.3's {@code PermissionUtils} (roadmap P44; legacy sat in
 * the common jar but every call site is client-side, so the port lives in
 * the client source set — same package kept for diff-ability).
 *
 * <p>Headless/parity notes: legacy checked
 * {@code !Minecraft.getMinecraft().world.isRemote} first (always false on
 * the client — dead branch kept out of the port) and then treated
 * singleplayer as "always allowed". A null {@code MinecraftClient} (unit
 * tests) is treated like singleplayer: the callback gets {@code true}
 * synchronously.</p>
 */
public class PermissionUtils
{
    /**
     * Side independent method for checking if it has the permission and then passing the result to the callback
     * @param player the player to check for the permission
     * @param permission the permission category
     * @param callback the callback to be executed after checking the player
     *                 or after the server has sent the permission result.
     */
    public static void hasPermission(PlayerEntity player, PermissionCategory permission, Consumer<Boolean> callback)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        /* In singleplayer there is no use in having a permission system */
        if (mc == null || mc.isIntegratedServerRunning())
        {
            callback.accept(true);
        }
        else
        {
            AbstractClientHandlerAnswer.requestServerAnswer(Dispatcher.DISPATCHER, new PacketRequestPermission(-1, permission), callback);
        }
    }
}
