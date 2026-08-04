package mchorse.blockbuster.commands.action;

import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.commands.BBCommandBase;
import mchorse.mclib.commands.utils.CommandException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;

/**
 * Sub-command /action stop
 *
 * This sub-command is responsible for stopping the action recording of
 * current player.
 */
public class SubCommandActionStop extends BBCommandBase
{
    @Override
    public String getName()
    {
        return "stop";
    }

    @Override
    public String getUsage(ServerCommandSource sender)
    {
        return "blockbuster.commands.action.stop";
    }

    @Override
    public String getSyntax()
    {
        return "{l}{6}/{r}action {8}stop{r}";
    }

    @Override
    public void executeCommand(MinecraftServer server, ServerCommandSource sender, String[] args) throws CommandException
    {
        CommonProxy.manager.halt(getCommandSenderAsPlayer(sender), false, true);
    }
}
