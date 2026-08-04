package mchorse.mclib.utils.resources;

import com.google.gson.JsonElement;
import mchorse.mclib.utils.ICopy;
import net.minecraft.nbt.NbtElement;

public interface IWritableLocation<T> extends ICopy<T>
{
    public void fromNbt(NbtElement nbt) throws Exception;

    public void fromJson(JsonElement element) throws Exception;

    public NbtElement writeNbt();

    public JsonElement writeJson();
}
