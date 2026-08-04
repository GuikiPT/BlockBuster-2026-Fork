package mchorse.chameleon.animation;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Animated actions config. This little dude right there is
 * responsible for storing configuration for the name of actions
 * which should be used for particular <s>set of skills</s> actions.
 *
 * <p>Keys are normalised to {@code under_score} case on the way in
 * ({@link #toKey}), because the editor lists them {@code PascalCase}
 * ({@code CrouchingIdle}) while the animator looks them up
 * {@code crouching_idle}. Whatever spelling a saved file uses, it lands in the
 * map under the underscore form.</p>
 *
 * <p>{@link #toNBT(NbtCompound)} returns {@code null} for an empty map — the
 * caller ({@code ChameleonMorph.toNBT}) uses that to omit the {@code Actions}
 * key entirely. An entry whose key already equals its animation name and whose
 * settings are all default is also omitted, since
 * {@link #getConfig(String)} reconstructs exactly that on read.</p>
 *
 * Legacy source: chameleon/.../animation/ActionsConfig.java
 */
public class ActionsConfig
{
    public Map<String, ActionConfig> actions = new HashMap<String, ActionConfig>();

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof ActionsConfig)
        {
            ActionsConfig config = (ActionsConfig) obj;

            return Objects.equals(this.actions, config.actions);
        }

        return super.equals(obj);
    }

    public void copy(ActionsConfig config)
    {
        this.actions.clear();
        this.actions.putAll(config.actions);
    }

    public void fromNBT(NbtCompound tag)
    {
        this.actions.clear();

        for (String key : tag.getKeys())
        {
            NbtElement base = tag.get(key);
            String newKey = this.toKey(key);
            ActionConfig config = new ActionConfig(newKey);

            config.fromNBT(base);
            this.actions.put(newKey, config);
        }
    }

    public NbtCompound toNBT()
    {
        return this.toNBT(new NbtCompound());
    }

    public NbtCompound toNBT(NbtCompound tag)
    {
        if (this.actions.isEmpty())
        {
            return null;
        }

        if (tag == null)
        {
            tag = new NbtCompound();
        }

        for (Map.Entry<String, ActionConfig> entry : this.actions.entrySet())
        {
            ActionConfig action = entry.getValue();
            String key = entry.getKey();

            if (!(key.equals(action.name) && action.isDefault()))
            {
                tag.put(key, action.toNBT());
            }
        }

        return tag;
    }

    /**
     * Get key for the action
     */
    public ActionConfig getConfig(String key)
    {
        ActionConfig output = this.actions.get(key);

        return output == null ? new ActionConfig(key) : output;
    }

    /**
     * Translates JSON or NBT (camelCase or PascalCase) based key into
     * internal under_score case.
     */
    public String toKey(String key)
    {
        return key.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
