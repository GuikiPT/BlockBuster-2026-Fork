package mchorse.chameleon.animation;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtString;

import java.util.Objects;

/**
 * How one of the morph's action slots ({@code idle}, {@code running}, …) maps to
 * a named animation, plus that animation's playback settings.
 *
 * <p><b>Two NBT shapes, and the choice is load-bearing.</b> When every setting is
 * at its default, {@link #toNBT()} writes a bare {@link NbtString} of the
 * animation name; otherwise a compound. {@link #fromNBT} accepts both. That is
 * how 2.7.2-era morph NBT is spelled, so a saved record/scene round-trips
 * byte-for-byte only if this stays asymmetric.</p>
 *
 * <p>The {@code Fade} key is written with {@code putInt} while the field is a
 * {@code float} — legacy did the same ({@code setInteger("Fade", (int) fade)}),
 * so a fractional fade is truncated on save. Preserved.</p>
 *
 * <p>Port note: {@code NBT.TAG_ANY_NUMERIC} is {@link NbtElement#NUMBER_TYPE}.</p>
 *
 * Legacy source: chameleon/.../animation/ActionConfig.java
 */
public class ActionConfig
{
    public String name = "";
    public boolean clamp = true;
    public boolean reset = true;
    public float speed = 1;
    public float fade = 5;
    public int tick = 0;

    public ActionConfig()
    {}

    public ActionConfig(String name)
    {
        this.name = name;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof ActionConfig)
        {
            ActionConfig config = (ActionConfig) obj;

            return Objects.equals(this.name, config.name)
                && this.clamp == config.clamp
                && this.reset == config.reset
                && this.speed == config.speed
                && this.fade == config.fade
                && this.tick == config.tick;
        }

        return super.equals(obj);
    }

    @Override
    public ActionConfig clone()
    {
        ActionConfig config = new ActionConfig(this.name);

        config.clamp = this.clamp;
        config.reset = this.reset;
        config.speed = this.speed;
        config.fade = this.fade;
        config.tick = this.tick;

        return config;
    }

    public void fromNBT(NbtElement base)
    {
        if (base instanceof NbtCompound)
        {
            NbtCompound tag = (NbtCompound) base;

            if (tag.contains("Name", NbtElement.STRING_TYPE)) this.name = tag.getString("Name");
            if (tag.contains("Clamp", NbtElement.NUMBER_TYPE)) this.clamp = tag.getBoolean("Clamp");
            if (tag.contains("Reset", NbtElement.NUMBER_TYPE)) this.reset = tag.getBoolean("Reset");
            if (tag.contains("Speed", NbtElement.NUMBER_TYPE)) this.speed = tag.getFloat("Speed");
            if (tag.contains("Fade", NbtElement.NUMBER_TYPE)) this.fade = tag.getInt("Fade");
            if (tag.contains("Tick", NbtElement.NUMBER_TYPE)) this.tick = tag.getInt("Tick");
        }
        else if (base instanceof NbtString)
        {
            this.name = ((NbtString) base).asString();
        }
    }

    public NbtElement toNBT()
    {
        if (!this.name.isEmpty() && this.isDefault())
        {
            return NbtString.of(this.name);
        }

        NbtCompound tag = new NbtCompound();

        if (!this.name.isEmpty()) tag.putString("Name", this.name);
        if (this.clamp != true) tag.putBoolean("Clamp", this.clamp);
        if (this.reset != true) tag.putBoolean("Reset", this.reset);
        if (this.speed != 1) tag.putFloat("Speed", this.speed);
        if (this.fade != 5) tag.putInt("Fade", (int) this.fade);
        if (this.tick != 0) tag.putInt("Tick", this.tick);

        return tag;
    }

    public boolean isDefault()
    {
        return this.clamp && this.reset && this.speed == 1 && this.fade == 5 && this.tick == 0;
    }
}
