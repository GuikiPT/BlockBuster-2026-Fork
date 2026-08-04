package mchorse.blockbuster.client.gui;

import java.util.function.Consumer;

import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.utils.LegacyKeyCodes;
import mchorse.mclib.utils.EntityUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.world.GameMode;

/**
 * State machine of {@code GuiImmersiveEditor} (roadmap P143).
 *
 * <p>This class owns every decision the legacy {@code show()} /
 * {@code closeScreen()} pair made; the concrete P143 screen
 * ({@code GuiImmersiveEditor extends GuiBase}) owns only the retained-mode
 * plumbing (root/viewport, the {@code morphs} menu, the {@code outerPanel}
 * layer, keybind registration) and drives this object.</p>
 *
 * <p><b>Reconciliation note (P143 surface fix).</b> The first landing of this
 * class stored the previous screen as a bare {@code Object}, the gamemode as a
 * hand-rolled {@code int} and typed {@code onClose} against itself, so the
 * retained-mode screen could not use it without casting at every call site and
 * re-deriving {@code GameMode}/{@code Perspective} mappings the tree already
 * has. The snapshot now carries the real 1.20.4 types
 * ({@link Screen}, {@link GameMode}) and the owner is a type parameter so
 * {@link #onClose} can be the legacy {@code Consumer<GuiImmersiveEditor>}.</p>
 *
 * <p>Legacy source: {@code blockbuster-1.12/.../client/gui/GuiImmersiveEditor.java}.
 * Legacy sent the literal chat message {@code "/gamemode 3"} (and
 * {@code "/gamemode " + lastMode.getID()} on restore). 1.20.4 has no numeric
 * gamemode arguments, so the port sends the same command with the mode
 * <em>name</em> through {@code player.networkHandler.sendChatCommand(...)} —
 * same observable behavior (OP required, appears in the server log), matching
 * the Aperture {@code CameraControl} precedent already in this tree.</p>
 *
 * <p>Two more legacy screen-level behaviors have no logic representation and
 * must be re-created by the concrete screen:</p>
 * <ul>
 *   <li>{@code doesGuiPauseGame()} returned {@code false} → override
 *       {@code Screen#shouldPause()} to return {@code false} (the world must
 *       keep ticking while the editor is open);</li>
 *   <li>{@code drawScreen(...)} drew
 *       {@code GuiDraw.drawCustomBackground(0, 0, width, height)} over the FULL
 *       screen before {@code super}, gated on
 *       {@code !morphs.isImmersionMode()} — i.e.
 *       {@link ImmersiveMorphMenuLogic#shouldDimBackground()} (the menu's own
 *       {@link ImmersiveMorphMenuLogic#DIM_COLOR} rect over its area is a
 *       separate, additional draw with the same gate).</li>
 * </ul>
 *
 * @param <T> the owning screen type handed to {@link #onClose} (legacy
 *            {@code Consumer<GuiImmersiveEditor>}). Headless tests use
 *            {@code ImmersiveEditorLogic<Object>}.
 */
public class ImmersiveEditorLogic<T>
{
    /* Lang keys — shared verbatim with the concrete screen and the morph menu
     * so neither side re-types them (all four exist in en_us.json). */
    public static final String CATEGORY_KEY = "blockbuster.gui.immersive_editor.keys.category";
    public static final String TOGGLE_OUTER_PANEL_KEY = "blockbuster.gui.immersive_editor.keys.toggle_outer_panel";
    public static final String HIDE_OUTER_PANEL_KEY = "blockbuster.gui.immersive_editor.hide_outer_panel";

    /** Legacy {@code Keyboard.KEY_F1} for the outer-panel toggle. */
    public static final int TOGGLE_OUTER_PANEL_KEYCODE = LegacyKeyCodes.KEY_F1;

    /** Legacy {@code thirdPersonView = 0} forced on show (first person). */
    public static final int FIRST_PERSON = 0;

    /* Snapshot (legacy show()). lastScreen doubles as the "is showing" guard. */
    public Screen lastScreen;
    public int lastTPS;
    public GameMode lastMode;

    public double lastPosX;
    public double lastPosY;
    public double lastPosZ;
    public float lastRotPitch;
    public float lastRotYaw;

