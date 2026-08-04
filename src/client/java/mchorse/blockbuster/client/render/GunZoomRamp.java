package mchorse.blockbuster.client.render;

/**
 * Pure (no-GL) port of the zoom ramp state machine in the legacy
 * {@code GunMiscRender.handleZoom} + the FOV / sensitivity formulas (P197).
 *
 * <p>The state ({@link #ZOOM_TIME}, {@link #UN_ZOOM_TIME}, {@link #onZoom}) is
 * static exactly like the legacy class, and {@link #advance} reproduces the
 * per-render-tick ramp with its edge-triggered {@code PacketZoomCommand} send
 * (through the injectable {@link EdgeSender} seam so a headless test can capture
 * the transitions without networking). The +0.1/-0.1 and +0.2/-0.2 rates and
 * the {@code onZoom} default of {@code true} are gameplay-visible constants —
 * kept verbatim.</p>
 *
 * Legacy source of truth:
 * {@code blockbuster-1.12/.../client/render/GunMiscRender.java}.
 */
public final class GunZoomRamp
{
    /** Zoom-in ramp progress, 0..1. */
    public static float ZOOM_TIME;

    /** Zoom-out ramp progress, 0..1. */
    public static float UN_ZOOM_TIME;

    /**
     * Whether the previous tick was in the zooming state. Legacy default is
     * {@code true} (a load-bearing quirk: the first non-zoom tick edge-sends a
     * spurious {@code zoomOn=false}).
     */
    public static boolean onZoom = true;

    private GunZoomRamp()
    {}

    /** Edge-transition sink (legacy {@code Dispatcher.sendToServer(PacketZoomCommand)}). */
    @FunctionalInterface
    public interface EdgeSender
    {
        void send(boolean zoomOn);
    }

    /**
     * Advance the ramp one render tick. Mirrors legacy {@code handleZoom}:
     * held → ZOOM_TIME climbs by {@code partialTick*0.1} (cap 1), UN_ZOOM_TIME
     * falls by {@code partialTick*0.2} (floor 0); released → the inverse. An
     * edge (held state changed since last tick) fires {@code sender.send}.
     */
    public static void advance(boolean keyDown, float partialTick, EdgeSender sender)
    {
        boolean zoomed = onZoom;

        if (keyDown)
        {
            onZoom = true;
            ZOOM_TIME = Math.min(ZOOM_TIME + partialTick * 0.1F, 1);
            UN_ZOOM_TIME = Math.max(UN_ZOOM_TIME - partialTick * 0.2F, 0);

            if (!zoomed && sender != null)
            {
                sender.send(true);
            }
        }
        else
        {
            onZoom = false;
            ZOOM_TIME = Math.max(ZOOM_TIME - partialTick * 0.1F, 0);
            UN_ZOOM_TIME = Math.min(UN_ZOOM_TIME + partialTick * 0.2F, 1);

            if (zoomed && sender != null)
            {
                sender.send(false);
            }
        }
    }

    /**
     * The zoomed FOV. Legacy mutated {@code gameSettings.fovSetting} to
     * {@code lastFov - lastFov * ZOOM_TIME * zoomFactor}; the mixin applies the
     * same formula to the value {@code GameRenderer.getFov} returns, avoiding
     * the option-mutation (behaviour-equal, implementation-different — see the
     * parity notes).
     */
    public static double zoomedFov(double baseFov, float zoomTime, float zoomFactor)
    {
        return baseFov - baseFov * zoomTime * zoomFactor;
    }

    /**
     * The zoomed look sensitivity. Legacy set
     * {@code mouseSensitivity = lastMouseSensitivity * mouseZoom - 0.3f}; the
     * {@code - 0.3f} constant can push the result negative (inverted look) for
     * small {@code mouseZoom} — that is legacy behaviour, preserved.
     */
    public static double zoomedSensitivity(double baseSensitivity, float mouseZoom)
    {
        return baseSensitivity * mouseZoom - 0.3F;
    }

    /** Reset the static ramp state (used by tests and on world unload). */
    public static void reset()
    {
        ZOOM_TIME = 0;
        UN_ZOOM_TIME = 0;
        onZoom = true;
    }
}
