package mchorse.blockbuster.client.gui;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import mchorse.mclib.client.gui.framework.elements.GuiModelRenderer;
import net.minecraft.entity.LivingEntity;

/**
 * State machine of {@code GuiImmersiveMorphMenu} (roadmap P143).
 *
 * <p>The concrete P143 menu ({@code GuiImmersiveMorphMenu extends
 * GuiCreativeMorphsMenu}) keeps the retained-mode/GL parts — the close button,
 * the F3 keybind, the {@code PreviewMorph} inner class, the
 * {@code target}/{@code updateCallback}/{@code frameProvider}/
 * {@code beforeRender}/{@code afterRender} seam fields — and delegates every
 * decision to this object. The Forge event seams become 1.20.4 hooks:</p>
 * <ul>
 *   <li>FOV force → {@code GameRenderer#getFov} @RETURN (shared S15 seam),
 *       gated on {@link #isImmersionMode()} (legacy {@code onFovModifierEvent},
 *       {@code EventPriority.LOWEST}),</li>
 *   <li>roll zero → the S15 camera-roll seam, gated on
 *       {@link #isImmersionMode()} (legacy {@code onCameraOrient},
 *       {@code EventPriority.LOWEST}),</li>
 *   <li>HUD cancel → {@code InGameHud#render} HEAD suppression, gated on
 *       {@link #hudSuppressed()} — <b>NOT</b> on {@link #isImmersionMode()};
 *       legacy {@code onRenderGameOverlayEvent} called
 *       {@code event.setCanceled(true)} unconditionally for the whole time the
 *       menu was subscribed to the event bus (registered in
 *       {@code GuiImmersiveEditor.show()}, unregistered in its
 *       {@code closeScreen()}),</li>
 *   <li>render-tick START/END morph swap → {@code WorldRenderEvents.START/END},
 *       driven by {@link #beginFrame} / {@link #endFrame}. Legacy's Phase.START
 *       body ran in this order: (1) {@link #isEditImmersion()} →
 *       {@code preview.renderComplete = false} + {@code updateCallback}, then
 *       (2) {@link #isImmersionMode()} → {@link #beginFrame} + the
 *       {@link ImmersiveOrbitCamera#place camera placement}.</li>
 * </ul>
 *
 * <p>All four seams only exist while the menu is "subscribed", i.e. between
 * {@code ImmersiveEditorLogic.show(...)} and its {@code closeScreen(...)} —
 * {@code ImmersiveEditorLogic.isShowing()} is the registration gate.</p>
 *
 * <p><b>Reconciliation note (P143 surface fix).</b> Two things made the first
 * landing unusable from the retained-mode menu:</p>
 * <ol>
 *   <li>{@code editMode}/{@code nested}/{@code targetPresent} were plain public
 *       mirror fields that the screen had to re-sync before <em>every</em> read.
 *       {@link #isImmersionMode()} is read from the draw path, the FOV mixin and
 *       both world-render callbacks, so a mirror is guaranteed to go stale.
 *       They are now backed by a {@link Host} the menu binds once
 *       ({@link #bind}); the fields survive as the unbound fallback that the
 *       headless tests drive.</li>
 *   <li>the per-frame morph swap was a single {@code swapRestore(...)}
 *       try/finally call, but legacy swaps at render-tick START and restores at
 *       render-tick END — two <em>separate</em> callbacks
 *       ({@code WorldRenderEvents.START}/{@code END}) that cannot share a stack
 *       frame. {@link #beginFrame}/{@link #endFrame} model that split and own
 *       the legacy {@code lastMorph} field.</li>
 * </ol>
 * <p>{@code refreshImmersive}'s renderer sync (which legacy did in full, not
 * just the {@code hideModel} line) is now {@link #refreshImmersive}, so the
 * screen does not re-derive {@code customEntity}/{@code fullScreen}/the relative
 * entity angles.</p>
 *
 * <p>Legacy source: {@code blockbuster-1.12/.../client/gui/GuiImmersiveMorphMenu.java}.</p>
 */
public class ImmersiveMorphMenuLogic
{
    /**
     * The retained-mode menu's live state. {@code GuiImmersiveMorphMenu}
     * implements this over its {@code GuiCreativeMorphsMenu} base
     * ({@code isEditMode()}, {@code isNested()}, {@code target != null}) and
     * binds itself in its constructor.
     */
    public interface Host
    {
        /** Legacy {@code isEditMode()}. */
        boolean isEditMode();

