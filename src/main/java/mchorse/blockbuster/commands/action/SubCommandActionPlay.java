package mchorse.blockbuster.commands.action;

import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.commands.BBCommandBase;
import mchorse.blockbuster.commands.CommandAction;
import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.blockbuster.recording.data.Mode;
import mchorse.mclib.commands.utils.CommandException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;

/**
 * Sub-command /action play
 *
 * This sub-command is responsible for starting playback of ghost actor from
 * given attributes.
 *
 * Quirk (kept): the actor is spawned AFTER {@code play()} — frame 0 is
 * applied to the not-yet-spawned entity and the deferred tracker queue
 * (P119) depends on that order.
 */
public class SubCommandActionPlay extends BBCommandBase
{
    @Override
    public String getName()
    {
        return "play";
    }

    @Override
    public String getUsage(ServerCommandSource sender)
    {
        return "blockbuster.commands.action.play";
    }

    @Override
    public String getSyntax()
    {
        return "{l}{6}/{r}action {8}play{r} {7}<filename> [invincibility] [morph_nbt]{r}";
    }

    @Override
    public int getRequiredArgs()
    {
        return 1;
    }

    @Override
    public void executeCommand(MinecraftServer server, ServerCommandSource sender, String[] args) throws CommandException
    {
        ServerWorld world = sender.getWorld();
        EntityActor actor = CommandAction.actorFromArgs(args, world);

        CommonProxy.manager.play(args[0], actor, Mode.BOTH, true);
        world.spawnEntity(actor);
    }
}
