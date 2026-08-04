package mchorse.blockbuster.commands;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.commands.modelblock.SubCommandModelBlockMorph;
import mchorse.blockbuster.commands.modelblock.SubCommandModelBlockProperty;
import mchorse.mclib.commands.SubCommandBase;
import mchorse.mclib.commands.utils.L10n;
import net.minecraft.server.command.ServerCommandSource;

/**
 * Command {@code /modelblock} — port of legacy {@code commands.CommandModelBlock}
 * (roadmap P207.4). Dispatches to {@code morph} and {@code property}
 * subcommands (registration order preserved).
 *
 * <p><b>Name parity:</b> the legacy {@code getName()} returns {@code "modelblock"}
 * (not {@code "model_block"}); the legacy string wins per the S19 quirk note.</p>
 */
public class CommandModelBlock extends SubCommandBase
{
    public CommandModelBlock()
    {
        this.add(new SubCommandModelBlockMorph());
        this.add(new SubCommandModelBlockProperty());
    }

    @Override
    public L10n getL10n()
    {
        return Blockbuster.l10n;
    }

    @Override
    public String getName()
    {
        return "modelblock";
    }

    @Override
    public String getUsage(ServerCommandSource sender)
    {
        return "blockbuster.commands.modelblock.help";
    }

    @Override
    public int getRequiredPermissionLevel()
    {
        return 2;
    }
}
