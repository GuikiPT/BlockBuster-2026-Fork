package mchorse.mclib.config.values;

import com.google.gson.JsonElement;
import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.ForgeByteBufUtils;
import mchorse.mclib.utils.Interpolation;
import mchorse.mclib.utils.resources.RLUtils;
import mchorse.mclib.utils.resources.ResourceLocation;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

/**
 * Port of McLib 2.4.3's {@code config/values/ValueRL.java} (roadmap P19).
 * GUI widget creation ({@code getFields}, the static texture picker) moved to
 * the S3 client-side factory registry — see {@link Value}.
 *
 * <p>Legacy quirks kept on purpose:</p>
 * <ul>
 * <li>{@link #get()} returns the server value only when the {@code useServer}
 * flag is set (not the usual null-check);</li>
 * <li>{@link #set(ResourceLocation)} stores the <b>reference</b> without
 * copying;</li>
 * <li>the ByteBuf form wraps the RL NBT in a compound under key {@code "RL"}
 * behind a presence boolean.</li>
 * </ul>
 */
public class ValueRL extends GenericValue<ResourceLocation> implements IServerValue
{
    private boolean useServer;

    public ValueRL(String id)
    {
        super(id);
    }

    public ValueRL(String id, ResourceLocation defaultValue)
    {
        super(id);

        this.defaultValue = defaultValue;
    }

    /**
     * @return the reference to {@link #value} or {@link #serverValue}.
     */
    @Override
    public ResourceLocation get()
    {
        return !this.useServer ? this.value : this.serverValue;
    }

    /**
     * Set this {@link #value} to the reference of the provided value.
     * <br>Note: This is how it was implemented before Chryfi did rewrites
     * and it has been used throughout McLib etc., so to avoid any problems, the old implementation is kept
     * @param value
     */
    @Override
    public void set(ResourceLocation value)
    {
        this.value = value;

        this.saveLater();
    }

    public void set(String value)
    {
        this.set(RLUtils.create(value));
    }

    @Override
    public void resetServer()
    {
        this.useServer = false;
        this.serverValue = null;
    }

    @Override
    public void reset()
    {
        this.set(RLUtils.clone(this.defaultValue));
    }

    @Override
    public void valueFromJSON(JsonElement element)
    {
        this.value = RLUtils.create(element);
    }

    @Override
    public JsonElement valueToJSON()
    {
        return RLUtils.writeJson(this.value);
    }

    @Override
    public void valueFromNBT(NbtElement tag)
    {
        this.set(RLUtils.create(tag));
    }

    @Override
    public NbtElement valueToNBT()
    {
        return RLUtils.writeNbt(this.value);
    }

    @Override
    public boolean parseFromCommand(String value)
    {
        this.set(RLUtils.create(value));

        return true;
    }

    @Override
    public void copy(Value value)
    {
        if (value instanceof ValueRL)
        {
            this.value = RLUtils.clone(((ValueRL) value).value);
        }
    }

    @Override
    public void copyServer(Value value)
    {
        if (value instanceof ValueRL)
        {
            this.useServer = true;
            this.serverValue = RLUtils.clone(((ValueRL) value).value);
        }
    }

    @Override
    public void fromBytes(ByteBuf buffer)
    {
        superFromBytes(buffer);

        this.value = this.readRL(buffer);
        this.defaultValue = this.readRL(buffer);
    }

    private ResourceLocation readRL(ByteBuf buffer)
    {
        if (buffer.readBoolean())
        {
            NbtCompound tag = ForgeByteBufUtils.readTag(buffer);

            return RLUtils.create(tag.get("RL"));
        }

        return null;
    }

    @Override
    public void toBytes(ByteBuf buffer)
    {
        superToBytes(buffer);

        this.writeRL(buffer, this.value);
        this.writeRL(buffer, this.defaultValue);
    }

    @Override
    public void valueFromBytes(ByteBuf buffer)
    {
        this.value = this.readRL(buffer);
    }

    @Override
    public void valueToBytes(ByteBuf buffer)
    {
        this.writeRL(buffer, this.value);
    }

    private void writeRL(ByteBuf buffer, ResourceLocation rl)
    {
        buffer.writeBoolean(rl != null);

        if (rl != null)
        {
            NbtCompound tag = new NbtCompound();

            tag.put("RL", RLUtils.writeNbt(rl));
            ForgeByteBufUtils.writeTag(buffer, tag);
        }
    }

    @Override
    public String toString()
    {
        return this.value == null ? "" : this.value.toString();
    }

    @Override
    public ValueRL copy()
    {
        ValueRL clone = new ValueRL(this.id);
        clone.value = RLUtils.clone(this.value);
        clone.defaultValue = RLUtils.clone(this.defaultValue);
        clone.serverValue = RLUtils.clone(this.serverValue);
        clone.useServer = this.useServer;

        return clone;
    }

    @Override
    public ResourceLocation interpolate(Interpolation interpolation, GenericBaseValue<?> to, float factor)
    {
        if (!(to.value instanceof ResourceLocation)) return RLUtils.clone(this.value);

        return factor == 1F ? RLUtils.clone((ResourceLocation) to.value) : RLUtils.clone(this.value);
    }
}
