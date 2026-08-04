package mchorse.blockbuster.utils;

import mchorse.blockbuster.events.TickHandler;

/**
 * Port of Blockbuster 1.12.2's {@code utils/ExpirableRunnable} (roadmap
 * P22.1). Expiry is checked BEFORE the age increment: lifetime N runs N+1
 * times (ages 0..N), dying on the run where {@code age >= lifetime} —
 * lifetime 0 dies on (after) the first run. Subclasses override {@code run()}
 * calling {@code super.run()} around their work.
 */
public abstract class ExpirableRunnable implements TickHandler.IRunnable
{
    public boolean isDead;
    public int age;
    public int lifetime;

    public ExpirableRunnable(int lifetime)
    {
        this.lifetime = lifetime;
    }

    public int getAge()
    {
        return this.age;
    }

    public int getLifetime()
    {
        return this.lifetime;
    }

    public void setLifetime(int lifetime)
    {
        this.lifetime = lifetime;
    }

    @Override
    public void run()
    {
        if (this.age >= this.lifetime)
        {
            this.isDead = true;
        }

        this.age += 1;
    }

    @Override
    public boolean shouldRemove()
    {
        return this.isDead;
    }
}
