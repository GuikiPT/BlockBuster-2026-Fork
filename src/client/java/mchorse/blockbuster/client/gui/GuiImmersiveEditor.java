package mchorse.blockbuster.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import mchorse.mclib.client.gui.framework.GuiBase;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.IGuiElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.keys.IKey;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.Window;

/**
 * Immersive Editor (roadmap P143).
 *
 * <p>The in-world editing overlay {@code Screen}: a transparent overlay over the
 * live world with orbit-camera control and a docked editing panel. Full port of
 * 1.12.2 {@code client/gui/GuiImmersiveEditor} (the {@code show()} /
 * {@code closeScreen()} world-state snapshot/restore machine + the F1
 * outer-panel layer). Every decision the legacy {@code show()}/
 * {@code closeScreen()} pair made lives on the reconciled A8 logic core
 * ({@link ImmersiveEditorLogic}); this class owns only the retained-mode
 * plumbing and drives that object.</p>
 *
 * <h2>Legacy → 1.20.4 seam map</h2>
 * <ul>
 *   <li><b>Screen install (legacy {@code mc.currentScreen = this} +
 *       {@code setWorldAndResolution}).</b> Legacy deliberately bypassed
 *       {@code displayGuiScreen} so the previous screen is <em>not</em>
 *       {@code onGuiClosed()}'d on open; the port assigns
 *       {@link MinecraftClient#currentScreen} directly and calls
 *       {@link Screen#init(MinecraftClient, int, int)} — same bypass.</li>
 *   <li><b>Gamemode switching.</b> Legacy sent the literal chat
 *       {@code "/gamemode 3"} on show and {@code "/gamemode " + lastMode.getID()}
 *       on close. 1.20.4 has no numeric gamemode args, so the port sends the
 *       same command with the mode <em>name</em> through
 *       {@code player.networkHandler.sendChatCommand(...)} (the strings are built
 *       by {@link ImmersiveEditorLogic#enterCommand()} /
 *       {@link ImmersiveEditorLogic.Restore#restoreCommand}) — same observable
 *       behavior (OP required, appears in the server log). See the A8 parity
 *       note; this is NOT a silent client-side switch.</li>
 *   <li><b>FOV force / roll zero / HUD suppression / per-frame morph swap +
 *       camera teleport.</b> Legacy {@code MinecraftForge.EVENT_BUS.register(
 *       this.morphs)}. On 1.20.4 these are global mixins/callbacks gated on the
 *       immersive editor being open; {@link #current} is the static handle they
 *       consult (set in {@link #show()}, cleared on close — the "register" /
 *       "unregister" analog), reaching the live state through
 *       {@code current.morphs} and {@code current.logic}. See the wiring note in
 *       the return of this unit.</li>
 * </ul>
 */
public class GuiImmersiveEditor extends GuiBase
{
    /**
     * Legacy {@code public static final IKey CATEGORY} — the keybind category
     * shared with {@link GuiImmersiveMorphMenu} (its F3 hide-GUI-model keybind
     * registers under this same category, exactly as legacy).
     */
    public static final IKey CATEGORY = IKey.lang(ImmersiveEditorLogic.CATEGORY_KEY);

    /**
     * The immersive editor currently open, or {@code null}. Mirrors legacy's
     * {@code mc.currentScreen = this} on show and the
     * {@code MinecraftForge.EVENT_BUS.register/unregister(this.morphs)} pair: the
     * FOV/roll/HUD/world-render seams gate on it (see class docs). Set as the
     * last step of {@link #show()}, cleared during the close restore (before
     * {@link #onClose} fires — matching legacy's unregister position).
     */
    public static GuiImmersiveEditor current;

    /** The in-world morph menu (C2). Owns the actual synchronize-to-world work. */
    public GuiImmersiveMorphMenu morphs;

    /** The auxiliary F1-toggled docking layer for the editing panel. */
    public GuiOuterScreen outerPanel;

