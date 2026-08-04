package mchorse.blockbuster.recording.actions;

import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

/**
 * Command action
 *
 * This class is responsible for executing commands.
 *
 * <p>Port note: legacy temporarily added the player to the op list (level 4)
 * around execution; the port builds a level-4 {@link ServerCommandSource}
 * instead — identical observable behavior (recorded actor commands run with
 * full op; that is legacy-intended, scenes depend on it) without mutating the
 * real op list.</p>
 */
public class CommandAction extends Action
{
    /**
     * Command to be executed
     */
    public String command = "";

    public CommandAction()
    {}

    public CommandAction(String command)
    {
        this.command = command;
    }

    @Override
    public void apply(LivingEntity actor)
    {
        if (!this.command.isEmpty())
        {
            MinecraftServer server = actor.getServer();

            if (server != null)
            {
                ServerCommandSource source;

                if (actor instanceof ServerPlayerEntity)
                {
                    source = ((ServerPlayerEntity) actor).getCommandSource().withLevel(4);
                }
                else
                {
                    /* Legacy CommandSender: named "CommandAction", positioned
                     * at the actor, all permissions, no feedback */
                    source = new ServerCommandSource(server, actor.getPos(), actor.getRotationClient(),
                        actor.getWorld() instanceof ServerWorld world ? world : server.getOverworld(),
                        4, "CommandAction", Text.literal("CommandAction"), server, actor).withSilent();
                }

                server.getCommandManager().executeWithPrefix(source, this.command);
            }
        }
    }

    @Override
    public void fromBuf(PacketByteBuf buf)
    {
        super.fromBuf(buf);
        this.command = buf.readString();
    }

    @Override
    public void toBuf(PacketByteBuf buf)
    {
        super.toBuf(buf);
        buf.writeString(this.command);
    }

    @Override
    public void fromNBT(NbtCompound tag)
    {
        this.command = tag.getString("Command");
    }

    @Override
    public void toNBT(NbtCompound tag)
    {
        tag.putString("Command", this.command);
    }
}
