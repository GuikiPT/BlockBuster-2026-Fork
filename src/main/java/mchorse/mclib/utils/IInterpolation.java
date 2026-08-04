package mchorse.mclib.utils;

import net.minecraft.text.Text;

/**
 * Port of McLib 2.4.3's {@code IInterpolation} (roadmap P9).
 *
 * Legacy used client-only {@code I18n.format}; the port routes name/tooltip
 * lookup through {@code Text.translatable(...).getString()} which resolves via
 * {@code Language} on both sides, so the interface can stay in the main source
 * set (it is implemented by serialized enum types).
 */
public interface IInterpolation
{
    public float interpolate(float a, float b, float x);

    public double interpolate(double a, double b, double x);

    public default String getName()
    {
        return Text.translatable(this.getKey()).getString();
    }

    public String getKey();

    public default String getTooltip()
    {
        return Text.translatable(this.getTooltipKey()).getString();
    }

    public String getTooltipKey();
}