    /**
     * The A8 state machine ({@code show()}/{@code closeScreen()} snapshot +
     * restore, gamemode command strings, the exactly-once {@code onClose} gate).
     */
    public final ImmersiveEditorLogic<GuiImmersiveEditor> logic = new ImmersiveEditorLogic<GuiImmersiveEditor>(this);

    /**
     * Legacy {@code Consumer<GuiImmersiveEditor> onClose} — consumers (P136/P139)
     * set this after {@link #show()}; it is forwarded into
     * {@link ImmersiveEditorLogic#onClose} at close time and fired exactly once
     * at the legacy position (after the world restore, before the menu is
     * hidden).
     */
    public Consumer<GuiImmersiveEditor> onClose;

    /**
     * The {@link ImmersiveEditorLogic.Restore} produced by the last
     * {@link #closeScreen()}, carried so {@link #removed()} can forward
     * {@code removed()} to the previous screen through
     * {@link ImmersiveEditorLogic.Restore#previousScreen} instead of the
     * already-nulled snapshot field (legacy dereferenced {@code this.lastScreen}
     * after {@code closeScreen()} nulled it — a latent NPE the A8 core documents
     * and this field routes around).
     */
    private ImmersiveEditorLogic.Restore lastRestore;

    public GuiImmersiveEditor(MinecraftClient mc)
    {
        this.morphs = new GuiImmersiveMorphMenu(mc);
        this.morphs.flex().relative(this.viewport).xy(0F, 0F).wh(1F, 1F);

        this.outerPanel = new GuiOuterScreen(mc);
        this.outerPanel.flex().relative(this.viewport).xy(0F, 0F).wh(1F, 1F);

        this.root.add(this.morphs, this.outerPanel);

        this.root.keys().register(IKey.lang(ImmersiveEditorLogic.TOGGLE_OUTER_PANEL_KEY), ImmersiveEditorLogic.TOGGLE_OUTER_PANEL_KEYCODE, () -> this.outerPanel.toggleVisible())
            .category(CATEGORY).active(() -> ImmersiveEditorLogic.outerPanelToggleActive(this.outerPanel));
    }

    /**
     * Legacy {@code show()}: snapshot the world state, install this screen over
     * the world (bypassing {@code displayGuiScreen} so the previous screen keeps
     * running), prime the morph menu, force first person + the player as the
     * render-view entity, switch into spectator via the gamemode command, and
     * arm the render/HUD/FOV/roll seams ({@link #current}). Order is 1:1 with
     * legacy.
     */
    public void show()
    {
        MinecraftClient mc = this.context.mc;

        /* Snapshot BEFORE we overwrite currentScreen (legacy read
         * mc.currentScreen into lastScreen first). */
        Screen previous = mc == null ? null : mc.currentScreen;

        this.logic.show(mc, previous);

        if (mc != null)
        {
            /* Legacy bypassed displayGuiScreen: assign the field directly so the
             * previous screen is NOT removed(), then init(mc, w, h) this one
             * (legacy setWorldAndResolution). */
            mc.currentScreen = this;

            Window window = mc.getWindow();

            if (window != null)
            {
                this.init(mc, window.getScaledWidth(), window.getScaledHeight());
            }
        }

        this.morphs.reload();
        this.morphs.resize();
        this.morphs.setVisible(true);
        this.outerPanel.setVisible(false);

        if (mc != null && mc.options != null)
        {
            /* Legacy gameSettings.thirdPersonView = 0 */
            mc.options.setPerspective(Perspective.FIRST_PERSON);
        }

        if (mc != null && mc.player != null)
        {
            /* Legacy setRenderViewEntity(mc.player). NOTE (A8 quirk): never
             * undone on close — do not "restore" the camera entity. */
            mc.setCameraEntity(mc.player);
        }

        /* Legacy: if (lastMode != SPECTATOR) player.sendChatMessage("/gamemode 3") */
        String enter = this.logic.enterCommand();

        if (enter != null && mc != null && mc.player != null)
        {
            mc.player.networkHandler.sendChatCommand(enter);
        }

        /* Legacy MinecraftForge.EVENT_BUS.register(this.morphs) — arm the seams. */
        current = this;
    }