        /** Legacy {@code isNested()}. */
        boolean isNested();

        /** Legacy {@code target != null}. */
        boolean hasTarget();
    }

    /** Legacy renderer FOV reset target on edit-mode exit. */
    public static final float DEFAULT_FOV = 70F;

    /**
     * Legacy {@code draw()} dim overlay drawn over the whole menu area when NOT
     * in immersion mode ({@code Gui.drawRect(..., 0x33000000)}).
     */
    public static final int DIM_COLOR = 0x33000000;

    /**
     * Lang key of the F3 hide-GUI-model keybind. Legacy registered it under
     * {@code GuiImmersiveEditor.CATEGORY} — i.e.
     * {@link ImmersiveEditorLogic#CATEGORY_KEY}, <em>not</em> a category of its
     * own — and gated it with {@code .active(() -> this.isImmersionMode())}
     * ({@link #guiModelToggleActive()}).
     */
    public static final String TOGGLE_GUI_MODEL_KEY = "blockbuster.gui.morphs.keys.toggle_gui_model";

    /**
     * Legacy {@code Keyboard.KEY_F3} (LWJGL2 code) for the hide-GUI-model
     * toggle. {@code LegacyKeyCodes} has no {@code KEY_F3} yet — see the P143
     * wiring note; the value is the LWJGL2 constant.
     */
    public static final int TOGGLE_GUI_MODEL_KEYCODE = 61;

    /** Legacy default ({@code immersionMode = true}). */
    public boolean immersionMode = true;

    /** Legacy default ({@code hideGuiModel = true}). */
    public boolean hideGuiModel = true;

    /**
     * Fallback mirror of {@code isEditMode()}, used only while no {@link Host}
     * is bound (headless tests). Bound screens must NOT write these.
     */
    public boolean editMode;

    /** Fallback mirror of {@code target != null} (see {@link #editMode}). */
    public boolean targetPresent;

    /** Fallback mirror of {@code isNested()} (see {@link #editMode}). */
    public boolean nested;

    private Host host;

    private final Deque<Boolean> stack = new ArrayDeque<>();

    /* Legacy `private AbstractMorph lastMorph` — erased so the swap seam stays
     * headless-testable with plain objects. */
    private Object lastMorph;
    private boolean previewInstalled;

    /**
     * Bind the live retained-mode state. Pass {@code null} to unbind (falls back
     * to the mirror fields).
     */
    public ImmersiveMorphMenuLogic bind(Host host)
    {
        this.host = host;

        return this;
    }

    public Host getHost()
    {
        return this.host;
    }

    /** Legacy {@code isEditMode()} — from the bound {@link Host} when present. */
    public boolean isEditMode()
    {
        return this.host == null ? this.editMode : this.host.isEditMode();
    }

    /** Legacy {@code isNested()} — from the bound {@link Host} when present. */
    public boolean isNested()
    {
        return this.host == null ? this.nested : this.host.isNested();
    }

    /** Legacy {@code target != null} — from the bound {@link Host} when present. */
    public boolean hasTarget()
    {
        return this.host == null ? this.targetPresent : this.host.hasTarget();
    }

    /**
     * Legacy {@code nestEdit(...)}: push the current immersion mode, then AND it
     * with {@code keepViewport} for the nested edit. Call BEFORE the
     * {@code super.nestEdit} that flips edit/nested state.
     */
    public void nestEdit(boolean keepViewport)
    {
        this.stack.push(this.immersionMode);
        this.immersionMode &= keepViewport;
    }

    /**
     * Legacy {@code restoreEdit()}: pop the immersion mode saved by the matching
     * {@link #nestEdit}. Call AFTER {@code super.restoreEdit}. An unbalanced pop
     * leaves {@code immersionMode} untouched instead of throwing (total rule;
     * legacy {@code Stack.pop()} threw {@code EmptyStackException}).
     */
    public void restoreEdit()
    {
        Boolean value = this.stack.poll();

        if (value != null)
        {
            this.immersionMode = value;
        }
    }

    /** Current nesting depth of the immersion stack (test aid). */
    public int stackDepth()
    {
        return this.stack.size();
    }

