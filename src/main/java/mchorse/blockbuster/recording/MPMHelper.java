package mchorse.blockbuster.recording;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;

/**
 * More Player Models compatibility helper (roadmap P109). MPM does not exist
 * on 1.20.4, so {@link #isLoaded()} is hardwired to false — but the seams
 * stay so {@code MPMData} tags inside old records' {@code PlayerData} are
 * preserved on load/save (the key must survive round-trips, P118 parity)
 * and simply go unused.
 */
public class MPMHelper
{
    public static boolean isLoaded()
    {
        return false;
    }

    public static NbtCompound getMPMData(PlayerEntity player)
    {
        return null;
    }

    public static void setMPMData(PlayerEntity player, NbtCompound tag)
    {}
}
