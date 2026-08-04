package mchorse.mclib.events;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

import java.util.function.Consumer;

/**
 * The bundled McLib event bus (roadmap P22): Fabric array-backed events
 * replacing the legacy {@code McLib.EVENT_BUS} (a dedicated Forge EventBus,
 * deliberately separate from the game-event bus — that separation is kept:
 * game hooks use Fabric API events directly, mod-to-mod registration flows
 * through here). Array-backed events fire in registration order, matching a
 * priority-less Forge bus.
 *
 * <p>Client-only events (dashboard panels, render overlay) stay as the
 * callback lists the S3 phases created in the client source set; multiskin
 * lands with S7.</p>
 */
public final class McLibEvents
{
    /**
     * Fired by {@code ConfigManager.register}; subscribers build their config
     * modules into the passed event. The legacy list
     * {@code ConfigManager.REGISTER_CALLBACKS} is invoked first (it predates
     * this bus in the port), then this event — both see the same event object.
     */
    public static final Event<Consumer<RegisterConfigEvent>> REGISTER_CONFIG =
        EventFactory.createArrayBacked(Consumer.class, callbacks -> event ->
        {
            for (Consumer<RegisterConfigEvent> callback : callbacks)
            {
                callback.accept(event);
            }
        });

    /**
     * Fired once during mod init, after config registration; subscribers
     * register their permission trees, then {@code loadPermissions()} runs.
     */
    public static final Event<Consumer<RegisterPermissionsEvent>> REGISTER_PERMISSIONS =
        EventFactory.createArrayBacked(Consumer.class, callbacks -> event ->
        {
            for (Consumer<RegisterPermissionsEvent> callback : callbacks)
            {
                callback.accept(event);
            }
        });

    /**
     * Fired (S7/P91) after a multiskin finishes CPU-side compositing, before
     * GPU upload — replaces the legacy client-only {@code MultiskinProcessedEvent}
     * posted on {@code McLib.EVENT_BUS}. Dispatched from
     * {@code TextureProcessor.postProcess}; the Blockbuster client subscribes
     * {@code ModelExtrudedLayer.forceReload}.
     */
    public static final Event<Consumer<MultiskinProcessedEvent>> MULTISKIN_PROCESSED =
        EventFactory.createArrayBacked(Consumer.class, callbacks -> event ->
        {
            for (Consumer<MultiskinProcessedEvent> callback : callbacks)
            {
                callback.accept(event);
            }
        });

    private McLibEvents()
    {}
}
