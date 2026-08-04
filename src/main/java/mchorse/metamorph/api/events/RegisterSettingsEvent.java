package mchorse.metamorph.api.events;

import java.util.HashMap;
import java.util.Map;

import mchorse.metamorph.api.MorphSettings;

/**
 * Fired to collect per-morph active {@link MorphSettings} (roadmap P49).
 *
 * Legacy source: .tools/legacy-src/metamorph/.../api/events/RegisterSettingsEvent.java
 */
public class RegisterSettingsEvent
{
    public Map<String, MorphSettings> settings = new HashMap<String, MorphSettings>();
}
