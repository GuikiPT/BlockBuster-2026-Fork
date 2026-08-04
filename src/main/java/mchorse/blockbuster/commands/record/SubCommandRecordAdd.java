package mchorse.blockbuster.commands.record;

import mchorse.blockbuster.commands.CommandRecord;
import mchorse.blockbuster.recording.actions.Action;
import mchorse.blockbuster.recording.actions.ActionRegistry;
import mchorse.blockbuster.recording.data.Record;
import mchorse.mclib.commands.SubCommandBase;
import mchorse.mclib.commands.utils.CommandException;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;

import java.util.List;

/**
 * Command /record add
 *
 * This command is responsible for adding a desired action to the given player
 * recording.
 */
public class SubCommandRecordAdd extends SubCommandRecordBase
{
    @Override
    public String getName()
    {
        return "add";
    }

    @Override
    public String getUsage(ServerCommandSource sender)
    {
        return "blockbuster.commands.record.add";
    }

    @Override
    public String getSyntax()
    {
        return "{l}{6}/{r}record {8}add{r} {7}<filename> <tick> <action_type> [data_tag]{r}";
    }

    @Override
    public int getRequiredArgs()
    {
        return 3;
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

        if (!ActionRegistry.NAME_TO_CLASS.containsKey(args[2]))
        {
            throw new CommandException("record.wrong_action", args[2]);
        }

        try
        {
            Action action = ActionRegistry.fromName(args[2]);

            if (args.length > 3)
            {
                /* legacy CommandMorph.mergeArgs(args, 3) */
                action.fromNBT(StringNbtReader.parse(String.join(" ", SubCommandBase.dropFirstArguments(args, 3))));
            }

            record.addAction(tick, action);
            record.dirty = true;
        }
        catch (Exception e)
        {
            throw new CommandException("record.add", args[2], e.getMessage());
        }
    }

    /**
     * Tab complete action
     */
    @Override
    public List<String> getTabCompletions(MinecraftServer server, ServerCommandSource sender, String[] args)
    {
        if (args.length == 3)
        {
            return getListOfStringsMatchingLastWord(args, ActionRegistry.NAME_TO_ID.keySet());
        }

        return super.getTabCompletions(server, sender, args);
    }
}
