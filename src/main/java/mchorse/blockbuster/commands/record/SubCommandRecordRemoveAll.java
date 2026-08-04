package mchorse.blockbuster.commands.record;

import mchorse.blockbuster.commands.CommandRecord;
import mchorse.blockbuster.recording.data.Record;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.commands.utils.CommandException;
import mchorse.mclib.network.mclib.Dispatcher;
import mchorse.mclib.network.mclib.common.PacketConfirm;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.function.Consumer;

public class SubCommandRecordRemoveAll extends SubCommandRecordBase
{
    @Override
    public String getName()
    {
        return "remove_all";
    }

    @Override
    public String getUsage(ServerCommandSource sender)
    {
        return "blockbuster.commands.record.remove_all";
    }

    @Override
    public String getSyntax()
    {
        return "{l}{6}/{r}record {8}remove_all{r} {7}<filename> [force]{r}";
    }

    @Override
    public int getRequiredArgs()
    {
        return 1;
    }

    @Override
    public void executeCommand(MinecraftServer server, ServerCommandSource sender, String[] args) throws CommandException
    {
        String filename = args[0];
        Record record = CommandRecord.getRecord(filename);
        boolean force = (args.length > 1) ? parseBoolean(args[1]) : false;
        ServerPlayerEntity player = getCommandSenderAsPlayer(sender);

        this.dispatchConfirm(player, force, filename, (value) ->
        {
            if (value)
            {
                record.actions.replaceAll((actions) ->
                {
                    return null;
                });
            }
        });
    }

    private void dispatchConfirm(ServerPlayerEntity player, boolean force, String filename, Consumer<Boolean> callback)
    {
        if (force)
        {
            callback.accept(force);
        }
        else
        {
            Dispatcher.sendTo(new PacketConfirm(PacketConfirm.GUI.MCSCREEN, IKey.format("blockbuster.commands.record.remove_all_modal", filename), (value) ->
            {
                callback.accept(value);
            }), player);
        }
    }
}
