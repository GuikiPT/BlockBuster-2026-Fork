package mchorse.blockbuster.commands.record;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.commands.CommandRecord;
import mchorse.blockbuster.recording.data.Record;
import mchorse.mclib.commands.utils.CommandException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;

/**
 * Command /record info
 *
 * This command is responsible for outputting information about given record.
 */
public class SubCommandRecordInfo extends SubCommandRecordBase
{
    @Override
    public String getName()
    {
        return "info";
    }

    @Override
    public String getUsage(ServerCommandSource sender)
    {
        return "blockbuster.commands.record.info";
    }

    @Override
    public String getSyntax()
    {
        return "{l}{6}/{r}record {8}info{r} {7}<filename>{r}";
    }

    @Override
    public void executeCommand(MinecraftServer server, ServerCommandSource sender, String[] args) throws CommandException
    {
        String filename = args[0];
        Record record = CommandRecord.getRecord(filename);

        Blockbuster.l10n.info(sender, "record.info", args[0], record.version, record.frames.size(), record.unload);
    }
}
