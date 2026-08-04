package mchorse.mclib.config;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.McLib;
import mchorse.mclib.config.json.ConfigParser;
import mchorse.mclib.config.values.Value;
import mchorse.mclib.network.ForgeByteBufUtils;
import mchorse.mclib.network.IByteBufSerializable;
import mchorse.mclib.utils.AtomicWrite;
import mchorse.mclib.utils.JsonUtils;
import mchorse.mclib.utils.PastCopies;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Port of McLib 2.4.3's {@code config/Config.java} (roadmap P20).
 *
 * <p>Port note: legacy {@code save(null)} sent a {@code PacketConfig} to the
 * server (the server-side config editing path). The port keeps the branch
 * behind the pluggable {@link #serverSender} hook, which defaults to a no-op
 * until S2 (networking) registers the real packet sender.</p>
 */
public class Config implements IByteBufSerializable
{
    /**
     * Invoked by {@link #save(File)} when {@code file} is null, i.e. this
     * config represents server-side values that must be sent back over the
     * network. S2 replaces this no-op with the {@code PacketConfig} sender.
     */
    public static Consumer<Config> serverSender = config -> {};

    public final String id;
    public final File file;

    public final Map<String, Value> values = new LinkedHashMap<String, Value>();

    private boolean serverSide;

    public Config(String id, File file)
    {
        this.id = id;
        this.file = file;
    }

    public Config(String id)
    {
        this.id = id;
        this.file = null;
    }

    public Config serverSide()
    {
        this.serverSide = true;

        return this;
    }

    public boolean isServerSide()
    {
        return this.serverSide;
    }

    public boolean hasSyncable()
    {
        for (Value value : this.values.values())
        {
            if (value.hasSyncable())
            {
                return true;
            }
        }

        return false;
    }

    /* Translation string related methods (legacy @SideOnly(CLIENT); plain strings, kept in main) */

    public String getTitleKey()
    {
        return this.id + ".config.title";
    }

    public String getCategoryTitleKey(Value value)
    {
        return this.id + ".config." + value.getPath() + ".title";
    }

    public String getCategoryTooltipKey(Value value)
    {
        return this.id + ".config." + value.getPath() + ".tooltip";
    }

    public String getValueLabelKey(Value value)
    {
        return this.id + ".config." + value.getPath();
    }

    public String getValueCommentKey(Value value)
    {
        return this.id + ".config.comments." + value.getPath();
    }

    /**
     * Get a value from category by their ids
     */
    public Value get(String category, String value)
    {
        Value cat = this.values.get(category);

        if (cat != null)
        {
            return cat.getSubValue(value);
        }

        return null;
    }

    /**
     * Save later in a separate thread
     */
    public void saveLater()
    {
        ConfigThread.add(this);
    }

    /**
     * Save config to default location
     */
    public void save()
    {
        this.save(this.file);
    }

    /**
     * Set by {@link mchorse.mclib.config.json.ConfigParser#fromJson} when a
     * config file <i>exists</i> but could not be read (roadmap <b>P284</b>).
     *
     * <p><b>The bug this closes.</b> A failed parse aborts the read loop, so
     * every value stays at its constructor default — and then the very next
     * {@code Value.set} anywhere in the GUI queues a {@code saveLater}, whose
     * 2-second debounce writes those defaults over the user's file. One
     * malformed byte in {@code config/blockbuster/config.json} silently reset
     * every Blockbuster setting, two seconds after the user toggled one
     * unrelated checkbox. The file being unreadable to <i>us</i> does not make
     * it worthless to the user — it is the only record of what they configured,
     * and it is usually one typo from being fine.</p>
     */
    public boolean unreadable;

    /** So the refusal is logged once, not every debounce tick. */
    private boolean warnedUnreadable;

    /**
     * Save config to given file
     */
    public boolean save(File file)
    {
        if (this.unreadable && file != null && file.exists())
        {
            if (!this.warnedUnreadable)
            {
                this.warnedUnreadable = true;

                McLib.LOGGER.error(
                    "Refusing to save config '{}': '{}' exists but could not be parsed, so everything in memory is "
                    + "at its default. Saving would replace your settings with those defaults. Fix or move the file "
                    + "and restart.", this.id, file);
            }

            return false;
        }

        try
        {
            if (file != null)
            {
                /* FileUtils.writeStringToFile parity: create missing parent folders */
                if (file.getParentFile() != null)
                {
                    Files.createDirectories(file.getParentFile().toPath());
                }

                /* P284: in place + truncating, no backup, for the file holding
                 * every setting the user has ever changed. Rotate then write
                 * atomically. */
                PastCopies.rotate(file, ".json");
                AtomicWrite.writeString(file, this.toJSON());
            }
            else
            {
                /* If file is null, that means that it was sent from server side */
                serverSender.accept(this);
            }

            return true;
        }
        catch (IOException e)
        {}

        return false;
    }

    /**
     * Copy all values from given config to this config
     */
    public void copy(Config config)
    {
        for (Map.Entry<String, Value> entry : config.values.entrySet())
        {
            this.values.get(entry.getKey()).copy(entry.getValue());
        }
    }

    public void copyServer(Config config)
    {
        for (Map.Entry<String, Value> entry : config.values.entrySet())
        {
            this.values.get(entry.getKey()).copyServer(entry.getValue());
        }
    }

    /**
     * Convert this config into JSON string
     */
    public String toJSON()
    {
        return JsonUtils.jsonToPretty(ConfigParser.toJson(this));
    }

    public Config filterSyncable()
    {
        return this.filter(Value::isSyncable);
    }

    public Config filterServerSide()
    {
        return this.filter(value -> !value.isClientSide());
    }

    public Config filter(Predicate<Value> predicate)
    {
        Config config = new Config(this.id);

        for (Value category : this.values.values())
        {
            List<Value> values = category.getSubValues().stream().filter(predicate).collect(Collectors.toList());

            if (!values.isEmpty())
            {
                Value newCategory = new Value(category.id);

                newCategory.setConfig(config);

                for (Value value : values)
                {
                    newCategory.addSubValue(value);
                }

                config.values.put(newCategory.id, newCategory);
            }
        }

        return config;
    }

    @Override
    public void fromBytes(ByteBuf buffer)
    {
        this.values.clear();

        for (int i = 0, c = buffer.readInt(); i < c; i++)
        {
            String key = ForgeByteBufUtils.readUTF8String(buffer);
            Value category = new Value(key);

            category.setConfig(this);
            category.fromBytes(buffer);
            this.values.put(key, category);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer)
    {
        buffer.writeInt(this.values.size());

        for (Map.Entry<String, Value> entry : this.values.entrySet())
        {
            ForgeByteBufUtils.writeUTF8String(buffer, entry.getKey());

            entry.getValue().toBytes(buffer);
        }
    }

    public void resetServerValues()
    {
        for (Value category : this.values.values())
        {
            category.resetServerValues();
        }
    }
}
