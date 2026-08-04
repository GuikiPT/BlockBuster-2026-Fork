package mchorse.aperture.camera.destination;

import mchorse.aperture.camera.CameraProfile;
import mchorse.mclib.utils.resources.ResourceLocation;

import java.io.File;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Abstract destination class (P181).
 *
 * Port notes: destinations encode as resource locations with domain
 * {@code "client"} or {@code "server"} (bundled non-validating
 * {@link ResourceLocation}, since profile filenames are not
 * Identifier-charset-safe). Legacy {@code create()} was
 * {@code @SideOnly(CLIENT)} and picked {@code ServerDestination} only when
 * connected to an Aperture server AND the player is OP
 * ({@code ClientProxy.server && OpHelper.isPlayerOp()}); the port keeps the
 * method here behind the {@link #serverDestinationCheck} seam installed by
 * the client entrypoint (headless default: client destination).
 *
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/camera/destination/AbstractDestination.java
 */
public abstract class AbstractDestination
{
    /**
     * Port seam: "connected to an Aperture server and OP" check (legacy
     * {@code ClientProxy.server && OpHelper.isPlayerOp()}), installed by
     * {@code mchorse.aperture.client.ApertureClient}.
     */
    public static BooleanSupplier serverDestinationCheck = () -> false;

    /**
     * Port seam: client cameras folder (legacy
     * {@code ClientProxy.getClientCameras()}), installed by the client
     * entrypoint. Null in the main menu / headless.
     */
    public static Supplier<File> clientCameras = () -> null;

    protected String filename;

    /**
     * Create destination from resource location
     */
    public static AbstractDestination fromResourceLocation(ResourceLocation resource)
    {
        if (resource.getResourceDomain().equals("client"))
        {
            return new ClientDestination(resource.getResourcePath());
        }

        return new ServerDestination(resource.getResourcePath());
    }

    /**
     * Create an abstract destination based on the game's state (i.e.
     * whether it's single or multiplayer game).
     */
    public static AbstractDestination create(String filename)
    {
        return serverDestinationCheck.getAsBoolean() ? new ServerDestination(filename) : new ClientDestination(filename);
    }

    /**
     * Abstract fixture's constructor. It accepts only the filename of a fixture.
     */
    public AbstractDestination(String filename)
    {
        this.setFilename(filename);
    }

    public String getFilename()
    {
        return this.filename;
    }

    public void setFilename(String filename)
    {
        this.filename = filename;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof AbstractDestination)
        {
            return this.filename.equals(((AbstractDestination) obj).getFilename());
        }

        return super.equals(obj);
    }

    /**
     * Rename camera profile
     */
    public abstract void rename(String name);

    /**
     * Save given camera profile
     */
    public abstract void save(CameraProfile profile);

    /**
     * Reload camera profile
     */
    public abstract void load();

    /**
     * Remove a camera profile
     */
    public abstract void remove();

    /**
     * Create a resource location out of destination
     */
    public abstract ResourceLocation toResourceLocation();
}
