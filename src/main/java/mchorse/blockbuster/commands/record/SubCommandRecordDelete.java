package mchorse.blockbuster.commands.record;

import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.commands.CommandRecord;
import mchorse.blockbuster.recording.RecordUtils;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.commands.utils.CommandException;
import mchorse.mclib.network.mclib.Dispatcher;
import mchorse.mclib.network.mclib.common.PacketConfirm;
import mchorse.mclib.utils.OpHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Command /record delete
 *
 * This command is responsible for deleting a recording (with a client-side
 * confirm modal unless {@code force}).
 */
public class SubCommandRecordDelete extends SubCommandRecordBase
{
    @Override
    public String getName()
    {
        return "delete";
    }

    @Override
    public String getUsage(ServerCommandSource sender)
    {
        return "blockbuster.commands.record.delete";
    }

    @Override
    public String getSyntax()
    {
        return "{l}{6}/{r}record {8}delete{r} {7}<filename> [force]{r}";
    }

    @Override
    public int getRequiredArgs()
    {
        return 1;
    }

    @Override
    public void executeCommand(MinecraftServer server, ServerCommandSource sender, String[] args) throws CommandException
    {
        ServerPlayerEntity player = getCommandSenderAsPlayer(sender);

        if (!OpHelper.isPlayerOp(player))
        {
            throw new CommandException("record.delete_rights");
        }

        String filename = args[0];

        /* throws exception if recording doesn't exist */
        CommandRecord.getRecord(filename);

        boolean force = (args.length > 1) ? parseBoolean(args[1]) : false;

        if (force)
        {
            this.deleteRecording(filename);
        }
        else
        {
            Dispatcher.sendTo(new PacketConfirm(PacketConfirm.GUI.MCSCREEN, IKey.format("blockbuster.commands.record.delete_modal", filename),
                (value) ->
                {
                    if (value)
                    {
                        this.deleteRecording(filename);
                    }
                }), player);
        }
    }

    private void deleteRecording(String filename)
    {
        try
        {
            RecordUtils.replayFile(filename).delete();
            RecordUtils.unloadRecord(CommonProxy.manager.records.get(filename));
            CommonProxy.manager.records.remove(filename);
        }
        catch (NullPointerException e)
        {}
    }
}
