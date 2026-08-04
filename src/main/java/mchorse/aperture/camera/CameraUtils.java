package mchorse.aperture.camera;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mchorse.aperture.Aperture;
import mchorse.aperture.capabilities.camera.Camera;
import mchorse.aperture.capabilities.camera.ICamera;
import mchorse.mclib.utils.AtomicWrite;
import mchorse.mclib.utils.JsonUtils;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Scanner;

/**
 * Utilities for camera classes (P170/P181).
 *
 * Port notes:
 * <ul>
 * <li>The server storage root ({@code <world save>/aperture/cameras/} —
 * NOT under {@code blockbuster/}; folder parity for old worlds) resolves
 * through {@link #serverDirectory}, set from
 * {@code ServerLifecycleEvents.SERVER_STARTED}
 * ({@code server.getSavePath(WorldSavePath.ROOT)}) by
 * {@code mchorse.aperture.CommonProxy}; legacy used
 * {@code DimensionManager.getCurrentSaveRootDirectory()}.</li>
 * <li>{@code sendProfileToPlayer}'s packet sends are P182 — routed through
 * the {@link #sender} seam (null = staleness bookkeeping still happens,
 * the send is logged and skipped). The staleness rule is preserved: skip
 * resend if the player capability holds the same name with a
 * newer-or-equal timestamp vs file mtime, unless {@code force}.</li>
 * <li>{@code parseAspectRation} — the misspelling is public API surface;
 * keep it.</li>
 * </ul>
 *
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/camera/CameraUtils.java
 */
public class CameraUtils
{
    /**
     * P182 seam mirroring the legacy server→client sends inside
     * sendProfileToPlayer/playerHasProfile.
     */
    public interface ProfileSender
    {
        /** Legacy {@code Dispatcher.sendTo(new PacketCameraProfile(filename, profile, play), player)} */
        void sendProfile(String filename, CameraProfile profile, boolean play, ServerPlayerEntity player);

        /** Legacy {@code Dispatcher.sendTo(new PacketCameraState(filename, true), player)} */
        void sendPlayState(String filename, ServerPlayerEntity player);
    }

    /**
     * Installed by P182 from {@code mchorse.aperture.CommonProxy#load} at mod
     * init, so it is non-null for the whole life of a running game; null means
     * the initializer has not run (tests, or a broken init order) and reduces
     * every send to local bookkeeping plus a warning.
     */
    public static ProfileSender sender;

    /**
     * Current world-save root ({@code <save>/.}) — set on server start,
     * nulled on stop. Tests may point this at a temp dir.
     */
    public static File serverDirectory;

    /**
     * Get path to camera profile file (located in current world save's folder)
     */
    public static File cameraFile(String filename)
    {
        File file = new File(serverDirectory, "aperture/cameras");

        if (!file.exists())
        {
            file.mkdirs();
        }

        return new File(file, filename + ".json");
    }

    /**
     * Read CameraProfile instance from given file
     */
    public static String readCameraProfile(String filename) throws Exception
    {
        File file = cameraFile(filename);
        DataInputStream stream = new DataInputStream(new FileInputStream(file));
        Scanner scanner = new Scanner(stream, "UTF-8");
        String content = scanner.useDelimiter("\\A").next();

        scanner.close();

        return content;
    }

    /**
     * Write CameraProfile instance to given file.
     *
     * <p><b>P284 deviation from 1.12.2, twice over.</b></p>
     *
     * <p>Legacy was {@code new PrintWriter(cameraFile(filename))} +
     * {@code print} + {@code close}, and both halves of that are unsafe.
     * {@link PrintWriter}'s {@code File} constructor <b>truncates the profile on
     * disk before a single byte of the new one is written</b>, and camera
     * profiles have no backup chain — that truncated file is the only copy of
     * the user's camera work. Worse, {@code PrintWriter} <i>never throws</i>:
     * it swallows every {@link IOException} into an internal error flag that
     * nothing here read, so a write that failed on a full disk still returned
     * normally and {@link #saveCameraProfile} told the player
     * {@code profile.save} — success — over an emptied file.</p>
     *
     * <p>Written atomically instead, and any I/O failure now propagates so
     * {@code saveCameraProfile} reports {@code profile.cant_save} truthfully.
     * The bytes on disk are unchanged.</p>
     */
    public static void writeCameraProfile(String filename, String profile) throws IOException
    {
        AtomicWrite.writeString(cameraFile(filename), profile);
    }

    /* Commands */

