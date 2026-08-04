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

/**
 * Command /record get
 *
 * This command is responsible for outputting data of action at given tick and
 * player recording.
 *
 * Quirk (kept): the lower bound check is {@code tick <= 0} (unlike
 * {@code add}'s {@code tick < 0}) — tick 0 is unreachable here.
 */
public class SubCommandRecordGet extends SubCommandRecordBase
{
    @Override
    public String getName()
    {
        return "get";
    }

    @Override
    public String getUsage(ServerCommandSource sender)
    {
        return "blockbuster.commands.record.get";
    }

    @Override
    public String getSyntax()
    {
        return "{l}{6}/{r}record {8}get{r} {7}<filename> <tick>{r}";
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

        if (tick <= 0 || tick >= record.actions.size())
        {
            throw new CommandException("record.tick_out_range", tick, record.actions.size() - 1);
        }

        List<Action> actions = record.actions.get(tick);

        if (actions == null)
        {
            throw new CommandException("record.no_action", filename, tick);
        }

        for (int i = 0, c = actions.size(); i < c; i++)
        {
            Action action = actions.get(i);
            NbtCompound tag = new NbtCompound();
            String type = ActionRegistry.NAME_TO_CLASS.inverse().get(action.getClass());
            action.toNBT(tag);

            Blockbuster.l10n.info(sender, "record.action", tick, type, i, tag.toString());
        }
    }
}
