package mchorse.blockbuster.audio;

import io.netty.buffer.ByteBuf;
import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.audio.PacketAudio;
import mchorse.mclib.config.values.ValueInt;
import mchorse.mclib.config.values.ValueString;
import mchorse.mclib.network.IByteBufSerializable;
import mchorse.mclib.network.INBTSerializable;
import mchorse.mclib.utils.ForgeUtils;
import mchorse.mclib.utils.ICopy;
import mchorse.mclib.utils.LatencyTimer;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Server-side per-scene audio controller (roadmap S16 P189). Full port of
 * Blockbuster 2.7.2's {@code mchorse.blockbuster.audio.AudioHandler}.
 *
 * <p>This is the engine grown out of the S11 P127 (de)serialization stub: the
 * scene play/pause/resume/goTo/stop verbs drive a 7-state {@link AudioState}
 * machine and broadcast {@link PacketAudio}s (with optional {@link LatencyTimer}
 * when {@code audio_sync} is on) to every player; a negative {@code audioShift}
 * encodes a start delay that {@link #update()} auto-fires once the scene tick
 * catches up. The McLib {@link ValueString}/{@link ValueInt} backing preserves
 * the {@code hasChanged()} write-gating in {@link #toNBT} (a scene that never had
 * audio produces <b>no</b> {@code Audio}/{@code AudioShift} keys) and the mclib
 * Value wire format in {@link #toBytes}.</p>
 *
 * <p>Load-bearing 1.12.2 quirks preserved verbatim:</p>
 * <ul>
 * <li><b>{@code SET <=> playing}</b>: {@link #goTo(int)} is the only producer of
 * {@code SET}, and only while playing — otherwise it emits {@code PAUSE_SET}.</li>
 * <li><b>PAUSE/STOP shift algebra</b>: shift is {@code -audioShift}, then the
 * packet adds {@code +audioShift} ⇒ the wire shift is exactly {@code 0}, which
 * trips the client's "keep current position" branch.</li>
 * <li>{@link #audioState} is a cache and is <b>never serialized</b> — a server
 * restart mid-play comes back STOP'd.</li>
 * <li>{@link #setAudioName(String)} coerces {@code null} to {@code ""}.</li>
 * <li>{@code fromNBT} unconditionally resets an absent {@code Audio} to
 * {@code ""} but leaves {@code audioShift} untouched when its key is absent.</li>
 * </ul>
 *
 * <p>Legacy source:
 * {@code blockbuster-1.12/src/main/java/mchorse/blockbuster/audio/AudioHandler.java}.</p>
 */
public class AudioHandler implements ICopy<AudioHandler>, INBTSerializable, IByteBufSerializable
{
    /**
     * Cache of the current audio state. This does not need to be serialized.
     */
    private AudioState audioState = AudioState.STOP;

    private final ValueInt audioShift = new ValueInt("audio_shift");
    private final ValueString audio = new ValueString("audio_name");

    /**
     * The global scene tick (not the tick relative to the audio timeline). Used
     * to decide when a negative {@link #audioShift} (a delay) has elapsed.
     */
    private int tick;

    public String getAudioName()
    {
        return this.audio.get();
    }

    public void setAudioName(String audio)
    {
        this.audio.set((audio == null) ? "" : audio);
    }

    public int getAudioShift()
    {
        return this.audioShift.get();
    }

    public void setAudioShift(int audioShift)
    {
        this.audioShift.set(audioShift);
    }

    public AudioState getAudioState()
    {
        return this.audioState;
    }

    public boolean hasAudio()
    {
        return this.audio.get() != null && !this.audio.get().isEmpty();
    }

    public boolean isPlaying()
    {
        switch (this.audioState)
        {
            case REWIND:
            case RESUME_SET:
            case RESUME:
            case SET:
                return true;
            case PAUSE:
            case STOP:
            case PAUSE_SET:
                return false;
        }

        return false;
    }

    public void pauseAudio()
    {
        this.setAudioStateTick(AudioState.PAUSE, this.tick);
    }

    /**
     * Pause the audio at a certain tick.
     *
     * @param tick the global scene tick (not the audio-timeline tick) — needed
     *             to decide whether a delayed audio can play yet.
     */
    public void pauseAudio(int tick)
    {
        this.setAudioStateTick(AudioState.PAUSE_SET, tick);
    }

    /**
     * Resume playing at a certain tick.
     *
     * @param tick the global scene tick to resume at.
     */
    public void resume(int tick)
    {
        this.setAudioStateTick(AudioState.RESUME_SET, tick);
    }

    /**
     * Stops the audio.
     */
    public void stopAudio()
    {
        this.audioState = AudioState.STOP;

        this.sendAudioState(AudioState.STOP, Blockbuster.audioSync.get());
    }

    /**
     * Go to a tick, whether paused/stopped or playing.
     *
     * <p>It is important that {@link AudioState#SET} is only used when going to a
     * tick <em>while playing</em> ({@code SET <=> playing}).</p>
     *
     * @param tick the global scene tick.
     */
    public void goTo(int tick)
    {
        this.setAudioStateTick(this.isPlaying() ? AudioState.SET : AudioState.PAUSE_SET, tick);
    }

    /**
     * @param tick the global scene tick.
     */
    public void startAudio(int tick)
    {
        this.setAudioStateTick(AudioState.REWIND, tick);
    }

    /**
     * Store the tick + state and broadcast. When {@link #audioShift} is negative
     * (a delay) and the tick has not reached it yet, the audio is stopped
     * instead (the delayed-start guard).
     *
     * @param state the state to store/broadcast.
     * @param tick  the global scene tick.
     */
    private void setAudioStateTick(AudioState state, int tick)
    {
        this.tick = tick;

        if (this.audioShift.get() < 0 && tick < -this.audioShift.get())
        {
            this.stopAudio();

            return;
        }

        this.audioState = state;

        this.sendAudioState(state, Blockbuster.audioSync.get());
    }

    /**
     * Call on the logical-server tick: advances the tick and, once a negative
     * (delayed) {@link #audioShift} elapses, auto-starts the audio.
     */
    public void update()
    {
        if (this.audioShift.get() < 0 && this.tick >= -this.audioShift.get() && !this.isPlaying())
        {
            this.startAudio(this.tick);
        }

        this.tick++;
    }

    /**
     * Broadcast the given state to every player (no-op unless {@link #hasAudio()}).
     *
     * @param sync whether to try and sync the audio between server and client.
     */
    private void sendAudioState(AudioState state, boolean sync)
    {
        if (!this.hasAudio())
        {
            return;
        }

        for (ServerPlayerEntity player : this.serverPlayers())
        {
            this.sendAudioStateToPlayer(state, (sync) ? new LatencyTimer() : null, player);
        }
    }

    /**
     * Send the current audio state to a single player (useful when a player
     * joins a server with audio already playing). {@code PAUSE}/{@code RESUME}
     * are converted to their {@code *_SET} equivalents so the joiner receives
     * the tick.
     */
    public void syncPlayer(ServerPlayerEntity player)
    {
        AudioState state = this.audioState;

        switch (this.audioState)
        {
            case PAUSE:
                state = AudioState.PAUSE_SET;
                break;
            case RESUME:
                state = AudioState.RESUME_SET;
                break;
        }

        this.sendAudioStateToPlayer(state, (Blockbuster.audioSync.get()) ? new LatencyTimer() : null, player);
    }

    /**
     * Send the audio to the provided player.
     *
     * <p>Callers must have already checked the tick/state so a delayed audio
     * never sends a play state before its delay elapses — otherwise the shift
     * passed to the client {@code AudioLibrary} could go negative.</p>
     *
     * @param state        the state to send.
     * @param latencyTimer optional timer to (approximately) measure the delay.
     * @param player       the target player.
     */
    private void sendAudioStateToPlayer(AudioState state, @Nullable LatencyTimer latencyTimer, ServerPlayerEntity player)
    {
        if (!this.hasAudio())
        {
            return;
        }

        int shift = 0;

        switch (state)
        {
            case REWIND:
            case RESUME_SET:
            case PAUSE_SET:
            case SET:
                shift = this.tick;
                break;
            case PAUSE:
            case STOP:
                shift = -this.audioShift.get();
                break;
        }

        PacketAudio packet = new PacketAudio(this.audio.get(), state, shift + this.audioShift.get(), latencyTimer);

        this.sendPacket(packet, player);
    }

    /**
     * Player-list seam (legacy {@code ForgeUtils.getServerPlayers()}); overridden
     * by headless tests to inject fake players.
     */
    protected Iterable<ServerPlayerEntity> serverPlayers()
    {
        return ForgeUtils.getServerPlayers();
    }

    /**
     * Wire seam (legacy {@code Dispatcher.sendTo(packet, player)}); overridden by
     * headless tests to capture packets instead of sending. The {@code null}
     * guard mirrors 1.12.2, which never dispatched to a null player.
     */
    protected void sendPacket(PacketAudio packet, ServerPlayerEntity player)
    {
        if (player != null)
        {
            Dispatcher.sendTo(packet, player);
        }
    }

    @Override
    public AudioHandler copy()
    {
        AudioHandler clone = new AudioHandler();

        clone.copy(this);

        return clone;
    }

    @Override
    public void copy(AudioHandler origin)
    {
        this.audio.copy(origin.audio);
        this.audioState = origin.audioState;
        this.audioShift.copy(origin.audioShift);
        this.tick = origin.tick;
    }

    @Override
    public void fromNBT(NbtCompound compound)
    {
        this.audio.set(compound.contains("Audio") ? compound.getString("Audio") : "");

        if (compound.contains("AudioShift"))
        {
            /* Older files stored the shift as a float in seconds; convert to
             * ticks (seconds * 20). Newer files store it as an int in ticks. */
            if (compound.get("AudioShift") instanceof NbtFloat)
            {
                this.audioShift.set((int) (compound.getFloat("AudioShift") * 20));
            }
            else
            {
                this.audioShift.set(compound.getInt("AudioShift"));
            }
        }
    }

    @Override
    public NbtCompound toNBT(NbtCompound compound)
    {
        if (this.audio.hasChanged())
        {
            compound.put("Audio", this.audio.valueToNBT());
        }

        if (this.audioShift.hasChanged())
        {
            compound.put("AudioShift", this.audioShift.valueToNBT());
        }

        return compound;
    }

    @Override
    public void fromBytes(ByteBuf byteBuf)
    {
        this.audio.fromBytes(byteBuf);
        this.audioShift.fromBytes(byteBuf);
    }

    @Override
    public void toBytes(ByteBuf byteBuf)
    {
        this.audio.toBytes(byteBuf);
        this.audioShift.toBytes(byteBuf);
    }
}
