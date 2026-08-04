package mchorse.aperture.camera.curves;

import mchorse.mclib.client.gui.utils.keys.IKey;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * Gamma / brightness curve (P180).
 *
 * <p>Legacy source:
 * {@code .tools/legacy-src/aperture/src/main/java/mchorse/aperture/camera/curves/BrightnessCurve.java}
 * — it read/wrote {@code Minecraft.gameSettings.gammaSetting} directly and could
 * push gamma beyond the vanilla {@code [0,1]} clamp.</p>
 *
 * <p>Port note (parity delta): on 1.20.4 gamma is a clamped {@code SimpleOption<Double>},
 * so the vanilla-safe subset drives it through the {@link #gammaGetter}/{@link #gammaSetter}
 * seam (installed by {@code ApertureClient} against
 * {@code MinecraftClient.options.getGamma()}). Values inside {@code [0,1]} match
 * 1.12.2 exactly; the legacy super-bright {@code >1} range is clamped by vanilla
 * — the lightmap-bypass for {@code >1} is deferred (documented delta, harmless).
 * The seam also keeps the class headless-testable (default no-op getter/setter).</p>
 */
public class BrightnessCurve extends AbstractCurve
{
    /**
     * Reads the current (captured-default) gamma. Installed by {@code ApertureClient};
     * defaults to {@code 1.0} so headless construction never touches Minecraft.
     */
    public static DoubleSupplier gammaGetter = () -> 1.0;

    /**
     * Writes gamma back into the game option. Installed by {@code ApertureClient};
     * defaults to a no-op for headless tests.
     */
    public static DoubleConsumer gammaSetter = (value) -> {};

    /**
     * Captured default gamma, restored on {@link #reset()} — mirrors legacy
     * {@code brightness = gammaSetting} field captured at construction.
     */
    public float brightness = (float) gammaGetter.getAsDouble();

    @Override
    public String getTranslatedName()
    {
        return IKey.lang("options.gamma").get();
    }

    @Override
    public void apply(double value)
    {
        gammaSetter.accept(value);
    }

    @Override
    public void reset()
    {
        gammaSetter.accept(this.brightness);
    }
}
