package mchorse.mclib.client.gui.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import java.util.function.Consumer;

/**
 * The port's single "display this screen" seam (roadmap S22/P228).
 *
 * <p>1.12.2 called {@code Minecraft.getMinecraft().displayGuiScreen(screen)}
 * inline from wherever a screen had to come up (the {@code IGuiHandler}
 * switch, {@code ClientHandlerConfirm}, {@code ClientHandlerPlaybackButton}).
 * A direct {@code MinecraftClient.setScreen} call is untestable headlessly —
 * {@link MinecraftClient#getInstance()} is {@code null} under JUnit, so a route
 * that opened a screen and a route that did nothing looked identical. Every
 * such call site therefore goes through {@link #open(Screen)}, and headless
 * tests swap {@link #opener} for a capturing consumer.</p>
 *
 * <p>There is no legacy counterpart class — this is port-only plumbing, kept
 * in the mclib package because both mclib and Blockbuster client code use it.
 * The default is the production behaviour (not a no-op), so nothing has to be
 * "installed" for screens to open; {@link #reset()} restores it after a
 * test.</p>
 */
public final class ScreenOpener
{
    /**
     * {@code Minecraft.getMinecraft().displayGuiScreen(screen)}. Null-safe on a
     * headless JVM (no client instance → nothing to show).
     */
    public static final Consumer<Screen> DEFAULT = (screen) ->
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc != null)
        {
            mc.setScreen(screen);
        }
    };

    /** The active screen setter. Tests replace it; production leaves it alone. */
    public static Consumer<Screen> opener = DEFAULT;

    private ScreenOpener()
    {}

    /** Display a screen through the current {@link #opener}. */
    public static void open(Screen screen)
    {
        opener.accept(screen);
    }

    /** Test hook: restore the production screen setter. */
    public static void reset()
    {
        opener = DEFAULT;
    }
}
