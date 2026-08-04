package mchorse.blockbuster.network.common;

import io.netty.buffer.ByteBuf;
import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.mclib.network.IMessage;
import mchorse.metamorph.api.MorphUtils;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.network.PacketByteBuf;

/**
 * Actor modify/sync packet (roadmap S10 — sent C→S when {@code GuiActor}
 * closes, P123, and S→C to trackers from {@link EntityActor#notifyPlayers},
 * P119.2).
 *
 * <p>1:1 field port of 1.12.2 {@code network/common/PacketModifyActor.java}.
 * The wire order is preserved exactly: {@code id} (int), {@code invisible}
 * (bool), {@code morph}, {@code offset} (int), a {@code previous != null}
 * boolean gating an optional {@code previous} morph, {@code previousOffset}
 * (int), {@code forceMorph} (bool).</p>
 *
 * <p><b>Morph typing:</b> both morphs ride the wire as real
 * {@link AbstractMorph}s through {@link MorphUtils#morphToBuf}/{@link
 * MorphUtils#morphFromBuf}, exactly as legacy did. (An earlier cut carried raw
 * {@code NbtCompound}s because {@code EntityActor.morph} had not yet been
 * bound to the Metamorph engine; that bridge is gone.) {@code morphToBuf}
 * already does the size-cap-free NBT write actor morphs need — they embed
 * whole custom models.</p>
 *
 * <p>Channel {@code blockbuster:modify_actor} (ledger slots 0 C / 1 S).</p>
 */
public class PacketModifyActor implements IMessage
{
    public int id;
    public AbstractMorph morph;
    public boolean invisible;

    public int offset;
    public AbstractMorph previous;
    public int previousOffset;
    public boolean forceMorph;

    public PacketModifyActor()
    {}

    /**
     * Snapshot the actor's current state (legacy constructor).
     *
     * <p>The pause fields ride along on every send, including the ones the
     * actor GUI triggers: when the actor is not mid-pause they are simply the
     * "not paused" sentinels ({@code -1}/{@code null}/{@code false}), and the
     * receiving side's {@code offset >= 0} test is what tells the two cases
     * apart.</p>
     */
    public PacketModifyActor(EntityActor actor)
    {
        this.id = actor.getId();
        this.morph = actor.morph.get();
        this.invisible = actor.invisible;

        this.offset = actor.pauseOffset;
        this.previous = actor.pausePreviousMorph;
        this.previousOffset = actor.pausePreviousOffset;
        this.forceMorph = actor.forceMorph;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        PacketByteBuf pbuf = buf instanceof PacketByteBuf ? (PacketByteBuf) buf : new PacketByteBuf(buf);

        this.id = pbuf.readInt();
        this.invisible = pbuf.readBoolean();
        this.morph = MorphUtils.morphFromBuf(pbuf);

        this.offset = pbuf.readInt();

        if (pbuf.readBoolean())
        {
            this.previous = MorphUtils.morphFromBuf(pbuf);
        }

        this.previousOffset = pbuf.readInt();
        this.forceMorph = pbuf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        PacketByteBuf pbuf = buf instanceof PacketByteBuf ? (PacketByteBuf) buf : new PacketByteBuf(buf);

        pbuf.writeInt(this.id);
        pbuf.writeBoolean(this.invisible);
        MorphUtils.morphToBuf(pbuf, this.morph);

        pbuf.writeInt(this.offset);
        pbuf.writeBoolean(this.previous != null);

        if (this.previous != null)
        {
            MorphUtils.morphToBuf(pbuf, this.previous);
        }

        pbuf.writeInt(this.previousOffset);
        pbuf.writeBoolean(this.forceMorph);
    }
}
