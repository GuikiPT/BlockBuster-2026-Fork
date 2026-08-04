package mchorse.metamorph.commands;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import mchorse.mclib.network.IMessage;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.MorphSettings;
import mchorse.metamorph.api.MorphUtils;
import mchorse.metamorph.network.Dispatcher;
import mchorse.metamorph.network.common.PacketBlacklist;
import mchorse.metamorph.network.common.PacketSettings;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.world.World;

/**
 * Server command {@code /metamorph} — Brigadier port of legacy
 * {@code mchorse.metamorph.commands.CommandMetamorph} (roadmap P60).
 *
 * <p>Legacy syntax {@code /metamorph reload <blacklist|morphs|remapper>}: reloads
 * the corresponding config off disk and swaps it into the live
 * {@link MorphManager} state:</p>
 *
 * <ul>
 *   <li>{@code blacklist} → {@link MorphUtils#reloadBlacklist()} +
 *       {@link MorphManager#setActiveBlacklist};</li>
 *   <li>{@code morphs} → {@link MorphUtils#reloadMorphSettings()} +
 *       {@link MorphManager#setActiveSettings};</li>
 *   <li>{@code remapper} → {@link MorphUtils#reloadRemapper()} +
 *       {@link MorphManager#setActiveMap}.</li>
 * </ul>
 *
 * <p>Permission level 3 (legacy {@code getRequiredPermissionLevel}). The
 * {@code blacklist} and {@code morphs} reloads also broadcast the new state to
 * every online player ({@code PacketBlacklist} / {@code PacketSettings}) —
 * legacy {@code broadcastPacket}, landed in S22 P222; {@code remapper} does
 * <b>not</b> broadcast, because the remap map is server-authoritative and legacy
 * never shipped it. The reload type completes exactly the three legacy
 * tokens.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/commands/CommandMetamorph.java
 */
public class CommandMetamorph
{
    public String getName()
    {
        return "metamorph";
    }

    public int getRequiredPermissionLevel()
    {
        return 3;
    }

    /**
     * Mount {@code /metamorph reload <type>} into the dispatcher.
     */
    public void register(CommandDispatcher<ServerCommandSource> dispatcher)
    {
        dispatcher.register(CommandManager.literal(this.getName())
            .requires(source -> source.hasPermissionLevel(this.getRequiredPermissionLevel()))
            .then(CommandManager.literal("reload")
                .then(CommandManager.argument("type", StringArgumentType.word())
                    .suggests((context, builder) -> CommandSource.suggestMatching(List.of("blacklist", "morphs", "remapper"), builder))
                    .executes(this::reload))));
    }

    /**
     * Broadcast seam (legacy {@code CommandMetamorph.broadcastPacket}).
     * Production fans the packet out to every connected player through the
     * bundled Metamorph dispatcher; headless tests swap in a collector, since
     * the send path needs a running {@code MinecraftServer}.
     */
    public Consumer<IMessage> broadcaster = Dispatcher::sendToAll;

    private int reload(CommandContext<ServerCommandSource> context)
    {
        ServerCommandSource source = context.getSource();

        this.reload(source.getWorld(), StringArgumentType.getString(context, "type"));

        return 1;
    }

    /**
     * Legacy {@code reload(World, String)}, verbatim in structure so it can be
     * driven headlessly: reload the config off disk, swap it into the live
     * {@link MorphManager}, then broadcast the result.
     */
    public void reload(World world, String type)
    {
        if (type.equals("blacklist"))
        {
            Set<String> blacklist = MorphUtils.reloadBlacklist();

            MorphManager.INSTANCE.setActiveBlacklist(world, blacklist);
            this.broadcastPacket(new PacketBlacklist(blacklist));
        }
        else if (type.equals("morphs"))
        {
            Map<String, MorphSettings> settings = MorphUtils.reloadMorphSettings();

            MorphManager.INSTANCE.setActiveSettings(settings);
            this.broadcastPacket(new PacketSettings(settings));
        }
        else if (type.equals("remapper"))
        {
            Map<String, String> map = MorphUtils.reloadRemapper();

            MorphManager.INSTANCE.setActiveMap(map);
        }
    }

    private void broadcastPacket(IMessage packet)
    {
        this.broadcaster.accept(packet);
    }
}
