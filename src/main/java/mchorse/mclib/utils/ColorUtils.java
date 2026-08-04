package mchorse.mclib.utils;

/**
 * Full port of McLib 2.4.3's ColorUtils (roadmap P13), minus the one GL call.
 *
 * Legacy source: .tools/legacy-src/mclib/src/main/java/mchorse/mclib/utils/ColorUtils.java
 *
 * NOTE: legacy {@code bindColor(int)} (a {@code GlStateManager.color(...)}
 * call) is deliberately NOT ported here — it returns in S3 as a
 * GuiDraw/RenderSystem call in the client source set.
 */
public class ColorUtils
{
    public static final int HALF_BLACK = 0x88000000;

    public static final Color COLOR = new Color();

    public static int multiplyColor(int color, float factor)
    {
        COLOR.set(color, true);
        COLOR.r *= factor;
        COLOR.g *= factor;
        COLOR.b *= factor;

        return COLOR.getRGBAColor();
    }

    public static int setAlpha(int color, float alpha)
    {
        COLOR.set(color, true);
        COLOR.a = alpha;

        return COLOR.getRGBAColor();
    }

    public static void interpolate(Color target, int a, int b, float x)
    {
        interpolate(target, a, b, x, true);
    }

    public static void interpolate(Color target, int a, int b, float x, boolean alpha)
    {
        target.set(a, alpha);
        COLOR.set(b, alpha);

        target.r = Interpolations.lerp(target.r, COLOR.r, x);
        target.g = Interpolations.lerp(target.g, COLOR.g, x);
        target.b = Interpolations.lerp(target.b, COLOR.b, x);

        if (alpha)
        {
            target.a = Interpolations.lerp(target.a, COLOR.a, x);
        }
    }

    public static int rgbaToInt(float r, float g, float b, float a)
    {
        COLOR.set(r, g, b, a);

        return COLOR.getRGBAColor();
    }

    public static int parseColor(String color)
    {
        return parseColor(color, 0);
    }

    public static int parseColor(String color, int orDefault)
    {
        try
        {
            return parseColorWithException(color);
        }
        catch (Exception e)
        {}

        return orDefault;
    }

    public static int parseColorWithException(String color) throws Exception
    {
        if (color.startsWith("#"))
        {
            color = color.substring(1);
        }

        if (color.length() == 6 || color.length() == 8)
        {
            if (color.length() == 8)
            {
                String alpha = color.substring(0, 2);
                String rest = color.substring(2);

                int a = Integer.parseInt(alpha, 16) << 24;
                int rgb = Integer.parseInt(rest, 16);

                return a + rgb;
            }

            return Integer.parseInt(color, 16);
        }

        throw new Exception("Given color \"" + color + "\" can't be parsed!");
    }
}
