package mchorse.mclib.commands.config;

import mchorse.mclib.McLib;
import mchorse.mclib.commands.SubCommandBase;
import mchorse.mclib.commands.utils.CommandException;
import mchorse.mclib.config.ConfigManager;
import mchorse.mclib.config.values.IServerValue;
import mchorse.mclib.config.values.Value;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;

/**
 * Port of McLib 2.4.3's {@code commands/config/SubCommandConfigSet}
 * (roadmap P207.5), verbatim.
 *
 * <p>Quirks preserved:</p>
 * <ul>
 * <li>the value is the <b>joined remainder</b> of the arguments
 * ({@code String.join(" ", dropFirstArguments(args, 1))}), so
 * {@code ValueRL}/string options with spaces parse;</li>
 * <li>a successful set on a {@code syncable} value re-broadcasts the whole
 * module's syncable subset ({@code filterSyncable()}) — the module id is the
 * address prefix up to the <b>first</b> period;</li>
 * <li>{@code config.invalid_format} reports {@code args[1]} — the <b>first</b>
 * value token, not the joined string;</li>
 * <li>a client-side (or non-{@link IServerValue}) option is an <b>info</b>
 * message, not an error.</li>
 * </ul>
 *
 * <p>Port note: the port's {@code ConfigManager.synchronizeConfig(Config)}
 * drops the legacy {@code MinecraftServer} argument — the broadcast target is
 * the S2 dispatcher hook, not a passed server.</p>
 *
 * Legacy source: .tools/legacy-src/mclib/src/main/java/mchorse/mclib/commands/config/SubCommandConfigSet.java
 */
public class SubCommandConfigSet extends SubCommandConfigBase
{
    @Override
    public String getName()
    {
        return "set";
    }

    @Override
    public String getUsage(ServerCommandSource sender)
    {
        return "mclib.commands.mclib.config.set";
    }

    @Override
    public String getSyntax()
    {
        return "{l}{6}/{r}mclib {8}config set{r} {7}<mod.category.option> <value...>{r}";
    }

    @Override
    public int getRequiredArgs()
    {
        return 2;
    }

    @Override
    public void executeCommand(MinecraftServer server, ServerCommandSource sender, String[] args) throws CommandException
    {
        Value value = SubCommandConfig.get(args[0]);

        if (!value.isClientSide() && value instanceof IServerValue)
        {
            String command = String.join(" ", SubCommandBase.dropFirstArguments(args, 1));

            if (((IServerValue) value).parseFromCommand(command))
            {
                if (value.isSyncable())
                {
                    String mod = args[0].substring(0, args[0].indexOf("."));

                    ConfigManager.synchronizeConfig(McLib.proxy.configs.modules.get(mod).filterSyncable());
                }

                this.getL10n().info(sender, "config.set", args[0], value.toString());
            }
            else
            {
                throw new CommandException("config.invalid_format", args[0], args[1]);
            }
        }
        else
        {
            this.getL10n().info(sender, "config.client_side", args[0]);
        }
    }
}