    /** The screen that owns this state machine (passed to {@link #onClose}). */
    public T owner;

    /**
     * Legacy {@code Consumer<GuiImmersiveEditor> onClose} — fired exactly once,
     * at the legacy position inside {@link #closeScreen} (after the world state
     * has been captured into the {@link Restore} and {@code lastScreen} has been
     * cleared, before the menu is hidden), then nulled.
     */
    public Consumer<T> onClose;

    public ImmersiveEditorLogic()
    {}

    public ImmersiveEditorLogic(T owner)
    {
        this.owner = owner;
    }

    /**
     * Snapshot the world state that must be restored on close. Mirrors legacy
     * {@code show()} recording {@code lastScreen}, {@code thirdPersonView}
     * ({@code lastTPS}), gamemode ({@code lastMode}), and player pos/rot.
     *
     * <p><b>The rest of legacy {@code show()} is retained-mode and belongs to
     * the concrete screen</b> — it is listed here because none of it is
     * modelled by this class and all of it is load-bearing, in this order:</p>
     * <ol>
     *   <li>{@code mc.currentScreen = this} + {@code setWorldAndResolution}
     *       (1.20.4: install the screen and {@code init(mc, w, h)} it) —
     *       legacy bypassed {@code displayGuiScreen} deliberately so the
     *       previous screen is NOT {@code onGuiClosed()}'d;</li>
     *   <li>{@code morphs.reload()}, {@code morphs.resize()},
     *       {@code morphs.setVisible(true)}, {@code outerPanel.setVisible(false)}
     *       — the outer panel always starts hidden;</li>
     *   <li>{@code mc.gameSettings.thirdPersonView = 0}
     *       ({@link #FIRST_PERSON}; 1.20.4
     *       {@code mc.options.setPerspective(Perspective.FIRST_PERSON)});</li>
     *   <li>{@code mc.setRenderViewEntity(mc.player)} (yarn
     *       {@code mc.setCameraEntity(mc.player)}). <b>Legacy quirk:</b> this is
     *       never undone in {@code closeScreen()} — do not "fix" it by
     *       restoring the previous camera entity;</li>
     *   <li>{@link #enterCommand()} when non-null;</li>
     *   <li>register the menu's render/HUD/FOV/roll hooks (legacy
     *       {@code MinecraftForge.EVENT_BUS.register(this.morphs)}).</li>
     * </ol>
     */
    public void show(Screen previousScreen, int thirdPersonView, GameMode gameMode,
        double posX, double posY, double posZ, float rotPitch, float rotYaw)
    {
        this.lastScreen = previousScreen;
        this.lastTPS = thirdPersonView;
        this.lastMode = gameMode;
        this.lastPosX = posX;
        this.lastPosY = posY;
        this.lastPosZ = posZ;
        this.lastRotPitch = rotPitch;
        this.lastRotYaw = rotYaw;
    }

    /**
     * Convenience overload taking the snapshot straight off the client, exactly
     * as legacy {@code show()} did ({@code mc.currentScreen},
     * {@code mc.gameSettings.thirdPersonView}, {@code EntityUtils.getGameMode()},
     * {@code mc.player.pos*}/{@code rotation*}). Every touchpoint is
     * null-guarded so a disconnecting client can't crash the open.
     *
     * @param previousScreen legacy {@code this.mc.currentScreen}, read by the
     *        caller <em>before</em> it installs the immersive screen
     */
    public void show(MinecraftClient mc, Screen previousScreen)
    {
        int tps = mc != null && mc.options != null ? thirdPersonView(mc.options.getPerspective()) : FIRST_PERSON;
        GameMode mode = EntityUtils.getGameMode();
        ClientPlayerEntity player = mc == null ? null : mc.player;

        if (player == null)
        {
            this.show(previousScreen, tps, mode, 0, 0, 0, 0F, 0F);
        }
        else
        {
            this.show(previousScreen, tps, mode,
                player.getX(), player.getY(), player.getZ(), player.getPitch(), player.getYaw());
        }
    }

    /** Whether {@link #show} ran and {@link #closeScreen} has not yet. */
    public boolean isShowing()
    {
        return this.lastScreen != null;
    }

