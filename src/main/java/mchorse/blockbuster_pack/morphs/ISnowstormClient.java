package mchorse.blockbuster_pack.morphs;

/**
 * Client-side emitter seam for {@link SnowstormMorph} (roadmap P164).
 *
 * <p>The Bedrock particle engine (S13) is client-only, so a {@code src/main}
 * morph cannot touch it directly. This interface lets {@link SnowstormMorph}
 * route its emitter-touching <b>data</b> behaviour to the client without
 * importing any client type. The concrete implementation
 * ({@code SnowstormClient}) lives in the client source set and is installed into
 * {@link SnowstormMorph#CLIENT} at client init.</p>
 *
 * <p>Rendering is deliberately <b>not</b> here: it goes through the P54 morph
 * renderer registry ({@code SnowstormMorphRenderer}), because a {@code render}
 * override on the morph class would bypass the dispatcher.</p>
 *
 * <p>On a dedicated server the seam is {@code null} and the morph behaves as
 * pure data — mirroring 1.12.2's {@code @SideOnly(Side.CLIENT)} stripping.</p>
 */
public interface ISnowstormClient
{
    /**
     * Called after {@link SnowstormMorph#scheme} is set. Mirrors the legacy
     * {@code if (this.emitter != null) this.setClientScheme(key)} guard: retire
     * the current emitter into {@code lastEmitters} and build a new one only
     * when an emitter already exists.
     */
    void setScheme(SnowstormMorph morph, String key);

    /**
     * Store then re-parse a single MoLang variable override on the live
     * emitter.
     */
    void replaceVariable(SnowstormMorph morph, String name, String expression);

    /**
     * Per-tick keep-alive: zero the sanity-tick counters of the current and
     * retired emitters so the sanity auto-kill does not reap a morph that is
     * still present.
     */
    void update(SnowstormMorph morph);

    /**
     * Emitter-side of a merge: with a live emitter, restart it on a scheme
     * change (updating {@link SnowstormMorph#scheme}) or, on the same scheme,
     * only re-parse the variables — never restarting so in-flight particles
     * survive.
     */
    void merge(SnowstormMorph morph, String incomingScheme);

    /** The emitter scheme identifier when present, else the morph name. */
    String getSubclassDisplayName(SnowstormMorph morph);
}
