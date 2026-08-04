package mchorse.mclib.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;

import java.nio.charset.StandardCharsets;

/**
 * Shim of Forge 1.12.2's {@code net.minecraftforge.fml.common.network.ByteBufUtils}
 * statics that legacy packet code calls everywhere (roadmap P24):
 * {@code readUTF8String}/{@code writeUTF8String}/{@code readTag}/{@code writeTag}.
 *
 * <p>Named {@code ForgeByteBufUtils} (not {@code ByteBufUtils}) because McLib
 * ships its own {@code mchorse.mclib.utils.ByteBufUtils} (P14) — the two
 * collided only via imports in 1.12.2; the distinct simple name keeps ported
 * files unambiguous with one import swap.</p>
 *
 * <p>Wire-format decisions (recorded per P24):</p>
 * <ul>
 * <li>UTF8 strings: Forge wrote <b>varint byte-length + UTF-8 bytes</b> — the
 * exact same varint encoding yarn's {@code PacketByteBuf.writeString} uses,
 * but Forge imposed no 32,767-char cap. Implemented here as raw varint+bytes
 * with no cap so chunk-reassembled buffers (P25) can carry oversized strings;
 * for all in-range values the bytes are identical to {@code writeString}.</li>
 * <li>NBT: via {@code PacketByteBuf.writeNbt}/{@code readNbt} (yarn 1.20.4,
 * verified via javap). {@link #readTag} additionally returns {@code null} on
 * an empty buffer — a total-reader guard legacy relied on implicitly (e.g.
 * {@code PacketDropItem} writes zero bytes for an empty stack).</li>
 * </ul>
 *
 * <p>Wire format is internal to the port (no 1.12 cross-version compat);
 * what matters is that P25 chunk reassembly decodes with the same helpers
 * that encoded.</p>
 */
public class ForgeByteBufUtils
{
    public static String readUTF8String(ByteBuf from)
    {
        int length = new PacketByteBuf(from).readVarInt();
        byte[] bytes = new byte[length];

        from.readBytes(bytes);

        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static void writeUTF8String(ByteBuf to, String string)
    {
        byte[] bytes = string.getBytes(StandardCharsets.UTF_8);

        new PacketByteBuf(to).writeVarInt(bytes.length);
        to.writeBytes(bytes);
    }

    public static NbtCompound readTag(ByteBuf from)
    {
        if (!from.isReadable())
        {
            return null;
        }

        return new PacketByteBuf(from).readNbt();
    }

    public static void writeTag(ByteBuf to, NbtCompound tag)
    {
        new PacketByteBuf(to).writeNbt(tag);
    }
}
