package mchorse.mclib.client.gui.framework;

import mchorse.mclib.client.gui.framework.elements.IViewport;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.framework.elements.utils.IViewportStack;
import mchorse.mclib.client.gui.utils.Area;
import mchorse.mclib.client.gui.utils.LegacyKeyCodes;
import mchorse.mclib.client.gui.utils.keys.CompoundKey;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.client.gui.utils.keys.LangKey;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Base class for GUI screens using this framework
 *
 * Port of McLib 2.4.3's {@code GuiBase} (roadmap P29) — the single vanilla
 * bridge of the whole framework: {@code extends Screen} (1.20.4 yarn),
 * translating 1.20.4 double mouse coordinates, GLFW keycodes and per-notch
 * scroll amounts into the legacy int-based {@link GuiContext} state.
 *
 * Boundary decisions:
 * - {@code initGui()} → {@link #init()}, {@code updateScreen()} →
 *   {@link #tick()}, {@code onGuiClosed()} → {@link #removed()},
 *   {@code drawScreen(...)} → {@link #render(DrawContext, int, int, float)}.
 * - {@link #shouldCloseOnEsc()} returns {@code false}; ESC is handled in the
 *   legacy order inside {@link #handleKeyTyped}: root hook first, raw
 *   {@code keyCode == 1} check after.
 * - 1.12.2's single {@code keyTyped(char, int)} stream is fed from BOTH
 *   {@link #keyPressed} (GLFW keycode → legacy LWJGL2 code via
 *   {@link LegacyKeyCodes}, {@code typedChar = 0}) and {@link #charTyped}
 *   (printable char, {@code keyCode = 0} so keybinds don't double-fire).
 * - 1.20.4's {@code verticalAmount} is +1 per notch up; legacy wheel was
 *   {@code -Mouse.getEventDWheel()} (i.e. -120 per notch up). Converted with
 *   {@code scroll = (int) (-verticalAmount * 120)} — see
 *   {@link #SCROLL_STEP}.
 * - GLFW always repeats keys; repeats are dropped unless
 *   {@code context.repeatEvents} is set (legacy
 *   {@code Keyboard.enableRepeatEvents}, toggled by text elements in P33).
 */
public class GuiBase extends Screen
{
    /**
     * Legacy scroll magnitude of one wheel notch (LWJGL2's
     * {@code Mouse.getEventDWheel()} unit), preserved so consumers of
     * {@code context.mouseWheel} see 1.12.2-scale values.
     */
    public static final int SCROLL_STEP = 120;

    private static GuiContext current;

    public GuiElement root;
    public GuiContext context = new GuiContext(this);
    public Area viewport = new Area();

    /**
     * Keys currently held, tracked to distinguish GLFW repeats (LWJGL2
     * delivered no repeats unless enabled). Indexed by GLFW keycode.
     */
    private final boolean[] pressedKeys = new boolean[512];

    /**
     * Whether the last {@link #keyPressed} was a dropped repeat — the GLFW
     * char callback fires on repeats too, so the following
     * {@link #charTyped} must be dropped as well.
     */
    private boolean droppedRepeat;

    public static GuiContext getCurrent()
    {
        return current;
    }

    public GuiBase()
    {
        super(Text.literal(""));

        current = this.context;

        this.context.mc = MinecraftClient.getInstance();
        this.context.font = this.context.mc == null ? null : this.context.mc.textRenderer;

        this.root = new GuiRootElement(this.context.mc);
        this.root.markContainer().flex().relative(this.viewport).wh(1F, 1F);
        this.root.keys().register(IKey.lang("mclib.gui.keys.list"), LegacyKeyCodes.KEY_F9, () -> this.context.keybinds.toggleVisible());

        this.context.keybinds.flex().relative(this.viewport).wh(0.5F, 1F);

        /* Legacy Keyboard.enableRepeatEvents(false) */
        this.context.repeatEvents = false;
    }

    /**
     *
     * @param clazz the class to search for in the children of this screen.root
     * @param <T>
     * @return null if GuiBase.screen or GuiBase.screen.root is null or if the children List is empty.
     */
    public static <T> List<T> getCurrentChildren(Class<T> clazz)
    {
        if (GuiBase.getCurrent() != null && GuiBase.getCurrent().screen != null && GuiBase.getCurrent().screen.root != null)
        {
            List<T> childList = GuiBase.getCurrent().screen.root.getChildren(clazz);

            return (childList.isEmpty()) ? null : childList;
        }

        return null;
    }

    /**
     * Legacy {@code updateScreen()}
     */
    @Override
    public void tick()
    {
        this.context.tick += 1;
    }

    /**
     * Legacy {@code initGui()} — also called by vanilla on resize
     */
    @Override
    protected void init()
    {
        current = this.context;

        if (!this.context.keybinds.hasParent())
        {
            this.root.add(this.context.keybinds);
        }

        this.viewport.set(0, 0, this.width, this.height);
        this.viewportSet();

        this.context.pushViewport(this.viewport);
        this.root.resize();
        this.context.popViewport();
    }

    protected void viewportSet()
    {}

    /**
     * Legacy {@code onGuiClosed()}
     */
    @Override
    public void removed()
    {
        current = null;
    }

    /**
     * Legacy {@code GuiBase} kept 1.12.2's default of not overriding
     * {@code doesGuiPauseGame}; the plan (S03 P29) pins the modern default to
     * non-pausing — subclasses may override (the legacy override point is
     * preserved).
     */
    @Override
    public boolean shouldPause()
    {
        return false;
    }

    /**
     * ESC handling happens inside the legacy key flow (root hook first,
     * {@code keyCode == 1} check after) — never let vanilla auto-close.
     */
    @Override
    public boolean shouldCloseOnEsc()
    {
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton)
    {
        this.context.setMouse((int) mouseX, (int) mouseY, mouseButton);

        if (this.root.isEnabled())
        {
            this.context.pushViewport(this.viewport);
            boolean result = this.root.mouseClicked(this.context);
            this.context.popViewport();

            return result;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount)
    {
        /* Legacy scroll = -Mouse.getEventDWheel(); GLFW gives +1 per notch up,
         * LWJGL2 gave +120 per notch up — flip the sign and restore the 120
         * step (see SCROLL_STEP) */
        int scroll = (int) (-verticalAmount * SCROLL_STEP);

        if (scroll == 0)
        {
            return false;
        }

        this.mouseScrolled((int) mouseX, (int) mouseY, scroll);

        return true;
    }

    protected void mouseScrolled(int x, int y, int scroll)
    {
        this.context.setMouseWheel(x, y, scroll);

        if (this.root.isEnabled())
        {
            this.context.pushViewport(this.viewport);
            this.root.mouseScrolled(this.context);
            this.context.popViewport();
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int state)
    {
        this.context.setMouse((int) mouseX, (int) mouseY, state);

        if (this.root.isEnabled())
        {
            this.context.pushViewport(this.viewport);
            this.root.mouseReleased(this.context);
            this.context.popViewport();
        }

        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        boolean repeat = keyCode >= 0 && keyCode < this.pressedKeys.length && this.pressedKeys[keyCode];

        if (keyCode >= 0 && keyCode < this.pressedKeys.length)
        {
            this.pressedKeys[keyCode] = true;
        }

        if (repeat && !this.context.repeatEvents)
        {
            this.droppedRepeat = true;

            return false;
        }

        this.droppedRepeat = false;

        /* GLFW keycode → legacy LWJGL2 code; no char on the key-down leg */
        this.handleKeyTyped((char) 0, LegacyKeyCodes.glfwToLegacy(keyCode));

        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers)
    {
        if (keyCode >= 0 && keyCode < this.pressedKeys.length)
        {
            this.pressedKeys[keyCode] = false;
        }

        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char typedChar, int modifiers)
    {
        if (this.droppedRepeat)
        {
            /* The char belongs to a dropped key repeat */
            return false;
        }

        /* keyCode 0 on the char leg so keybinds don't double-fire */
        this.handleKeyTyped(typedChar, 0);

        return true;
    }

    /**
     * Legacy {@code keyTyped(char, int)} — both {@link #keyPressed} and
     * {@link #charTyped} funnel here. Order is load-bearing: root handlers
     * first, then the {@link #keyPressed(char, int)} hook, then the raw ESC
     * check.
     */
    protected void handleKeyTyped(char typedChar, int keyCode)
    {
        this.context.setKey(typedChar, keyCode);

        if (this.root.isEnabled() && this.root.keyTyped(this.context))
        {
            return;
        }

        this.context.pushViewport(this.viewport);
        this.keyPressed(typedChar, keyCode);
        this.context.popViewport();

        if (keyCode == LegacyKeyCodes.KEY_ESCAPE)
        {
            this.closeScreen();
        }
    }

    /**
     * This method is getting called when there are no active text
     * fields in the GUI (this can be used for handling shortcuts)
     */
    public void keyPressed(char typedChar, int keyCode)
    {}

    /**
     * This method is called when this screen is about to get closed
     */
    protected void closeScreen()
    {
        MinecraftClient mc = this.context.mc;

        if (mc == null)
        {
            /* Headless (unit test) context */
            return;
        }

        mc.setScreen(null);

        /* Legacy: mc.setIngameFocus() only if no other screen replaced this
         * one — Blockbuster relies on this to chain editors */
        if (mc.currentScreen == null && mc.mouse != null)
        {
            mc.mouse.lockCursor();
        }

        this.context.repeatEvents = false;
    }

    public void closeThisScreen()
    {
        this.closeScreen();
    }

    /**
     * Legacy {@code drawScreen(int, int, float)}
     */
    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float partialTicks)
    {
        /* Per-frame translation cache tick (legacy bumped these on resource
         * reload from ClientProxy; per-frame is a superset that also picks up
         * language switches — TODO(P29.1): move to a resource-reload hook) */
        LangKey.lastTime += 1;
        CompoundKey.lastTime += 1;

        /* Expose the frame's DrawContext to the framework (P31 GuiDraw) */
        this.context.drawContext = drawContext;
        GuiDraw.bindDrawContext(drawContext);

        /* 1.20.4 hands the HUD and the screen the same DrawContext and flushes
         * it once, after both — so anything the HUD buffered (its text, the F3
         * overlay) would otherwise be painted on top of this screen's panels.
         * Push it out here, before the first panel background covers the spot
         * it belongs under. */
        GuiDraw.flush();

        /* ...and the HUD shares the frame's depth buffer too: its glyph layers
         * sit 0.03 in front of the GUI plane, so without this clear every panel
         * background drawn below is rejected by GL_LEQUAL on exactly the pixels
         * of the chat / hotbar counts / F3 overlay, and that text bleeds through
         * an opaque panel. See GuiDraw.clearDepth. */
        GuiDraw.clearDepth();

        this.context.setMouse(mouseX, mouseY);
        this.context.partialTicks = partialTicks;

        if (this.root.isVisible())
        {
            this.context.reset();
            this.context.pushViewport(this.viewport);

            this.root.draw(this.context);

            this.context.popViewport();
            this.context.drawTooltip();
            this.context.postRenderCallbacks.forEach((element) ->
            {
                element.accept(this.context);
            });
        }
    }

    public static class GuiRootElement extends GuiElement implements IViewport
    {
        public GuiRootElement(MinecraftClient mc)
        {
            super(mc);
        }

        @Override
        public void apply(IViewportStack stack)
        {
            stack.pushViewport(this.area);
        }

        @Override
        public void unapply(IViewportStack stack)
        {
            stack.popViewport();
        }
    }
}
