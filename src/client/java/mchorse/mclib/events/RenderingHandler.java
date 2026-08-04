package mchorse.mclib.events;

/**
 * Port of McLib 2.4.3's {@code events/RenderingHandler} (roadmap P22).
 *
 * The only contract of the flag is "true while the render thread is inside a
 * frame" — worker threads (multiskin loader, S7) consult it to avoid
 * off-thread GL misuse. Legacy toggled it on Forge's RenderTickEvent
 * START/END; the port toggles around the world-render span via Fabric's
 * {@code WorldRenderEvents} (wired in the client initializer) — slightly
 * narrower than a full frame, which only makes the guard more conservative
 * (recorded deviation).
 */
public class RenderingHandler
{
    private static volatile boolean isMinecraftRendering;

    public static boolean isMinecraftRendering()
    {
        return isMinecraftRendering;
    }

    public static void setRendering(boolean rendering)
    {
        isMinecraftRendering = rendering;
    }
}
