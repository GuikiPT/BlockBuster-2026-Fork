package mchorse.blockbuster.audio;

/**
 * Port of Blockbuster 2.7.2's {@code audio/AudioState}.
 *
 * <p><b>Ordinal order is the wire contract</b> — {@code PacketAudio} serializes
 * {@code state.ordinal()}, so this enum must never be reordered or have
 * constants inserted in the middle. Consumed here by {@code AudioLibrary}
 * (roadmap P188, client playback state machine); the server-side producer
 * ({@code AudioHandler}) is roadmap P189.</p>
 */
public enum AudioState
{
    REWIND, PAUSE, PAUSE_SET, RESUME, RESUME_SET, SET, STOP
}
