package mchorse.blockbuster.client.video;

import java.util.List;

/**
 * GL/Minecraft-facing operations the {@link CaptureFramebufferState} machine
 * drives (P200). Abstracted so the swap/restore/resize bookkeeping is unit
 * testable against a recorded-call fake, while the real adapter
 * ({@code CaptureFramebuffer}, src/client) implements these against
 * {@code MinecraftClient.framebuffer} and the {@code WorldRenderer} sub-buffers.
 *
 * <p>Framebuffers are opaque {@link Object} handles here so this interface (and
 * the state machine) carries no client-only Minecraft types.</p>
 */
public interface FramebufferGl
{
    /** The framebuffer the client currently renders into. */
    Object currentTarget();

    /** Reassign the client's active framebuffer (the access-widened field). */
    void setTarget(Object framebuffer);

    /**
     * The six {@code WorldRenderer} sub-framebuffers (entity outlines,
     * translucent, entity, particles, weather, clouds) that must be resized in
     * lockstep with the main target, or outlines/translucency render at the
     * wrong size.
     */
    List<Object> subFramebuffers();

    /** Resize a framebuffer to {@code width x height}. */
    void resize(Object framebuffer, int width, int height);

    /** This framebuffer's current texture width. */
    int width(Object framebuffer);

    /** This framebuffer's current texture height. */
    int height(Object framebuffer);

    /** Bind a framebuffer for writing (clears/sets viewport). */
    void beginWrite(Object framebuffer);

    /** Blit a framebuffer to the screen as a preview at the given size. */
    void draw(Object framebuffer, int width, int height);
}
