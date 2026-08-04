package mchorse.blockbuster.commands.modelblock;

import com.google.common.collect.ImmutableList;
import mchorse.blockbuster.common.tileentity.TileEntityModel;
import mchorse.blockbuster.network.common.PacketModifyModelBlock;
import mchorse.mclib.commands.McCommandBase;
import mchorse.mclib.commands.utils.CommandException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;

import java.util.List;

/**
 * {@code /modelblock property <x> <y> <z> <property:enabled> <value>} — port of
 * legacy {@code SubCommandModelBlockProperty} (roadmap P207.4).
 *
 * <p>{@link #PROPERTIES} is the single-entry whitelist {@code ["enabled"]} — the
 * map-logic extension point; unknown properties raise
 * {@code modelblock.wrong_property}. Sets the boolean, {@code markDirty}s, then
 * re-broadcasts within 64 blocks (merge flag off).</p>
 */
public class SubCommandModelBlockProperty extends SubCommandModelBlockBase
{
    public static final List<String> PROPERTIES = ImmutableList.of("enabled");

    @Override
    public String getName()
    {
        return "property";
    }

    @Override
    public String getUsage(ServerCommandSource sender)
    {
        return "blockbuster.commands.modelblock.property";
    }

    @Override
    public String getSyntax()
    {
        return "{l}{6}/{r}modelblock {8}property{r} {7}<x> <y> <z> <property:enabled> <value>{r}";
    }

    @Override
    public int getRequiredArgs()
    {
        return 5;
    }

    @Override
    public void executeCommand(MinecraftServer server, ServerCommandSource sender, String[] args) throws CommandException
    {
        TileEntityModel model = this.getModelBlock(sender, args);
        String property = args[3];

        if (!PROPERTIES.contains(property))
        {
            throw new CommandException("modelblock.wrong_property", property);
        }

        if (property.equals("enabled"))
        {
            model.getSettings().setEnabled(parseBoolean(args[4]));
        }

        model.markDirty();

        PacketModifyModelBlock message = new PacketModifyModelBlock(model.getPos(), model);

        broadcastAround(sender, model.getPos(), message);
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ServerCommandSource sender, String[] args)
    {
        if (args.length == 4)
        {
            return getListOfStringsMatchingLastWord(args, PROPERTIES);
        }
        else if (args.length == 5)
        {
            String property = args[3];

            if (property.equals("enabled"))
            {
                return getListOfStringsMatchingLastWord(args, McCommandBase.BOOLEANS);
            }
        }

        return super.getTabCompletions(server, sender, args);
    }
}
