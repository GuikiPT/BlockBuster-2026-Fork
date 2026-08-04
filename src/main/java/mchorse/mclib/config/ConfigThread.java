package mchorse.mclib.config;

import mchorse.mclib.utils.Timer;

import java.util.ArrayList;
import java.util.List;

/**
 * Config thread
 *
 * This bad boy is responsible for saving configs after some time
 *
 * <p>Port of McLib 2.4.3's {@code config/ConfigThread.java} (roadmap P20),
 * verbatim: singleton instance, 2000 ms wall-clock {@link Timer}, 100 ms poll
 * loop, save happens 2 s after the <b>last</b> mark, instance cleared on
 * completion. Racy by design — keep the {@code synchronized static add}.</p>
 */
public class ConfigThread implements Runnable
{
    private static ConfigThread instance;

    public List<Config> configs = new ArrayList<Config>();
    public Timer timer = new Timer(2000);

    public static synchronized void add(Config config)
    {
        if (instance == null)
        {
            instance = new ConfigThread();
            instance.addConfig(config);
            new Thread(instance).start();
        }
        else
        {
            instance.addConfig(config);
        }
    }

    public void addConfig(Config config)
    {
        if (this.configs.indexOf(config) == -1)
        {
            this.configs.add(config);
        }

        this.timer.mark();
    }

    @Override
    public void run()
    {
        while (!this.timer.checkReset())
        {
            try
            {
                Thread.sleep(100);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }

        for (Config config : this.configs)
        {
            config.save();
        }

        instance = null;
    }
}
