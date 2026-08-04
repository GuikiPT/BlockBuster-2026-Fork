package mchorse.blockbuster.network.server;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.network.common.PacketReloadModels;
import mchorse.mclib.network.ServerMessageHandler;
import mchorse.mclib.utils.OpHelper;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server handler for {@link PacketReloadModels} (roadmap P69).
 *
 * <p>Permission is op level 2 (legacy {@code player.canUseCommand(2, "")} →
 * yarn {@code hasPermissionLevel(2)}); on success it reloads the server models
 * and reports {@code model.reload} success, on failure it reports the <b>same</b>
 * {@code model.reload} key as an error (the one l10n key serves both paths, per
 * 1.12.2).</p>
 *
 * <p>Legacy source: {@code blockbuster-1.12/.../network/server/ServerHandlerReloadModels.java}.</p>
 */
public class ServerHandlerReloadModels extends ServerMessageHandler<PacketReloadModels>
{
    @Override
    public void run(ServerPlayerEntity player, PacketReloadModels message)
    {
        if (player.hasPermissionLevel(OpHelper.VANILLA_OP_LEVEL))
        {
            reload(message.force);
            Blockbuster.l10n.success(player, "model.reload");
        }
        else
        {
            Blockbuster.l10n.error(player, "model.reload");
        }
    }

    /**
     * The permission-gated action, extracted so the branch is unit-testable
     * without a live {@link ServerPlayerEntity} (the l10n feedback is the only
     * player-bound side effect and is exercised separately).
     *
     * @return {@code true} when permitted (the reload ran), {@code false}
     *         otherwise.
     */
    public static boolean handle(boolean permitted, boolean force)
    {
        if (permitted)
        {
            reload(force);

            return true;
        }

        return false;
    }

    private static void reload(boolean force)
    {
        Blockbuster.reloadServerModels(force);
    }
}
