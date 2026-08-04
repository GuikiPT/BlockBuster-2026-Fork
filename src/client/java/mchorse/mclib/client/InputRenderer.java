package mchorse.mclib.client;

import mchorse.mclib.McLib;
import mchorse.mclib.client.gui.framework.GuiBase;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.Icons;
import mchorse.mclib.utils.ColorUtils;
import mchorse.mclib.utils.Interpolation;
import mchorse.mclib.utils.KeyCodes;
import mchorse.mclib.utils.Keys;
import mchorse.mclib.utils.MatrixUtils;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.ToIntFunction;

/**
 * Mouse renderer — port of McLib 2.4.3's
 * {@code mchorse.mclib.client.InputRenderer} (roadmap P44.2).
 *
 * <p>This class is responsible for rendering a mouse pointer on the screen,
 * the 12×16 pixel-art mouse with pressed halves and the animated scroll
 * wheel, and the pressed-key name feed with repeat counters and slide
 * animation.</p>
 *
 * <h2>Port notes (source-verified against the legacy file)</h2>
 *
 * <ul>
 * <li><b>Draw hook.</b> Legacy drew from {@code DrawScreenEvent.Post} only —
 * verified: the legacy {@code InputRenderer} never subscribed to its own
 * {@code RenderOverlayEvent}; the ASM-injected
 * {@code preRenderOverlay}/{@code postRenderOverlay} statics merely
 * <i>posted</i> those events for other consumers (Aperture's letterbox). So
 * the overlay renders <b>only while a screen is open</b>, above the screen —
 * which is exactly {@code ScreenEvents.afterRender} on Fabric. The P44.1
 * {@code HudRenderCallback} seam is deliberately untouched: it fires inside
 * {@code InGameHud.render}, i.e. <i>below</i> any open screen, which would be
 * the wrong layer and a behavior change against 1.12.2.</li>
 * <li><b>Ortho projection.</b> The legacy {@code setupOrthoProjection}
 * (znear 1000 / zfar 3000 / translate −2000) belonged to the ASM overlay
 * hook, not to this drawing; the screen pass already has GUI ortho bound, so
 * only the legacy {@code translate(0, 0, 1000)} of {@code renderMouse}
 * survives (as a {@code MatrixStack} push/translate/pop).</li>
 * <li><b>Key codes.</b> Legacy read LWJGL2 scancodes from
 * {@code Keyboard.getEventKey()}; GLFW codes are translated back through
 * {@link KeyCodes#glfwToLwjgl2} so {@link Keys#getKeyName} and the on-disk
 * key-name table stay the legacy ones.</li>
 * <li><b>{@code Mouse.getDWheel()}</b> has no 1.20.4 equivalent (LWJGL2
 * polled-and-cleared a global accumulator), so scroll is accumulated from
 * {@code ScreenMouseEvents.afterMouseScroll} and drained the same way, in
 * the legacy ±120-per-notch units (only the sign is ever used).</li>
 * <li><b>Seams.</b> Everything that legacy read off a global
 * ({@code System.currentTimeMillis}, {@code Keyboard.isKeyDown},
 * {@code Mouse.isButtonDown}, {@code fontRenderer.getStringWidth}) and every
 * draw primitive is a {@code protected} method so the state machines are
 * headless-testable ({@code InputRendererTest}).</li>
 * </ul>
 *
 * Legacy source: .tools/legacy-src/mclib/src/main/java/mchorse/mclib/client/InputRenderer.java
 */
public class InputRenderer
{
    public static boolean disabledForFrame = false;

    private List<PressedKey> pressedKeys = new ArrayList<PressedKey>();
    private float lastQX = 1;
    private float lastQY = 0;
    private float currentQX = 0;
    private float currentQY = 1;
    private long lastDWheelTime;
    private int lastDWheelScroll;

    /**
     * Replacement for LWJGL2's global {@code Mouse.getDWheel()} accumulator:
     * summed from screen scroll events, drained by {@link #consumeScroll()}.
     */
    private int dWheel;

    /**
     * Suppress all three visualizations for exactly one drawn frame (video
     * capture and {@code GuiCameraEditor}'s hidden-panels mode call this).
     */
    public static void disable()
    {
        disabledForFrame = true;
    }

