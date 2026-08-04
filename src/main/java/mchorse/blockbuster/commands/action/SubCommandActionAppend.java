package mchorse.blockbuster.commands.action;

import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.commands.BBCommandBase;
import mchorse.blockbuster.recording.data.Mode;
import mchorse.mclib.commands.utils.CommandException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Sub-command /action append
 *
 * This sub-command is responsible for starting recording actions on top of
 * an existing record with a tick offset.
 */
public class SubCommandActionAppend extends BBCommandBase
{
    @Override
    public String getName()
    {
        return "append";
    }

    @Override
    public String getUsage(ServerCommandSource sender)
    {
        return "blockbuster.commands.action.append";
    }

    @Override
    public String getSyntax()
    {
        return "{l}{6}/{r}action {8}append{r} {7}<filename> <offset> [scene]{r}";
    }

    @Override
    public int getRequiredArgs()
    {
        return 2;
    }

    @Override
    public void executeCommand(MinecraftServer server, ServerCommandSource sender, String[] args) throws CommandException
    {
        ServerPlayerEntity player = getCommandSenderAsPlayer(sender);
        int offset = parseInt(args[1], 0);

        if (args.length >= 3)
        {
            Object scene = CommonProxy.scenes.get(args[2], sender.getWorld());

            if (scene != null)
            {
                CommonProxy.scenes.record(args[2], args[0], offset, player);
            }
        }
        else
        {
            CommonProxy.manager.record(args[0], player, Mode.ACTIONS, true, true, offset, null);
        }
    }
}
