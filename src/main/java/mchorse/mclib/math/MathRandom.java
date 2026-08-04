package mchorse.mclib.math;

import java.util.function.DoubleSupplier;

/**
 * Determinism seam for the MoLang {@code Math.random(...)} / {@code Math.die_roll(...)}
 * family (roadmap P156).
 *
 * <p>The Snowstorm particle presets drive per-particle values (lifetimes, sizes,
 * accelerations) through MoLang expressions such as {@code Math.random(1, 1.4)}.
 * Those built-in functions historically called {@link Math#random()} directly,
 * which makes an end-to-end particle simulation non-reproducible. This seam gives
 * the P156 golden harness a single place to install a seeded
 * {@link DoubleSupplier} so the whole MoLang RNG stream becomes deterministic.</p>
 *
 * <p>The default supplier is {@code Math::random}, so production behavior is
 * byte-identical to the legacy 1.12.2 engine (same distribution, same call site).
 * This intentionally mirrors
 * {@code mchorse.blockbuster.client.particles.emitter.RandomProvider}, which seams
 * the emitter/particle {@code Math.random()} sites; the two are independent streams
 * (each reproducible on its own seed), so their draw interleaving does not matter
 * for determinism.</p>
 */
public final class MathRandom
{
    private static DoubleSupplier supplier = Math::random;

    private MathRandom()
    {}

    /**
     * Draw the next pseudo-random double in {@code [0, 1)} from the active
     * supplier. Mirrors {@link Math#random()} by default.
     */
    public static double nextDouble()
    {
        return supplier.getAsDouble();
    }

    /**
     * Install a custom supplier (e.g. a seeded {@link java.util.Random}'s
     * {@code nextDouble}). Passing {@code null} restores the default.
     */
    public static void set(DoubleSupplier newSupplier)
    {
        supplier = newSupplier == null ? Math::random : newSupplier;
    }

    /**
     * Restore the default {@code Math::random} supplier.
     */
    public static void reset()
    {
        supplier = Math::random;
    }
}
