package mchorse.mclib.commands.config;

import mchorse.mclib.commands.utils.CommandException;
import mchorse.mclib.config.values.Value;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;

/**
 * Port of McLib 2.4.3's {@code commands/config/SubCommandConfigPrint}
 * (roadmap P207.5), verbatim.
 *
 * <p>Both branches are <b>info</b> messages (blue {@code (i)} marker):
 * a client-side value is not an error, it just can't be read server-side.</p>
 *
 * Legacy source: .tools/legacy-src/mclib/src/main/java/mchorse/mclib/commands/config/SubCommandConfigPrint.java
 */
public class SubCommandConfigPrint extends SubCommandConfigBase
{
    @Override
    public String getName()
    {
        return "print";
    }

    @Override
    public String getUsage(ServerCommandSource sender)
    {
        return "mclib.commands.mclib.config.print";
    }

    @Override
    public String getSyntax()
    {
        return "{l}{6}/{r}mclib {8}config print{r} {7}<mod.category.option>{r}";
    }

    @Override
    public void executeCommand(MinecraftServer server, ServerCommandSource sender, String[] args) throws CommandException
    {
        Value value = SubCommandConfig.get(args[0]);

        if (!value.isClientSide())
        {
            this.getL10n().info(sender, "config.print", args[0], value.toString());
        }
        else
        {
            this.getL10n().info(sender, "config.client_side", args[0]);
        }
    }
}
