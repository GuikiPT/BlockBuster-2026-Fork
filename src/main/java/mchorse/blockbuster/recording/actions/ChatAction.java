package mchorse.blockbuster.recording.actions;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.recording.RecordUtils;
import mchorse.mclib.utils.TextUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;

/**
 * Chat action
 *
 * Sends chat message with some formatting.
 */
public class ChatAction extends Action
{
    public String message = "";

    public ChatAction()
    {}

    public ChatAction(String message)
    {
        this.message = message;
    }

    public String getMessage(LivingEntity actor)
    {
        String message = this.message;
        String prefix = Blockbuster.recordChatPrefix.get();

        if (!prefix.isEmpty())
        {
            message = prefix.replace("%NAME%", actor == null ? "Player" : actor.getName().getString()) + message;
        }

        return TextUtils.processColoredText(message);
    }

    /**
     * Legacy {@code RecordUtils.broadcastMessage(this.getMessage(actor))} —
     * every online player gets the (prefixed, colour-processed) line.
     */
    @Override
    public void apply(LivingEntity actor)
    {
        RecordUtils.broadcastMessage(this.getMessage(actor));
    }

    @Override
    public void fromBuf(PacketByteBuf buf)
    {
        super.fromBuf(buf);
        this.message = buf.readString();
    }

    @Override
    public void toBuf(PacketByteBuf buf)
    {
        super.toBuf(buf);
        buf.writeString(this.message);
    }

    @Override
    public void fromNBT(NbtCompound tag)
    {
        this.message = tag.getString("Message");
    }

    @Override
    public void toNBT(NbtCompound tag)
    {
        tag.putString("Message", this.message);
    }
}