    /**
     * The gamemode-command argument to switch <em>into</em> the editor, or
     * {@code null} when the player is already spectator (legacy skipped the
     * {@code "/gamemode 3"} chat message in that case).
     */
    public String enterCommand()
    {
        return this.lastMode == GameMode.SPECTATOR ? null : "gamemode " + modeName(GameMode.SPECTATOR);
    }

    /**
     * The result of a {@link #closeScreen} call — what the screen should apply.
     * {@code ran} is false when there was nothing to restore (double-close /
     * never shown), which is also the screen's exactly-once gate for its own
     * teardown; {@code restoreCommand} is null when the player was null
     * (disconnect while editing) so no command is sent.
     */
    public static final class Restore
    {
        public final boolean ran;

        /**
         * The screen that was open before the editor (legacy {@code lastScreen},
         * captured here <em>before</em> the field is cleared). Legacy
         * {@code onGuiClosed()} dereferenced {@code this.lastScreen} AFTER
         * {@code closeScreen()} had nulled it — a latent NPE; the port forwards
         * through this field instead (see class parity notes).
         */
        public final Screen previousScreen;

        public final int thirdPersonView;
        public final String restoreCommand;
        public final double posX;
        public final double posY;
        public final double posZ;
        public final float rotPitch;
        public final float rotYaw;

        private Restore(boolean ran, Screen previousScreen, int tps, String cmd,
            double x, double y, double z, float pitch, float yaw)
        {
            this.ran = ran;
            this.previousScreen = previousScreen;
            this.thirdPersonView = tps;
            this.restoreCommand = cmd;
            this.posX = x;
            this.posY = y;
            this.posZ = z;
            this.rotPitch = pitch;
            this.rotYaw = yaw;
        }

        /** {@link #thirdPersonView} as the 1.20.4 option value. */
        public Perspective perspective()
        {
            return ImmersiveEditorLogic.perspective(this.thirdPersonView);
        }

        static Restore noop()
        {
            return new Restore(false, null, 0, null, 0, 0, 0, 0F, 0F);
        }
    }

    /**
     * Legacy {@code closeScreen()}. Guarded on {@code lastScreen == null}
     * (nothing to do). Clears {@code lastScreen}, fires {@code onClose} exactly
     * once, and returns the values the screen must restore. When
     * {@code playerPresent} is false, {@code restoreCommand} and the pos/rot are
     * suppressed (legacy only restored those {@code if (mc.player != null)}).
     *
     * <p><b>Ordering:</b> legacy applied the whole world restore (screen swap +
     * resize, {@code thirdPersonView}, the gamemode command, the position/
     * rotation teleport) <em>before</em> firing {@code onClose} — P136/P139
     * close handlers reopen a dashboard and send packets and legacy guaranteed
     * they ran with the player already teleported back and already out of
     * spectator. This overload cannot honour that (the caller only sees the
     * {@link Restore} after {@code onClose} has fired), so use
     * {@link #closeScreen(boolean, Consumer)} from the concrete screen and keep
     * this one for tests / callers with no {@code onClose}.</p>
     */
    public Restore closeScreen(boolean playerPresent)
    {
        return this.closeScreen(playerPresent, null);
    }