    /**
     * Legacy {@code isImmersionMode()}: {@code isEditMode() && immersionMode &&
     * target != null}.
     *
     * <p>Also the gate legacy used around the {@code beforeRender}/
     * {@code afterRender} seam hooks in {@code beforeRenderModel(context)} /
     * {@code afterRenderModel(context)} ({@code isImmersionMode() && hook != null},
     * both after the {@code super} call).</p>
     */
    public boolean isImmersionMode()
    {
        return this.isEditMode() && this.immersionMode && this.hasTarget();
    }

    /**
     * The legacy render-tick {@code Phase.START} <em>pre</em>-gate:
     * {@code isEditMode() && this.immersionMode} — deliberately WITHOUT the
     * {@code target != null} term that {@link #isImmersionMode()} adds. Legacy:
     *
     * <pre>
     * if (this.isEditMode() &amp;&amp; this.immersionMode &amp;&amp; event.phase == Phase.START)
     * {
     *     this.preview.renderComplete = false;
     *     if (this.updateCallback != null) this.updateCallback.accept(this);
     * }
     * </pre>
     *
     * So the preview's {@code renderComplete} flag is cleared and the P136/P139
     * {@code updateCallback} fires even when no target entity is bound yet —
     * that asymmetry is load-bearing (the callback is what installs the target
     * in the first place). Do not collapse this into
     * {@link #isImmersionMode()}.
     */
    public boolean isEditImmersion()
    {
        return this.isEditMode() && this.immersionMode;
    }

    /**
     * Legacy {@code onRenderGameOverlayEvent}: {@code event.setCanceled(true)}
     * with no condition at all. The handler only existed while the menu was
     * registered on the Forge event bus, which the editor did in {@code show()}
     * and undid in {@code closeScreen()}, so the observable rule is "the vanilla
     * HUD is fully suppressed for as long as the immersive editor is open" —
     * including while a nested, non-immersive sub-editor is on top.
     *
     * <p>Constant by design: the {@code InGameHud#render} HEAD mixin must gate
     * on the editor being open ({@code ImmersiveEditorLogic.isShowing()}) and
     * then on this, never on {@link #isImmersionMode()}.</p>
     */
    public boolean hudSuppressed()
    {
        return true;
    }

    /**
     * Legacy {@code draw(context)} first branch: the {@link #DIM_COLOR} rect is
     * drawn over the menu area when NOT in immersion mode.
     */
    public boolean shouldDimBackground()
    {
        return !this.isImmersionMode();
    }

    /**
     * Legacy {@code draw(context)} second branch:
     * {@code if (this.isEditMode()) this.refreshImmersive();} — the renderer
     * sync runs on edit mode alone, so it still clears
     * {@code hideModel}/{@code customEntity}/{@code fullScreen} while editing
     * with immersion off.
     */
    public boolean shouldRefreshImmersive()
    {
        return this.isEditMode();
    }

    /**
     * F3 toggle of {@code hideGuiModel}. Legacy keybind is active only while in
     * immersion mode; returns the new value.
     */
    public boolean toggleGuiModel()
    {
        return this.hideGuiModel = !this.hideGuiModel;
    }

    /** Whether the F3 hide-GUI-model keybind is active (immersion mode only). */
    public boolean guiModelToggleActive()
    {
        return this.isImmersionMode();
    }

    /**
     * What {@code exit()} should do, matching the legacy branch:
     * <pre>
     * if (isEditMode() || isNested()) { if (isEditMode()) fov = 70; super.exit(); }
     * else                            { closeThisScreen(); }
     * </pre>
     */
    public enum Exit
    {
        /** In edit mode: reset renderer FOV to {@link #DEFAULT_FOV}, then {@code super.exit()}. */
        RESET_FOV_AND_POP,
        /** Nested (not edit): {@code super.exit()} without FOV reset. */
        POP,
        /** Top-level: close the whole immersive editor screen. */
        CLOSE_SCREEN
    }

    public Exit exit()
    {
        if (this.isEditMode())
        {
            return Exit.RESET_FOV_AND_POP;
        }

        if (this.isNested())
        {
            return Exit.POP;
        }

        return Exit.CLOSE_SCREEN;
    }

    /**
     * Legacy {@code getFrame(tick)} gating: the {@code frameProvider} is
     * consulted only at the top level ({@code !isNested()}).
     */
    public boolean shouldProvideFrame()
    {
        return !this.isNested();
    }

