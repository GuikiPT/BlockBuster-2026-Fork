package mchorse.blockbuster.client.video;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * The recorder core (P200/P201): pulls one output frame at a time from a
 * {@link FrameSource} and pushes it through a {@link FrameQueue} that an encoder
 * thread drains into the selected {@link FrameSink} (ffmpeg or the PNG-sequence
 * fallback, P201).
 *
 * <p>In production the source is {@link FramebufferFrameSource} — the GL
 * readback of the main framebuffer's colour attachment — installed by
 * {@link VideoCaptureWiring} (S22 P234). Headless tests drive the very same
 * class with a synthetic source, which is what the {@link FrameSource} seam
 * exists for.</p>
 *
 * <h2>Why the frame source is pulled, not pushed (S22 P234 parity note)</h2>
 * <p>P200 originally landed this class with an inlined two-slot PBO ring: frame
 * {@code N} was read into slot A while slot B (holding frame {@code N-1}) was
 * mapped, so the readback DMA overlapped the encoder write. That shape is
 * <b>not expressible</b> through a 1:1 {@code readFrame(dst)} seam — the first
 * mapped read is garbage ({@code counter != 0} guard) and the last rendered
 * frame needs a separate flush, i.e. {@code N} renders produce {@code N-1}
 * frames plus an epilogue. Wiring the recorder into the live client (P234)
 * required the seam, so the ring was collapsed into a synchronous readback and
 * the recorder now delivers <b>exactly one frame per {@link #recordFrame()}
 * call</b>, with no priming and no flush epilogue. That also settles S18 open
 * question 1 (flush vs. drop) in favour of "every rendered output frame is
 * encoded, exactly once".</p>
 *
 * <p>The cost is a GL sync per captured frame, which is irrelevant here: the
 * P199 fixed-timestep clock already decouples capture from wall time, so a
 * slower readback makes the recording take longer in real seconds without
 * changing a single output byte. Frame-perfect beats realtime (same reasoning
 * as {@link FrameQueue}'s blocking backpressure).</p>
 */
public class VideoRecorder
{
    private static final Logger LOGGER = LoggerFactory.getLogger("blockbuster-video");

    private boolean recording;
    private boolean failed;
    private VideoParams params;
    private FrameSource source;
    private FrameSink sink;
    private FrameQueue queue;

    /** Direct staging buffer handed to the GL readback; sized to one frame. */
    private ByteBuffer scratch;

    private int frames;

    public boolean isRecording()
    {
        return this.recording;
    }

    /**
     * Whether the last recording ended because the pipeline failed (sink could
     * not be opened, or the encoder thread died). Drives the P202
     * premature-stop modal.
     */
    public boolean hasFailed()
    {
        return this.failed;
    }

    public VideoParams params()
    {
        return this.params;
    }

    /** Output frames handed to the sink pipeline so far. */
    public int frames()
    {
        return this.frames;
    }

    /**
     * Begin a recording.
     *
     * @param source          where each output frame's pixels come from — the GL
     *                        framebuffer readback in game, a generator in tests
     * @param ffmpegAvailable result of {@link FfmpegLocator#checkAvailable}
     *                        (false ⇒ the PNG-sequence fallback is selected)
     */
    public void startRecording(VideoParams params, FrameSource source, String ffmpegPath, boolean ffmpegAvailable, boolean encoderLog)
    {
        if (this.recording)
        {
            return;
        }

        this.params = params;
        this.source = source;
        this.failed = false;
        this.frames = 0;

        int size = params.frameByteSize();

        try
        {
            this.sink = SinkFactory.select(params, ffmpegPath, ffmpegAvailable, encoderLog);
            this.sink.begin(params.width(), params.height(), params.format());
        }
        catch (IOException e)
        {
            LOGGER.error("Failed to open video sink; aborting recording", e);

            this.sink = null;
            this.failed = true;

            return;
        }

        this.queue = new FrameQueue(this.sink, VideoConfig.ENCODER_QUEUE_CAPACITY, size);
        this.queue.start();

        this.scratch = ByteBuffer.allocateDirect(size);

        this.recording = true;
    }

    /**
     * Capture one output frame. Call once per frame the P199 clock marks as a
     * real output frame ({@link CaptureClock#canRender()}), from the render
     * thread — the production source issues GL calls.
     */
    public void recordFrame()
    {
        if (!this.recording)
        {
            return;
        }

        try
        {
            this.scratch.clear();
            this.source.readFrame(this.scratch);
            this.scratch.flip();

            if (this.scratch.remaining() != this.params.frameByteSize())
            {
                /* Total-reader spirit: a source that under-fills the buffer would
                 * desync every following frame in the stream — stop instead. */
                LOGGER.error("Frame source produced {} bytes, expected {}; stopping recording",
                    this.scratch.remaining(), this.params.frameByteSize());

                this.failed = true;
                this.stopRecording();

                return;
            }

            if (!this.submit(this.scratch))
            {
                this.failed = true;
                this.stopRecording();

                return;
            }

            this.frames++;
        }
        catch (Exception e)
        {
            LOGGER.error("Frame capture failed", e);

            this.failed = true;
            this.stopRecording();
        }
    }

    private boolean submit(ByteBuffer frame)
    {
        try
        {
            return this.queue.submit(frame);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();

            return false;
        }
    }

    /**
     * Finalize the recording: drain the encoder queue and close the sink. Safe
     * to call when not recording, and safe to call twice (the P202 teardown path
     * must never throw into the editor).
     */
    public void stopRecording()
    {
        if (!this.recording)
        {
            return;
        }

        this.recording = false;
        this.source = null;
        this.scratch = null;

        if (this.queue != null)
        {
            this.queue.stop();

            if (this.queue.error() != null)
            {
                this.failed = true;
            }

            this.queue = null;
        }

        try
        {
            if (this.sink != null)
            {
                this.sink.end();
            }
        }
        catch (IOException e)
        {
            LOGGER.warn("Failed to finalize video sink", e);

            this.failed = true;
        }
        finally
        {
            this.sink = null;
        }
    }
}