    /**
     * Legacy {@code onGuiClosed()}: run the McLib teardown ({@link #closeScreen()})
     * then forward {@code removed()} to the previous screen. Legacy forwarded
     * through {@code this.lastScreen} AFTER {@code closeScreen()} had nulled it
     * (a latent NPE); the port forwards through the captured
     * {@link ImmersiveEditorLogic.Restore#previousScreen} instead.
     */
    @Override
    public void removed()
    {
        boolean wasShowing = this.logic.isShowing();

        this.closeScreen();

        if (wasShowing && this.lastRestore != null && this.lastRestore.previousScreen != null)
        {
            this.lastRestore.previousScreen.removed();
        }

        super.removed();
    }

    /**
     * Legacy {@code doesGuiPauseGame()} = false: the world must keep ticking
     * behind the overlay (the morph preview and per-frame camera sync are live).
     */
    @Override
    public boolean shouldPause()
    {
        return false;
    }

    /**
     * Legacy {@code closeScreen()}: restore the snapshot the world was in before
     * the editor, fire {@link #onClose} exactly once, and tear the menu down.
     * Idempotent — guarded on {@link ImmersiveEditorLogic#isShowing()} (legacy
     * {@code if (this.lastScreen == null) return;}).
     *
     * <p>Legacy statement order is preserved: {@code morphs.finish()} first, then
     * the world restore (screen swap + init, perspective, gamemode command,
     * teleport) applied at the exact legacy position inside
     * {@link ImmersiveEditorLogic#closeScreen(boolean, Consumer)} — before
     * {@code onClose} — then {@code morphs.setVisible(false)} last.</p>
     */
    @Override
    protected void closeScreen()
    {
        if (!this.logic.isShowing())
        {
            return;
        }

        /* Legacy first statement of closeScreen(). */
        this.morphs.finish();

        final MinecraftClient mc = this.context.mc;
        boolean playerPresent = mc != null && mc.player != null;

        /* Forward the editor-level onClose into the logic so it fires at the
         * legacy position (after the world restore); logic nulls its copy after
         * firing, so clear ours too (legacy set onClose = null after accept). */
        this.logic.onClose = this.onClose;
        this.onClose = null;

        this.lastRestore = this.logic.closeScreen(playerPresent, (r) -> this.applyRestore(mc, r));

        /* Legacy very-last statement of closeScreen(). */
        this.morphs.setVisible(false);
    }

    /**
     * The world restore, applied by {@link ImmersiveEditorLogic#closeScreen(
     * boolean, Consumer)} at the legacy position (after the snapshot is read,
     * before {@code onClose}). Legacy order:
     * <ol>
     *   <li>{@code mc.currentScreen = lastScreen} + {@code setWorldAndResolution}
     *       (direct assignment, so we are not routed through {@code setScreen});</li>
     *   <li>{@code gameSettings.thirdPersonView = lastTPS} — unconditional, even
     *       with no player;</li>
     *   <li>when a player is present: {@code sendChatMessage("/gamemode " + id)}
     *       then {@code setPositionAndRotation(x, y, z, yaw, pitch)} — note the
     *       yaw-then-pitch argument order (the reverse of the field order).</li>
     * </ol>
     * The {@link #current} clear is the {@code EVENT_BUS.unregister(this.morphs)}
     * analog, at its legacy position (between the teleport and {@code onClose}).
     */
    private void applyRestore(MinecraftClient mc, ImmersiveEditorLogic.Restore r)
    {
        if (mc != null)
        {
            /* 1. Reinstall the previous screen (legacy direct field assignment +
             *    setWorldAndResolution). */
            mc.currentScreen = r.previousScreen;

            Window window = mc.getWindow();

            if (r.previousScreen != null && window != null)
            {
                r.previousScreen.init(mc, window.getScaledWidth(), window.getScaledHeight());
            }

            /* 2. Perspective — unconditional (legacy thirdPersonView = lastTPS). */
            if (mc.options != null)
            {
                mc.options.setPerspective(r.perspective());
            }

            /* 3. Gamemode command + teleport — only with a live player
             *    (r.restoreCommand is null when the player was absent). */
            if (r.restoreCommand != null && mc.player != null)
            {
                mc.player.networkHandler.sendChatCommand(r.restoreCommand);
                mc.player.updatePositionAndAngles(r.posX, r.posY, r.posZ, r.rotYaw, r.rotPitch);
            }
        }

        /* Legacy MinecraftForge.EVENT_BUS.unregister(this.morphs) — disarm the
         * seams before onClose runs (P136/P139 close handlers re-open a
         * dashboard and expect the editor already torn down). */
        if (current == this)
        {
            current = null;
        }
    }

