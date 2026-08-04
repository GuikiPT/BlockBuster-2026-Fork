package mchorse.blockbuster.audio;

import mchorse.mclib.utils.wav.WavePlayer;
import mchorse.mclib.utils.wav.Waveform;
import org.lwjgl.openal.AL10;

import java.io.File;

/**
 * Port of Blockbuster 2.7.2's {@code audio/AudioFile} (roadmap P188).
 *
 * <p>Holds a lazily-loaded {@link WavePlayer} + {@link Waveform} for one
 * {@code .wav} on disk plus its last-known mtime ({@link #update}) which drives
 * hot reload via {@link #canBeUpdated()}. A parse failure leaves both handles
 * {@code null} ({@link #isEmpty()}), which is cached under the name as an empty
 * placeholder by {@code AudioLibrary}.</p>
 *
 * <p>Client-side class (references AL/GL types); legacy had no {@code @SideOnly}
 * but was only ever touched from {@code ClientProxy}.</p>
 */
public class AudioFile
{
    public String name;
    public File file;
    public WavePlayer player;
    public Waveform waveform;
    public long update;

    private boolean wasPaused;

    public AudioFile(String name, File file, WavePlayer player, Waveform waveform, long update)
    {
        this.name = name;
        this.file = file;
        this.player = player;
        this.waveform = waveform;
        this.update = update;
    }

    public boolean canBeUpdated()
    {
        return this.update < this.file.lastModified();
    }

    public boolean isEmpty()
    {
        return this.player == null || this.waveform == null;
    }

    public void delete()
    {
        if (this.player != null)
        {
            this.player.delete();
            this.player = null;
        }

        if (this.waveform != null)
        {
            this.waveform.delete();
            this.waveform = null;
        }
    }

    /**
     * Game-pause bridging (P188.1). Reads the raw AL source state (deliberately
     * bypassing mod-level state) and remembers a source that was already
     * {@code AL_PAUSED} before the game paused, so a user-paused track does not
     * wrongly resume when the ESC menu closes.
     *
     * <p>The lifecycle wiring that calls this on the game-pause edge is P188.1's
     * client tick hook — this phase ports the method itself.</p>
     */
    public void pause(boolean pause)
    {
        if (this.player == null) return;

        int state = this.player.getSourceState();

        if (!pause && this.wasPaused)
        {
            this.wasPaused = false;

            return;
        }

        this.wasPaused = pause && state == AL10.AL_PAUSED;

        if (pause && state == AL10.AL_PLAYING)
        {
            this.player.pause();
        }
        else if (!pause && state == AL10.AL_PAUSED)
        {
            this.player.play();
        }
    }

    /**
     * Test seam (P188.1): the {@code wasPaused} handshake flag. Legacy keeps
     * the field private with no accessor; package-private here so the
     * 6-sequence bridge matrix can assert the flag transitions directly instead
     * of inferring them from a third call.
     */
    boolean wasPaused()
    {
        return this.wasPaused;
    }
}
