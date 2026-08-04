package mchorse.blockbuster.commands.action;

import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.commands.BBCommandBase;
import mchorse.blockbuster.recording.RecordUtils;
import mchorse.mclib.commands.utils.CommandException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;

/**
 * Sub-command /action request
 *
 * This command is responsible for requesting record frames from the server.
 * (Legacy quirk kept: it also tries to play a scene by the same name first.)
 */
public class SubCommandActionRequest extends BBCommandBase
{
    @Override
    public String getName()
    {
        return "request";
    }

    @Override
    public String getUsage(ServerCommandSource sender)
    {
        return "blockbuster.commands.action.request";
    }

    @Override
    public String getSyntax()
    {
        return "{l}{6}/{r}action {8}request{r} {7}<filename>{r}";
    }

    @Override
    public int getRequiredArgs()
    {
        return 1;
    }

    @Override
    public void executeCommand(MinecraftServer server, ServerCommandSource sender, String[] args) throws CommandException
    {
        CommonProxy.scenes.play(args[0], sender.getWorld());
        RecordUtils.sendRecordTo(args[0], getCommandSenderAsPlayer(sender));
    }
}
