package mchorse.blockbuster.commands;

import java.util.List;

import mchorse.blockbuster.Blockbuster;
import mchorse.mclib.commands.utils.CommandException;
import mchorse.mclib.commands.utils.EntitySelectorUtils;
import net.minecraft.command.EntitySelector;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;

/**
 * Command {@code /spectate <player> <entity>} — port of legacy
 * {@code commands.CommandSpectate} (roadmap P125).
 *
 * <p>Makes {@code <player>} spectate {@code <entity>}: forces
 * {@link GameMode#SPECTATOR} if the player isn't already spectating, then
 * attaches the camera to the first matched entity that isn't the player itself
 * (the guard against self-spectation with {@code @e}-style selectors). A
 * missing player errors {@code commands.no_player}; an empty entity match
 * errors {@code commands.no_entity} (both via {@link mchorse.mclib.commands.utils.L10n#error}).</p>
 *
 * <p>1.20.4 mappings: legacy {@code CommandBase.getPlayer} →
 * {@link EntitySelector#getPlayer(ServerCommandSource)} (a bare name still
 * resolves); legacy {@code EntitySelector.matchEntities} →
 * {@link EntitySelector#getEntities(ServerCommandSource)}; legacy
 * {@code player.setGameType(GameType.SPECTATOR)} →
 * {@link ServerPlayerEntity#changeGameMode(GameMode)}; legacy
 * {@code player.setSpectatingEntity(entity)} →
 * {@link ServerPlayerEntity#setCameraEntity(Entity)}.</p>
 */
public class CommandSpectate extends BBCommandBase
{
    @Override
    public String getName()
    {
        return "spectate";
    }

    @Override
    public String getUsage(ServerCommandSource sender)
    {
        return "blockbuster.commands.spectate.help";
    }

    @Override
    public String getSyntax()
    {
        return "{l}{6}/{r}spectate {8}<player>{r} {7}<entity>{r}";
    }

    /*
     * Legacy CommandSpectate does NOT override getRequiredPermissionLevel, so
     * in 1.12.2 it inherited vanilla CommandBase's default of op-level 4 (unlike
     * top-level Blockbuster commands, which drop to 2). Inherit the level-4
     * default from BBCommandBase/McCommandBase to preserve that behavior bar.
     */

    @Override
    public int getRequiredArgs()
    {
        return 2;
    }

    @Override
    public void executeCommand(MinecraftServer server, ServerCommandSource sender, String[] args) throws CommandException
    {
        ServerPlayerEntity player = getPlayer(sender, args[0]);

        if (player == null)
        {
            Blockbuster.l10n.error(sender, "commands.no_player", args[0]);

            return;
        }

        List<? extends Entity> entities = getEntities(sender, args[1]);

        if (entities.isEmpty())
        {
            Blockbuster.l10n.error(sender, "commands.no_entity", args[1]);

            return;
        }

        if (!player.isSpectator())
        {
            player.changeGameMode(GameMode.SPECTATOR);
        }

        for (Entity entity : entities)
        {
            if (entity != player)
            {
                player.setCameraEntity(entity);

                break;
            }
        }
    }

    /**
     * Resolve a single player from a name or {@code @}-selector token (legacy
     * vanilla {@code CommandBase.getPlayer}). Returns {@code null} when nothing
     * matches so the caller can surface the {@code commands.no_player} error,
     * matching the legacy null-guard.
     *
     * <p>P273: goes through {@link EntitySelectorUtils}, which actually
     * <i>parses</i> the token ({@code EntitySelectorReader.read()}) — the
     * earlier {@code .build()} call skipped parsing entirely and resolved
     * every token to the first online player.</p>
     */
    private static ServerPlayerEntity getPlayer(ServerCommandSource sender, String target)
    {
        return EntitySelectorUtils.getPlayer(sender, target);
    }

    /**
     * Resolve all entities matching a token (legacy
     * {@code EntitySelector.matchEntities}). Returns an empty list on a
     * parse/no-match failure so the caller surfaces {@code commands.no_entity}.
     */
    private static List<? extends Entity> getEntities(ServerCommandSource sender, String target)
    {
        return EntitySelectorUtils.getEntities(sender, target);
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ServerCommandSource sender, String[] args)
    {
        if (args.length == 1)
        {
            return getListOfStringsMatchingLastWord(args, server.getPlayerNames());
        }

        return super.getTabCompletions(server, sender, args);
    }
}
