package mchorse.metamorph.api.events;

import java.util.Set;
import java.util.TreeSet;

/**
 * Fired to collect the morph blacklist (roadmap P49).
 *
 * <p>Legacy extended Forge's {@code Event} and was posted on
 * {@code MinecraftForge.EVENT_BUS}; here it is a plain data object dispatched
 * through {@link mchorse.metamorph.api.MetamorphEvents#REGISTER_BLACKLIST}.
 * The set is a {@link TreeSet} (sorted) so downstream packet ordering is
 * stable.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../api/events/RegisterBlacklistEvent.java
 */
public class RegisterBlacklistEvent
{
    public Set<String> blacklist = new TreeSet<String>();
}
