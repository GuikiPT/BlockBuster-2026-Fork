package mchorse.metamorph.network.common.survival;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.IMessage;
import mchorse.metamorph.api.MorphUtils;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.network.PacketByteBuf;

/**
 * Apply a morph to a tracked player by entity id (roadmap P55). Server→client
 * only: sent to everyone tracking a player when that player (de)morphs, and to a
 * client that starts tracking an already-morphed player.
 *
 * <p>Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/network/common/survival/PacketMorphPlayer.java</p>
 */
public class PacketMorphPlayer implements IMessage
{
    public int id;
    public AbstractMorph morph;

    public PacketMorphPlayer()
    {}

    public PacketMorphPlayer(int id, AbstractMorph morph)
    {
        this.id = id;
        this.morph = morph;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.id = buf.readInt();
        this.morph = MorphUtils.morphFromBuf(buf instanceof PacketByteBuf ? (PacketByteBuf) buf : new PacketByteBuf(buf));
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeInt(this.id);
        MorphUtils.morphToBuf(buf instanceof PacketByteBuf ? (PacketByteBuf) buf : new PacketByteBuf(buf), this.morph);
    }
}
