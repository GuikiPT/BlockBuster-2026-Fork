package mchorse.mclib.utils;

import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * Full port of McLib 2.4.3's ByteBufUtils (roadmap P14). Java serialization
 * over the buf is kept as-is (legacy camera sync uses it — revisit call
 * sites in S15).
 *
 * Legacy source: .tools/legacy-src/mclib/src/main/java/mchorse/mclib/utils/ByteBufUtils.java
 */
public class ByteBufUtils
{
    public static void writeObject(ByteBuf to, Object object)
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        try
        {
            ObjectOutputStream output = new ObjectOutputStream(bos);

            output.writeObject(object);
            output.flush();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        writeByteArray(to, bos.toByteArray());
    }

    @Nullable
    public static Object readObject(ByteBuf from)
    {
        ByteArrayInputStream bis = new ByteArrayInputStream(readByteArray(from));
        Object result = null;

        try
        {
            ObjectInputStream input = new ObjectInputStream(bis);

            result = input.readObject();
        }
        catch (IOException | ClassNotFoundException e)
        {
            e.printStackTrace();
        }

        return result;
    }

    public static byte[] readByteArray(ByteBuf from)
    {
        int size = from.readInt();
        ByteBuf bytes = from.readBytes(size);

        byte[] array = new byte[bytes.capacity()];

        bytes.getBytes(0, array);

        return array;
    }

    public static void writeByteArray(ByteBuf to, byte[] array)
    {
        to.writeInt(array.length);
        to.writeBytes(array);
    }
}
