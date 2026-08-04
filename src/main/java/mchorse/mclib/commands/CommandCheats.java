package mchorse.mclib.commands;

import mchorse.blockbuster.mixin.LevelPropertiesAccessor;
import mchorse.mclib.McLib;
import mchorse.mclib.commands.utils.CommandException;
import mchorse.mclib.commands.utils.L10n;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.world.SaveProperties;
import net.minecraft.world.level.LevelInfo;

import java.util.List;

/**
 * Command {@code /cheats} — port of McLib 2.4.3's
 * {@code mchorse.mclib.commands.CommandCheats} (roadmap P207.5).
 *
 * <p>{@code /cheats <enabled:true|false>} toggles cheat commands in a
 * singleplayer world (publishing an adventure map / re-enabling commands in a
 * downloaded world for a video). Two legacy properties are load-bearing:</p>
 *
 * <ul>
 * <li>it is <b>permission-free</b> ({@code checkPermission} returns
 * {@code true} — level 0 here), and</li>
 * <li>the actual gate is <b>registration</b>: legacy {@code McLib.serverInit}
 * registered it only when {@code server.isSinglePlayer()}, which is
 * {@code CommandManager.RegistrationEnvironment.INTEGRATED} on 1.20.4.</li>
 * </ul>
 *
 * <p>Port note: legacy called {@code sender.getEntityWorld().getWorldInfo()
 * .setAllowCommands(...)}. 1.20.4's {@code LevelInfo} is immutable, so the
 * flag is flipped by swapping a copied {@code LevelInfo} into the save through
 * {@code LevelPropertiesAccessor} (see that mixin). Like legacy, no
 * command-tree/op resync is broadcast — the change takes hold the way it did
 * in 1.12.2.</p>
 *
 * Legacy source: .tools/legacy-src/mclib/src/main/java/mchorse/mclib/commands/CommandCheats.java
 */
public class CommandCheats extends McCommandBase
{
    @Override
    public L10n getL10n()
    {
        return McLib.l10n;
    }

    @Override
    public String getName()
    {
        return "cheats";
    }

    @Override
    public String getUsage(ServerCommandSource sender)
    {
        return "mclib.commands.cheats";
    }

    @Override
    public String getSyntax()
    {
        return "{l}{6}/{r}cheats {7}<enabled:true|false>{r}";
    }

    /**
     * Legacy {@code checkPermission} returns true — anyone can run it (the
     * command only exists in singleplayer).
     */
    @Override
    public int getRequiredPermissionLevel()
    {
        return 0;
    }

    @Override
    public int getRequiredArgs()
    {
        return 1;
    }

    @Override
    public void executeCommand(MinecraftServer server, ServerCommandSource sender, String[] args) throws CommandException
    {
        this.setAllowCommands(server, parseBoolean(args[0]));

        this.saveAllWorlds(server);
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ServerCommandSource sender, String[] args)
    {
        if (args.length == 1)
        {
            return getListOfStringsMatchingLastWord(args, McCommandBase.BOOLEANS);
        }

        return super.getTabCompletions(server, sender, args);
    }

    /* Seams over the save properties (headless-testable) */

    /**
     * Legacy {@code getWorldInfo().setAllowCommands(enabled)}.
     */
    protected void setAllowCommands(MinecraftServer server, boolean enabled)
    {
        SaveProperties properties = server == null ? null : server.getSaveProperties();

        if (properties instanceof LevelPropertiesAccessor accessor)
        {
            accessor.mclib$setLevelInfo(withCommandsAllowed(properties.getLevelInfo(), enabled));
        }
    }

    /**
     * Legacy {@code server.saveAllWorlds(false)} (not silent).
     */
    protected void saveAllWorlds(MinecraftServer server)
    {
        if (server != null)
        {
            server.saveAll(false, true, false);
        }
    }

    /**
     * Copy of {@code info} with the cheat flag flipped (1.20.4's
     * {@code LevelInfo} is immutable — pure, so the copy is unit-testable).
     */
    public static LevelInfo withCommandsAllowed(LevelInfo info, boolean enabled)
    {
        return new LevelInfo(
            info.getLevelName(),
            info.getGameMode(),
            info.isHardcore(),
            info.getDifficulty(),
            enabled,
            info.getGameRules(),
            info.getDataConfiguration());
    }
}