    /**
     * Send a camera profile that was read from given file to player.
     *
     * This method also checks if player has same named camera profile, and if
     * it's expired (server has newer version), send him new one.
     */
    public static void sendProfileToPlayer(String filename, ServerPlayerEntity player, boolean play, boolean force)
    {
        try
        {
            if (!force && playerHasProfile(player, filename, play))
            {
                return;
            }

            if (!cameraFile(filename).isFile())
            {
                Aperture.l10n.error(player, "profile.cant_load", filename);

                return;
            }

            CameraProfile profile = readProfile(filename);
            ICamera recording = Camera.get(player);

            recording.setCurrentProfile(filename);
            recording.setCurrentProfileTimestamp(System.currentTimeMillis());

            if (sender != null)
            {
                sender.sendProfile(filename, profile, play, player);
            }
            else
            {
                /* Unreachable in a running game — CommonProxy.load() assigns
                 * `sender` from the mod initializer. Kept so a broken init
                 * order logs instead of NPE-ing mid-command. */
                Aperture.LOGGER.warn("sendProfileToPlayer('" + filename + "') called before mchorse.aperture.CommonProxy.load() installed CameraUtils.sender — profile read and capability updated, send skipped");
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            Aperture.l10n.error(player, "profile.cant_load", filename);
        }
    }

    public static CameraProfile readProfile(String filename) throws Exception
    {
        return readProfileFromJSON(readCameraProfile(filename));
    }

    /**
     * Parse a camera profile out of JSON. Total by design — bad input yields an
     * <b>empty</b> profile rather than throwing.
     *
     * <p><b>P284 audit note (behaviour unchanged, logging added).</b> That empty
     * profile is a placeholder, and this port hands it to the camera editor,
     * which will happily save it back over the file it came from — a profile has
     * no backup chain, so a transient read failure would become permanent loss.
     * The same is true one level down and cannot be guarded from here:
     * {@code ValueFixtures.fromJSON} drops any fixture whose {@code type} is not
     * in {@code FixtureRegistry} and any entry that is not a JSON object, and
     * {@code FixtureSerializer.fromJSON} keeps a fixture whose own
     * {@code fromJSON} threw half-way. Both are silent, both are legacy-faithful,
     * and both re-serialize lossily.</p>
     *
     * <p>The port is <i>not</i> exposed to the unknown-type leg today: its
     * {@code FixtureRegistry}/{@code ModifierRegistry} registrations were
     * diffed against Aperture 1.8.2 during P284 and are identical (7 fixtures,
     * 10 modifiers), so no profile authored by 1.12.2 Aperture can carry a type
     * this build does not know. The warning below is what makes it visible if
     * that ever stops being true.</p>
     */
    public static CameraProfile readProfileFromJSON(String json) throws Exception
    {
        CameraProfile profile = new CameraProfile(null);
        JsonElement element = new JsonParser().parse(json);

        if (!element.isJsonObject())
        {
            Aperture.LOGGER.warn("Camera profile JSON is not an object — returning an empty placeholder profile. Saving it back will replace the file it came from.");

            return profile;
        }

        profile.fromJSON(element.getAsJsonObject());

        return profile;
    }

    /**
     * Checks whether player has older camera profile
     */
    static boolean playerHasProfile(ServerPlayerEntity player, String filename, boolean play)
    {
        ICamera recording = Camera.get(player);
        File profile = cameraFile(filename);

        if (hasSameNewerProfile(recording, filename, profile))
        {
            if (play)
            {
                if (sender != null)
                {
                    sender.sendPlayState(filename, player);
                }
            }
            else
            {
                Aperture.l10n.info(player, "profile.loaded", filename);
            }

            return true;
        }

        return false;
    }

    /**
     * The pure staleness rule (extracted for headless testing): the player
     * already has this profile when the names match AND the capability
     * timestamp is newer-or-equal than the file's mtime.
     */
    public static boolean hasSameNewerProfile(ICamera recording, String filename, File profile)
    {
        boolean hasSame = recording.currentProfile().equals(filename);
        boolean isNewer = recording.currentProfileTimestamp() >= profile.lastModified();

        return hasSame && isNewer;
    }

    /**
     * Save given camera profile to file. Inform user about the problem, if the
     * camera profile couldn't be saved.
     */
    public static boolean saveCameraProfile(String filename, String profile, ServerPlayerEntity player)
    {
        try
        {
            writeCameraProfile(filename, profile);
        }
        catch (IOException e)
        {
            e.printStackTrace();
            Aperture.l10n.error(player, "profile.cant_save", filename);

            return false;
        }

        return true;
    }

    /**
     * Turn a camera profile into JSON string
     *
     * This method is also responsible for doing JSON prettifying, such as
     * making sure there are 4 spaces for indentation.
     */
    public static String toJSON(CameraProfile profile)
    {
        JsonObject object = new JsonObject();

        profile.toJSON(object);

        return JsonUtils.jsonToPretty(object);
    }

    /**
     * Rename camera profile
     */
    public static boolean renameProfile(String from, String to)
    {
        File fromFile = cameraFile(from);
        File toFile = cameraFile(to);

        return fromFile.renameTo(toFile);
    }

    /**
     * Rename camera profile
     */
    public static boolean removeProfile(String profile)
    {
        File file = cameraFile(profile);

        return file.delete();
    }

    public static float parseAspectRation(String ratio, float old)
    {
        try
        {
            return Float.parseFloat(ratio);
        }
        catch (Exception e)
        {
            try
            {
                String[] strips = ratio.split(":");

                if (strips.length >= 2)
                {
                    return Float.parseFloat(strips[0]) / Float.parseFloat(strips[1]);
                }
            }
            catch (Exception ee)
            {}
        }

        return old;
    }
}
