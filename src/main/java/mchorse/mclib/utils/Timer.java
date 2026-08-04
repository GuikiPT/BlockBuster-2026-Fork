package mchorse.mclib.utils;

/**
 * Full port of McLib 2.4.3's Timer (roadmap P14). Wall-clock based
 * (System.currentTimeMillis) by design — do not switch to nanoTime.
 *
 * Legacy source: .tools/legacy-src/mclib/src/main/java/mchorse/mclib/utils/Timer.java
 */
public class Timer
{
    public boolean enabled;
    public long time;
    public long duration;

    public Timer(long duration)
    {
        this.duration = duration;
    }

    public long getRemaining()
    {
        return this.time - System.currentTimeMillis();
    }

    public void mark()
    {
        this.mark(this.duration);
    }

    public void mark(long duration)
    {
        this.enabled = true;
        this.time = System.currentTimeMillis() + duration;
    }

    public void reset()
    {
        this.enabled = false;
    }

    public boolean checkReset()
    {
        boolean enabled = this.check();

        if (enabled)
        {
            this.reset();
        }

        return enabled;
    }

    public boolean check()
    {
        return this.enabled && this.isTime();
    }

    public boolean isTime()
    {
        return System.currentTimeMillis() >= this.time;
    }

    public boolean checkRepeat()
    {
        if (!this.enabled)
        {
            this.mark();
        }

        return this.checkReset();
    }
}
