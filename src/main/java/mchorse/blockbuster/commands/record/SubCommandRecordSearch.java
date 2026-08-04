package mchorse.blockbuster.commands.record;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.commands.CommandRecord;
import mchorse.blockbuster.recording.actions.Action;
import mchorse.blockbuster.recording.actions.ActionRegistry;
import mchorse.blockbuster.recording.data.Record;
import mchorse.mclib.commands.utils.CommandException;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;

import java.util.List;

public class SubCommandRecordSearch extends SubCommandRecordBase
{
    @Override
    public String getName()
    {
        return "search";
    }

    @Override
    public String getUsage(ServerCommandSource sender)
    {
        return "blockbuster.commands.record.search";
    }

    @Override
    public String getSyntax()
    {
        return "{l}{6}/{r}record {8}search{r} {7}<filename> <action_type> [limit] [output_tags]{r}";
    }

    @Override
    public int getRequiredArgs()
    {
        return 2;
    }

    @Override
    public void executeCommand(MinecraftServer server, ServerCommandSource sender, String[] args) throws CommandException
    {
        if (!ActionRegistry.NAME_TO_CLASS.containsKey(args[1]))
        {
            throw new CommandException("record.wrong_action", args[1]);
        }

        String filename = args[0];
        byte type = ActionRegistry.NAME_TO_ID.get(args[1]).byteValue();
        Record record = CommandRecord.getRecord(filename);

        int i = 0;
        int tick = -1;

        int limit = record.actions.size() + 1;
        boolean outputData = args.length >= 4 ? parseBoolean(args[3]) : false;

        if (args.length >= 3)
        {
            int temp = parseInt(args[2], -1);

            if (temp >= 0)
            {
                limit = temp;
            }
        }

        Blockbuster.l10n.info(sender, "record.search_type", args[1]);

        for (List<Action> actions : record.actions)
        {
            tick++;

            if (actions == null)
            {
                continue;
            }

            if (i >= limit)
            {
                break;
            }

            int j = -1;

            for (Action action : actions)
            {
                j++;

                if (ActionRegistry.getType(action) != type)
                {
                    continue;
                }

                if (outputData)
                {
                    NbtCompound tag = new NbtCompound();
                    action.toNBT(tag);

                    Blockbuster.l10n.info(sender, "record.search_action_data", tick, j, tag.toString());
                }
                else
                {
                    Blockbuster.l10n.info(sender, "record.search_action", tick, j);
                }
            }

            i++;
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ServerCommandSource sender, String[] args)
    {
        if (args.length == 2)
        {
            return getListOfStringsMatchingLastWord(args, ActionRegistry.NAME_TO_CLASS.keySet());
        }

        return super.getTabCompletions(server, sender, args);
    }
}
