package mchorse.blockbuster.commands.action;

import mchorse.blockbuster.capabilities.recording.Recording;
import mchorse.blockbuster.commands.BBCommandBase;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.recording.PacketUnloadRecordings;
import mchorse.mclib.commands.utils.CommandException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Sub-command /action clear
 *
 * This sub-command is responsible for clearing out tracking information about
 * the player records on the server and also sending a packet for unloading the
 * records on the client.
 *
 * Used in some buggy situations when server doesn't send unload packet or
 * doesn't want to send clients a new record.
 */
public class SubCommandActionClear extends BBCommandBase
{
    @Override
    public String getName()
    {
        return "clear";
    }

    @Override
    public String getUsage(ServerCommandSource sender)
    {
        return "blockbuster.commands.action.clear";
    }

    @Override
    public String getSyntax()
    {
        return "{l}{6}/{r}action {8}clear{r}";
    }

    @Override
    public void executeCommand(MinecraftServer server, ServerCommandSource sender, String[] args) throws CommandException
    {
        ServerPlayerEntity player = getCommandSenderAsPlayer(sender);
        Recording.get(player).removeRecordings();

        Dispatcher.sendTo(new PacketUnloadRecordings(), player);
    }
}
