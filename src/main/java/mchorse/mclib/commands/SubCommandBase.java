package mchorse.mclib.commands;

import mchorse.mclib.commands.utils.CommandException;
import mchorse.mclib.commands.utils.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Abstract sub-command base handler command — port of McLib 2.4.3's
 * {@code mchorse.mclib.commands.SubCommandBase} (roadmap P120). Dispatches
 * {@code args[0]} against a {@link LinkedHashMap} of subcommands, exactly
 * like legacy (registration order shows in the usage listing).
 */
public abstract class SubCommandBase extends McCommandBase
{
    /**
     * Sub-commands list, add your sub commands in this list.
     */
    protected Map<String, McCommandBase> subcommands = new LinkedHashMap<String, McCommandBase>();

    /**
     * Drop only the first argument
     */
    public static String[] dropFirstArgument(String[] input)
    {
        return dropFirstArguments(input, 1);
    }

    /**
     * Totally not copied from CommandHandler.
     */
    public static String[] dropFirstArguments(String[] input, int amount)
    {
        String[] astring = new String[input.length - amount];
        System.arraycopy(input, amount, astring, 0, input.length - amount);

        return astring;
    }

    /**
     * Add a sub-command to the sub-commands map
     */
    protected void add(McCommandBase subcommand)
    {
        this.subcommands.put(subcommand.getName(), subcommand);
    }

    @Override
    public String getSyntax()
    {
        return "";
    }

    @Override
    public Text getUsageMessage(ServerCommandSource sender)
    {
        MutableText message = Text.translatable(this.getUsage(sender));

        message.formatted(Formatting.WHITE);
        message.append(Text.literal("\n\n"));

        int i = 0;
        int c = this.subcommands.size();

        for (McCommandBase command : this.subcommands.values())
        {
            String extra = i == c - 1 ? "" : "\n";

            message.append(Text.literal(command.getProcessedSyntax() + extra));

            i += 1;
        }

        return message;
    }

    /**
     * Execute the command
     *
     * This method basically delegates the execution to the matched
     * sub-command, if the command was found, otherwise it shows usage
     * message.
     */
    @Override
    public void execute(MinecraftServer server, ServerCommandSource sender, String[] args) throws CommandException
    {
        if (args.length < 1)
        {
            throw new WrongUsageException(this.getUsageMessage(sender));
        }

        McCommandBase command = this.subcommands.get(args[0]);

        if (command != null)
        {
            if (args.length >= 2 && args[1].equals("-h"))
            {
                throw new WrongUsageException(command.getUsageMessage(sender));
            }

            command.execute(server, sender, dropFirstArgument(args));
        }
        else
        {
            throw new WrongUsageException(this.getUsageMessage(sender));
        }
    }

    @Override
    public void executeCommand(MinecraftServer server, ServerCommandSource sender, String[] args) throws CommandException
    {}

    /**
     * Get completions for this command or its sub-commands.
     */
    @Override
    public List<String> getTabCompletions(MinecraftServer server, ServerCommandSource sender, String[] args)
    {
        if (args.length == 0)
        {
            return Collections.emptyList();
        }

        Collection<McCommandBase> commands = this.subcommands.values();

        if (args.length == 1)
        {
            List<String> options = new ArrayList<String>();

            for (McCommandBase command : commands)
            {
                options.add(command.getName());
            }

            return getListOfStringsMatchingLastWord(args, options);
        }

        McCommandBase command = this.subcommands.get(args[0]);

        if (command != null)
        {
            return command.getTabCompletions(server, sender, dropFirstArgument(args));
        }

        return Collections.emptyList();
    }
}
