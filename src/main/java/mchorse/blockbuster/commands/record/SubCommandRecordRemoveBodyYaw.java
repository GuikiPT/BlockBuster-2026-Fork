package mchorse.blockbuster.commands.record;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.commands.CommandRecord;
import mchorse.blockbuster.recording.RecordUtils;
import mchorse.blockbuster.recording.data.Frame;
import mchorse.blockbuster.recording.data.Record;
import mchorse.mclib.commands.utils.CommandException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;

public class SubCommandRecordRemoveBodyYaw extends SubCommandRecordBase
{
    @Override
    public String getName()
    {
        return "remove_body_yaw";
    }

    @Override
    public String getUsage(ServerCommandSource sender)
    {
        return "blockbuster.commands.record.remove_body_yaw";
    }

    @Override
    public String getSyntax()
    {
        return "{l}{6}/{r}record {8}remove_body_yaw{r} {7}<filename>{r}";
    }

    @Override
    public void executeCommand(MinecraftServer server, ServerCommandSource sender, String[] args) throws CommandException
    {
        String filename = args[0];
        Record record = CommandRecord.getRecord(filename);

        for (int i = 0, c = record.frames.size(); i < c; i++)
        {
            Frame frame = record.frames.get(i);

            frame.hasBodyYaw = false;
            frame.bodyYaw = 0F;
        }

        RecordUtils.dirtyRecord(record);

        Blockbuster.l10n.success(sender, "record.remove_body_yaw", filename);
    }
}
