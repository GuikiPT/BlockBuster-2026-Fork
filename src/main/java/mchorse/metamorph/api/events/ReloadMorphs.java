package mchorse.metamorph.api.events;

/**
 * Reload morphs event (roadmap P56).
 *
 * <p>Fired when the creative morphs picker is (re)initialized. Dispatched
 * through {@link mchorse.metamorph.api.MetamorphEvents#RELOAD_MORPHS}.</p>
 *
 * <p>Port note: legacy was {@code @SideOnly(Side.CLIENT)}. This event object is
 * side-agnostic and harmless on the server, but the firing site is the client
 * creative-picker GUI (S7); the event carries no data — subscribers just react
 * to the (re)init signal.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../api/events/ReloadMorphs.java
 */
public class ReloadMorphs
{
    public ReloadMorphs()
    {}
}
