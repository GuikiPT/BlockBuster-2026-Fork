package mchorse.aperture.camera.destination;

import mchorse.aperture.Aperture;
import mchorse.aperture.camera.CameraProfile;
import mchorse.mclib.utils.resources.RLUtils;
import mchorse.mclib.utils.resources.ResourceLocation;

/**
 * Server destination (P181) — proxies save/load/rename/remove via the
 * Aperture channel packets.
 *
 * Port notes: this class keeps the legacy shape by delegating each operation to
 * a {@link NetworkProxy} seam rather than referencing the packet classes
 * directly. P182 landed and fills the seam in
 * {@code mchorse.aperture.CommonProxy#load} with the four legacy packets
 * ({@code PacketCameraProfile}, {@code PacketLoadCameraProfile},
 * {@code PacketRenameCameraProfile}, {@code PacketRemoveCameraProfile} on
 * {@code mchorse.aperture.network.Dispatcher}).
 *
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/camera/destination/ServerDestination.java
 */
public class ServerDestination extends AbstractDestination
{
    /**
     * P182 seam mirroring the four legacy client→server packets.
     */
    public interface NetworkProxy
    {
        void saveProfile(String filename, CameraProfile profile);

        void loadProfile(String filename, boolean force);

        void renameProfile(String from, String to);

        void removeProfile(String filename);
    }

    /**
     * Installed by P182's Dispatcher registration ({@code CommonProxy.load()},
     * mod init) and therefore non-null for the whole life of a running game;
     * null means the initializer has not run (tests, or a broken init order)
     * and every operation degrades to warn + no-op.
     */
    public static NetworkProxy network;

    public ServerDestination(String filename)
    {
        super(filename);
    }

    @Override
    public boolean equals(Object obj)
    {
        return super.equals(obj) && obj instanceof ServerDestination;
    }

    @Override
    public void rename(String name)
    {
        if (network != null)
        {
            network.renameProfile(this.filename, name);
        }
        else
        {
            this.warn("rename");
        }
    }

    @Override
    public void save(CameraProfile profile)
    {
        if (network != null)
        {
            network.saveProfile(this.filename, profile);
        }
        else
        {
            this.warn("save");
        }
    }

    @Override
    public void load()
    {
        if (network != null)
        {
            network.loadProfile(this.filename, true);
        }
        else
        {
            this.warn("load");
        }
    }

    @Override
    public void remove()
    {
        if (network != null)
        {
            network.removeProfile(this.filename);
        }
        else
        {
            this.warn("remove");
        }
    }

    /**
     * Unreachable in a running game (see {@link #network}); the guard exists so
     * a broken init order logs instead of NPE-ing inside the camera editor.
     */
    private void warn(String operation)
    {
        Aperture.LOGGER.warn("ServerDestination." + operation + "('" + this.filename + "') called before mchorse.aperture.CommonProxy.load() installed ServerDestination.network — ignored");
    }

    @Override
    public ResourceLocation toResourceLocation()
    {
        return RLUtils.create("server", this.filename);
    }
}
