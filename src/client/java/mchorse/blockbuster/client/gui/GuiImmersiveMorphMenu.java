package mchorse.blockbuster.client.gui;

import java.util.function.Consumer;
import java.util.function.Function;

import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.blockbuster.recording.data.Frame;
import mchorse.blockbuster_pack.client.gui.GuiSequencerMorph.GuiSequencerMorphRenderer;
import mchorse.mclib.client.gui.framework.GuiBase;
import mchorse.mclib.client.gui.framework.elements.GuiModelRenderer;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.metamorph.api.EntityUtils;
import mchorse.metamorph.api.MorphAPI;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.client.MorphRenderUtils;
import mchorse.metamorph.client.gui.creative.GuiCreativeMorphsMenu;
import mchorse.metamorph.client.gui.creative.GuiMorphRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Immersive morph menu (roadmap P143).
 *
 * <p>The concrete in-world morph picker: the creative morph menu
 * ({@link GuiCreativeMorphsMenu}) rendered over the live world by
 * {@code GuiImmersiveEditor}, swapping the edited entity's morph for a live
 * preview every frame and teleporting the client camera around it like the GUI
 * model-renderer's orbit. It is the "actual executor" that synchronises the
 * model-editing process to the game world.</p>
 *
 * <p>1:1 port of Blockbuster 2.7.2's
 * {@code mchorse.blockbuster.client.gui.GuiImmersiveMorphMenu}. The retained-mode
 * plumbing (close button, F3 keybind, {@link PreviewMorph}, the
 * {@code target}/{@code updateCallback}/{@code frameProvider}/
 * {@code beforeRender}/{@code afterRender} seam fields) lives here; every state
 * decision and the orbit-camera math are delegated to the headless
 * {@link ImmersiveMorphMenuLogic} (bound as its {@link ImmersiveMorphMenuLogic.Host})
 * and {@link ImmersiveOrbitCamera} respectively (see their reconciliation notes).</p>
 *
 * <p>The legacy Forge {@code @SubscribeEvent} seams become 1.20.4 hooks that call
 * the public methods on this class (registered by {@code GuiImmersiveEditor} while
 * the editor is showing):</p>
 * <ul>
 *   <li>render-tick {@code Phase.START} &rarr; {@link #onRenderTickStart()}
 *       (drive from {@code WorldRenderEvents.START});</li>
 *   <li>render-tick {@code Phase.END} &rarr; {@link #onRenderTickEnd()}
 *       (drive from {@code WorldRenderEvents.END});</li>
 *   <li>{@code onFovModifierEvent} &rarr; {@link #shouldForceFov()} /
 *       {@link #getForcedFov()} read by the {@code GameRenderer#getFov} mixin;</li>
 *   <li>{@code onCameraOrient} &rarr; {@link #shouldZeroRoll()} read by the
 *       camera-roll seam;</li>
 *   <li>{@code onRenderGameOverlayEvent} &rarr; {@link #hudSuppressed()} read by
 *       the {@code InGameHud#render} HEAD suppression.</li>
 * </ul>
 *
 * Legacy source: blockbuster-1.12/src/main/java/mchorse/blockbuster/client/gui/GuiImmersiveMorphMenu.java
 */
public class GuiImmersiveMorphMenu extends GuiCreativeMorphsMenu implements ImmersiveMorphMenuLogic.Host
{
    /** Headless state machine + orbit-camera driver (A8). */
    public final ImmersiveMorphMenuLogic logic = new ImmersiveMorphMenuLogic();

    /** Edited entity whose morph is swapped for the {@link #preview} each frame. */
    public LivingEntity target;

    /** Fired every render-tick START while editing in immersion mode (P136/P139). */
    public Consumer<GuiImmersiveMorphMenu> updateCallback;

    /** Recording-frame provider supplied to nested editors at top level (P139). */
    public Function<Integer, Frame> frameProvider;

    /** Extra draw hooks run inside the model-render pass while in immersion mode. */
    public Consumer<GuiContext> beforeRender;
    public Consumer<GuiContext> afterRender;

    private final PreviewMorph preview = new PreviewMorph();

    public GuiImmersiveMorphMenu(MinecraftClient mc)
    {
        super(mc, true, null);

        this.logic.bind(this);

        GuiButtonElement close = new GuiButtonElement(mc, IKey.str("X"), (b) -> this.exit());
        close.flex().w(20);

        this.bar.add(close);

        this.keys()
            .register(IKey.lang(ImmersiveMorphMenuLogic.TOGGLE_GUI_MODEL_KEY), ImmersiveMorphMenuLogic.TOGGLE_GUI_MODEL_KEYCODE, () -> this.logic.toggleGuiModel())
            .category(IKey.lang(ImmersiveEditorLogic.CATEGORY_KEY))
            .active(() -> this.logic.guiModelToggleActive());
    }

    /* ImmersiveMorphMenuLogic.Host — isEditMode()/isNested() come from the
     * GuiCreativeMorphsList base with the legacy semantics the logic expects. */

    @Override
    public boolean hasTarget()
    {
        return this.target != null;
    }

    /** Legacy {@code isImmersionMode()}. */
    public boolean isImmersionMode()
    {
        return this.logic.isImmersionMode();
    }

    @Override
    public void nestEdit(AbstractMorph selected, boolean editing, boolean keepViewport, Consumer<AbstractMorph> callback)
    {
        this.logic.nestEdit(keepViewport);

        super.nestEdit(selected, editing, keepViewport, callback);
    }

    @Override
    public void restoreEdit()
    {
        super.restoreEdit();

        this.logic.restoreEdit();
    }

    @Override
    public void exit()
    {
        switch (this.logic.exit())
        {
            case RESET_FOV_AND_POP:
                this.editor.delegate.renderer.fov = ImmersiveMorphMenuLogic.DEFAULT_FOV;
                super.exit();
                break;

            case POP:
                super.exit();
                break;

            case CLOSE_SCREEN:
                /* WIRING(P143): C1 owns GuiImmersiveEditor. Legacy called
                 * ((GuiImmersiveEditor) mc.currentScreen).closeThisScreen(). */
                if (this.mc.currentScreen instanceof GuiImmersiveEditor editor)
                {
                    editor.closeThisScreen();
                }

                break;
        }
    }

    @Override
    public void finish()
    {
        super.finish();

        this.frameProvider = null;
        this.beforeRender = null;
        this.afterRender = null;

        this.pickMorph(this.getSelected());
    }

    @Override
    public void draw(GuiContext context)
    {
        if (this.logic.shouldDimBackground())
        {
            GuiDraw.drawRect(this.area.x, this.area.y, this.area.ex(), this.area.ey(), ImmersiveMorphMenuLogic.DIM_COLOR);
        }

        if (this.logic.shouldRefreshImmersive())
        {
            this.refreshImmersive();
        }

        super.draw(context);
    }

    @Override
    protected void beforeRenderModel(GuiContext context)
    {
        super.beforeRenderModel(context);

        if (this.logic.isImmersionMode() && this.beforeRender != null)
        {
            this.beforeRender.accept(context);
        }
    }

    @Override
    protected void afterRenderModel(GuiContext context)
    {
        super.afterRenderModel(context);

        if (this.logic.isImmersionMode() && this.afterRender != null)
        {
            this.afterRender.accept(context);
        }
    }

    /** Legacy {@code getFrame(int)} — the null + top-level-only gate lives in the logic. */
    public Frame getFrame(int tick)
    {
        return this.logic.getFrame(this.frameProvider, tick);
    }

    /* ------------------------------------------------------------------ */
    /* Per-frame render-tick seams (legacy onRenderTick, split START/END)  */
    /* ------------------------------------------------------------------ */

    /**
     * Legacy render-tick {@code Phase.START}. First the pre-gate clears the
     * preview's render flag and fires the update callback (which is what
     * installs the target); then the target's morph is swapped for the
     * {@link #preview}, and — in immersion mode only — the client camera is
     * teleported to the GUI-orbit-derived world position.
     *
     * <p><b>Deliberate legacy divergence:</b> legacy gated the reset/callback on
     * {@code isEditImmersion()} and the swap on {@code isImmersionMode()}, so
     * with immersion toggled off the world entity kept rendering its own
     * (stale) morph next to the viewport's live copy — the edited block drew
     * twice. The reset/callback/swap now run for any editing session with a
     * target, in both modes: the world render always shows the live preview,
     * and {@code computeHideModel} suppresses the viewport copy once the world
     * confirmed the draw. Only the camera teleport remains immersion-only —
     * parking the camera is what immersion mode <i>is</i>.</p>
     */
    public void onRenderTickStart()
    {
        if (this.logic.isEditMode())
        {
            this.preview.renderComplete = false;

            if (this.updateCallback != null)
            {
                this.updateCallback.accept(this);
            }

            if (this.target != null)
            {
                this.logic.<AbstractMorph>beginFrame(() -> EntityUtils.getMorph(this.target), this::applyMorph, this.preview);
            }
        }

        if (this.logic.isImmersionMode())
        {
            GuiModelRenderer renderer = this.editor.delegate.renderer;

            ImmersiveOrbitCamera.place(renderer, this.target, this.mc.player);
        }
    }

    /**
     * Legacy render-tick {@code Phase.END}: put back the morph the matching
     * {@link #onRenderTickStart()} captured off the target. Unconditional —
     * {@code endFrame} is a no-op unless a preview is actually installed, so
     * this can never re-apply a stale morph, and no gate drift between START
     * and END can leak the preview onto the entity.
     */
    public void onRenderTickEnd()
    {
        this.logic.<AbstractMorph>endFrame(this::applyMorph);
    }

    /**
     * Legacy morph swap-in/out branch, used verbatim for both the preview install
     * (START) and the restore (END). Actor via {@code morph.setDirect}, player via
     * {@link MorphAPI#morph}.
     */
    private void applyMorph(AbstractMorph morph)
    {
        if (this.target instanceof EntityActor actor)
        {
            actor.morph.setDirect(morph);
        }
        else if (this.target instanceof PlayerEntity player)
        {
            MorphAPI.morph(player, morph, true);
        }
    }

    /* ------------------------------------------------------------------ */
    /* FOV / roll / HUD seams (legacy @SubscribeEvent handlers)            */
    /* ------------------------------------------------------------------ */

    /**
     * Legacy {@code onFovModifierEvent} gate ({@code EventPriority.LOWEST}): the
     * renderer FOV is forced onto the game camera only in immersion mode.
     */
    public boolean shouldForceFov()
    {
        return this.logic.isImmersionMode();
    }

    /** The FOV the {@code GameRenderer#getFov} mixin should return (renderer FOV). */
    public float getForcedFov()
    {
        return this.editor.delegate.renderer.fov;
    }

    /** Legacy {@code onCameraOrient}: camera roll is zeroed in immersion mode. */
    public boolean shouldZeroRoll()
    {
        return this.logic.isImmersionMode();
    }

    /**
     * Legacy {@code onRenderGameOverlayEvent}: the vanilla HUD is fully suppressed
     * for the whole time the editor is open (unconditional — NOT gated on
     * immersion mode).
     */
    public boolean hudSuppressed()
    {
        return this.logic.hudSuppressed();
    }

    /**
     * Legacy {@code refreshImmersive()}: sync the in-GUI model renderer's
     * hide-model / custom-entity / full-screen flags and the relative entity
     * angles off the live target.
     */
    public void refreshImmersive()
    {
        GuiModelRenderer renderer = this.editor.delegate.renderer;

        this.logic.refreshImmersive(renderer, this.target, this.preview.renderComplete, this.doRenderOnionSkin, this.haveOnionSkin());
    }

    /**
     * Internal preview morph: renders whatever the editor is currently editing
     * (via its model renderer) onto the target entity in the world, and flags that
     * a world render actually happened ({@code renderComplete}) so
     * {@link #refreshImmersive()} may hide the redundant GUI model.
     */
    public class PreviewMorph extends AbstractMorph
    {
        public boolean renderComplete;

        @Override
        public void renderOnScreen(PlayerEntity player, int x, int y, float scale, float alpha)
        {}

        @Override
        public void render(LivingEntity entity, double x, double y, double z, float entityYaw, float partialTicks)
        {
            this.renderComplete = true;

            GuiImmersiveMorphMenu menu = GuiImmersiveMorphMenu.this;
            AbstractMorph morph = menu.editor.delegate.morph;
            GuiModelRenderer renderer = menu.editor.delegate.renderer;

            /* P143: a sequencer being edited draws itself through its own
             * preview player (which owns the scrub clock), so the generic draw
             * below is suppressed (morph = null). The context's partial ticks
             * are overwritten with the *render* partial ticks first — legacy
             * mc.getRenderPartialTicks(); inside a world render the GUI context
             * still carries the last GUI frame's value. */
            if (renderer instanceof GuiSequencerMorphRenderer)
            {
                GuiContext context = GuiBase.getCurrent();

                if (context != null)
                {
                    context.partialTicks = menu.mc == null ? partialTicks : menu.mc.getTickDelta();

                    ((GuiSequencerMorphRenderer) renderer).doRender(context, entity, x, y, z);
                }

                morph = null;
            }
            else if (renderer instanceof GuiMorphRenderer)
            {
                morph = ((GuiMorphRenderer) renderer).morph;
            }

            if (morph != null)
            {
                /* Legacy routed through MorphUtils.render (error-guarded
                 * renderDirect + shared-tessellator recovery) — MorphRenderUtils
                 * is the client half of it. Draws nothing until the S6 client
                 * render pipeline fills AbstractMorph#render. */
                MorphRenderUtils.render(morph, entity, x, y, z, entityYaw, partialTicks);
            }
        }

        @Override
        public AbstractMorph create()
        {
            return null;
        }

        @Override
        public float getWidth(LivingEntity target)
        {
            return 0;
        }

        @Override
        public float getHeight(LivingEntity target)
        {
            return 0;
        }
    }
}
