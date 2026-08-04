package mchorse.blockbuster.aperture;

import mchorse.aperture.CommonProxy;
import mchorse.aperture.camera.ModifierRegistry;
import mchorse.blockbuster.aperture.camera.modifiers.TrackerModifier;

/**
 * S22 <b>P240</b> — register Blockbuster's {@code "tracker"} camera modifier
 * into Aperture's registry (legacy {@code CameraHandler.registerModifiers()}).
 *
 * <h2>Why this is the batch's highest-priority item</h2>
 * <p>Byte id 10 / string id {@code "tracker"} was <i>reserved</i> but never
 * registered. {@code ModifierSerializer.fromJSON} looks the string up in
 * {@code ModifierRegistry.NAME_TO_CLASS} and returns {@code null} when it
 * misses; {@code ValueModifiers} skips nulls. So a 1.12.2 camera profile
 * carrying a tracker modifier loaded <b>without it</b>, and the editor's next
 * save wrote the loss back to disk. The wire format was never at risk (the id
 * was held open) — the user's files were.</p>
 *
 * <h2>Ordering is the contract</h2>
 * <p>Modifier byte ids are pure registration order, so {@code "tracker"} must
 * be registered <b>after</b> Aperture's own ten
 * ({@code angle}=0 … {@code dolly_zoom}=9) to land on 10, exactly as legacy's
 * {@code FMLPostInitializationEvent}-time registration did. {@link #install()}
 * therefore calls {@code CommonProxy.preLoad()} itself (it is idempotent and
 * synchronized) rather than trusting the caller's order, and then asserts the
 * resulting id — the same startup parity guard Aperture's fixture registration
 * uses for {@code manual != 6}.</p>
 *
 * <p>One {@code install()}, one line in {@code Blockbuster.registerContent()}
 * (S22 shared-file protocol). The client half — the timeline colour, the panel
 * class, the entity query and the capture hook — is
 * {@code mchorse.blockbuster.client.aperture.TrackerModifierClientWiring}.</p>
 *
 * Legacy source: blockbuster-1.12/src/main/java/mchorse/blockbuster/aperture/CameraHandler.java
 * ({@code registerModifiers})
 */
public final class TrackerModifierWiring
{
    /** Frozen wire/disk id: Blockbuster's tracker is the 11th modifier. */
    public static final byte TRACKER_ID = 10;

    /** Frozen JSON discriminator. */
    public static final String TRACKER_NAME = "tracker";

    private TrackerModifierWiring()
    {}

    /** Register the tracker modifier. Idempotent (the registry ignores dupes). */
    public static synchronized void install()
    {
        /* Aperture's ten modifiers first — ids are registration order. */
        CommonProxy.preLoad();

        ModifierRegistry.register(TRACKER_NAME, TrackerModifier.class);

        Byte id = ModifierRegistry.CLASS_TO_ID.get(TrackerModifier.class);

        if (id == null || id.byteValue() != TRACKER_ID)
        {
            throw new IllegalStateException("Aperture modifier registration order is broken: tracker == " + id + ", expected " + TRACKER_ID);
        }
    }
}
