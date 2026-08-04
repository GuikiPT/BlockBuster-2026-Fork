package mchorse.aperture.camera;

import mchorse.aperture.Aperture;
import mchorse.aperture.camera.destination.AbstractDestination;
import mchorse.aperture.camera.destination.ServerDestination;
import mchorse.mclib.utils.resources.ResourceLocation;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.List;

/**
 * Camera API (P181) — server- and client-side profile listing and playback
 * kickoff used by Blockbuster's scene playback-item handling.
 *
 * Port notes: {@code getClientProfiles} was {@code @SideOnly(CLIENT)} and
 * resolves via the {@link AbstractDestination#clientCameras} seam;
 * client-destination playback ({@code PacketCameraState}) goes through the
 * {@link CameraUtils#sender} seam, which P182 installs from
 * {@code mchorse.aperture.CommonProxy#load} at mod init.
 *
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/camera/CameraAPI.java
 */
public class CameraAPI
{
    /**
     * Server side code to start playing camera by providing a resource location
     */
    public static void playCameraProfile(ServerPlayerEntity player, ResourceLocation resource)
    {
        playCameraProfile(player, AbstractDestination.fromResourceLocation(resource));
    }

    /**
     * Server side code to start playing camera by providing an abstract destination
     */
    public static void playCameraProfile(ServerPlayerEntity player, AbstractDestination destination)
    {
        if (destination instanceof ServerDestination)
        {
            CameraUtils.sendProfileToPlayer(destination.getFilename(), player, true, false);
        }
        else if (CameraUtils.sender != null)
        {
            CameraUtils.sender.sendPlayState(destination.getFilename(), player);
        }
        else
        {
            /* Unreachable in a running game: CommonProxy.load() installs the
             * sender from Blockbuster's mod initializer, before any player can
             * hold a playback button. Kept (and honest about the condition) so
             * a broken init order degrades to a log line, not an NPE. */
            Aperture.LOGGER.warn("playCameraProfile('" + destination.getFilename() + "') called before mchorse.aperture.CommonProxy.load() installed CameraUtils.sender — ignored");
        }
    }

    /**
     * Server side code to start playing the current camera profile that the
     * player has (legacy {@code CameraAPI.playCurrentProfile} sent
     * {@code PacketCameraState(true)}).
     *
     * <p>Routed through the same {@link CameraUtils#sender} seam as
     * {@link #playCameraProfile}, which P182 installs at mod init.</p>
     */
    public static void playCurrentProfile(ServerPlayerEntity player)
    {
        if (CameraUtils.sender != null)
        {
            CameraUtils.sender.sendPlayState(null, player);
        }
        else
        {
            /* See playCameraProfile: not reachable once the mod initializer has
             * run; the guard only keeps a broken init from crashing. */
            Aperture.LOGGER.warn("playCurrentProfile() called before mchorse.aperture.CommonProxy.load() installed CameraUtils.sender — ignored");
        }
    }

    /**
     * Get a list of camera profile names on the client side (from the config)
     */
    public static List<String> getClientProfiles()
    {
        List<String> files = new ArrayList<String>();
        File folder = AbstractDestination.clientCameras.get();
        File[] profiles = null;

        if (folder != null)
        {
            profiles = folder.listFiles(new JSONFileFilter());
        }

        if (profiles != null)
        {
            for (File file : profiles)
            {
                String filename = file.getName();

                filename = filename.substring(0, filename.lastIndexOf(".json"));
                files.add(filename);
            }
        }

        return files;
    }

    /**
     * Get a list of camera profile names on the server side (from world save
     * aperture/cameras folder).
     */
    public static List<String> getServerProfiles()
    {
        File file = new File(CameraUtils.serverDirectory, "aperture/cameras");
        List<String> files = new ArrayList<String>();

        file.mkdirs();

        for (File profile : file.listFiles(new JSONFileFilter()))
        {
            String filename = profile.getName();

            files.add(filename.substring(0, filename.lastIndexOf(".json")));
        }

        return files;
    }

    /**
     * JSON file filter
     */
    public static class JSONFileFilter implements FileFilter
    {
        @Override
        public boolean accept(File file)
        {
            return file.isFile() && file.getName().endsWith(".json");
        }
    }
}
