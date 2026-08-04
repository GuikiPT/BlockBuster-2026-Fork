package mchorse.blockbuster_pack;

import mchorse.metamorph.api.MetamorphEvents;
import mchorse.metamorph.api.events.RegisterBlacklistEvent;

/**
 * Blockbuster's Metamorph event hook (roadmap P157).
 *
 * <p>Faithful port of legacy {@code mchorse.blockbuster_pack.MetamorphHandler}.
 * Blacklists the {@code "blockbuster:actor"} entity id so players cannot acquire a
 * morph by killing a Blockbuster actor.</p>
 *
 * <p>Port note: legacy subscribed via Forge's {@code @SubscribeEvent} on
 * {@code MinecraftForge.EVENT_BUS}; here it registers a callback on the bundled
 * {@link MetamorphEvents#REGISTER_BLACKLIST} array-backed event — exactly how the
 * bundled {@code RegisterHandler} subscribes. Wire it in the common initializer
 * where the other blacklist collectors register:
 * {@code new MetamorphHandler().register();}.</p>
 *
 * Legacy source: blockbuster-1.12/.../mchorse/blockbuster_pack/MetamorphHandler.java
 */
public class MetamorphHandler
{
    /**
     * Subscribe this handler's callback to the blacklist-collection event.
     */
    public void register()
    {
        MetamorphEvents.REGISTER_BLACKLIST.register(this::onBlacklistReload);
    }

    public void onBlacklistReload(RegisterBlacklistEvent event)
    {
        event.blacklist.add("blockbuster:actor");
    }
}
