package mchorse.blockbuster.capabilities.recording;

/**
 * Duck interface implemented (by mixin) on every {@code PlayerEntity} to carry
 * its {@link IRecording} capability instance (roadmap P112).
 *
 * <p>On 1.12.2 Forge the capability rode {@code ICapabilityProvider}; the
 * rewrite (per the technique ledger — no Cardinal dependency) attaches the
 * instance directly to the entity through this duck, created lazily by the
 * {@code PlayerEntityRecordingMixin}. {@link Recording#get} casts through this
 * interface.</p>
 */
public interface RecordingHolder
{
    IRecording blockbuster$getRecording();
}
