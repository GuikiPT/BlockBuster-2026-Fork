package mchorse.mclib.config.values;

import com.google.gson.JsonElement;
import io.netty.buffer.ByteBuf;
import mchorse.mclib.utils.Interpolation;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;

import java.util.Arrays;

/**
 * The Array of ItemStacks will not contain null values. Null values will be replaced with {@link ItemStack#EMPTY}
 *
 * <p>Port of McLib 2.4.3's {@code config/values/ValueItemSlots.java} (roadmap
 * P19). Legacy quirks kept on purpose:</p>
 * <ul>
 * <li>{@link #equals(Object)} compares {@code this.value} against
 * {@code this.defaultValue} instead of the other object's value — a legacy bug
 * that Blockbuster behavior depends on;</li>
 * <li>{@link #valueFromJSON(JsonElement)} / {@link #valueToJSON()} are the
 * legacy empty {@code TODO} stubs — item slots are <b>never</b> persisted to
 * JSON (gun/inventory configs rely on NBT-only persistence).</li>
 * </ul>
 */
public class ValueItemSlots extends GenericValue<ItemStack[]>
{
    private int size;

    /**
     * Stand-ins for Forge's {@code ByteBufUtils.readItemStack/writeItemStack}
     * on raw Netty buffers, via yarn 1.20.4 {@link PacketByteBuf} (the 1.20.4
     * vanilla stack wire encoding — the config wire format is intra-mod).
     */
    private static ItemStack readItemStack(ByteBuf buffer)
    {
        return new PacketByteBuf(buffer).readItemStack();
    }

    private static void writeItemStack(ByteBuf buffer, ItemStack stack)
    {
        new PacketByteBuf(buffer).writeItemStack(stack);
    }

    public ValueItemSlots(String id, int size)
    {
        super(id);

        this.size = size;
        this.defaultValue = this.getNullValue();

        this.reset();
    }

    /**
     * Sets the defaultValue to a copy of the provided defaultValue array.
     * @param id
     * @param defaultValue
     */
    public ValueItemSlots(String id, ItemStack[] defaultValue)
    {
        super(id);

        this.size = defaultValue.length;
        this.defaultValue = new ItemStack[defaultValue.length];

        for (int i = 0; i < this.defaultValue.length; i++)
        {
            this.defaultValue[i] = (defaultValue[i] == null) ? this.getNullElementValue() : defaultValue[i].copy();
        }

        this.reset();
    }

    /**
     * Set this value to a copy of the provided array
     * @param value
     */
    @Override
    public void set(ItemStack[] value)
    {
        if (value == null) return;

        for (int i = 0; i < value.length && i < this.value.length; i++)
        {
            this.value[i] = (value[i] == null) ? this.getNullElementValue() : value[i].copy();
        }

        this.saveLater();
    }

    /**
     * Set the provided index to a copy of the provided ItemStack.<br>
     * If the provided ItemStack is null, {@link ItemStack#EMPTY} will be set.<br>
     * Do nothing if the provided index is out of bounds.
     * @param itemStack
     * @param index
     */
    public void set(ItemStack itemStack, int index)
    {
        if (index < this.value.length)
        {
            this.value[index] = (itemStack == null) ? this.getNullElementValue() : itemStack.copy();

            this.saveLater();
        }
    }

    @Override
    public void reset()
    {
        this.value = new ItemStack[this.defaultValue.length];

        for (int i = 0; i < this.value.length; i++)
        {
            this.set(this.defaultValue[i], i);
        }
    }

    /**
     * @return a deep copy of the array
     */
    @Override
    public ItemStack[] get()
    {
        ItemStack[] copy = new ItemStack[this.value.length];

        for (int i = 0; i < this.value.length; i++)
        {
            copy[i] = this.value[i].copy();
        }

        return copy;
    }

    /**
     * @param index
     * @return a copy of the ItemStack at the provided index
     * @throws IndexOutOfBoundsException
     */
    public ItemStack get(int index) throws IndexOutOfBoundsException
    {
        return this.value[index].copy();
    }

    public int size()
    {
        return this.size;
    }

    /**
     * @return {@link ItemStack#EMPTY}, the default value that the type of the array should produce instead of null.
     */
    protected ItemStack getNullElementValue()
    {
        return ItemStack.EMPTY;
    }

    /**
     * @return an ItemStack array with the {@link #size} of this object filled with {@link ItemStack#EMPTY}
     */
    @Override
    protected ItemStack[] getNullValue()
    {
        ItemStack[] nullValue = new ItemStack[this.size];

        Arrays.fill(nullValue, this.getNullElementValue());

        return nullValue;
    }

