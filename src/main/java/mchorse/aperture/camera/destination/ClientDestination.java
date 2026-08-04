package mchorse.aperture.camera.destination;

import mchorse.aperture.Aperture;
import mchorse.aperture.camera.CameraProfile;
import mchorse.aperture.camera.CameraUtils;
import mchorse.mclib.utils.AtomicWrite;
import mchorse.mclib.utils.resources.RLUtils;
import mchorse.mclib.utils.resources.ResourceLocation;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Client destination (P181) — saves and reloads camera profiles from
 * Aperture's config folder ({@code config/aperture/cameras/<world key>/}).
 *
 * Port notes: the cameras folder resolves through
 * {@link AbstractDestination#clientCameras} (legacy
 * {@code ClientProxy.getClientCameras()}); GUI side effects — the profiles
 * manager rename/merge and the l10n chat feedback — are deferred to P183
 * behind {@link #loadedProfileConsumer}/{@link #renamedCallback} (default
 * no-op). File contents are byte-identical to legacy (pretty-printed
 * 4-space JSON, UTF-8).
 *
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/camera/destination/ClientDestination.java
 */
public class ClientDestination extends AbstractDestination
{
    /**
     * P183 seam: receives freshly loaded profiles (legacy
     * {@code ClientProxy.getCameraEditor().profiles.addProfile(...)}).
     */
    public static Consumer<CameraProfile> loadedProfileConsumer = profile -> {};

    /**
     * P183 seam: notified after a successful on-disk rename (legacy
     * {@code profiles.rename(this, name)}).
     */
    public static BiConsumer<ClientDestination, String> renamedCallback = (destination, name) -> {};

    public ClientDestination(String filename)
    {
        super(filename);
    }

    @Override
    public boolean equals(Object obj)
    {
        return super.equals(obj) && obj instanceof ClientDestination;
    }

    @Override
    public void rename(String name)
    {
        File from = new File(clientCameras.get(), this.filename + ".json");
        File to = new File(clientCameras.get(), name + ".json");

        if (from.renameTo(to))
        {
            renamedCallback.accept(this, name);
        }
    }

    @Override
    public void save(CameraProfile profile)
    {
        try
        {
            File file = new File(clientCameras.get(), this.filename + ".json");

            /* P284: Files.write truncates before the new JSON exists and client
             * camera profiles have no backup chain, so a failure mid-write was
             * the end of the profile. Same bytes, written atomically. */
            AtomicWrite.writeString(file, CameraUtils.toJSON(profile));
        }
        catch (Exception e)
        {
            Aperture.LOGGER.error("Couldn't save client camera profile '" + this.filename + "'", e);
        }
    }

    @Override
    public void load()
    {
        try
        {
            File file = new File(clientCameras.get(), this.filename + ".json");
            String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            CameraProfile newProfile = CameraUtils.readProfileFromJSON(json);

            newProfile.setDestination(this);
            newProfile.dirty = false;

            loadedProfileConsumer.accept(newProfile);
        }
        catch (Exception e)
        {
            Aperture.LOGGER.error("Couldn't load client camera profile '" + this.filename + "'", e);
        }
    }

    @Override
    public void remove()
    {
        new File(clientCameras.get(), this.filename + ".json").delete();
    }

    @Override
    public ResourceLocation toResourceLocation()
    {
        return RLUtils.create("client", this.filename);
    }
}
