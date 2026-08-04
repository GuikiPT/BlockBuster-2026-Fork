package mchorse.aperture.camera.curves;

import mchorse.aperture.client.AsmRenderingHandler;
import mchorse.aperture.client.AsmRenderingHandler.Curve;
import mchorse.mclib.client.gui.utils.keys.IKey;

/**
 * A vanilla-render curve (P180): drives one {@link Curve} channel by writing its
 * interpolated value into the {@link AsmRenderingHandler#values} sink, which the
 * render mixins ({@code ClientWorldMixin}, {@code WorldCelestialMixin},
 * {@code BackgroundRendererMixin}) read back per frame.
 *
 * <p>Verbatim port of
 * {@code .tools/legacy-src/aperture/src/main/java/mchorse/aperture/camera/curves/VanillaAsmCurve.java}.
 * Legacy resolved {@code getTranslatedName} via {@code I18n.format}; here it goes
 * through {@link IKey} exactly like the rest of the ported GUI.</p>
 */
public class VanillaAsmCurve extends AbstractCurve
{
    public final Curve curve;

    public VanillaAsmCurve(Curve curve)
    {
        this.curve = curve;
    }

    @Override
    public String getTranslatedName()
    {
        return IKey.lang("aperture.gui.curves." + this.convertTranslateKey(this.curve.name())).get();
    }

    @Override
    public void apply(double value)
    {
        AsmRenderingHandler.values.put(this.curve, value);
    }

    @Override
    public void reset()
    {
        AsmRenderingHandler.values.remove(this.curve);
    }
}