    /**
     * Wire the legacy subscriptions: {@code DrawScreenEvent.Post} →
     * {@code ScreenEvents.afterRender}, {@code GuiScreenEvent
     * .KeyboardInputEvent.Post} → {@code ScreenKeyboardEvents.afterKeyPress},
     * the {@code Mouse.getDWheel()} feed →
     * {@code ScreenMouseEvents.afterMouseScroll}, and
     * {@code RenderWorldLastEvent} → {@code WorldRenderEvents.END}.
     */
    public void register()
    {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) ->
        {
            ScreenEvents.afterRender(screen).register(this::onDrawEvent);
            ScreenKeyboardEvents.afterKeyPress(screen).register(this::onKeyPressedInGUI);
            ScreenMouseEvents.afterMouseScroll(screen).register((s, mouseX, mouseY, horizontal, vertical) -> this.onScroll(vertical));
        });

        WorldRenderEvents.END.register(context -> this.onRenderLast());
    }

    /* Shift -6 and -8 to get it into the center */
    public static void renderMouseButtons(int x, int y, int scroll, boolean left, boolean right, boolean middle, boolean isScrolling)
    {
        /* Outline */
        GuiDraw.drawRect(x - 1, y, x + 13, y + 16, 0xff000000);
        GuiDraw.drawRect(x, y - 1, x + 12, y + 17, 0xff000000);
        /* Background */
        GuiDraw.drawRect(x, y + 1, x + 12, y + 15, 0xffffffff);
        GuiDraw.drawRect(x + 1, y, x + 11, y + 1, 0xffffffff);
        GuiDraw.drawRect(x + 1, y + 15, x + 11, y + 16, 0xffffffff);
        /* Over outline */
        GuiDraw.drawRect(x, y + 7, x + 12, y + 8, 0xffeeeeee);

        if (left)
        {
            GuiDraw.drawRect(x + 1, y, x + 6, y + 7, 0xffcccccc);
            GuiDraw.drawRect(x, y + 1, x + 1, y + 7, 0xffaaaaaa);
        }

        if (right)
        {
            GuiDraw.drawRect(x + 6, y, x + 11, y + 7, 0xffaaaaaa);
            GuiDraw.drawRect(x + 11, y + 1, x + 12, y + 7, 0xff888888);
        }

        if (middle || isScrolling)
        {
            int offset = 0;

            if (isScrolling)
            {
                offset = scroll < 0 ? 1 : -1;
            }

            GuiDraw.drawRect(x + 4, y, x + 8, y + 6, 0x20000000);
            GuiDraw.drawRect(x + 5, y + 1 + offset, x + 7, y + 5 + offset, 0xff444444);
            GuiDraw.drawRect(x + 5, y + 4 + offset, x + 7, y + 5 + offset, 0xff333333);
        }
    }

    public static void renderMouseWheel(int x, int y, int scroll, long current)
    {
        int color = McLib.primaryColor.get();

        GuiDraw.drawDropShadow(x, y, x + 4, y + 16, 2, ColorUtils.HALF_BLACK + color, color);
        GuiDraw.drawRect(x, y, x + 4, y + 16, 0xff111111);
        GuiDraw.drawRect(x + 1, y, x + 3, y + 15, 0xff2a2a2a);

        int offset = (int) ((current % 1000 / 50) % 4);

        if (scroll >= 0)
        {
            offset = 3 - offset;
        }

        for (int i = 0; i < 4; i++)
        {
            GuiDraw.drawRect(x, y + offset, x + 4, y + offset + 1, 0x88555555);

            y += 4;
        }
    }

    /**
     * Legacy {@code onDrawEvent(DrawScreenEvent.Post)}.
     */
    public void onDrawEvent(Screen screen, DrawContext context, int mouseX, int mouseY, float tickDelta)
    {
        /* GuiBase.render binds the frame's context itself; non-GuiBase screens
         * (vanilla menus) do not, and the GuiDraw statics below need it */
        GuiDraw.bindDrawContext(context);

        this.draw(screen.width, screen.height, mouseX, mouseY);
    }

    /**
     * The screen-size-driven half of {@code onDrawEvent} (split out so the
     * one-frame suppression, the config gating and the placement math are
     * reachable without a {@code Screen}).
     */
    public void draw(int width, int height, int mouseX, int mouseY)
    {
        if (disabledForFrame)
        {
            disabledForFrame = false;

            return;
        }

        this.renderMouse(mouseX, mouseY);

        if (McLib.enableKeystrokeRendering.get())
        {
            this.renderKeys(width, height, mouseX, mouseY);
        }
    }

    /**
     * Draw mouse cursor
     */
    private void renderMouse(int x, int y)
    {
        DrawContext context = GuiDraw.getDrawContext();

        if (context != null)
        {
            /* Legacy GlStateManager.translate(0, 0, 1000) */
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 1000);
        }

        if (McLib.enableCursorRendering.get())
        {
            this.drawCursor(x, y);
        }

        if (McLib.enableMouseButtonRendering.get())
        {
            boolean left = this.isMouseButtonDown(0);
            boolean right = this.isMouseButtonDown(1);
            boolean middle = this.isMouseButtonDown(2);

            int scroll = this.consumeScroll();
            long current = this.now();
            boolean isScrolling = scroll != 0 || current - this.lastDWheelTime < 500;

            if (scroll != 0)
            {
                this.lastDWheelTime = current;
                this.lastDWheelScroll = scroll;
            }

            if (scroll == 0 && isScrolling)
            {
                scroll = this.lastDWheelScroll;
            }

            x += 16;
            y += 2;

            if (left || right || middle || isScrolling)
            {
                this.drawMouseButtons(x, y, scroll, left, right, middle, isScrolling);
            }

            if (isScrolling)
            {
                x += 16;

                this.drawMouseWheel(x, y, scroll, current);
            }
        }

        if (context != null)
        {
            context.getMatrices().pop();
        }
    }

    /**
     * Render pressed key strokes
     */
    private void renderKeys(int width, int height, int mouseX, int mouseY)
    {
        float lqx = Math.round(mouseX / (float) width);
        float lqy = Math.round(mouseY / (float) height);
        int mode = McLib.keystrokeMode.get();

        if (lqx == this.currentQX && lqy == this.currentQY)
        {
            this.currentQX = this.lastQX;
            this.currentQY = this.lastQY;
        }

        if (mode == 1)
        {
            this.currentQX = 0;
            this.currentQY = 1;
        }
        else if (mode == 2)
        {
            this.currentQX = 1;
            this.currentQY = 1;
        }
        else if (mode == 3)
        {
            this.currentQX = 1;
            this.currentQY = 0;
        }
        else if (mode == 4)
        {
            this.currentQX = 0;
            this.currentQY = 0;
        }

        float qx = this.currentQX;
        float qy = this.currentQY;

        int fy = qy > 0.5F ? 1 : -1;
        int offset = McLib.keystrokeOffset.get();
        int mx = offset + (int) (qx * (width - offset * 2));
        int my = offset + (int) (qy * (height - 20 - offset * 2));

        long now = this.now();
        IntPredicate down = this::isKeyDown;
        Iterator<PressedKey> it = this.pressedKeys.iterator();

        while (it.hasNext())
        {
            PressedKey key = it.next();

            if (key.expired(now, down))
            {
                it.remove();
            }
            else
            {
                int x = mx + (qx < 0.5F ? key.x : -(key.x + key.width + 10));
                int y = my + (int) (Interpolation.EXP_INOUT.interpolate(0, 1, key.getFactor(now)) * 50 * fy) + (key.i % 2 == 0 ? -1 : 0);

                this.drawKeystroke(key, x, y);
            }
        }

        this.lastQX = lqx;
        this.lastQY = lqy;
    }

    /**
     * Legacy {@code onKeyPressedInGUI(GuiScreenEvent.KeyboardInputEvent.Post)}
     * — {@code afterKeyPress} is press-only, matching the legacy
     * {@code Keyboard.getEventKeyState()} guard (GLFW repeats arrive here too,
     * which is what drives the {@code (n)} repeat counter).
     */
    public void onKeyPressedInGUI(Screen screen, int glfwKey, int scancode, int modifiers)
    {
        this.onKey(KeyCodes.glfwToLwjgl2(glfwKey), screen.width);
    }

    /**
     * The keycode-agnostic half of the key feed — {@code key} is an
     * <b>LWJGL2</b> scancode, exactly what legacy fed in.
     */
    public void onKey(int key, int screenWidth)
    {
        /* Legacy: inputFocused == "no McLib element is focused" (the name is
         * legacy's; a focused text field suppresses the feed) */
        boolean inputFocused = !this.hasFocusedElement();

        if (!inputFocused)
        {
            return;
        }

        /* Legacy: Keyboard.getEventKey() == 0 ? Keyboard.getEventCharacter() +
         * 256 : Keyboard.getEventKey(). getEventCharacter() returns a char, so
         * the pseudo-code is always >= 256 and the guard below always drops it
         * — the branch is source-verified dead, and unmapped GLFW keys
         * (glfwToLwjgl2 -> KEY_NONE) take the same path. */
        if (key == Keys.KEY_NONE)
        {
            return;
        }

        if (key >= Keys.KEYBOARD_SIZE || key < 0)
        {
            return;
        }

        PressedKey last = null;
        int offset = -1000;

        for (PressedKey pressed : this.pressedKeys)
        {
            if (pressed.key == key)
            {
                offset = pressed.increment(this::stringWidth);
            }
            else if (offset != -1000)
            {
                pressed.x += offset;
            }

            last = pressed;
        }

        if (offset != -1000)
        {
            return;
        }

        offset = McLib.keystrokeOffset.get();

        int x = last == null ? 0 : last.x + last.width + 5;
        String name = Keys.getKeyName(key);
        PressedKey newKey = new PressedKey(key, x, this.now(), name, this.stringWidth(name));

        if (newKey.x + newKey.width + offset > screenWidth - offset * 2)
        {
            newKey.x = 0;
        }

        this.pressedKeys.add(newKey);
    }

    /**
     * Accumulate a scroll notch (legacy LWJGL2 delivered ±120 per notch into
     * the {@code Mouse.getDWheel()} accumulator).
     */
    public void onScroll(double vertical)
    {
        if (vertical != 0)
        {
            this.dWheel += (int) Math.signum(vertical) * 120;
        }
    }

    /**
     * Release the matrix at the end of frame to avoid messing
     * up matrix capture even more (legacy {@code onRenderLast}).
     */
    public void onRenderLast()
    {
        MatrixUtils.releaseMatrix();
    }

    /* State exposed for the headless tests (legacy kept these private) */

    public List<PressedKey> getPressedKeys()
    {
        return this.pressedKeys;
    }

    public float getQuadrantX()
    {
        return this.currentQX;
    }

    public float getQuadrantY()
    {
        return this.currentQY;
    }

    /* Seams over the LWJGL/Minecraft globals legacy polled directly */

    protected long now()
    {
        return System.currentTimeMillis();
    }

    /**
     * Legacy {@code GuiBase.getCurrent().activeElement != null} — the feed is
     * suppressed while a McLib element (e.g. a text field) has focus.
     */
    protected boolean hasFocusedElement()
    {
        GuiContext current = GuiBase.getCurrent();

        return current != null && current.activeElement != null;
    }

    protected int consumeScroll()
    {
        int scroll = this.dWheel;

        this.dWheel = 0;

        return scroll;
    }

    protected boolean isMouseButtonDown(int button)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null || mc.getWindow() == null)
        {
            return false;
        }

        return GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), button) == GLFW.GLFW_PRESS;
    }

    /**
     * @param key LWJGL2 scancode (legacy {@code Keyboard.isKeyDown})
     */
    protected boolean isKeyDown(int key)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        int glfw = KeyCodes.lwjgl2ToGlfw(key);

        if (mc == null || mc.getWindow() == null || glfw == KeyCodes.GLFW_KEY_UNKNOWN)
        {
            return false;
        }

        return InputUtil.isKeyPressed(mc.getWindow().getHandle(), glfw);
    }

    protected int stringWidth(String text)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null || mc.textRenderer == null || text == null)
        {
            return 0;
        }

        return mc.textRenderer.getWidth(text);
    }

    /* Draw seams (recorded by the headless tests) */

    protected void drawCursor(int x, int y)
    {
        Icons.CURSOR.render(x, y);
    }

    protected void drawMouseButtons(int x, int y, int scroll, boolean left, boolean right, boolean middle, boolean isScrolling)
    {
        renderMouseButtons(x, y, scroll, left, right, middle, isScrolling);
    }

    protected void drawMouseWheel(int x, int y, int scroll, long current)
    {
        renderMouseWheel(x, y, scroll, current);
    }

    protected void drawKeystroke(PressedKey key, int x, int y)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        GuiDraw.drawDropShadow(x, y, x + 10 + key.width, y + 20, 4, 0x44000000, 0);
        GuiDraw.drawRect(x, y, x + 10 + key.width, y + 20, 0xff000000 + McLib.primaryColor.get());

        if (mc != null && mc.textRenderer != null)
        {
            GuiDraw.drawStringWithShadow(mc.textRenderer, key.getLabel(), x + 5, y + 6, 0xffffff);
        }
    }

    /**
     * Information about pressed key strokes.
     *
     * <p>Port note: legacy read {@code System.currentTimeMillis()},
     * {@code Keyboard.isKeyDown} and {@code fontRenderer.getStringWidth}
     * straight out of the constructor / methods; here the clock, the key-down
     * predicate and the string-width function are passed in so the lifecycle
     * is headless-testable. Field layout and all constants are legacy.</p>
     */
    public static class PressedKey
    {
        public static int INDEX = 0;

        public int key;
        public long time;
        public int x;

        public String name;
        public int width;
        public int i;
        public int times = 1;

        public PressedKey(int key, int x, long time, String name, int width)
        {
            this.key = key;
            this.time = time;
            this.x = x;

            this.name = name;
            this.width = width;
            this.i = INDEX++;
        }

        public float getFactor(long now)
        {
            return (now - this.time - 500) / 1000F;
        }

        public boolean expired(long now, IntPredicate down)
        {
            if (down.test(this.key))
            {
                this.time = now;
            }

            return now - this.time > 1500;
        }

        public String getLabel()
        {
            if (this.times > 1)
            {
                return this.name + " (" + this.times + ")";
            }

            return this.name;
        }

        public int increment(ToIntFunction<String> widths)
        {
            int lastWidth = this.width;

            this.times++;
            this.width = widths.applyAsInt(this.getLabel());

            return this.width - lastWidth;
        }
    }
}
