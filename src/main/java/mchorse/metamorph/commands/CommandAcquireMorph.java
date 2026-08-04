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
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Server command {@code /acquire_morph} — Brigadier port of legacy
 * {@code mchorse.metamorph.commands.CommandAcquireMorph} (roadmap P60).
 *
 * <p>Legacy syntax {@code /acquire_morph <player> <name> [nbt…]}: sends the
 * built morph ({@code {...nbt, Name: name}}) to the target player as an acquired
 * (survival) morph via {@link MorphAPI#acquire(ServerPlayerEntity, AbstractMorph)}.
 * A missing name is a usage error (legacy required args.length &gt;= 2, expressed
 * here by the un-executable {@code /acquire_morph <player>} node); a null morph
 * (unknown factory) or a rejected acquisition raises
 * {@code metamorph.error.acquire}; NBT parse failure reuses
 * {@code metamorph.error.morph.nbt}; success is {@code metamorph.success.acquire}.</p>
 *
 * <p>Permission level 3 (legacy {@code getRequiredPermissionLevel} — "same as
 * /op"). The command-block bypass legacy granted via {@code checkPermission} is a
 * documented minor gap: 1.20.4 command blocks run at level 2 and so cannot
 * invoke this level-3 command.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/commands/CommandAcquireMorph.java
 */
public class CommandAcquireMorph
{
    /** {@code metamorph.error.acquire} — carries the morph name. */
    public static final DynamicCommandExceptionType ACQUIRE_ERROR =
        new DynamicCommandExceptionType(name -> Text.translatable("metamorph.error.acquire", name));

    public String getName()
    {
        return "acquire_morph";
    }

    public int getRequiredPermissionLevel()
    {
        return 3;
    }

    /**
     * Mount {@code /acquire_morph <player> <name> [nbt…]} into the dispatcher.
     */
    public void register(CommandDispatcher<ServerCommandSource> dispatcher)
    {
        dispatcher.register(CommandManager.literal(this.getName())
            .requires(source -> source.hasPermissionLevel(this.getRequiredPermissionLevel()))
            .then(CommandManager.argument("target", EntityArgumentType.player())
                .then(CommandManager.argument("name", StringArgumentType.word())
                    .executes(context -> this.acquire(context, ""))
                    .then(CommandManager.argument("nbt", StringArgumentType.greedyString())
                        .executes(context -> this.acquire(context, StringArgumentType.getString(context, "nbt")))))));
    }

    private int acquire(CommandContext<ServerCommandSource> context, String mergedTagArgs) throws CommandSyntaxException
    {
        ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "target");
        String name = StringArgumentType.getString(context, "name");

        NbtCompound tag = CommandMorph.parseTag(mergedTagArgs);

        tag.putString("Name", name);

        AbstractMorph morph = MorphManager.INSTANCE.morphFromNBT(tag);

        if (!MorphAPI.acquire(player, morph))
        {
            throw ACQUIRE_ERROR.create(name);
        }

        context.getSource().sendFeedback(() -> Text.translatable("metamorph.success.acquire", player.getGameProfile().getName(), name), false);

        return 1;
    }
}
