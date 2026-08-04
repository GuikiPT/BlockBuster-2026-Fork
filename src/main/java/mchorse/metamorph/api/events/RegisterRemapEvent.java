package mchorse.metamorph.api.events;

import java.util.HashMap;
import java.util.Map;

/**
 * Fired to collect morph ID remappings (roadmap P49).
 *
 * Legacy source: .tools/legacy-src/metamorph/.../api/events/RegisterRemapEvent.java
 */
public class RegisterRemapEvent
{
    public Map<String, String> map = new HashMap<String, String>();
}
