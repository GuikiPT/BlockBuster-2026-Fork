package mchorse.mclib.utils;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.EncoderException;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtTagSizeTracker;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.network.PacketByteBuf;

import javax.vecmath.Vector3f;
import java.io.IOException;

/**
 * Full port of McLib 2.4.3's NBTUtils (roadmap P14).
 *
 * Legacy source: .tools/legacy-src/mclib/src/main/java/mchorse/mclib/utils/NBTUtils.java
 */
public class NBTUtils
{
    /**
     * Deep copy with <b>1.12.2's</b> key ordering (S22 P287).
     *
     * <p>NBT compounds serialise in their backing {@code HashMap}'s iteration
     * order, which is bucket order — a function of the map's <i>table
     * capacity</i>. 1.12.2's {@code NBTTagCompound.copy()} put every key into a
     * <b>fresh</b> {@code NBTTagCompound}, so the copy kept the default
     * capacity 16 and therefore the same byte order as the original. 1.20.4's
     * {@link NbtCompound#copy()} instead builds the map with
     * {@code Maps.newHashMap(transformValues(...))}, i.e. {@code new
     * HashMap<>(map)}, which <i>pre-sizes the table from the entry count</i>:
     * a 4-key compound lands in a capacity-8 table instead of 16, and its keys
     * come out in a different order.
     *
     * <p>That is invisible to every reader (NBT is keyed, and
     * {@code NbtCompound.equals} is order-insensitive) but it breaks
     * write-back-byte-identity, which is this port's stated bar. It was found
     * by {@code RecordGoldenTest} on {@code E_inv_ite@.dat}, where the verbatim
     * legacy {@code Stack/tag/BlockEntityTag/Morph} compound
     * ({@code Pose, Texture, Name, ForcedSettings}) resaved as
     * {@code Texture, Pose, Name, ForcedSettings} — the exact bucket order of
     * capacity 8 versus capacity 16.
     *
     * <p>Use this instead of {@link NbtCompound#copy()} whenever the compound
     * is <b>legacy data being preserved verbatim for re-emit</b>. It is not
     * needed for compounds the port builds itself: those already start from a
     * fresh default-capacity map.
     */
    public static NbtCompound legacyCopy(NbtCompound compound)
    {
        NbtCompound copy = new NbtCompound();

        for (String key : compound.getKeys())
        {
            copy.put(key, legacyCopy(compound.get(key)));
        }

        return copy;
    }

    /**
     * {@link #legacyCopy(NbtCompound)} for an arbitrary tag — recurses through
     * lists so nested compounds get the fresh-map treatment too (1.12.2's
     * {@code NBTTagList.copy()} likewise rebuilt element by element).
     */
    public static NbtElement legacyCopy(NbtElement element)
    {
        if (element instanceof NbtCompound)
        {
            return legacyCopy((NbtCompound) element);
        }

        if (element instanceof NbtList)
        {
            NbtList list = (NbtList) element;
            NbtList copy = new NbtList();

            for (int i = 0; i < list.size(); i++)
            {
                copy.add(legacyCopy(list.get(i)));
            }

            return copy;
        }

        return element.copy();
    }

    public static void readFloatList(NbtList list, float[] array)
    {
        int count = Math.min(array.length, list.size());

        for (int i = 0; i < count; i++)
        {
            array[i] = list.getFloat(i);
        }
    }

    public static NbtList writeFloatList(NbtList list, float[] array)
    {
        for (int i = 0; i < array.length; i++)
        {
            list.add(NbtFloat.of(array[i]));
        }

        return list;
    }

    public static void readFloatList(NbtList list, Vector3f vector)
    {
        if (list.size() != 3)
        {
            return;
        }

        vector.x = list.getFloat(0);
        vector.y = list.getFloat(1);
        vector.z = list.getFloat(2);
    }

    public static NbtList writeFloatList(NbtList list, Vector3f vector)
    {
        list.add(NbtFloat.of(vector.x));
        list.add(NbtFloat.of(vector.y));
        list.add(NbtFloat.of(vector.z));

        return list;
    }

    /**
     * Read an NBT compound from the buffer without the vanilla 2 MiB size
     * cap (legacy {@code readInfiniteTag} over
     * {@code CompressedStreamTools.read(..., NBTSizeTracker.INFINITE)}).
     * Peeks the first byte: 0 (the null sentinel) returns null.
     */
    public static NbtCompound readInfiniteTag(ByteBuf buf)
    {
        int i = buf.readerIndex();
        byte b0 = buf.readByte();

        if (b0 == 0)
        {
            return null;
        }
        else
        {
            buf.readerIndex(i);

            try
            {
                /* NbtTagSizeTracker.EMPTY has a 0 byte quota, which 1.20.1's
                 * `add` reads as "no limit" — the direct equivalent of legacy
                 * NBTSizeTracker.INFINITE. */
                return NbtIo.read(new ByteBufInputStream(buf), NbtTagSizeTracker.EMPTY);
            }
            catch (IOException ioexception)
            {
                throw new EncoderException(ioexception);
            }
        }
    }

    /**
     * PacketByteBuf convenience overload (same unlimited-size behavior);
     * returns null for the null sentinel written by
     * {@link PacketByteBuf#writeNbt}.
     */
    public static NbtCompound readInfiniteTag(PacketByteBuf buf)
    {
        NbtElement element = buf.readUnlimitedNbt();

        return element instanceof NbtCompound ? (NbtCompound) element : null;
    }

    /**
     * Parse an SNBT compound tolerantly (P40/P41 clipboard interop).
     *
     * 1.12.2's {@code NBTTagList.toString()} prefixed list entries with
     * their index ({@code [0:1.0d,1:2.0d]}) — a form 1.20.4's
     * {@link StringNbtReader} rejects, while legacy {@code JsonToNBT}
     * accepted both. Modern SNBT is tried first; on failure the indexed
     * prefixes are stripped and parsing is retried. Total-reader rule:
     * returns {@code null} instead of throwing.
     */
    public static NbtCompound parseSnbtCompound(String snbt)
    {
        if (snbt == null)
        {
            return null;
        }

        try
        {
            return StringNbtReader.parse(snbt);
        }
        catch (Exception e)
        {}

        try
        {
            return StringNbtReader.parse(stripListIndices(snbt));
        }
        catch (Exception e)
        {}

        return null;
    }

    /**
     * Strip legacy 1.12.2 {@code index:} prefixes from SNBT list entries
     * ({@code [0:1.0d,1:2.0d]} → {@code [1.0d,2.0d]}). Only used as a
     * fallback after modern parsing failed, so quoted strings that happen
     * to contain a {@code [N:} sequence can't be corrupted on the happy
     * path.
     */
    public static String stripListIndices(String snbt)
    {
        return snbt.replaceAll("([\\[,])\\s*\\d+\\s*:", "$1");
    }
}
