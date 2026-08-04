package mchorse.mclib.utils.keyframes;

import mchorse.mclib.utils.IInterpolation;
import mchorse.mclib.utils.Interpolations;

public class KeyframeInterpolations
{
    public static final IInterpolation CONSTANT = new IInterpolation()
    {
        @Override
        public float interpolate(float a, float b, float x)
        {
            return a;
        }

        @Override
        public double interpolate(double a, double b, double x)
        {
            return a;
        }

        @Override
        public String getKey()
        {
            return "mclib.interpolations.const";
        }

        @Override
        public String getTooltipKey()
        {
            return "mclib.interpolations.tooltips.const";
        }
    };

    public static final IInterpolation HERMITE = new IInterpolation()
    {
        @Override
        public float interpolate(float a, float b, float x)
        {
            return (float) Interpolations.cubicHermite(a, a, b, b, x);
        }

        @Override
        public double interpolate(double a, double b, double x)
        {
            return Interpolations.cubicHermite(a, a, b, b, x);
        }

        @Override
        public String getKey()
        {
            return "mclib.interpolations.hermite";
        }

        @Override
        public String getTooltipKey()
        {
            return "mclib.interpolations.tooltips.hermite";
        }
    };

    public static final IInterpolation BEZIER = new IInterpolation()
    {
        @Override
        public float interpolate(float a, float b, float x)
        {
            return (float) Interpolations.cubicHermite(a, a, b, b, x);
        }

        @Override
        public double interpolate(double a, double b, double x)
        {
            return Interpolations.cubicHermite(a, a, b, b, x);
        }

        @Override
        public String getKey()
        {
            return "mclib.interpolations.bezier";
        }

        @Override
        public String getTooltipKey()
        {
            return "mclib.interpolations.tooltips.bezier";
        }
    };
}
