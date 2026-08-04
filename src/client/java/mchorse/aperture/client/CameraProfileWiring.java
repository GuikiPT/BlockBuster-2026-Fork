package mchorse.aperture.client;

import mchorse.aperture.Aperture;
import mchorse.aperture.ClientProxy;
import mchorse.aperture.camera.CameraControl;
import mchorse.aperture.camera.CameraProfile;
import mchorse.aperture.camera.destination.ClientDestination;
import mchorse.aperture.client.gui.GuiCameraEditor;
import mchorse.blockbuster.aperture.CameraHandler;

/**
 * S22 <b>P239</b> — camera-profile callbacks &amp; autosave.
 *
 * <h2>What was dark</h2>
 * <ul>
 *   <li>{@link CameraControl#dirtyProfilesSaver} was {@code null}. The
 *       disconnect path ({@code CameraControl.reset()}) guards on it, so
 *       {@code Aperture.profileAutoSave} ({@code camera.auto_save}, default
 *       <b>true</b>) did nothing: leaving a world dropped every unsaved edit
 *       in the camera editor's profile list. <b>Installing the seam was only
 *       half of it</b> — {@code onUserLogOut} nulled
 *       {@code ClientProxy.cameraEditor} on the line <i>before</i>
 *       {@code reset()}, so {@link #saveDirtyProfiles} still bailed on the null
 *       editor every time. Fixed in {@code KeyboardHandler.onUserLogOut}, which
 *       carries the explanation.</li>
 *   <li>{@link ClientDestination#loadedProfileConsumer} was the no-op default,
 *       so a profile read off disk ({@code ClientDestination.load()}) never
 *       reached {@code GuiProfilesManager}. Picking a not-yet-loaded profile in
 *       the editor list silently kept the empty placeholder.</li>
 *   <li>{@link ClientDestination#renamedCallback} was likewise never assigned,
 *       so a successful on-disk rename left the editor list showing the old
 *       filename until the editor was reopened.</li>
 *   <li>{@link CameraHandler#cameraEditorOpen} defaulted to {@code () -> false},
 *       which killed two Blockbuster branches outright: {@code GuiRecordList}'s
 *       "only list the open scene's records" director filter and
 *       {@code GuiMorphActionPanel}'s immersive/onion-skin camera-editor mode.
 *       The real predicate already existed as
 *       {@link RenderingHandler#isCameraEditorOpen()} — it was simply never
 *       hooked up.</li>
 * </ul>
 *
 * <p>All four are pure installs of behaviour that legacy inlined into
 * client-only code; the bodies below are the legacy bodies
 * ({@code CameraControl.saveCameraProfiles}, {@code ClientDestination.load} /
 * {@code .rename}, {@code CameraHandler.isCameraEditorOpen}).</p>
 *
 * <p><b>Known remaining divergence</b> (pre-existing, not introduced here):
 * legacy {@code ClientDestination.save/load} also sent
 * {@code Aperture.l10n.success/error} chat feedback. The port logs instead —
 * {@code ClientDestination} is main-source and has no client player to send
 * to. Restoring it needs a new seam and is not part of P239.</p>
 *
 * <p>One {@code install()}, one line in {@code ApertureClient} (S22 shared-file
 * protocol).</p>
 *
 * Legacy sources:
 * .tools/legacy-src/aperture/src/main/java/mchorse/aperture/camera/CameraControl.java,
 * .tools/legacy-src/aperture/src/main/java/mchorse/aperture/camera/destination/ClientDestination.java,
 * blockbuster-1.12/src/main/java/mchorse/blockbuster/aperture/CameraHandler.java
 */
public final class CameraProfileWiring
{
    private CameraProfileWiring()
    {}

    /** Install the P239 seams. Idempotent (plain field assignment). */
    public static void install()
    {
        CameraControl.dirtyProfilesSaver = CameraProfileWiring::saveDirtyProfiles;
        ClientDestination.loadedProfileConsumer = CameraProfileWiring::addLoadedProfile;
        ClientDestination.renamedCallback = CameraProfileWiring::onRenamed;

        CameraHandler.cameraEditorOpen = RenderingHandler::isCameraEditorOpen;
    }

    /**
     * Legacy {@code CameraControl.saveCameraProfiles(GuiCameraEditor)}: walk
     * the editor's profile list and {@code save()} every dirty one.
     *
     * <p>Reads {@link ClientProxy#cameraEditor} directly (not
     * {@code getCameraEditor()}) exactly like legacy — a client that never
     * opened the editor has nothing to save and must not construct one on the
     * disconnect path.</p>
     */
    public static void saveDirtyProfiles()
    {
        GuiCameraEditor editor = ClientProxy.cameraEditor;

        if (editor == null)
        {
            return;
        }

        for (CameraProfile profile : editor.profiles.profiles.list.getList())
        {
            if (!profile.dirty)
            {
                continue;
            }

            /* Per profile, because this now actually runs on the disconnect
             * path (see KeyboardHandler.onUserLogOut). A ServerDestination
             * saves by sending a packet, and by the time DISCONNECT fires the
             * play network handler is gone — Fabric throws rather than
             * dropping it. Unguarded, that one throw would abandon every
             * remaining profile in the list and escape into the disconnect
             * event itself; a client-stored profile has no reason to go down
             * with a server-stored one. */
            try
            {
                profile.save();
            }
            catch (Exception e)
            {
                Aperture.LOGGER.error("Couldn't auto-save camera profile '" + profile.getDestination() + "' on disconnect", e);
            }
        }
    }

    /**
     * Legacy {@code ClientDestination.load()} tail:
     * {@code ClientProxy.getCameraEditor().profiles.addProfile(newProfile)} —
     * which merges into the existing list entry when one already points at the
     * same destination, and selects it in the editor.
     */
    public static void addLoadedProfile(CameraProfile profile)
    {
        if (profile == null)
        {
            return;
        }

        try
        {
            ClientProxy.getCameraEditor().profiles.addProfile(profile);
        }
        catch (Exception e)
        {
            /* Total-reader rule: a broken/absent editor must not turn a disk
             * read into a crash. */
            Aperture.LOGGER.error("Couldn't hand a loaded camera profile to the editor", e);
        }
    }

    /**
     * Legacy {@code ClientDestination.rename(name)} tail:
     * {@code ClientProxy.getCameraEditor().profiles.rename(this, name)} —
     * re-points the list entry's destination filename and re-sorts.
     */
    public static void onRenamed(ClientDestination destination, String name)
    {
        try
        {
            ClientProxy.getCameraEditor().profiles.rename(destination, name);
        }
        catch (Exception e)
        {
            Aperture.LOGGER.error("Couldn't rename camera profile '" + name + "' in the editor", e);
        }
    }
}