    /**
     * Legacy {@code getFrame(int tick)} in full — the menu keeps the
     * {@code Function<Integer, Frame> frameProvider} field (P139 writes it) and
     * routes it through here so the null + nesting gate is not restated.
     */
    public <F> F getFrame(Function<Integer, F> frameProvider, int tick)
    {
        if (frameProvider != null && this.shouldProvideFrame())
        {
            return frameProvider.apply(tick);
        }

        return null;
    }

    /**
     * Legacy {@code refreshImmersive()} hide-model rule:
     * {@code isImmersionMode() && renderComplete && hideGuiModel &&
     * (!doRenderOnionSkin || !haveOnionSkin)}.
     *
     * <p><b>Deliberate legacy divergence:</b> the {@code immersionMode} term is
     * dropped ({@code isEditMode() && hasTarget()} instead of
     * {@code isImmersionMode()}), matching the broadened preview swap in
     * {@code GuiImmersiveMorphMenu.onRenderTickStart} — with a target present
     * the world render carries the live preview in <i>both</i> modes, so the
     * viewport copy is redundant in both. {@code renderComplete} still guards:
     * if the world did not actually draw the preview this frame (block culled,
     * rendering disabled), the viewport copy stays visible as the fallback.</p>
     */
    public boolean computeHideModel(boolean renderComplete, boolean doRenderOnionSkin, boolean haveOnionSkin)
    {
        return this.isEditMode()
            && this.hasTarget()
            && renderComplete
            && this.hideGuiModel
            && (!doRenderOnionSkin || !haveOnionSkin);
    }

    /**
     * The full legacy {@code refreshImmersive()} write set for the in-GUI model
     * renderer. {@code entity*} values are only meaningful (and only written by
     * {@link #applyTo}) when {@link #customEntity} is true — legacy left them
     * untouched otherwise.
     */
    public static final class RendererSync
    {
        public final boolean hideModel;
        public final boolean customEntity;
        public final boolean fullScreen;

        /** Legacy {@code target.rotationPitch}. */
        public final float entityPitch;
        /** Legacy {@code target.rotationYawHead - target.rotationYaw}. */
        public final float entityYawHead;
        /** Legacy {@code target.renderYawOffset - target.rotationYaw}. */
        public final float entityYawBody;
        /** Legacy {@code target.ticksExisted}. */
        public final int entityTicksExisted;

        private RendererSync(boolean hideModel, boolean customEntity, boolean fullScreen,
            float entityPitch, float entityYawHead, float entityYawBody, int entityTicksExisted)
        {
            this.hideModel = hideModel;
            this.customEntity = customEntity;
            this.fullScreen = fullScreen;
            this.entityPitch = entityPitch;
            this.entityYawHead = entityYawHead;
            this.entityYawBody = entityYawBody;
            this.entityTicksExisted = entityTicksExisted;
        }

        /** Write this sync onto the renderer in the exact legacy order. */
        public void applyTo(GuiModelRenderer renderer)
        {
            if (renderer == null)
            {
                return;
            }

            renderer.hideModel = this.hideModel;
            renderer.customEntity = this.customEntity;
            renderer.fullScreen = this.fullScreen;

            if (renderer.customEntity)
            {
                renderer.entityPitch = this.entityPitch;
                renderer.entityYawHead = this.entityYawHead;
                renderer.entityYawBody = this.entityYawBody;
                renderer.entityTicksExisted = this.entityTicksExisted;
            }
        }
    }

    /**
     * Pure form of legacy {@code refreshImmersive()} — the entity angles are
     * passed in already as absolute values and made relative here, exactly as
     * legacy did.
     *
     * @param renderComplete     legacy {@code preview.renderComplete}
     * @param doRenderOnionSkin  legacy {@code this.doRenderOnionSkin}
     * @param haveOnionSkin      legacy {@code this.haveOnionSkin()}
     * @param targetPitch        legacy {@code target.rotationPitch}
     * @param targetYawHead      legacy {@code target.rotationYawHead}
     * @param targetBodyYaw      legacy {@code target.renderYawOffset}
     * @param targetYaw          legacy {@code target.rotationYaw}
     * @param targetTicksExisted legacy {@code target.ticksExisted}
     */
    public RendererSync refreshImmersive(boolean renderComplete, boolean doRenderOnionSkin, boolean haveOnionSkin,
        float targetPitch, float targetYawHead, float targetBodyYaw, float targetYaw, int targetTicksExisted)
    {
        boolean immersion = this.isImmersionMode();

        return new RendererSync(
            this.computeHideModel(renderComplete, doRenderOnionSkin, haveOnionSkin),
            immersion,
            immersion,
            targetPitch,
            targetYawHead - targetYaw,
            targetBodyYaw - targetYaw,
            targetTicksExisted);
    }

