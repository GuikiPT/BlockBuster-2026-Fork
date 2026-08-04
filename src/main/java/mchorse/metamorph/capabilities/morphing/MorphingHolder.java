package mchorse.metamorph.capabilities.morphing;

/**
 * Duck interface implemented (by mixin) on every {@code PlayerEntity} to carry
 * its {@link IMorphing} capability instance (roadmap P52).
 *
 * <p>On 1.12.2 Forge the capability rode {@code ICapabilityProvider}; the
 * rewrite (per the technique ledger — no Cardinal dependency, mirroring P112's
 * {@code RecordingHolder}) attaches the instance directly to the entity through
 * this duck, created lazily by the {@code PlayerEntityMorphingMixin}.
 * {@link Morphing#get} casts through this interface.</p>
 */
public interface MorphingHolder
{
    IMorphing metamorph$getMorphing();
}
