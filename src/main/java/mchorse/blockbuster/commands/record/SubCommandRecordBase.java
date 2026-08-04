package mchorse.blockbuster.commands.record;

import mchorse.blockbuster.commands.BBCommandBase;
import mchorse.blockbuster.recording.RecordUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;

import java.util.List;

public abstract class SubCommandRecordBase extends BBCommandBase
{
    @Override
    public int getRequiredArgs()
    {
        return 1;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ServerCommandSource sender, String[] args)
    {
        if (args.length == 1)
        {
            return getListOfStringsMatchingLastWord(args, RecordUtils.getReplays());
        }

        return super.getTabCompletions(server, sender, args);
    }
}
