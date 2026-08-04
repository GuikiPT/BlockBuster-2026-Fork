package mchorse.blockbuster.client.video;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * {@link FrameSink} that streams raw frames to an external {@code ffmpeg}
 * process over stdin (P201).
 *
 * <p>Process lifecycle mirrors the BBS pattern minus its {@code Unsafe} tricks:
 * {@code redirectErrorStream(true)} + a file redirect so an un-drained stderr
 * can never deadlock the encoder; frames written through a
 * {@link WritableByteChannel} over stdin at full-frame granularity (no tiny
 * {@code BufferedOutputStream} to unwrap — we hand it whole frames, so the
 * default buffering is not the throughput bottleneck BBS fought). Closing stdin
 * (not killing the process) is what tells ffmpeg to finalize the container, so
 * {@link #end()} does {@code channel.close()} → {@code waitFor(timeout)} →
 * {@code destroy()} in that order.</p>
 */
public class FfmpegSink implements FrameSink
{
    private static final Logger LOGGER = LoggerFactory.getLogger("blockbuster-video");

    private final List<String> args;
    private final File workingDir;
    private final File logFile;

    private Process process;
    private WritableByteChannel channel;

    public FfmpegSink(List<String> args, File workingDir, File logFile)
    {
        this.args = args;
        this.workingDir = workingDir;
        this.logFile = logFile;
    }

    /**
     * Assemble a sink from recording parameters and a resolved binary. The
     * argument template (audio vs. video-only) is chosen by whether
     * {@code params} carries an audio track.
     */
    public static FfmpegSink create(VideoParams params, String binary, String videoTemplate, String audioTemplate, boolean encoderLog)
    {
        String template = params.hasAudio() ? audioTemplate : videoTemplate;
        String audio = params.hasAudio() ? params.audioTrack().getAbsolutePath() : null;
        List<String> args = FfmpegArgs.build(binary, template, params, params.name(), audio);

        File dir = params.exportDir();
        File log = encoderLog ? new File(dir, params.name() + ".log") : new File(dir, "video.log");

        return new FfmpegSink(args, dir, log);
    }

    public List<String> args()
    {
        return this.args;
    }

    /**
     * The file ffmpeg's merged stdout/stderr is redirected to: a shared
     * {@code video.log} next to the output, or a per-recording
     * {@code <name>.log} when {@code video.encoder_log} is on.
     */
    public File logFile()
    {
        return this.logFile;
    }

    @Override
    public void begin(int width, int height, VideoFormat format) throws IOException
    {
        if (this.workingDir != null)
        {
            this.workingDir.mkdirs();
        }

        LOGGER.info("Recording video with ffmpeg arguments: {}", this.args);

        ProcessBuilder builder = new ProcessBuilder(this.args);

        builder.redirectErrorStream(true);

        if (this.logFile != null)
        {
            builder.redirectOutput(this.logFile);
        }

        if (this.workingDir != null)
        {
            builder.directory(this.workingDir);
        }

        this.process = builder.start();
        this.channel = Channels.newChannel(this.process.getOutputStream());
    }

    @Override
    public void frame(ByteBuffer data) throws IOException
    {
        if (this.channel == null)
        {
            throw new IOException("ffmpeg sink written before begin()");
        }

        while (data.hasRemaining())
        {
            this.channel.write(data);
        }
    }

    @Override
    public void end() throws IOException
    {
        try
        {
            if (this.channel != null && this.channel.isOpen())
            {
                this.channel.close();
            }
        }
        finally
        {
            this.channel = null;

            if (this.process != null)
            {
                try
                {
                    if (!this.process.waitFor(1, TimeUnit.MINUTES))
                    {
                        LOGGER.warn("ffmpeg did not exit within a minute; destroying");
                    }
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
                finally
                {
                    this.process.destroy();
                    this.process = null;
                }
            }
        }
    }
}
