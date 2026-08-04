package mchorse.blockbuster.client.video;

import mchorse.blockbuster.ClientProxy;
import mchorse.blockbuster.utils.BlockbusterPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.function.Supplier;

/**
 * Client-side "which {@code .wav} is this scene playing" tracker — the source
 * behind {@link MinemaBackend#audioResolver} (S22 P234).
 *
 * <h2>Why the packet is the source of truth</h2>
 * <p>Scenes live on the server ({@code Scene}/{@code AudioHandler}, S11/S16);
 * the client never holds a {@code Scene} object, only the {@code SceneLocation}
 * name the camera editor is bound to. The one thing the client <i>is</i> told is
 * {@code PacketAudio} — the scene's audio file name, its state and its shift,
 * broadcast by {@code AudioHandler} on every play/pause/goTo/stop. That packet
 * is therefore the audio track to mux, and the client audio handler feeds it
 * here on its way into the S16 {@code AudioLibrary}.</p>
 *
 * <p>The name is remembered across state changes (including {@code STOP}) on
 * purpose: legacy's recording flow is "start recording → the panel rewinds and
 * starts playback", so at the moment {@link MinemaBackend} builds its
 * {@link VideoParams} the scene audio has usually just been <i>stopped</i> by
 * the rewind. Keeping the last known track is what makes the muxed file
 * non-silent. It is cleared on disconnect ({@link #reset()}).</p>
 *
 * <h2>v1 limitation: start-aligned audio only</h2>
 * <p>{@code PacketAudio.shift} carries the scene's audio offset (S16
 * {@code PacketAudioShift}). The P202 sketch scopes v1 to start-aligned audio;
 * a non-zero shift is muxed unshifted with a logged warning rather than dropped,
 * so the user still gets a track to nudge in an editor.</p>
 */
public final class SceneAudioTracker
{
    private static final Logger LOGGER = LoggerFactory.getLogger("blockbuster-video");

    /**
     * The {@code config/blockbuster/audio} folder. Resolved through the live
     * {@code ClientProxy.audio} library when it exists (so a relocated library
     * is honoured) and through {@link BlockbusterPaths} otherwise — which is
     * also the seam headless tests override.
     */
    public static Supplier<File> audioFolder = SceneAudioTracker::defaultFolder;

    private static volatile String name = "";
    private static volatile int shift;

    private SceneAudioTracker()
    {}

    /** Record the audio track a {@code PacketAudio} just referenced. */
    public static void accept(String audio, int shift)
    {
        if (audio == null || audio.isEmpty())
        {
            return;
        }

        name = audio;
        SceneAudioTracker.shift = shift;
    }

    /** Forget the tracked track (disconnect / world unload). */
    public static void reset()
    {
        name = "";
        shift = 0;
    }

    /** The last audio track name the client was told about ({@code ""} if none). */
    public static String name()
    {
        return name;
    }

    /** The last audio shift in ticks (0 = start-aligned). */
    public static int shift()
    {
        return shift;
    }

    private static File defaultFolder()
    {
        if (ClientProxy.audio != null && ClientProxy.audio.folder != null)
        {
            return ClientProxy.audio.folder;
        }

        return BlockbusterPaths.audio().toFile();
    }

    /**
     * Resolve the {@code .wav} to mux into the next recording, or {@code null}
     * for a silent video (no scene audio, audio muxing disabled, or the file is
     * gone). Never throws — {@link MinemaBackend} treats a failure as "no
     * audio", and so does this.
     */
    public static File resolve()
    {
        /* S22/P250: live video.audio read. As a folded `static final true`
         * constant this guard compiled away entirely, so switching muxing off
         * was impossible. */
        if (!VideoConfig.audio())
        {
            return null;
        }

        String track = name;

        if (track == null || track.isEmpty())
        {
            return null;
        }

        File folder = audioFolder.get();

        if (folder == null)
        {
            return null;
        }

        File file = new File(folder, track + ".wav");

        if (!file.isFile())
        {
            LOGGER.warn("Scene audio '{}' is not on disk ({}); recording without audio", track, file);

            return null;
        }

        if (shift != 0)
        {
            LOGGER.warn("Scene audio '{}' has a shift of {} ticks; v1 muxes start-aligned audio only — the track will not be offset in the exported video",
                track, shift);
        }

        return file;
    }
}
