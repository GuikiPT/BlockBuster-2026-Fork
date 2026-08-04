package mchorse.blockbuster.commands.action;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.commands.BBCommandBase;
import mchorse.mclib.commands.utils.CommandException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

public class SubCommandActionCancel extends BBCommandBase
{
    @Override
    public String getName()
    {
        return "cancel";
    }

    @Override
    public String getUsage(ServerCommandSource sender)
    {
        return "blockbuster.commands.action.cancel";
    }

    @Override
    public String getSyntax()
    {
        return "{l}{6}/{r}action {8}cancel{r}";
    }

    @Override
    public void executeCommand(MinecraftServer server, ServerCommandSource sender, String[] args) throws CommandException
    {
        ServerPlayerEntity player = getCommandSenderAsPlayer(sender);

        if (CommonProxy.manager.cancel(player))
        {
            Blockbuster.l10n.info(sender, "action.cancel");
        }
    }
}
