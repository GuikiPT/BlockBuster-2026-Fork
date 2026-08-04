package mchorse.mclib.client.gui.utils;

import java.util.HashMap;
import java.util.Map;

/**
 * This class connects Icons with Strings.
 * It is used in Mappet's UI API
 *
 * Port of McLib 2.4.3's {@code IconRegistry} (roadmap P31). The registry is
 * an addon-facing contract: duplicate keys print a stacktrace but do NOT
 * throw, and the OLD value is kept (legacy behavior, pinned by test).
 */
public class IconRegistry
{
    public static final Map<String, Icon> icons = new HashMap<String, Icon>();

    public static Icon register(String key, Icon icon)
    {
        if (icons.containsKey(key))
        {
            try
            {
                throw new IllegalStateException("[Icons] Icon " + key + " was already registered prior...");
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        else
        {
            icons.put(key, icon);
        }

        return icon;
    }
}
