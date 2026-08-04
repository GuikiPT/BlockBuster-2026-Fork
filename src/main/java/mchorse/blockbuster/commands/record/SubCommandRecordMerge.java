package mchorse.blockbuster.commands.record;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.commands.CommandRecord;
import mchorse.blockbuster.recording.RecordUtils;
import mchorse.blockbuster.recording.actions.Action;
import mchorse.blockbuster.recording.actions.MorphAction;
import mchorse.blockbuster.recording.data.Record;
import mchorse.mclib.commands.utils.CommandException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Command /record merge
 *
 * This command is responsible for merging the actions in the
 * source record into the destination record.
 */
public class SubCommandRecordMerge extends SubCommandRecordBase
{
    @Override
    public String getName()
    {
        return "merge";
    }

    @Override
    public String getUsage(ServerCommandSource sender)
    {
        return "blockbuster.commands.record.merge";
    }

    @Override
    public String getSyntax()
    {
        return "{l}{6}/{r}record {8}merge{r} {7}<source> <destination> [morph_only]{r}";
    }

    @Override
    public int getRequiredArgs()
    {
        return 2;
    }

    @Override
    public void executeCommand(MinecraftServer server, ServerCommandSource sender, String[] args) throws CommandException
    {
        Record src = CommandRecord.getRecord(args[0]);
        Record dst = CommandRecord.getRecord(args[1]);
        boolean morph = false;

        if (args.length > 2)
        {
            morph = parseBoolean(args[2]);
        }

        int size = Math.min(src.actions.size(), dst.actions.size());

        for (int i = 0; i < size; i++)
        {
            if (i >= dst.actions.size())
            {
                break;
            }

            List<Action> srcActions = src.actions.get(i);
            List<Action> dstActions = dst.actions.get(i);

            if (srcActions != null)
            {
                for (Action action : srcActions)
                {
                    if (!morph || action instanceof MorphAction)
                    {
                        if (dstActions == null)
                        {
                            dstActions = new ArrayList<Action>();

                            dst.actions.set(i, dstActions);
                        }

                        dstActions.add(action);
                    }
                }
            }
        }

        try
        {
            RecordUtils.saveRecord(dst);

            Blockbuster.l10n.success(sender, "record.merge", args[0], args[1]);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            Blockbuster.l10n.error(sender, "record.couldnt_save", args[1]);
        }
    }
}