    /**
     * Retained-mode form of {@link #refreshImmersive}: reads the angles off the
     * live target and writes the result straight onto the GUI model renderer.
     * A null {@code target} yields zeroed angles (they are unused unless
     * {@code customEntity}, which implies a target).
     */
    public RendererSync refreshImmersive(GuiModelRenderer renderer, LivingEntity target,
        boolean renderComplete, boolean doRenderOnionSkin, boolean haveOnionSkin)
    {
        RendererSync sync = target == null
            ? this.refreshImmersive(renderComplete, doRenderOnionSkin, haveOnionSkin, 0F, 0F, 0F, 0F, 0)
            : this.refreshImmersive(renderComplete, doRenderOnionSkin, haveOnionSkin,
                target.getPitch(), target.headYaw, target.bodyYaw, target.getYaw(), target.age);

        sync.applyTo(renderer);

        return sync;
    }

    /* ------------------------------------------------------------------ */
    /* Per-frame preview morph swap (render-tick START / END)              */
    /* ------------------------------------------------------------------ */

    /**
     * Legacy render-tick {@code Phase.START}: remember the target's current
     * morph ({@code lastMorph = EntityUtils.getMorph(target)}) and install the
     * preview morph on it. The caller keeps the legacy
     * {@code target instanceof EntityActor ? morph.setDirect(...) :
     * MorphAPI.morph(...)} branch inside {@code apply}.
     *
     * <p>Calling this twice without an intervening {@link #endFrame} overwrites
     * the remembered morph with the preview — the caller must gate on
     * {@code isImmersionMode()} exactly as legacy did.</p>
     *
     * @param capture supplies the target's current morph
     * @param apply   installs a morph on the target ({@code null} allowed)
     * @param preview the preview morph rendered while editing
     */
    public <M> void beginFrame(Supplier<M> capture, Consumer<M> apply, M preview)
    {
        this.lastMorph = capture.get();
        this.previewInstalled = true;

        apply.accept(preview);
    }

    /**
     * Legacy render-tick {@code Phase.END}: put back what {@link #beginFrame}
     * captured (possibly {@code null} — legacy restored a null morph verbatim).
     * A no-op when no preview is currently installed, so an END without a START
     * cannot re-apply a stale morph.
     */
    @SuppressWarnings("unchecked")
    public <M> void endFrame(Consumer<M> apply)
    {
        if (!this.previewInstalled)
        {
            return;
        }

        M original = (M) this.lastMorph;

        this.lastMorph = null;
        this.previewInstalled = false;

        apply.accept(original);
    }

    /** Whether a preview morph is currently installed on the target. */
    public boolean isPreviewInstalled()
    {
        return this.previewInstalled;
    }

    /**
     * Drop a pending swap without restoring (legacy {@code finish()} tears the
     * menu down mid-frame). Returns the morph that was captured, so the caller
     * can restore it itself if it wants to.
     *
     * <p>The rest of legacy {@code finish()} is retained-mode and stays on the
     * menu, in this order: {@code super.finish()}, then
     * {@code frameProvider = beforeRender = afterRender = null}, then
     * {@code this.pickMorph(getSelected())} — that last call is what actually
     * puts a morph back on the target, which is why legacy never restored
     * {@code lastMorph} on this path.</p>
     */
    public Object discardFrame()
    {
        Object morph = this.lastMorph;

        this.lastMorph = null;
        this.previewInstalled = false;

        return morph;
    }

    /**
     * Single-call form of the swap/restore invariant, for callers that DO own
     * both ends within one stack frame (and for the state-machine tests): the
     * original reference is <em>always</em> restored, even if the render step
     * throws.
     *
     * @param capture supplies the target's current morph
     * @param apply   installs a morph on the target
     * @param preview the preview morph rendered while editing
     * @param render  the render step run with the preview installed
     */
    public static <M> void swapRestore(Supplier<M> capture, Consumer<M> apply, M preview, Runnable render)
    {
        M original = capture.get();

        apply.accept(preview);

        try
        {
            render.run();
        }
        finally
        {
            apply.accept(original);
        }
    }
}
