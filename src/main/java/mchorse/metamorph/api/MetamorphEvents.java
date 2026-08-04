package mchorse.metamorph.api;

import mchorse.metamorph.api.events.AcquireMorphEvent;
import mchorse.metamorph.api.events.MorphActionEvent;
import mchorse.metamorph.api.events.MorphEvent;
import mchorse.metamorph.api.events.RegisterBlacklistEvent;
import mchorse.metamorph.api.events.RegisterRemapEvent;
import mchorse.metamorph.api.events.RegisterSettingsEvent;
import mchorse.metamorph.api.events.ReloadMorphs;
import mchorse.metamorph.api.events.SpawnGhostEvent;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

import java.util.function.Consumer;

/**
 * Bundled Metamorph event bus (roadmap P49 collectors + P56 morph events).
 *
 * <p>Fabric array-backed events replacing the legacy Forge
 * {@code MinecraftForge.EVENT_BUS.post(...)} calls. The bundled
 * {@link RegisterHandler} subscribes here exactly like it did through
 * {@code @SubscribeEvent} on Forge; Blockbuster (S14) subscribes the same
 * way. Array-backed events fire in registration order — matching a
 * priority-less Forge bus.</p>
 *
 * <p>P56 morph events: the {@code *_PRE} events are cancelable — their invoker
 * returns {@code true} when a handler cancels. To preserve Forge's default
 * {@code receiveCanceled=false} behavior, the invoker short-circuits: the first
 * handler that returns {@code true} cancels and the remaining handlers are not
 * called. Handlers mutate the passed event object in place (replacing
 * {@code morph}, flipping {@code force}); firing code reads those fields back
 * after the post, exactly like legacy {@code MorphAPI.morph}. The {@code *_POST}
 * events and {@link #MORPH_ACTION}/{@link #RELOAD_MORPHS} are informational
 * (void {@link Consumer}s).</p>
 */
public final class MetamorphEvents
{
    /**
     * Cancelable callback for {@link MorphEvent.Pre}. Return {@code true} to
     * cancel the (de)morph.
     */
    @FunctionalInterface
    public interface MorphPreCallback
    {
        boolean onMorph(MorphEvent.Pre event);
    }

    /**
     * Cancelable callback for {@link AcquireMorphEvent.Pre}. Return {@code true}
     * to cancel the acquisition.
     */
    @FunctionalInterface
    public interface AcquireMorphPreCallback
    {
        boolean onAcquire(AcquireMorphEvent.Pre event);
    }

    /**
     * Cancelable callback for {@link SpawnGhostEvent.Pre}. Return {@code true}
     * to cancel the ghost spawn.
     */
    @FunctionalInterface
    public interface SpawnGhostPreCallback
    {
        boolean onSpawnGhost(SpawnGhostEvent.Pre event);
    }

    /**
     * Fired before a player (de)morphs. Cancelable; handlers may replace
     * {@code event.morph} / flip {@code event.force}.
     *
     * <p>Fired by {@code MorphAPI.morph} (P48): it posts a
     * {@link MorphEvent.Pre}, checks the boolean return for cancellation, then
     * reads back {@code event.morph}/{@code event.force} before applying.</p>
     */
    public static final Event<MorphPreCallback> MORPH_PRE =
        EventFactory.createArrayBacked(MorphPreCallback.class, callbacks -> event ->
        {
            for (MorphPreCallback callback : callbacks)
            {
                if (callback.onMorph(event))
                {
                    return true;
                }
            }

            return false;
        });

    /**
     * Fired after a player successfully (de)morphs. Informational.
     *
     * <p>Fired by {@code MorphAPI.morph} (P48) after applying.</p>
     */
    public static final Event<Consumer<MorphEvent.Post>> MORPH_POST =
        EventFactory.createArrayBacked(Consumer.class, callbacks -> event ->
        {
            for (Consumer<MorphEvent.Post> callback : callbacks)
            {
                callback.accept(event);
            }
        });

