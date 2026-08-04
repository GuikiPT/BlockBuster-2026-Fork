package mchorse.blockbuster.commands.record;

import mchorse.blockbuster.commands.CommandRecord;
import mchorse.blockbuster.recording.RecordUtils;
import mchorse.blockbuster.recording.data.Record;
import mchorse.mclib.commands.utils.CommandException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Command /record tp
 *
 * This command is responsible for teleporting the player to the given
 * frame at tick in given player recording.
 */
public class SubCommandRecordTP extends SubCommandRecordBase
{
    @Override
    public String getName()
    {
        return "tp";
    }

    @Override
    public String getUsage(ServerCommandSource sender)
    {
        return "blockbuster.commands.record.tp";
    }

    @Override
    public String getSyntax()
    {
        return "{l}{6}/{r}record {8}tp{r} {7}<filename> [tick]{r}";
    }

    @Override
    public void executeCommand(MinecraftServer server, ServerCommandSource sender, String[] args) throws CommandException
    {
        String filename = args[0];
        int tick = args.length > 1 ? parseInt(args[1]) : 0;
        Record record = CommandRecord.getRecord(filename);
        ServerPlayerEntity player = getCommandSenderAsPlayer(sender);

        RecordUtils.applyFrameOnEntity(player, record, tick);
    }
}