    /**
     * Legacy {@code closeScreen()} with the world restore applied at the exact
     * legacy position: after the snapshot is read, <em>before</em>
     * {@link #onClose} fires.
     *
     * <p>The screen's {@code applyRestore} should do, in legacy order:</p>
     * <ol>
     *   <li>{@code mc.setScreen(r.previousScreen)} / resize it (legacy
     *       {@code mc.currentScreen = lastScreen} +
     *       {@code setWorldAndResolution}),</li>
     *   <li>{@code mc.options.setPerspective(r.perspective())} (legacy
     *       {@code thirdPersonView = lastTPS}) — unconditional, even with no
     *       player,</li>
     *   <li>when {@code r.restoreCommand != null}:
     *       {@code player.networkHandler.sendChatCommand(r.restoreCommand)} then
     *       <b>{@code player.updatePositionAndAngles(r.posX, r.posY, r.posZ,
     *       r.rotYaw, r.rotPitch)}</b> — note legacy's argument order is
     *       <em>yaw then pitch</em>, the reverse of the field order in
     *       {@link Restore}. Legacy used {@code setPositionAndRotation} only
     *       here (unlike the per-frame camera write, which also called
     *       {@code setLocationAndAngles}); do not add the second call.</li>
     * </ol>
     *
     * <p>Legacy also ran {@code morphs.finish()} as the very first statement of
     * {@code closeScreen()} (before any of the above) and
     * {@code morphs.setVisible(false)} as the very last (after
     * {@code onClose}), and unregistered the menu from the event bus between the
     * player restore and {@code onClose}. Those are retained-mode steps the
     * screen owns; {@link #isShowing()} is the same guard this method uses, so
     * the screen can bracket them with it.</p>
     *
     * @param applyRestore applied with the fresh {@link Restore} at the legacy
     *        position; may be {@code null}. Not called when nothing was showing.
     */
    public Restore closeScreen(boolean playerPresent, Consumer<Restore> applyRestore)
    {
        if (this.lastScreen == null)
        {
            return Restore.noop();
        }

        Screen previous = this.lastScreen;
        int tps = this.lastTPS;
        Restore restore;

        if (playerPresent)
        {
            restore = new Restore(true, previous, tps, "gamemode " + modeName(this.lastMode),
                this.lastPosX, this.lastPosY, this.lastPosZ, this.lastRotPitch, this.lastRotYaw);
        }
        else
        {
            restore = new Restore(true, previous, tps, null, 0, 0, 0, 0F, 0F);
        }

        /* Cleared before anything can re-enter (legacy nulled it after the
         * restore, which let a re-entrant close double-restore). */
        this.lastScreen = null;

        if (applyRestore != null)
        {
            applyRestore.accept(restore);
        }

        if (this.onClose != null)
        {
            Consumer<T> consumer = this.onClose;

            this.onClose = null;
            consumer.accept(this.owner);
        }

        return restore;
    }

    /**
     * Convenience overload: {@code playerPresent} is derived from the client
     * (legacy {@code if (this.mc.player != null)}).
     */
    public Restore closeScreen(MinecraftClient mc)
    {
        return this.closeScreen(mc != null && mc.player != null, null);
    }

    /**
     * Convenience overload of {@link #closeScreen(boolean, Consumer)} deriving
     * {@code playerPresent} from the client.
     */
    public Restore closeScreen(MinecraftClient mc, Consumer<Restore> applyRestore)
    {
        return this.closeScreen(mc != null && mc.player != null, applyRestore);
    }

    /**
     * F1 outer-panel toggle is active only when the panel has children (legacy
     * {@code .active(() -> !outerPanel.getChildren().isEmpty())}).
     */
    public static boolean outerPanelToggleActive(int outerPanelChildCount)
    {
        return outerPanelChildCount > 0;
    }

    /**
     * Retained-mode form of {@link #outerPanelToggleActive(int)} — bind directly
     * as {@code .active(() -> ImmersiveEditorLogic.outerPanelToggleActive(this.outerPanel))}.
     * A null panel is inactive (total rule).
     */
    public static boolean outerPanelToggleActive(GuiElement outerPanel)
    {
        return outerPanel != null && !outerPanel.getChildren().isEmpty();
    }

    /**
     * Map a gamemode to its 1.20.4 command name. {@code null} (gamemode not yet
     * known from the server) falls back to survival, matching McLib's
     * {@code EntityUtils.getGameMode()} default — total rule, never throws.
     */
    public static String modeName(GameMode mode)
    {
        return mode == null ? GameMode.SURVIVAL.getName() : mode.getName();
    }

    /**
     * Legacy {@code gameSettings.thirdPersonView} (0 first person, 1 third
     * person back, 2 third person front) → 1.20.4 {@link Perspective}. The
     * enum's declaration order is exactly the legacy numbering. Out-of-range
     * values fall back to first person (total rule).
     */
    public static Perspective perspective(int thirdPersonView)
    {
        Perspective[] values = Perspective.values();

        if (thirdPersonView < 0 || thirdPersonView >= values.length)
        {
            return Perspective.FIRST_PERSON;
        }

        return values[thirdPersonView];
    }

    /** Inverse of {@link #perspective(int)}; {@code null} → first person. */
    public static int thirdPersonView(Perspective perspective)
    {
        return perspective == null ? FIRST_PERSON : perspective.ordinal();
    }
}
