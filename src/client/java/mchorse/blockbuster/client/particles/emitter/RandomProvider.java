package mchorse.blockbuster.client.particles.emitter;

import java.util.function.DoubleSupplier;

/**
 * Determinism seam for the Snowstorm particle engine (roadmap P149, formalized
 * by the P156 golden).
 *
 * <p>Every {@code Math.random()} call site in {@link BedrockEmitter} and
 * {@link BedrockParticle} routes through {@link #nextDouble()} so a headless test
 * can install a seeded {@link DoubleSupplier} and reproduce the exact simulation
 * trajectory. The default supplier is {@code Math::random}, so production
 * behavior is byte-identical to the legacy 1.12.2 engine (same call order, same
 * distribution). Tests call {@link #set(DoubleSupplier)} before building an
 * emitter/particle and {@link #reset()} afterwards.</p>
 *
 * <p>The exact number and order of draws is a behavioral contract: constructing
 * an emitter draws 4 (its {@code random1..4}); constructing a particle draws 7
 * (4 randoms, then 3 for the initial normalized speed direction); each
 * {@code stop()} rerolls the emitter's 4 randoms.</p>
 */
public final class RandomProvider
{
    private static DoubleSupplier supplier = Math::random;

    private RandomProvider()
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
