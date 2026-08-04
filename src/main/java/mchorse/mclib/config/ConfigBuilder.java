package mchorse.mclib.config;

import mchorse.mclib.config.values.Value;
import mchorse.mclib.config.values.ValueBoolean;
import mchorse.mclib.config.values.ValueDouble;
import mchorse.mclib.config.values.ValueFloat;
import mchorse.mclib.config.values.ValueInt;
import mchorse.mclib.config.values.ValueRL;
import mchorse.mclib.config.values.ValueString;
import mchorse.mclib.utils.resources.ResourceLocation;

import java.io.File;

/**
 * Port of McLib 2.4.3's {@code config/ConfigBuilder.java} (roadmap P20),
 * verbatim (ResourceLocation is the bundled
 * {@code mchorse.mclib.utils.resources.ResourceLocation}).
 */
public class ConfigBuilder
{
    private Config config;
    private Value category;

    public ConfigBuilder(String id, File file)
    {
        this.config = new Config(id, file);
    }

    public Config getConfig()
    {
        return this.config;
    }

    public Value getCategory()
    {
        return this.category;
    }

    public ConfigBuilder category(String id)
    {
        /* Legacy McLib called category(id) exactly once per category, chaining
         * every value in that single call. The port splits a category's values
         * across phases (e.g. "general" is added to by P128/P203/P90/P92), so
         * category(id) is called repeatedly for the same id. Re-using the
         * existing Value keeps earlier-registered options instead of replacing
         * the category with a fresh empty one and silently dropping them
         * (legacy single-call semantics are preserved: the first call still
         * creates the category). */
        Value existing = this.config.values.get(id);

        if (existing != null)
        {
            this.category = existing;
        }
        else
        {
            this.config.values.put(id, this.category = new Value(id));
            this.category.setConfig(this.config);
        }

        return this;
    }

    public ConfigBuilder register(Value value)
    {
        this.category.addSubValue(value);
        value.setConfig(this.config);

        return this;
    }

    public ValueInt getInt(String id, int defaultValue)
    {
        ValueInt value = new ValueInt(id, defaultValue);

        this.register(value);

        return value;
    }

    public ValueInt getInt(String id, int defaultValue, int min, int max)
    {
        ValueInt value = new ValueInt(id, defaultValue, min, max);

        this.register(value);

        return value;
    }

    public ValueFloat getFloat(String id, float defaultValue)
    {
        ValueFloat value = new ValueFloat(id, defaultValue);

        this.register(value);

        return value;
    }

    public ValueFloat getFloat(String id, float defaultValue, float min, float max)
    {
        ValueFloat value = new ValueFloat(id, defaultValue, min, max);

        this.register(value);

        return value;
    }

    public ValueDouble getDouble(String id, double defaultValue)
    {
        ValueDouble value = new ValueDouble(id, defaultValue);

        this.register(value);

        return value;
    }

    public ValueDouble getDouble(String id, double defaultValue, double min, double max)
    {
        ValueDouble value = new ValueDouble(id, defaultValue, min, max);

        this.register(value);

        return value;
    }

    public ValueBoolean getBoolean(String id, boolean defaultValue)
    {
        ValueBoolean value = new ValueBoolean(id, defaultValue);

        this.register(value);

        return value;
    }

    public ValueString getString(String id, String defaultValue)
    {
        ValueString value = new ValueString(id, defaultValue);

        this.register(value);

        return value;
    }

    public ValueRL getRL(String id, ResourceLocation defaultValue)
    {
        ValueRL value = new ValueRL(id, defaultValue);

        this.register(value);

        return value;
    }
}