    /**
     * Legacy bug kept verbatim: compares {@code this.value} against
     * {@code this.defaultValue}, ignoring the other object's contents beyond
     * its length.
     */
    @Override
    public boolean equals(Object obj)
    {
        if (!(obj instanceof ValueItemSlots))
        {
            return false;
        }

        ValueItemSlots valueObj = (ValueItemSlots) obj;

        if (this.value.length != valueObj.value.length)
        {
            return false;
        }

        for (int i = 0; i < this.value.length; i++)
        {
            if (!ItemStack.areEqual(this.value[i], this.defaultValue[i]))
            {
                return false;
            }
        }

        return true;
    }

    /**
     * @return true if any ItemStack in {@link #value} differs from {@link #defaultValue}.
     */
    @Override
    public boolean hasChanged()
    {
        for (int i = 0; i < this.value.length; i++)
        {
            if (!ItemStack.areEqual(this.value[i], this.defaultValue[i]))
            {
                return true;
            }
        }

        return false;
    }

    @Override
    public ValueItemSlots copy()
    {
        ValueItemSlots copy = new ValueItemSlots(this.id, this.defaultValue);

        copy.set(this.value);

        return copy;
    }

    @Override
    public void copy(Value origin)
    {
        superCopy(origin);

        if (origin instanceof ValueItemSlots)
        {
            ValueItemSlots valueItemSlots = (ValueItemSlots) origin;

            for (int i = 0; i < valueItemSlots.value.length && i < this.value.length; i++)
            {
                this.value[i] = valueItemSlots.value[i].copy();
            }
        }
    }

    @Override
    public void fromBytes(ByteBuf buffer)
    {
        this.size = buffer.readInt();

        this.value = new ItemStack[this.size];
        this.defaultValue = new ItemStack[this.size];

        for (int i = 0; i < this.value.length; i++)
        {
            this.value[i] = buffer.readBoolean() ? readItemStack(buffer) : this.getNullElementValue();
        }

        for (int i = 0; i < this.defaultValue.length; i++)
        {
            this.defaultValue[i] = buffer.readBoolean() ? readItemStack(buffer) : this.getNullElementValue();
        }
    }

    @Override
    public void toBytes(ByteBuf buffer)
    {
        buffer.writeInt(this.size);

        for (int i = 0; i < this.value.length; i++)
        {
            buffer.writeBoolean(this.value[i] != null);

            if (this.value[i] != null)
            {
                writeItemStack(buffer, this.value[i]);
            }
        }

        for (int i = 0; i < this.defaultValue.length; i++)
        {
            buffer.writeBoolean(this.defaultValue[i] != null);

            if (this.defaultValue[i] != null)
            {
                writeItemStack(buffer, this.defaultValue[i]);
            }
        }
    }

    @Override
    public void valueFromBytes(ByteBuf buffer)
    {
        this.size = buffer.readInt();

        this.value = new ItemStack[this.size];

        for (int i = 0; i < this.value.length; i++)
        {
            this.value[i] = buffer.readBoolean() ? readItemStack(buffer) : this.getNullElementValue();
        }
    }

    @Override
    public void valueToBytes(ByteBuf buffer)
    {
        buffer.writeInt(this.size);

        for (int i = 0; i < this.value.length; i++)
        {
            buffer.writeBoolean(this.value[i] != null);

            if (this.value[i] != null)
            {
                writeItemStack(buffer, this.value[i]);
            }
        }
    }

    /*TODO*/
    @Override
    public void valueFromJSON(JsonElement element)
    {

    }

    /*TODO*/
    @Override
    public JsonElement valueToJSON()
    {
        return null;
    }

    @Override
    public void valueFromNBT(NbtElement tag)
    {
        if (tag instanceof NbtList)
        {
            NbtList items = (NbtList) tag;

            for (int i = 0; i < items.size() && i < this.value.length; i++)
            {
                this.value[i] = ItemStack.fromNbt(items.getCompound(i));
            }
        }
    }

    @Override
    public NbtElement valueToNBT()
    {
        NbtList list = new NbtList();

        for (int i = 0; i < this.value.length; i++)
        {
            NbtCompound tag = new NbtCompound();
            ItemStack stack = this.value[i];

            if (!stack.isEmpty())
            {
                stack.writeNbt(tag);
            }

            list.add(tag);
        }

        return list;
    }

    @Override
    public String toString()
    {
        String str = "";

        for (int i = 0; i < this.value.length; i++)
        {
            str += this.value[i].toString() + ((i + 1 == this.value.length) ? "" : ", ");
        }

        return str;
    }

    public ItemStack[] interpolate(Interpolation interpolation, GenericBaseValue<?> to, float factor)
    {
        if (!(to.value instanceof ItemStack[])) return this.copy().value;

        return factor == 1F ? (ItemStack[]) to.copy().value : this.copy().value;
    }
}
