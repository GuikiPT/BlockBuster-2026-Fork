package mchorse.mclib.commands.config;

import mchorse.mclib.McLib;
import mchorse.mclib.commands.SubCommandBase;
import mchorse.mclib.commands.utils.CommandException;
import mchorse.mclib.commands.utils.L10n;
import mchorse.mclib.config.values.Value;
import net.minecraft.server.command.ServerCommandSource;

/**
 * Port of McLib 2.4.3's {@code commands/config/SubCommandConfig}
 * (roadmap P207.5), verbatim.
 *
 * <p>{@link #get(String)} addresses a config value as
 * {@code mod.category.option} — <b>exactly</b> three period-separated
 * segments; anything else (or a missing module/category/option, which
 * surfaces as an NPE inside the try) becomes
 * {@code config.invalid_id}.</p>
 *
 * Legacy source: .tools/legacy-src/mclib/src/main/java/mchorse/mclib/commands/config/SubCommandConfig.java
 */
public class SubCommandConfig extends SubCommandBase
{
    public static Value get(String id) throws CommandException
    {
        try
        {
            String[] splits = id.split("\\.");

            if (splits.length != 3)
            {
                throw new Exception("Identifier should have exactly 3 strings separated by a period!");
            }

            return McLib.proxy.configs.modules.get(splits[0]).values.get(splits[1]).getSubValue(splits[2]);
        }
        catch (Exception e)
        {
            throw new CommandException("config.invalid_id", id);
        }
    }

    public SubCommandConfig()
    {
        this.add(new SubCommandConfigPrint());
        this.add(new SubCommandConfigSet());
    }

    @Override
    public String getName()
    {
        return "config";
    }

    @Override
    public String getUsage(ServerCommandSource sender)
    {
        return "mclib.commands.mclib.config.help";
    }

    @Override
    public String getSyntax()
    {
        return "{l}{6}/{r}mclib {8}config{r}";
    }

    @Override
    public L10n getL10n()
    {
        return McLib.l10n;
    }
}
