package mchorse.blockbuster.commands;

import mchorse.mclib.commands.utils.CommandException;
import mchorse.mclib.commands.utils.EntitySelectorUtils;
import net.minecraft.command.EntitySelector;
import net.minecraft.command.EntitySelectorReader;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;

/**
 * Command {@code /mount <target> [destination]} — port of legacy
 * {@code commands.CommandMount} (roadmap P125).
 *
 * <p>With a {@code [destination]} the {@code <target>} entity starts riding it
 * ({@code target.startRiding(dest)}); without a destination the command is the
 * <b>dismount</b> form ({@code target.dismountRidingEntity()}) — hence one
 * required arg, not two. Both selectors are expected to resolve a single
 * entity (the legacy help text warns against ambiguous selectors); this ports
 * against the P119 {@code MountingAction} playback where actors are ridable but
 * not steerable (a passenger without control).</p>
 *
 * <p>1.20.4 mappings: legacy {@code CommandBase.getEntity} →
 * {@link EntitySelectorReader} + {@link EntitySelector#getEntity(ServerCommandSource)};
 * legacy {@code target.startRiding(dest)} → {@link Entity#startRiding(Entity)};
 * legacy {@code target.dismountRidingEntity()} → {@link Entity#stopRiding()}.</p>
 */
public class CommandMount extends BBCommandBase
{
    @Override
    public String getName()
    {
        return "mount";
    }

    @Override
    public String getUsage(ServerCommandSource sender)
    {
        return "blockbuster.commands.mount.help";
    }

    @Override
    public String getSyntax()
    {
        return "{l}{6}/{r}mount {8}<target>{r} {7}[destination]{r}";
    }

    /*
     * Legacy CommandMount does NOT override getRequiredPermissionLevel, so in
     * 1.12.2 it inherited vanilla CommandBase's default of op-level 4 (unlike
     * top-level Blockbuster commands, which drop to 2). Inherit the level-4
     * default from BBCommandBase/McCommandBase to preserve that behavior bar.
     */

    @Override
    public int getRequiredArgs()
    {
        return 1;
    }

    @Override
    public void executeCommand(MinecraftServer server, ServerCommandSource sender, String[] args) throws CommandException
    {
        Entity target = getEntity(sender, args[0]);

        if (args.length > 1)
        {
            target.startRiding(getEntity(sender, args[1]));
        }
        else
        {
            target.stopRiding();
        }
    }

    /**
     * Resolve a single entity from a selector token (legacy vanilla
     * {@code CommandBase.getEntity}). A parse/no-match failure surfaces as the
     * vanilla "no entity found" error, matching the legacy
     * {@code EntityNotFoundException}.
     *
     * <p>P273: goes through {@link EntitySelectorUtils}, which actually
     * <i>parses</i> the token ({@code EntitySelectorReader.read()}) — the
     * earlier {@code .build()} call skipped parsing entirely and resolved
     * every token to the first online player.</p>
     */
    private static Entity getEntity(ServerCommandSource sender, String target) throws CommandException
    {
        Entity entity = EntitySelectorUtils.getEntity(sender, target);

        if (entity == null)
        {
            throw new CommandException("argument.entity.notfound.entity");
        }

        return entity;
    }
}
