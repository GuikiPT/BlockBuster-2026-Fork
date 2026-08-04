package mchorse.blockbuster.recording;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Scheduled recorder class (roadmap P109) — countdown state between
 * {@code RecordManager.record()} and the recorder's promotion in
 * {@code checkScheduled()}.
 */
public class ScheduledRecording
{
    public RecordRecorder recorder;
    public ServerPlayerEntity player;
    public Runnable runnable;
    public int countdown;
    public int offset;

    public ScheduledRecording(RecordRecorder recorder, ServerPlayerEntity player, Runnable runnable, int countdown, int offset)
    {
        this.recorder = recorder;
        this.player = player;
        this.runnable = runnable;
        this.countdown = countdown;
        this.offset = offset;
    }

    public void run()
    {
        if (this.runnable != null)
        {
            this.runnable.run();
        }
    }
}
