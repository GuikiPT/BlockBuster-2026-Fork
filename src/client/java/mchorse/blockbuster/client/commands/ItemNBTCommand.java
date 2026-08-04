package mchorse.blockbuster.client.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import mchorse.blockbuster.Blockbuster;
import mchorse.mclib.commands.McCommandBase;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.concurrent.CompletableFuture;

/**
 * Client command {@code /item_nbt <give_command>} — port of legacy
 * {@code commands.CommandItemNBT} (roadmap P207.3).
 *
 * <p>Copies the held main-hand stack's NBT to the OS clipboard. When the single
 * boolean arg is {@code true}/{@code 1} it instead builds a full
 * <b>1.12-format</b> {@code /give @p <id> <count> <damage> <nbt>} string —
 * parity over usefulness (1.20.4 dropped the {@code <damage>}/meta give
 * grammar; the checklist documents the deliberate legacy-format retention).
 * Empty hand → {@code commands.item_nbt_empty}. Permission-free (a client
 * command, intercepted before send exactly like 1.12's
 * {@code ClientCommandHandler}); {@code getRequiredArgs() == 1}.</p>
 *
 * <p>Legacy read {@code Minecraft.getMinecraft().player.getHeldItemMainhand()};
 * the port reads {@code MinecraftClient.getInstance().player.getMainHandStack()}
 * and writes the clipboard via {@code keyboard.setClipboard(String)}.</p>
 */
public class ItemNBTCommand
{
    private static final String SYNTAX = "{l}{6}/{r}item_nbt {7}<give_command>{r}";

    public static void register()
    {
        ClientCommandRegistrationCallback.EVENT.register(ItemNBTCommand::registerCommands);
    }

    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registry)
    {
        dispatcher.register(ClientCommandManager.literal("item_nbt")
            /* No args: legacy getRequiredArgs() == 1 → usage message. */
            .executes(context ->
            {
                usage(context.getSource());

                return 1;
            })
            .then(ClientCommandManager.argument("args", StringArgumentType.greedyString())
                .suggests(ItemNBTCommand::suggestBooleans)
                .executes(context ->
                {
                    run(context.getSource(), StringArgumentType.getString(context, "args").split(" "));

                    return 1;
                })));
    }

    private static void usage(FabricClientCommandSource source)
    {
        MutableText message = Text.literal(McCommandBase.processSyntax(SYNTAX));

        message.formatted(Formatting.WHITE);
        message.append(Text.literal("\n\n"));
        message.append(Text.translatable("blockbuster.commands.item_nbt.help"));

        source.sendFeedback(message);
    }

    private static void run(FabricClientCommandSource source, String[] args)
    {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;

        if (player == null)
        {
            return;
        }

        ItemStack stack = player.getMainHandStack();

        if (stack.isEmpty())
        {
            source.sendFeedback(Blockbuster.l10n.error("commands.item_nbt_empty"));

            return;
        }

        if (!McCommandBase.BOOLEANS.contains(args[0]))
        {
            /* Legacy CommandBase.parseBoolean threw a vanilla CommandException.
             * 1.20.4 deleted the whole commands.generic.* family, so S22 P297
             * ships the four strings 1.12.2 vanilla had in
             * assets/mclib/lang/en_us.json — same key, same legacy wording,
             * same argument order. */
            source.sendFeedback(Text.translatable("commands.generic.boolean.invalid", args[0]));

            return;
        }

        boolean command = args[0].equals("true") || args[0].equals("1");

        NbtCompound stackTag = new NbtCompound();

        stack.writeNbt(stackTag);

        String nbt = stack.hasNbt() ? stack.getNbt().toString() : null;
        String output = formatOutput(command, stackTag.getString("id"), stack.getCount(), stack.getDamage(), nbt);

        MinecraftClient.getInstance().keyboard.setClipboard(output);
        source.sendFeedback(Blockbuster.l10n.success("commands.item_nbt"));
    }

    /**
     * Assemble the clipboard string exactly as legacy {@code CommandItemNBT}
     * did — pure and headless-testable.
     *
     * <p>{@code command == true} → the <b>1.12-format</b>
     * {@code /give @p <id> <count> <damage>[ <nbt>]} string (parity over 1.20.4
     * give grammar). {@code command == false} → the raw item NBT, or literal
     * {@code "{}"} when the stack has none.</p>
     */
    public static String formatOutput(boolean command, String id, int count, int damage, String nbt)
    {
        if (command)
        {
            String output = "/give @p " + id + " " + count + " " + damage;

            if (nbt != null)
            {
                output += " " + nbt;
            }

            return output;
        }

        return nbt != null ? nbt : "{}";
    }

    private static CompletableFuture<Suggestions> suggestBooleans(CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder)
    {
        /* Tab-complete only the first arg (the boolean); the greedy tail keeps
         * the remaining text as one token. */
        String remaining = builder.getRemaining();

        if (!remaining.contains(" "))
        {
            for (String option : McCommandBase.BOOLEANS)
            {
                if (option.regionMatches(true, 0, remaining, 0, remaining.length()))
                {
                    builder.suggest(option);
                }
            }
        }

        return builder.buildFuture();
    }
}
