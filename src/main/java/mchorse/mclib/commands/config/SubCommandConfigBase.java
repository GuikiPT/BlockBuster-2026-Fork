package mchorse.mclib.commands.config;

import mchorse.mclib.McLib;
import mchorse.mclib.commands.McCommandBase;
import mchorse.mclib.commands.utils.L10n;
import mchorse.mclib.config.Config;
import mchorse.mclib.config.values.Value;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of McLib 2.4.3's {@code commands/config/SubCommandConfigBase}
 * (roadmap P207.5), verbatim.
 *
 * <p>Tab-completes the {@code mod.category.option} address space of the live
 * {@link mchorse.mclib.config.ConfigManager}, <b>skipping client-side
 * values</b> — they can't be set from the server side.</p>
 *
 * Legacy source: .tools/legacy-src/mclib/src/main/java/mchorse/mclib/commands/config/SubCommandConfigBase.java
 */
public abstract class SubCommandConfigBase extends McCommandBase
{
    @Override
    public int getRequiredArgs()
    {
        return 1;
    }

    @Override
    public L10n getL10n()
    {
        return McLib.l10n;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ServerCommandSource sender, String[] args)
    {
        if (args.length == 1)
        {
            List<String> ids = new ArrayList<String>();

            for (Config config : McLib.proxy.configs.modules.values())
            {
                for (Value category : config.values.values())
                {
                    for (Value value : category.getSubValues())
                    {
                        if (value.isClientSide())
                        {
                            continue;
                        }

                        ids.add(config.id + "." + category.id + "." + value.id);
                    }
                }
            }

            return getListOfStringsMatchingLastWord(args, ids);
        }

        return super.getTabCompletions(server, sender, args);
    }
}
