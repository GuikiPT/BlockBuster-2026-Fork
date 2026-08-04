package mchorse.aperture.events;

import mchorse.aperture.camera.CameraProfile;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Camera profile was changed event class (P170).
 *
 * Port notes: legacy posted this on {@code MinecraftForge.EVENT_BUS} — the
 * game bus, not McLib's own bus, so the P22 {@code McLibEvents} replacement
 * (mod-to-mod config/permission/multiskin registration) is deliberately not
 * its home. The event keeps its own minimal listener registry instead:
 * {@code CameraProfile.setDirty} posts to it (including for
 * {@code setDirty(false)}, which the P183 SAVE→SAVED icon flip depends on),
 * and the client entrypoint subscribes legacy's sole handler
 * ({@code KeyboardHandler.onCameraProfileChanged} → {@code GuiCameraEditor
 * .updateSaveButton}). Posting is common-side and total: no subscribers
 * (dedicated server / headless) is simply a no-op.
 *
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/events/CameraProfileChangedEvent.java
 */
public class CameraProfileChangedEvent
{
    /**
     * Port event seam — subscribers to profile-change notifications.
     */
    public static final List<Consumer<CameraProfileChangedEvent>> LISTENERS = new CopyOnWriteArrayList<Consumer<CameraProfileChangedEvent>>();

    /**
     * Changed camera profile.
     *
     * Technically this camera profile can be substituted, but I'm not sure how
     * this application can be useful.
     */
    public CameraProfile profile;

    public CameraProfileChangedEvent(CameraProfile profile)
    {
        this.profile = profile;
    }

    /**
     * Post this event to all subscribers (legacy
     * {@code MinecraftForge.EVENT_BUS.post}).
     */
    public static void post(CameraProfileChangedEvent event)
    {
        for (Consumer<CameraProfileChangedEvent> listener : LISTENERS)
        {
            listener.accept(event);
        }
    }
}
