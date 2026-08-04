package mchorse.aperture.events;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Typed callback registry replacing legacy {@code ClientProxy.EVENT_BUS}
 * (a Forge {@code EventBus}) for the camera editor events (P183).
 *
 * The cross-mod seam Blockbuster's P185.1 handlers consume: call sites keep
 * the legacy shape ({@code ClientProxy.EVENT_BUS.post(new
 * CameraEditorEvent.Scrubbed(...))}); listeners subscribe per event type
 * instead of via {@code @SubscribeEvent} reflection.
 */
public class CameraEditorCallbacks
{
    public final List<Consumer<CameraEditorEvent.Init>> init = new ArrayList<Consumer<CameraEditorEvent.Init>>();
    public final List<Consumer<CameraEditorEvent.Scrubbed>> scrubbed = new ArrayList<Consumer<CameraEditorEvent.Scrubbed>>();
    public final List<Consumer<CameraEditorEvent.Playback>> playback = new ArrayList<Consumer<CameraEditorEvent.Playback>>();
    public final List<Consumer<CameraEditorEvent.Rewind>> rewind = new ArrayList<Consumer<CameraEditorEvent.Rewind>>();
    public final List<Consumer<CameraEditorEvent.Options>> options = new ArrayList<Consumer<CameraEditorEvent.Options>>();

    public void onInit(Consumer<CameraEditorEvent.Init> listener)
    {
        this.init.add(listener);
    }

    public void onScrubbed(Consumer<CameraEditorEvent.Scrubbed> listener)
    {
        this.scrubbed.add(listener);
    }

    public void onPlayback(Consumer<CameraEditorEvent.Playback> listener)
    {
        this.playback.add(listener);
    }

    public void onRewind(Consumer<CameraEditorEvent.Rewind> listener)
    {
        this.rewind.add(listener);
    }

    public void onOptions(Consumer<CameraEditorEvent.Options> listener)
    {
        this.options.add(listener);
    }

    /**
     * Dispatch an event to the listeners of its concrete type (legacy
     * {@code EVENT_BUS.post(event)}).
     */
    public void post(CameraEditorEvent event)
    {
        if (event instanceof CameraEditorEvent.Init)
        {
            this.dispatch(this.init, (CameraEditorEvent.Init) event);
        }
        else if (event instanceof CameraEditorEvent.Scrubbed)
        {
            this.dispatch(this.scrubbed, (CameraEditorEvent.Scrubbed) event);
        }
        else if (event instanceof CameraEditorEvent.Playback)
        {
            this.dispatch(this.playback, (CameraEditorEvent.Playback) event);
        }
        else if (event instanceof CameraEditorEvent.Rewind)
        {
            this.dispatch(this.rewind, (CameraEditorEvent.Rewind) event);
        }
        else if (event instanceof CameraEditorEvent.Options)
        {
            this.dispatch(this.options, (CameraEditorEvent.Options) event);
        }
    }

    private <T extends CameraEditorEvent> void dispatch(List<Consumer<T>> listeners, T event)
    {
        for (Consumer<T> listener : listeners)
        {
            listener.accept(event);
        }
    }
}
