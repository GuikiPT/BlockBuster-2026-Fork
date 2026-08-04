package mchorse.blockbuster.commands.record;

import mchorse.blockbuster.commands.CommandRecord;
import mchorse.blockbuster.recording.actions.Action;
import mchorse.blockbuster.recording.data.Record;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.commands.utils.CommandException;
import mchorse.mclib.network.mclib.Dispatcher;
import mchorse.mclib.network.mclib.common.PacketConfirm;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.function.Consumer;

/**
 * Command /record remove
 *
 * This command is responsible for removing action(s) from given player
 * recording.
 */
public class SubCommandRecordRemove extends SubCommandRecordBase
{
    @Override
    public String getName()
    {
        return "remove";
    }

    @Override
    public String getUsage(ServerCommandSource sender)
    {
        return "blockbuster.commands.record.remove";
    }

    @Override
    public String getSyntax()
    {
        return "{l}{6}/{r}record {8}remove{r} {7}<filename> <tick> [index] [force]{r}";
    }

    @Override
    public int getRequiredArgs()
    {
        return 2;
    }

    @Override
    public void executeCommand(MinecraftServer server, ServerCommandSource sender, String[] args) throws CommandException
    {
        String filename = args[0];
        int tick = parseInt(args[1], 0);
        Record record = CommandRecord.getRecord(filename);

        if (tick < 0 || tick >= record.actions.size())
        {
            throw new CommandException("record.tick_out_range", tick, record.actions.size() - 1);
        }

        this.removeActions(args, sender, record, tick);
    }

    /**
     * Remove action(s) from given record at given tick
     */
    private void removeActions(String[] args, ServerCommandSource sender, Record record, int tick) throws CommandException
    {
        ServerPlayerEntity player = getCommandSenderAsPlayer(sender);

        boolean force = (args.length > 3) ? parseBoolean(args[3]) : false;

        /* index of -1 means to remove all actions */
        if (args.length > 2 && parseInt(args[2]) != -1)
        {
            int index = parseInt(args[2]);
            List<Action> actions = record.actions.get(tick);

            if (actions == null)
            {
                throw new CommandException("record.already_empty", args[1], args[0]);
            }

            if (index < -1 || index >= actions.size())
            {
                throw new CommandException("record.index_out_range", index, actions.size() - 1);
            }

            dispatchConfirm(player, force, (value) ->
            {
                if (value)
                {
                    /* Remove action at given tick */
                    if (actions.size() <= 1)
                    {
                        record.actions.set(tick, null);
                    }
                    else
                    {
                        actions.remove(index);
                    }
                }
            });
        }
        else
        {
            dispatchConfirm(player, force, (value) ->
            {
                if (value)
                {
                    /* Remove all actions at tick */
                    record.actions.set(tick, null);
                }
            });
        }

        record.dirty = true;
    }

    private void dispatchConfirm(ServerPlayerEntity player, boolean force, Consumer<Boolean> callback)
    {
        if (force)
        {
            callback.accept(force);
        }
        else
        {
            Dispatcher.sendTo(new PacketConfirm(PacketConfirm.GUI.MCSCREEN, IKey.lang("blockbuster.commands.record.remove_modal"), (value) ->
            {
                callback.accept(value);
            }), player);
        }
    }
}
