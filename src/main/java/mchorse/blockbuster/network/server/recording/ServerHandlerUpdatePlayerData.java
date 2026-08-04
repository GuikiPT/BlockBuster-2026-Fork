package mchorse.blockbuster.network.server.recording;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.network.common.recording.PacketUpdatePlayerData;
import mchorse.blockbuster.recording.MPMHelper;
import mchorse.blockbuster.recording.data.Record;
import mchorse.mclib.network.ServerMessageHandler;
import mchorse.mclib.utils.OpHelper;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server handler for {@link PacketUpdatePlayerData} (roadmap P116) — OP-only.
 *
 * <p>Writes the <b>requesting</b> player's entity NBT into the record's
 * {@code playerData} (re-skin a record as yourself), nesting the MPM data under
 * {@code "MPMData"} when MPM is loaded (never, on 1.20.4). Unknown record →
 * {@code record.not_exist} l10n error.</p>
 */
public class ServerHandlerUpdatePlayerData extends ServerMessageHandler<PacketUpdatePlayerData>
{
    @Override
    public void run(ServerPlayerEntity player, PacketUpdatePlayerData message)
    {
        if (!OpHelper.isPlayerOp(player))
        {
            return;
        }

        Record record = null;

        try
        {
            record = CommonProxy.manager.get(message.record);
        }
        catch (Exception e)
        {}

        if (record == null)
        {
            Blockbuster.l10n.error(player, "record.not_exist", message.record);

            return;
        }

        NbtCompound tag = new NbtCompound();

        /* Legacy wrote writeEntityToNBT — the CUSTOM half only. P286: yarn's
         * exact counterpart is writeCustomDataToNbt (public on PlayerEntity),
         * matching RecordManager.setupPlayerData. */
        player.writeCustomDataToNbt(tag);
        record.playerData = tag;

        if (MPMHelper.isLoaded())
        {
            tag = MPMHelper.getMPMData(player);

            if (tag != null)
            {
                record.playerData.put("MPMData", tag);
            }
        }

        record.dirty = true;
        record.resetUnload();
    }
}
