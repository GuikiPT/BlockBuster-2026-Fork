package mchorse.mclib.commands;

import mchorse.mclib.McLib;
import mchorse.mclib.commands.config.SubCommandConfig;
import mchorse.mclib.commands.utils.L10n;
import net.minecraft.server.command.ServerCommandSource;

/**
 * Command {@code /mclib} — port of McLib 2.4.3's
 * {@code mchorse.mclib.commands.CommandMcLib} (roadmap P207.5).
 *
 * <p>{@code /mclib config print|set <mod.category.option> [value...]}. The
 * command does <b>not</b> override the vanilla {@code CommandBase} permission
 * level, so it stays at <b>4</b> (legacy behavior — unlike Metamorph's
 * {@code /metamorph}, which drops to 3). Its subcommands use the bundled L10n
 * markers, unlike Metamorph's vanilla-styled commands: mixed styling is
 * legacy-accurate.</p>
 *
 * Legacy source: .tools/legacy-src/mclib/src/main/java/mchorse/mclib/commands/CommandMcLib.java
 */
public class CommandMcLib extends SubCommandBase
{
    public CommandMcLib()
    {
        this.add(new SubCommandConfig());
    }

    @Override
    public String getName()
    {
        return "mclib";
    }

    @Override
    public String getUsage(ServerCommandSource sender)
    {
        return "mclib.commands.mclib.help";
    }

    @Override
    public String getSyntax()
    {
        return "";
    }

    @Override
    public L10n getL10n()
    {
        return McLib.l10n;
    }
}