    /**
     * Legacy {@code drawScreen(...)}: draw the blurred custom background over the
     * FULL screen before the framework draw, but ONLY when not in immersion mode
     * (in immersion mode the live world shows through). The morph menu's own
     * per-area dim rect is a separate draw inside {@link GuiImmersiveMorphMenu}.
     */
    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float partialTicks)
    {
        if (!this.morphs.isImmersionMode())
        {
            this.context.drawContext = drawContext;
            GuiDraw.bindDrawContext(drawContext);
            GuiDraw.drawCustomBackground(0, 0, this.width, this.height);
        }

        super.render(drawContext, mouseX, mouseY, partialTicks);
    }

    /**
     * The auxiliary docking layer toggled by F1. A plain {@link GuiElement} whose
     * input handlers dispatch to children topmost-first over a defensive snapshot
     * (legacy iterated an {@code ImmutableList.copyOf(getChildren())} in reverse,
     * so a handler that mutates the child list mid-dispatch cannot throw a
     * {@code ConcurrentModificationException}). Draws the custom background, the
     * children, and the "hide outer panel" hint label.
     *
     * <p>Deviation: legacy used Guava's {@code ImmutableList.copyOf}; the port
     * uses {@code new ArrayList<>(getChildren())} — the same read-only snapshot
     * semantics without pulling Guava into the call.</p>
     */
    public static class GuiOuterScreen extends GuiElement
    {
        public GuiOuterScreen(MinecraftClient mc)
        {
            super(mc);
        }

        @Override
        public boolean mouseClicked(GuiContext context)
        {
            List<IGuiElement> list = new ArrayList<IGuiElement>(this.getChildren());

            for (int i = list.size() - 1; i >= 0; i--)
            {
                IGuiElement element = list.get(i);

                if (element.isEnabled() && element.mouseClicked(context))
                {
                    return true;
                }
            }

            return false;
        }

        @Override
        public boolean mouseScrolled(GuiContext context)
        {
            List<IGuiElement> list = new ArrayList<IGuiElement>(this.getChildren());

            for (int i = list.size() - 1; i >= 0; i--)
            {
                IGuiElement element = list.get(i);

                if (element.isEnabled() && element.mouseScrolled(context))
                {
                    return true;
                }
            }

            return false;
        }

        @Override
        public void mouseReleased(GuiContext context)
        {
            List<IGuiElement> list = new ArrayList<IGuiElement>(this.getChildren());

            for (int i = list.size() - 1; i >= 0; i--)
            {
                IGuiElement element = list.get(i);

                if (element.isEnabled())
                {
                    element.mouseReleased(context);
                }
            }
        }

        @Override
        public void draw(GuiContext context)
        {
            GuiDraw.drawCustomBackground(this.area.x, this.area.y, this.area.ex(), this.area.ey());

            super.draw(context);

            GuiDraw.drawTextBackground(this.font, IKey.lang(ImmersiveEditorLogic.HIDE_OUTER_PANEL_KEY).get(), this.area.x + 5, this.area.y + 5, 0xFFFFFF, 0);
        }
    }
}
