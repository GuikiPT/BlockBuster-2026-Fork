package mchorse.blockbuster.commands.modelblock;

import mchorse.blockbuster.common.tileentity.TileEntityModel;
import mchorse.blockbuster.network.common.PacketModifyModelBlock;
import mchorse.mclib.commands.SubCommandBase;
import mchorse.mclib.commands.utils.CommandException;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;

/**
 * {@code /modelblock morph <x> <y> <z> [morph_nbt]} — port of legacy
 * {@code SubCommandModelBlockMorph} (roadmap P207.4).
 *
 * <p>Sets (or, when the NBT tail is omitted, clears) the block's morph, then
 * re-broadcasts the block to every player within 64 blocks (merge flag on).
 * A morph-parse failure <b>silently clears the morph to {@code null}</b> — the
 * legacy empty {@code catch} is load-bearing (map-makers rely on it).</p>
 */
public class SubCommandModelBlockMorph extends SubCommandModelBlockBase
{
    @Override
    public String getName()
    {
        return "morph";
    }

    @Override
    public String getUsage(ServerCommandSource sender)
    {
        return "blockbuster.commands.modelblock.morph";
    }

    @Override
    public String getSyntax()
    {
        return "{l}{6}/{r}modelblock {8}morph{r} {7}<x> <y> <z> [morph_nbt]{r}";
    }

    @Override
    public void executeCommand(MinecraftServer server, ServerCommandSource sender, String[] args) throws CommandException
    {
        TileEntityModel model = this.getModelBlock(sender, args);
        String morphData = args.length >= 4 ? String.join(" ", SubCommandBase.dropFirstArguments(args, 3)) : null;
        AbstractMorph morph = null;

        if (morphData != null)
        {
            try
            {
                morph = MorphManager.INSTANCE.morphFromNBT(StringNbtReader.parse(morphData));
            }
            catch (Exception e)
            {}
        }

        model.setMorph(morph);

        PacketModifyModelBlock message = new PacketModifyModelBlock(model.getPos(), model, true);

        broadcastAround(sender, model.getPos(), message);
    }
}
