package mchorse.mclib.commands;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import mchorse.mclib.commands.utils.CommandException;
import mchorse.mclib.commands.utils.L10n;
import mchorse.mclib.commands.utils.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * McHorse's base command class — port of McLib 2.4.3's
 * {@code mchorse.mclib.commands.McCommandBase} (roadmap P120).
 *
 * <p>1.12.2 commands were string-array based ({@code ICommand}); Brigadier is
 * bridged through exactly one seam: {@link #register(CommandDispatcher)}
 * mounts the command as {@code literal(name) + greedyString("args")} and
 * splits the raw string on single spaces, so every legacy parsing quirk
 * survives — {@code 1/0} booleans, {@code ~}-relative coordinates, morph NBT
 * JSON with interior spaces, loose optional args. Subcommand bodies port 1:1
 * against the legacy vanilla {@code CommandBase} static helpers bundled
 * below.</p>
 *
 * <p>Like the legacy class, {@link #execute} catches {@link CommandException}
 * and renders mod keys through {@link L10n#error} via the
 * {@code mclib.commands.wrapper} ("%s") lang key; {@code -h} and
 * missing-required-args show the colored syntax + usage message.</p>
 */
public abstract class McCommandBase
{
    public static final List<String> BOOLEANS = ImmutableList.of("true", "false", "1", "0");

    public static String processSyntax(String str)
    {
        return str.replaceAll("\\{([\\w\\d_]+)\\}", "§$1");
    }

    public abstract L10n getL10n();

    public abstract String getName();

    /**
     * Usage translation key (legacy {@code getUsage(ICommandSender)}).
     */
    public abstract String getUsage(ServerCommandSource sender);

    public abstract String getSyntax();

    public String getProcessedSyntax()
    {
        return processSyntax(this.getSyntax());
    }

    public Text getUsageMessage(ServerCommandSource sender)
    {
        MutableText message = Text.literal(this.getProcessedSyntax());

        message.formatted(Formatting.WHITE);

        return message
            .append(Text.literal("\n\n"))
            .append(Text.translatable(this.getUsage(sender)));
    }

    /**
     * Get the count of arguments which are required
     */
    public int getRequiredArgs()
    {
        return 0;
    }

    /**
     * Legacy vanilla {@code CommandBase} default (4); top-level Blockbuster
     * commands override to 2.
     */
    public int getRequiredPermissionLevel()
    {
        return 4;
    }

    public void execute(MinecraftServer server, ServerCommandSource sender, String[] args) throws CommandException
    {
        if (args.length >= 1 && args[0].equals("-h"))
        {
            throw new WrongUsageException(this.getUsageMessage(sender));
        }

        if (args.length < this.getRequiredArgs())
        {
            throw new WrongUsageException(this.getUsageMessage(sender));
        }

        this.executeCommand(server, sender, args);
    }

    /**
     * Execute the command's task
     */
    public abstract void executeCommand(MinecraftServer server, ServerCommandSource sender, String[] args) throws CommandException;

    public List<String> getTabCompletions(MinecraftServer server, ServerCommandSource sender, String[] args)
    {
        return Collections.emptyList();
    }

    /* Brigadier bridge */

    /**
     * Mount this command into the Brigadier dispatcher (the single seam
     * between the legacy string-array command surface and 1.20.4).
     */
    public void register(CommandDispatcher<ServerCommandSource> dispatcher)
    {
        dispatcher.register(CommandManager.literal(this.getName())
            .requires(source -> source.hasPermissionLevel(this.getRequiredPermissionLevel()))
            .executes(context -> this.run(context.getSource(), new String[0]))
            .then(CommandManager.argument("args", StringArgumentType.greedyString())
                .suggests(this::suggest)
                .executes(context -> this.run(context.getSource(), splitArgs(StringArgumentType.getString(context, "args"))))));
    }

    /**
     * Legacy CommandHandler split: single spaces, interior empty tokens kept
     * (they re-join into multi-space NBT), trailing empties dropped.
     */
    public static String[] splitArgs(String input)
    {
        return input.split(" ");
    }

    private int run(ServerCommandSource source, String[] args)
    {
        try
        {
            this.execute(source.getServer(), source, args);

            return 1;
        }
        catch (WrongUsageException e)
        {
            source.sendError(Text.translatable("mclib.commands.wrapper", e.getComponent()));
        }
        catch (CommandException e)
        {
            if (e.isVanilla())
            {
                source.sendError(Text.translatable(e.getMessage(), e.getErrorObjects()));
            }
            else
            {
                source.sendError(Text.translatable("mclib.commands.wrapper", this.getL10n().error(e.getMessage(), e.getErrorObjects())));
            }
        }

        return 0;
    }

    private CompletableFuture<Suggestions> suggest(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder)
    {
        String remaining = builder.getRemaining();
        /* -1 keeps the trailing "" that represents the token being typed */
        String[] args = remaining.isEmpty() ? new String[] {""} : remaining.split(" ", -1);

        List<String> completions = this.getTabCompletions(context.getSource().getServer(), context.getSource(), args);

        SuggestionsBuilder offset = builder.createOffset(builder.getStart() + remaining.lastIndexOf(' ') + 1);

        for (String completion : completions)
        {
            offset.suggest(completion);
        }

        return offset.buildFuture();
    }

    /* Legacy vanilla CommandBase static helpers (1.12.2 semantics) */

    public static boolean parseBoolean(String input) throws CommandException
    {
        if (input.equals("true") || input.equals("1"))
        {
            return true;
        }
        else if (input.equals("false") || input.equals("0"))
        {
            return false;
        }

        throw new CommandException("commands.generic.boolean.invalid", input);
    }

    public static int parseInt(String input) throws CommandException
    {
        try
        {
            return Integer.parseInt(input);
        }
        catch (NumberFormatException e)
        {
            throw new CommandException("commands.generic.num.invalid", input);
        }
    }

    public static int parseInt(String input, int min) throws CommandException
    {
        return parseInt(input, min, Integer.MAX_VALUE);
    }

    public static int parseInt(String input, int min, int max) throws CommandException
    {
        int i = parseInt(input);

        if (i < min)
        {
            throw new CommandException("commands.generic.num.tooSmall", i, min);
        }
        else if (i > max)
        {
            throw new CommandException("commands.generic.num.tooBig", i, max);
        }

        return i;
    }

    public static double parseDouble(String input) throws CommandException
    {
        try
        {
            double d = Double.parseDouble(input);

            if (!Double.isFinite(d))
            {
                throw new CommandException("commands.generic.num.invalid", input);
            }

            return d;
        }
        catch (NumberFormatException e)
        {
            throw new CommandException("commands.generic.num.invalid", input);
        }
    }

    /**
     * Vanilla 1.12.2 coordinate parsing: {@code ~} prefixes are relative to
     * {@code base}; {@code centerBlock} adds 0.5 to whole non-relative
     * numbers; result bounds ±30,000,000.
     */
    public static double parseDouble(double base, String input, boolean centerBlock) throws CommandException
    {
        return parseDouble(base, input, -30000000, 30000000, centerBlock);
    }

    public static double parseDouble(double base, String input, int min, int max, boolean centerBlock) throws CommandException
    {
        boolean relative = input.startsWith("~");
        double d = relative ? base : 0.0D;

        if (!relative || input.length() > 1)
        {
            boolean hasPoint = input.contains(".");

            if (relative)
            {
                input = input.substring(1);
            }

            d += parseDouble(input);

            if (centerBlock && !hasPoint && !relative)
            {
                d += 0.5D;
            }
        }

        if (d < min)
        {
            throw new CommandException("commands.generic.double.tooSmall", d, min);
        }
        else if (d > max)
        {
            throw new CommandException("commands.generic.double.tooBig", d, max);
        }

        return d;
    }

    public static ServerPlayerEntity getCommandSenderAsPlayer(ServerCommandSource sender) throws CommandException
    {
        if (sender.getEntity() instanceof ServerPlayerEntity player)
        {
            return player;
        }

        throw new CommandException("permissions.requires.player");
    }

    /**
     * Legacy {@code getListOfStringsMatchingLastWord} (case-insensitive
     * prefix filter against the token being typed).
     */
    public static List<String> getListOfStringsMatchingLastWord(String[] args, Collection<?> possibleCompletions)
    {
        String last = args[args.length - 1];
        List<String> list = new ArrayList<String>();

        for (Object candidate : possibleCompletions)
        {
            String s = String.valueOf(candidate);

            if (s.regionMatches(true, 0, last, 0, last.length()))
            {
                list.add(s);
            }
        }

        return list;
    }

    public static List<String> getListOfStringsMatchingLastWord(String[] args, String... possibleCompletions)
    {
        return getListOfStringsMatchingLastWord(args, Arrays.asList(possibleCompletions));
    }
}
