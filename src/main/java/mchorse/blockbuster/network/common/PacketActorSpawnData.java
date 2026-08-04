package mchorse.blockbuster.network.common;

import io.netty.buffer.ByteBuf;
import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.mclib.network.ForgeByteBufUtils;
import mchorse.mclib.network.IMessage;
import mchorse.metamorph.api.MorphUtils;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.network.PacketByteBuf;

/**
 * Actor spawn data (roadmap P119.2 — port addition, no 1.12 ledger slot).
 *
 * <p>Replacement for Forge's {@code IEntityAdditionalSpawnData}, which yarn
 * 1.20.4's {@code EntitySpawnS2CPacket} has no equivalent of — the same
 * approach {@code PacketGunProjectileSpawnData} takes for gun projectiles. The
 * payload mirrors legacy {@code EntityActor.writeSpawnData} field for field:
 * morph, {@code invisible}, {@code enableBurning}, {@code noClip}, then an
 * optional playback block ({@code playing}, {@code tick}, record filename, and
 * an optional replay morph), then {@code invulnerable} and
 * {@code renderLast}.</p>
 *
 * <p>Why an actor needs this at all: an actor's entire appearance is its morph,
 * and a client that starts tracking one mid-scene has no other way to learn
 * what it looks like or where in its recording it is. Without the payload the
 * actor spawns morphless and plays from tick 0.</p>
 */
public class PacketActorSpawnData implements IMessage
{
    public int id;
    public AbstractMorph morph;
    public boolean invisible;
    public boolean enableBurning;
    public boolean noClip;

    /** Whether the playback block below is present. */
    public boolean hasPlayback;
    public boolean playing;
    public int tick;
    public String filename = "";

    /** Optional — the replay's own morph, the previous-morph fallback. */
    public AbstractMorph replayMorph;

    public boolean invulnerable;
    public boolean renderLast;

    public PacketActorSpawnData()
    {}

    /** Legacy {@code writeSpawnData}'s snapshot half. */
    public PacketActorSpawnData(EntityActor actor)
    {
        this.id = actor.getId();
        this.morph = actor.morph.get();
        this.invisible = actor.invisible;
        this.enableBurning = actor.enableBurning;
        this.noClip = actor.noClip;

        this.hasPlayback = actor.playback != null;

        if (this.hasPlayback)
        {
            this.playing = actor.playback.playing;
            this.tick = actor.playback.tick;
            this.filename = actor.playback.record == null ? "" : actor.playback.record.filename;
            this.replayMorph = actor.playback.getReplay() == null ? null : actor.playback.getReplay().morph;
        }

        this.invulnerable = actor.isInvulnerable();
        this.renderLast = actor.renderLast;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        PacketByteBuf pbuf = buf instanceof PacketByteBuf ? (PacketByteBuf) buf : new PacketByteBuf(buf);

        this.id = pbuf.readInt();
        this.morph = MorphUtils.morphFromBuf(pbuf);
        this.invisible = pbuf.readBoolean();
        this.enableBurning = pbuf.readBoolean();
        this.noClip = pbuf.readBoolean();

        this.hasPlayback = pbuf.readBoolean();

        if (this.hasPlayback)
        {
            this.playing = pbuf.readBoolean();
            this.tick = pbuf.readInt();
            this.filename = ForgeByteBufUtils.readUTF8String(pbuf);

            if (pbuf.readBoolean())
            {
                this.replayMorph = MorphUtils.morphFromBuf(pbuf);
            }
        }

        this.invulnerable = pbuf.readBoolean();
        this.renderLast = pbuf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        PacketByteBuf pbuf = buf instanceof PacketByteBuf ? (PacketByteBuf) buf : new PacketByteBuf(buf);

        pbuf.writeInt(this.id);
        MorphUtils.morphToBuf(pbuf, this.morph);
        pbuf.writeBoolean(this.invisible);
        pbuf.writeBoolean(this.enableBurning);
        pbuf.writeBoolean(this.noClip);

        pbuf.writeBoolean(this.hasPlayback);

        if (this.hasPlayback)
        {
            pbuf.writeBoolean(this.playing);
            pbuf.writeInt(this.tick);
            ForgeByteBufUtils.writeUTF8String(pbuf, this.filename == null ? "" : this.filename);

            pbuf.writeBoolean(this.replayMorph != null);

            if (this.replayMorph != null)
            {
                MorphUtils.morphToBuf(pbuf, this.replayMorph);
            }
        }

        pbuf.writeBoolean(this.invulnerable);
        pbuf.writeBoolean(this.renderLast);
    }
}
