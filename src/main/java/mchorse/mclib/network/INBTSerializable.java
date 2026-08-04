package mchorse.mclib.network;

import net.minecraft.nbt.NbtCompound;

/**
 * Port of McLib 2.4.3's {@code INBTSerializable} (roadmap P14/P24 seam —
 * defined in S1 so keyframes/config values compile; S2 wires it to packets).
 * 1.12.2 {@code NBTTagCompound} maps to yarn {@code NbtCompound}.
 */
public interface INBTSerializable
{
    public void fromNBT(NbtCompound tag);

    public NbtCompound toNBT(NbtCompound tag);

    public default NbtCompound toNBT()
    {
        return this.toNBT(new NbtCompound());
    }
}
