package mchorse.blockbuster.commands;

import java.util.List;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.recording.scene.Scene;
import mchorse.mclib.commands.utils.CommandException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;

/**
 * Command {@code /scene} (roadmap P132).
 *
 * <p>Responsible for playing, stopping or toggling scene playback, and toggling
 * a scene's looping.</p>
 *
 * <p>Legacy source:
 * {@code blockbuster-1.12/src/main/java/mchorse/blockbuster/commands/CommandScene.java}.
 * Single command (not sub-command based): {@code args[0]} is the action,
 * {@code args[1]} the scene name, {@code args[2]} the loop flag. Permission
 * level 2, two required args.</p>
 *
 * <p><b>Documented deviations:</b></p>
 * <ul>
 *   <li>Legacy {@code /scene loop <name>} without a flag read {@code args[2]}
 *       and threw {@code ArrayIndexOutOfBoundsException} (surfaced as a command
 *       error). Here the guard {@code args.length > 2} is checked first, so the
 *       branch is simply skipped — the crash is never reproduced (ground rule).</li>
 *   <li>Legacy had no {@code <name>} tab completion; only the four subcommands
 *       are suggested (first arg), matching legacy.</li>
 * </ul>
 */
public class CommandScene extends BBCommandBase
{
    @Override
    public String getName()
    {
        return "scene";
    }

    @Override
    public String getUsage(ServerCommandSource sender)
    {
        return "blockbuster.commands.scene";
    }

    @Override
    public String getSyntax()
    {
        return "{l}{6}/{r}scene {8}<play|toggle|stop|loop>{r} {7}<name> [flag]{r}";
    }

    @Override
    public int getRequiredPermissionLevel()
    {
        return 2;
    }

    @Override
    public int getRequiredArgs()
    {
        return 2;
    }

    @Override
    public void executeCommand(MinecraftServer server, ServerCommandSource sender, String[] args) throws CommandException
    {
        String action = args[0];
        String name = args[1];
        Scene scene = CommonProxy.scenes.get(name, sender.getWorld());

        if (scene == null)
        {
            Blockbuster.l10n.error(sender, "scene.no_scene", name);

            return;
        }

        String play = "scene.play";
        String stop = "scene.stop";

        if (action.equals("play"))
        {
            if (scene.playing)
            {
                Blockbuster.l10n.error(sender, "scene.playing", name);

                return;
            }

            scene.startPlayback(0);
            Blockbuster.l10n.success(sender, play, name);
        }
        else if (action.equals("stop"))
        {
            if (!scene.playing)
            {
                Blockbuster.l10n.error(sender, "scene.stopped", name);

                return;
            }

            scene.stopPlayback(true);
            Blockbuster.l10n.success(sender, stop, name);
        }
        else if (action.equals("loop") && args.length > 2)
        {
            scene.loops = parseBoolean(args[2]);

            try
            {
                CommonProxy.scenes.save(scene.getId(), scene);

                Blockbuster.l10n.info(sender, "scene." + (scene.loops ? "looped" : "unlooped"));
            }
            catch (Exception e)
            {}
        }
        else if (action.equals("toggle"))
        {
            boolean isPlaying = scene.togglePlayback();

            Blockbuster.l10n.success(sender, isPlaying ? play : stop, name);
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ServerCommandSource sender, String[] args)
    {
        if (args.length == 1)
        {
            return getListOfStringsMatchingLastWord(args, "play", "stop", "toggle", "loop");
        }

        return super.getTabCompletions(server, sender, args);
    }
}
