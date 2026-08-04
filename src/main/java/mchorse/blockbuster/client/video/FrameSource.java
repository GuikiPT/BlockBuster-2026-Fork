package mchorse.blockbuster.client.video;

import java.nio.ByteBuffer;

/**
 * The capture <b>input</b> seam (introduced by P206). It is the mirror image of
 * {@link FrameSink}: the recorder pulls one output frame's worth of pixels from
 * a {@code FrameSource} and pushes it into the sink pipeline.
 *
 * <p>In production the implementation is
 * {@code mchorse.blockbuster.client.video.FramebufferFrameSource}: a GL readback
 * of the client framebuffer's colour attachment, installed into
 * {@code VideoRecorder} by {@code VideoCaptureWiring} (S22 P234). Extracting the
 * seam as an interface is what lets the whole recording loop run headlessly: the
 * P206 end-to-end dry run and the P234 recorder test feed a synthetic generator
 * instead of GL, so frame pacing, counts, and the sink pipeline can be asserted
 * with no GL context and no window.</p>
 */
public interface FrameSource
{
    /**
     * Fill {@code dst} with exactly one frame's pixel bytes — {@code dst.remaining()}
     * bytes, in the {@link VideoFormat} the recording declared (BGR/BGRA, bottom-up
     * GL row order). The caller supplies a cleared buffer sized to
     * {@link VideoParams#frameByteSize()} and flips it after this returns, so the
     * source must consume the whole buffer.
     */
    void readFrame(ByteBuffer dst);
}