    /**
     * Fired before a player acquires a morph. Cancelable; handlers may replace
     * {@code event.morph}.
     *
     * <p>Fired by {@code MorphAPI.acquire} (P48) against the {@code Morphing}
     * storage (P52).</p>
     */
    public static final Event<AcquireMorphPreCallback> ACQUIRE_MORPH_PRE =
        EventFactory.createArrayBacked(AcquireMorphPreCallback.class, callbacks -> event ->
        {
            for (AcquireMorphPreCallback callback : callbacks)
            {
                if (callback.onAcquire(event))
                {
                    return true;
                }
            }

            return false;
        });

    /**
     * Fired after a player successfully acquires a morph. Informational.
     *
     * <p>Fired by {@code MorphAPI.acquire} (P48).</p>
     */
    public static final Event<Consumer<AcquireMorphEvent.Post>> ACQUIRE_MORPH_POST =
        EventFactory.createArrayBacked(Consumer.class, callbacks -> event ->
        {
            for (Consumer<AcquireMorphEvent.Post> callback : callbacks)
            {
                callback.accept(event);
            }
        });

    /**
     * Fired before a morph ghost spawns from a kill. Cancelable; handlers may
     * replace {@code event.morph} or set it to {@code null} (no ghost).
     *
     * <p>Fired by {@code MorphHandler.onPlayerKillEntity} (P52.1); the ghost
     * entity itself landed with P56.1.</p>
     */
    public static final Event<SpawnGhostPreCallback> SPAWN_GHOST_PRE =
        EventFactory.createArrayBacked(SpawnGhostPreCallback.class, callbacks -> event ->
        {
            for (SpawnGhostPreCallback callback : callbacks)
            {
                if (callback.onSpawnGhost(event))
                {
                    return true;
                }
            }

            return false;
        });

    /**
     * Fired after a morph ghost successfully spawns. Informational.
     *
     * <p>Fired by {@code MorphHandler.onPlayerKillEntity} (P52.1).</p>
     */
    public static final Event<Consumer<SpawnGhostEvent.Post>> SPAWN_GHOST_POST =
        EventFactory.createArrayBacked(Consumer.class, callbacks -> event ->
        {
            for (Consumer<SpawnGhostEvent.Post> callback : callbacks)
            {
                callback.accept(event);
            }
        });

    /**
     * Fired after a server-side morph action executes. Informational.
     *
     * <p>Fired by {@code ServerHandlerAction} (P55) when the server handles a
     * morph-action packet; Blockbuster's {@code ActionHandler} subscribes to it
     * to capture the action into a recording.</p>
     */
    public static final Event<Consumer<MorphActionEvent>> MORPH_ACTION =
        EventFactory.createArrayBacked(Consumer.class, callbacks -> event ->
        {
            for (Consumer<MorphActionEvent> callback : callbacks)
            {
                callback.accept(event);
            }
        });

    /**
     * Fired when the creative morphs picker (re)initializes. Informational.
     *
     * <p>SEAM(S7): fired by the client creative-picker GUI on (re)init.</p>
     */
    public static final Event<Consumer<ReloadMorphs>> RELOAD_MORPHS =
        EventFactory.createArrayBacked(Consumer.class, callbacks -> event ->
        {
            for (Consumer<ReloadMorphs> callback : callbacks)
            {
                callback.accept(event);
            }
        });

    public static final Event<Consumer<RegisterBlacklistEvent>> REGISTER_BLACKLIST =
        EventFactory.createArrayBacked(Consumer.class, callbacks -> event ->
        {
            for (Consumer<RegisterBlacklistEvent> callback : callbacks)
            {
                callback.accept(event);
            }
        });

    public static final Event<Consumer<RegisterSettingsEvent>> REGISTER_SETTINGS =
        EventFactory.createArrayBacked(Consumer.class, callbacks -> event ->
        {
            for (Consumer<RegisterSettingsEvent> callback : callbacks)
            {
                callback.accept(event);
            }
        });

    public static final Event<Consumer<RegisterRemapEvent>> REGISTER_REMAP =
        EventFactory.createArrayBacked(Consumer.class, callbacks -> event ->
        {
            for (Consumer<RegisterRemapEvent> callback : callbacks)
            {
                callback.accept(event);
            }
        });

    private MetamorphEvents()
    {}
}
