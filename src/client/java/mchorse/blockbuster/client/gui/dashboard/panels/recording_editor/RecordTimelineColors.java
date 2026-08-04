package mchorse.blockbuster.client.gui.dashboard.panels.recording_editor;

import mchorse.blockbuster.recording.actions.Action;
import mchorse.blockbuster.recording.actions.ActionRegistry;

/**
 * Timeline action-cell colour derivation (P138).
 *
 * <p>The recording editor colours each action cell by its registry <b>byte ID</b>:
 * {@code hue = (ActionRegistry.getType(action) - 1) / getMaxID()}. This makes the
 * registry ID a <b>load-bearing GUI contract</b> — any ID renumbering (already
 * forbidden by P104) would repaint the timeline. Extracted here so the mapping can
 * be asserted headlessly, guarding that contract, without the GL widget.</p>
 *
 * <p>Legacy source:
 * {@code GuiRecordTimeline.drawAction} (lines ~1659-1672). The HSV→RGB conversion
 * is Minecraft's {@code MathHelper.hsvToRgb} algorithm, inlined verbatim here so
 * the value is identical to the game's without a GL/Knot bootstrap.</p>
 */
public class RecordTimelineColors
{
    /**
     * The load-bearing hue for an action's cell: {@code (getType - 1) / maxID}.
     * Registry byte IDs drive the timeline palette — this is the value the ID
     * contract must keep stable.
     */
    public static float actionHue(Action action)
    {
        return (ActionRegistry.getType(action) - 1) / ((float) ActionRegistry.getMaxID());
    }

    /**
     * Base cell colour (full saturation/value): {@code hsvToRgb(hue, 1, 1)}.
     */
    public static int actionColor(Action action)
    {
        return hsvToRgb(actionHue(action), 1F, 1F);
    }

    /**
     * Complementary outline hue used for a selected cell. Legacy clamps the
     * shifted hue into {@code [0, 0.5]} so purple/blue outlines (which "don't pop
     * enough") never appear.
     */
    public static float selectedHue(float hue)
    {
        return clamp((hue - 0.5F) < 0 ? 0.5F + hue : hue - 0.5F, 0F, 0.5F);
    }

    /**
     * Outline colour for the currently-focused selected cell:
     * {@code hsvToRgb(selectedHue(hue), 0.5, 1)}.
     */
    public static int selectedColor(Action action)
    {
        return hsvToRgb(selectedHue(actionHue(action)), 0.5F, 1F);
    }

    /**
     * Minecraft's {@code MathHelper.hsvToRgb(hue, saturation, value)} inlined
     * (stable across MC versions). Returns a packed 0xRRGGBB int.
     */
    public static int hsvToRgb(float hue, float saturation, float value)
    {
        int i = (int) (hue * 6.0F) % 6;
        float f = hue * 6.0F - i;
        float g = value * (1.0F - saturation);
        float h = value * (1.0F - f * saturation);
        float k = value * (1.0F - (1.0F - f) * saturation);
        float l;
        float m;
        float n;

        switch (i)
        {
            case 0:
                l = value;
                m = k;
                n = g;
                break;
            case 1:
                l = h;
                m = value;
                n = g;
                break;
            case 2:
                l = g;
                m = value;
                n = k;
                break;
            case 3:
                l = g;
                m = h;
                n = value;
                break;
            case 4:
                l = k;
                m = g;
                n = value;
                break;
            case 5:
                l = value;
                m = g;
                n = h;
                break;
            default:
                throw new RuntimeException("Something went wrong when converting from HSV to RGB. Input was " + hue + ", " + saturation + ", " + value);
        }

        int o = clampInt((int) (l * 255.0F), 0, 255);
        int p = clampInt((int) (m * 255.0F), 0, 255);
        int q = clampInt((int) (n * 255.0F), 0, 255);

        return o << 16 | p << 8 | q;
    }

    private static float clamp(float value, float min, float max)
    {
        return value < min ? min : (Math.min(value, max));
    }

    private static int clampInt(int value, int min, int max)
    {
        return value < min ? min : (Math.min(value, max));
    }
}
