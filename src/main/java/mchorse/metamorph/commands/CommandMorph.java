package mchorse.metamorph.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;

import mchorse.metamorph.api.MorphAPI;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Server command {@code /morph} — Brigadier port of legacy
 * {@code mchorse.metamorph.commands.CommandMorph} (roadmap P60).
 *
 * <p>Legacy syntax {@code /morph <player> [name] [nbt…]}: no name demorphs the
 * target ({@code metamorph.success.demorph}); a name (plus the optional
 * space-joined NBT tail) morphs the target into
 * {@code MorphManager.morphFromNBT({...nbt, Name: name})} with force
 * ({@code metamorph.success.morph}). The stored translation keys are preserved
 * exactly:</p>
 *
 * <ul>
 *   <li>{@code metamorph.error.morph.nbt} — NBT parse failure (message tail).</li>
 *   <li>{@code metamorph.error.morph.factory} — no factory produced a morph for
 *       the given name.</li>
 *   <li>{@code metamorph.success.demorph} / {@code metamorph.success.morph}.</li>
 * </ul>
 *
 * <p>Permission level 2 (legacy {@code getRequiredPermissionLevel}; command
 * blocks run at level 2 on 1.20.4, so the legacy command-block bypass is
 * satisfied by construction). The greedy NBT tail preserves legacy tolerance of
 * spaces inside the SNBT (legacy {@code mergeArgs} re-joined the arg array);
 * parse failure is a command error, never a crash. Ported Brigadier-directly
 * (legacy extended vanilla {@code CommandBase}), matching {@code CommandAperture}.</p>
 *
 * <p>Non-parity note: legacy echoed the raw {@code args[0]} target token in the
 * feedback/error messages; here the resolved player's name is used (the selector
 * has already been resolved by {@link EntityArgumentType}).</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/commands/CommandMorph.java
 */
public class CommandMorph
{
    /** {@code metamorph.error.morph.nbt} — carries the parser message tail. */
    public static final DynamicCommandExceptionType NBT_ERROR =
        new DynamicCommandExceptionType(message -> Text.translatable("metamorph.error.morph.nbt", message));

    /** {@code metamorph.error.morph.factory} — carries the morph name. */
    public static final DynamicCommandExceptionType FACTORY_ERROR =
        new DynamicCommandExceptionType(name -> Text.translatable("metamorph.error.morph.factory", name));

    public String getName()
    {
        return "morph";
    }

    public int getRequiredPermissionLevel()
    {
        return 2;
    }

    /**
     * Mount {@code /morph <player> [name] [nbt…]} into the dispatcher.
     */
    public void register(CommandDispatcher<ServerCommandSource> dispatcher)
    {
        dispatcher.register(CommandManager.literal(this.getName())
            .requires(source -> source.hasPermissionLevel(this.getRequiredPermissionLevel()))
            .then(CommandManager.argument("target", EntityArgumentType.player())
                .executes(this::demorph)
                .then(CommandManager.argument("name", StringArgumentType.word())
                    .executes(context -> this.morph(context, ""))
                    .then(CommandManager.argument("nbt", StringArgumentType.greedyString())
                        .executes(context -> this.morph(context, StringArgumentType.getString(context, "nbt")))))));
    }

    /**
     * {@code /morph <player>} — demorph the target (legacy args.length &lt; 2).
     */
    private int demorph(CommandContext<ServerCommandSource> context) throws CommandSyntaxException
    {
        ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "target");

        MorphAPI.demorph(player);

        String name = player.getGameProfile().getName();

        context.getSource().sendFeedback(() -> Text.translatable("metamorph.success.demorph", name), false);

        return 1;
    }

    /**
     * {@code /morph <player> <name> [nbt…]} — morph the target with force
     * (legacy args.length &gt;= 2).
     */
    private int morph(CommandContext<ServerCommandSource> context, String mergedTagArgs) throws CommandSyntaxException
    {
        ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "target");
        String name = StringArgumentType.getString(context, "name");

        NbtCompound tag = parseTag(mergedTagArgs);

        tag.putString("Name", name);

        AbstractMorph newMorph = MorphManager.INSTANCE.morphFromNBT(tag);

        if (newMorph == null)
        {
            throw FACTORY_ERROR.create(name);
        }

        MorphAPI.morph(player, newMorph, true);

        context.getSource().sendFeedback(() -> Text.translatable("metamorph.success.morph", player.getGameProfile().getName(), name), false);

        return 1;
    }

    /**
     * Parse the space-joined SNBT tail (legacy {@code JsonToNBT.getTagFromJson}
     * over {@code mergeArgs}); empty tail → fresh compound; parse failure →
     * {@link #NBT_ERROR} (legacy {@code metamorph.error.morph.nbt}).
     */
    static NbtCompound parseTag(String mergedTagArgs) throws CommandSyntaxException
    {
        if (mergedTagArgs.isEmpty())
        {
            return new NbtCompound();
        }

        try
        {
            return StringNbtReader.parse(mergedTagArgs);
        }
        catch (Exception e)
        {
            throw NBT_ERROR.create(e.getMessage());
        }
    }
}
