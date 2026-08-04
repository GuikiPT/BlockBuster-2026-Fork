package mchorse.metamorph.network.common.survival;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.IMessage;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Sync the owner's morph-state bookkeeping (roadmap P55): the server's dummy
 * entity id for an {@link EntityMorph} inner entity plus the squid-air drown
 * counters. Server→owner only.
 *
 * <p>Quirk (P53): the client overwrites its EntityMorph inner entity's id with
 * {@link #entityID} so the two sides agree — EntityMorph client code depends on
 * it.</p>
 *
 * <p>Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/network/common/survival/PacketMorphState.java</p>
 */
public class PacketMorphState implements IMessage
{
    public int entityID = 0;
    public boolean hasSquidAir = true;
    public int squidAir = 300;

    public PacketMorphState()
    {}

    public PacketMorphState(PlayerEntity player, IMorphing morphing)
    {
        if (morphing != null)
        {
            AbstractMorph morph = morphing.getCurrentMorph();

            if (morph instanceof EntityMorph)
            {
                LivingEntity entity = ((EntityMorph) morph).getEntity(player.getWorld());

                if (entity != null)
                {
                    entityID = entity.getId();
                }
            }

            this.hasSquidAir = morphing.getHasSquidAir();
            this.squidAir = morphing.getSquidAir();
        }
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.entityID = buf.readInt();
        this.hasSquidAir = buf.readBoolean();
        this.squidAir = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeInt(this.entityID);
        buf.writeBoolean(this.hasSquidAir);
        buf.writeInt(this.squidAir);
    }
}
