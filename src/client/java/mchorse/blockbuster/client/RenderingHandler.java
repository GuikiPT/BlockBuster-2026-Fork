package mchorse.blockbuster.client;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.ClientProxy;
import mchorse.blockbuster.client.particles.emitter.BedrockEmitter;
import mchorse.blockbuster.client.render.IRenderLast;
import mchorse.blockbuster.client.render.RenderLastSort;
import mchorse.blockbuster.client.render.RenderLastSweep;
import mchorse.blockbuster.client.textures.GifTexture;
import mchorse.blockbuster.client.video.ChromaSky;
import mchorse.blockbuster.client.video.ShaderPackVideoCompat;
import mchorse.blockbuster.common.entity.EntityActor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Blockbuster's client-side rendering handler — the Bedrock ("Snowstorm")
 * emitter-collection management half is roadmap P149.
 *
 * <p>This mirrors the 1.12.2 {@code mchorse.blockbuster.client.RenderingHandler}
 * but carries, for now, only the emitter collection: the live {@link #emitters}
 * list, the deferred-add queue used while the list is being iterated
 * ({@link #emittersAdd} / {@link #emitterIsIterating}), and the tick/reset
 * drivers {@link #updateEmitters()} and {@link #resetEmitters()}. In the port
 * these are driven from {@code BlockbusterClient}:
 * {@code ClientTickEvents.END_CLIENT_TICK → updateEmitters()} and
 * {@code ClientPlayConnectionEvents.DISCONNECT → resetEmitters()}.</p>
 *
 * <p>The <b>render</b> half (the static {@code renderParticles(partialTicks)}
 * pass, its depth sort over {@code Blockbuster.snowstormDepthSorting}, and the
 * per-frame {@code emitter.running = emitter.sanityTicks < 2} sanity auto-kill)
 * is deferred to P153 and will be appended here. The many Forge-event render
 * hooks of the legacy class (recording overlay, render-last, hand/camera) belong
 * to their own later phases and are added incrementally; this class is written to
 * be append-only so those merges stay clean.</p>
 */
public class RenderingHandler
{
    /**
     * Bedrock particle emitters
     */
    private static final List<BedrockEmitter> emitters = new ArrayList<BedrockEmitter>();
    private static final List<BedrockEmitter> emittersAdd = new ArrayList<BedrockEmitter>();
    private static boolean emitterIsIterating;

    public static void addEmitter(BedrockEmitter emitter, LivingEntity target)
    {
        if (!emitter.added)
        {
            if (emitterIsIterating)
            {
                emittersAdd.add(emitter);
            }
            else
            {
                emitters.add(emitter);
            }

            emitter.added = true;
            emitter.setTarget(target);
        }
    }

    private static void addEmitters()
    {
        if (!emittersAdd.isEmpty())
        {
            emitters.addAll(emittersAdd);
            emittersAdd.clear();
        }
    }

    public static void updateEmitters()
    {
        List<BedrockEmitter> emittersRemove = new ArrayList<>();

        emitterIsIterating = true;

        for (BedrockEmitter emitter : emitters)
        {
            emitter.update();

            if (emitter.isFinished())
            {
                emittersRemove.add(emitter);

                emitter.added = false;
            }
        }

        if (!emittersRemove.isEmpty())
        {
            emitters.removeAll(emittersRemove);
        }

        addEmitters();

        emitterIsIterating = false;
    }

    public static void resetEmitters()
    {
        emitters.clear();
    }

    /* ------------------------------------------------------------------ *
     * P188.1: game-pause bridging                                         *
     * ------------------------------------------------------------------ */

    /**
     * Legacy {@code RenderingHandler.wasPaused} (1.12.2 line 105): the last
     * observed game-pause state, so {@link #updateAudioPause(boolean)} only
     * fires on the <b>edge</b>. Instance field in 1.12.2 (the handler was a
     * Forge event subscriber instance); static here because the port's
     * RenderingHandler is a static holder.
     */
    private static boolean wasPaused;

    /**
     * Legacy {@code onRenderLast}'s pause block (1.12.2 lines 526–533):
     *
     * <pre>boolean isPaused = mc.isGamePaused();
     * if (this.wasPaused != isPaused) { ClientProxy.audio.pause(isPaused); this.wasPaused = isPaused; }</pre>
     *
     * <p>Our OpenAL sources are ours to drive — vanilla's {@code SoundSystem}
     * knows nothing about them, so without this bridge a scene's {@code .wav}
     * keeps playing through the ESC menu. 1.12.2 hung the check on
     * {@code RenderWorldLastEvent} purely because it was a convenient per-frame
     * callback; the port drives it from {@code ClientTickEvents.END_CLIENT_TICK}
     * (yarn {@code MinecraftClient.isPaused()}), which is the same edge at
     * coarser granularity — AL keeps playing between frames either way, so only
     * the transition matters.</p>
     *
     * <p>The {@code ClientProxy.audio} null-guard is a port addition: legacy
     * always had the library constructed by {@code ClientProxy.load}, and the
     * port constructs it at client init too, but headless tests and any pre-init
     * tick must not NPE.</p>
     */
    public static void updateAudioPause(boolean isPaused)
    {
        if (wasPaused != isPaused)
        {
            if (ClientProxy.audio != null)
            {
                ClientProxy.audio.pause(isPaused);
            }

            wasPaused = isPaused;
        }
    }

    /**
     * The last observed game-pause state (P188.1 edge detector).
     */
    public static boolean isGamePaused()
    {
        return wasPaused;
    }

    /**
     * Test seam (P188.1): clear the pause edge state. The flag is a static in
     * the port, so headless tests that drive {@link #updateAudioPause(boolean)}
     * must normalise it rather than inherit the previous test's edge.
     */
    public static void resetGamePauseEdge()
    {
        wasPaused = false;
    }

    /**
     * The live emitter list (package/engine internal). Exposed for P153's render
     * pass and for headless tests that assert collection lifecycle.
     */
    public static List<BedrockEmitter> getEmitters()
    {
        return emitters;
    }

    /* ------------------------------------------------------------------ *
     * P80.3: the sorted render-last tail pass                             *
     * ------------------------------------------------------------------ *
     *
     * The tail pass uses `renderLasts` (plural), legacy's own name for its own
     * list, and everything here is suffixed to match: addRenderLast /
     * drawRenderLast / isRenderingLast.
     */

    /**
     * Legacy {@code renderLasts} (1.12.2 line 95): everything that opted out of
     * the normal pass this frame and must be drawn, back to front, at the tail.
     */
    private static final List<IRenderLast> renderLasts = new ArrayList<IRenderLast>();

    /**
     * Legacy {@code renderedEntityActors} (1.12.2 line 94): the actors the
     * normal pass actually drew this frame, so the {@code actorAlwaysRender}
     * sweep does not draw them a second time.
     *
     * <p>Legacy filled this by ASM, inserting {@code addRenderedEntity} after
     * the second {@code renderEntityStatic} call in
     * {@code RenderGlobal.renderEntities} (and scanning for the highest-index
     * {@code Entity} local so Optifine's local reindexing could not break it).
     * The port fills it from {@code RenderActor.render}, which is the same
     * event — "this actor was just drawn" — reached without a transformer.</p>
     */
    private static final List<EntityActor> renderedEntityActors = new ArrayList<EntityActor>();

    /**
     * Legacy {@code isRenderingLast} (1.12.2 line 103) — <b>load-bearing
     * re-entrancy guard</b>, not bookkeeping.
     *
     * <p>The tail pass draws by re-entering the very renderers that enqueued:
     * {@code RenderActor.shouldRender} and {@code TileEntityModelRenderer.render}
     * both ask "is my render-last flag set?" again. With this flag up,
     * {@link #addRenderLast} refuses and returns {@code false}, so the caller's
     * {@code renderLast && addRenderLast(...)} is false and it draws normally —
     * which is exactly what the tail pass wants it to do. Without the guard an
     * item would re-enqueue itself forever and never be drawn at all.</p>
     */
    private static boolean isRenderingLast;

    /**
     * The deferred draw seam (mirrors {@code TileEntityModelRenderer.morphDrawer}).
     * Defaults to {@link InertRenderLastDrawer#INSTANCE} — the queue works, the
     * sort works, nothing is drawn — and is assigned at client init by
     * {@code RenderLastPass.install()}. Headless tests swap in a recorder.
     */
    public static RenderLastDrawer renderLastDrawer = InertRenderLastDrawer.INSTANCE;

    /**
     * Supplier of every live actor in the client world, for the
     * {@code actorAlwaysRender} sweep (legacy
     * {@code mc.world.getEntities(EntityActor.class, IS_ALIVE)}). Null → no
     * sweep, which is the correct headless behaviour.
     */
    public static Supplier<List<EntityActor>> aliveActorSupplier;

    /** Set once so a broken drawer logs a single line, not one per frame. */
    private static boolean loggedRenderLastFailure;

    /**
     * Legacy {@code addRenderLast} (1.12.2 lines 128-139), verbatim semantics:
     * enqueue only when the queue is not being drained.
     *
     * @return true if added to the list; false if refused, in which case the
     *         caller draws normally instead of vanishing.
     */
    public static boolean addRenderLast(IRenderLast renderLast)
    {
        /* only add a render last object when the renderLasts List is not iterated */
        if (!isRenderingLast)
        {
            renderLasts.add(renderLast);

            return true;
        }

        return false;
    }

    /**
     * Legacy's enqueue-or-draw decision, as one call so every call site
     * short-circuits identically:
     * {@code settings.isRenderLast() && RenderingHandler.addRenderLast(this)}
     * ({@code TileEntityModel.shouldRenderInPass}) and
     * {@code entity.renderLast && RenderingHandler.addRenderLast(entity)}
     * ({@code RenderActor.shouldRender}).
     *
     * <p>The short-circuit is not incidental: an item whose flag is off must not
     * touch the queue at all.</p>
     *
     * @return true when the caller should draw <b>nothing</b> now because the
     *         tail pass has taken ownership of this item for this frame
     */
    public static boolean deferRenderLast(boolean wantsRenderLast, IRenderLast item)
    {
        return wantsRenderLast && addRenderLast(item);
    }

    /** Whether the tail pass is currently draining (legacy {@code isRenderingLast}). */
    public static boolean isRenderingLast()
    {
        return isRenderingLast;
    }

    /**
     * Legacy {@code addRenderedEntity} (1.12.2 lines 145-151): record that the
     * normal pass drew this actor, so the sweep skips it.
     */
    public static void addRenderedEntity(EntityActor actor)
    {
        if (actor != null)
        {
            renderedEntityActors.add(actor);
        }
    }

    /** The pending tail-pass queue (tests + the sweep's overlap check). */
    public static List<IRenderLast> getRenderLasts()
    {
        return renderLasts;
    }

    /** The actors the normal pass drew this frame (tests + the sweep). */
    public static List<EntityActor> getRenderedEntityActors()
    {
        return renderedEntityActors;
    }

    /**
     * Test seam: drop all tail-pass state. Statics survive between tests in one
     * JVM, and a leaked queue entry would reorder a later test's sort.
     */
    public static void resetRenderLast()
    {
        renderLasts.clear();
        renderedEntityActors.clear();
        isRenderingLast = false;
        loggedRenderLastFailure = false;
    }

    /**
     * Legacy {@code renderLastEntities()} (1.12.2 lines 337-407) — the drain.
     *
     * <p>Order of business, matching legacy: the {@code actorAlwaysRender}
     * sweep, then the farthest-first sort, then the draws with
     * {@link #isRenderingLast} raised, then the clear.</p>
     *
     * <h2>Two deliberate deviations from 1.12.2</h2>
     * <ol>
     *   <li>Legacy clears at lines 403-406, <b>outside</b> any {@code finally},
     *   so an exception from a single draw leaks both lists into the next
     *   frame — where the sweep's {@code removeAll} then silently suppresses
     *   actors that were never actually drawn. The port clears in a
     *   {@code finally}. This is the "leak guard's modern equivalent" the plan
     *   asks for and it is an improvement, not parity.</li>
     *   <li>A single item that throws is caught and logged once, and the rest of
     *   the queue still draws. Legacy would have aborted the whole pass. This
     *   follows the port's standing totality rule — one bad legacy morph must
     *   not blank every other render-last object in the scene. An
     *   {@link Error} is still allowed to propagate (the {@code finally} runs
     *   either way), because that is not ours to swallow.</li>
     * </ol>
     *
     * <p>Legacy also began with {@code if (getRenderPass() != 0) { clear;
     * return; }} — 1.12.2's two-pass Forge TESR render. 1.20.4 has no render
     * passes, so the guard has no analogue and no port; the tail pass simply
     * runs once per frame.</p>
     *
     * @param originX sort origin — see {@code RenderLastPass} for the
     *                eye-vs-feet decision
     */
    public static void drawRenderLast(MatrixStack matrices, VertexConsumerProvider consumers, double originX, double originY, double originZ, float tickDelta)
    {
        try
        {
            /* Legacy lines 350-363: pull in actors the normal pass never saw. */
            if (Blockbuster.actorAlwaysRender.get() && aliveActorSupplier != null)
            {
                renderLasts.addAll(RenderLastSweep.missing(aliveActorSupplier.get(), renderedEntityActors, renderLasts));
            }

            /* Legacy lines 366-376: farthest first (back to front). */
            RenderLastSort.sort(renderLasts, tickDelta, originX, originY, originZ);

            isRenderingLast = true;

            for (int i = 0; i < renderLasts.size(); i++)
            {
                IRenderLast item = renderLasts.get(i);

                matrices.push();

                try
                {
                    renderLastDrawer.draw(item, matrices, consumers, originX, originY, originZ, tickDelta);
                }
                catch (Exception e)
                {
                    if (!loggedRenderLastFailure)
                    {
                        loggedRenderLastFailure = true;

                        Blockbuster.LOGGER.error("Blockbuster: a render-last object failed to draw; skipping it for the rest of this session's logging", e);
                    }
                }
                finally
                {
                    matrices.pop();
                }
            }
        }
        finally
        {
            isRenderingLast = false;

            renderLasts.clear();
            renderedEntityActors.clear();
        }
    }

    /**
     * How a queued item is actually drawn. Legacy branched inline on
     * {@code instanceof EntityActor} / {@code instanceof TileEntityModel} and
     * called {@code renderEntityStatic} / {@code TileEntityRendererDispatcher
     * .render}; the port keeps the same branch but behind a seam, so the queue
     * lifecycle is assertable with no GL context.
     *
     * <p>{@code originX/Y/Z} is the camera position: {@code WorldRenderContext}
     * requires every vertex fed to {@code consumers()} to be camera-relative,
     * and the world {@link MatrixStack} at the injection point already is.</p>
     */
    public interface RenderLastDrawer
    {
        void draw(IRenderLast item, MatrixStack matrices, VertexConsumerProvider consumers, double originX, double originY, double originZ, float tickDelta);
    }

    /** Draws nothing, totally. The default until {@code RenderLastPass.install()}. */
    public static final class InertRenderLastDrawer implements RenderLastDrawer
    {
        public static final InertRenderLastDrawer INSTANCE = new InertRenderLastDrawer();

        private InertRenderLastDrawer()
        {}

        @Override
        public void draw(IRenderLast item, MatrixStack matrices, VertexConsumerProvider consumers, double originX, double originY, double originZ, float tickDelta)
        {}
    }

    /* ------------------------------------------------------------------ *
     * P83: item-render context seam                                       *
     * ------------------------------------------------------------------ *
     *
     * The 1.12.2 item TEISRs received neither the holder entity nor the
     * camera transform, so legacy plumbed both through statics on this class
     * ({@code lastItemHolder}, {@code itemTransformType}), populated from the
     * ASM-wrapped {@code RenderItem} methods
     * ({@code RenderItemTransformer}/{@code RenderEntityItemTransformer}).
     *
     * On 1.20.4 the {@code DynamicItemRenderer.render} callback receives the
     * {@link ModelTransformationMode} directly, so the whole
     * {@code setTSRTTransform} ASM hack dissolves — the mode is threaded as a
     * parameter and mirrored here only so morph renderers that historically
     * queried the global ({@code TileEntityGunItemStackRenderer} reads
     * {@code RenderingHandler.itemTransformType}) keep working unchanged. The
     * holder seam is likewise kept because {@code ItemStack.getHolder()}
     * returns non-null only for item frames / item entities (the GROUND case);
     * held-in-hand renders still need our own populated holder for the gun's
     * hand transforms (S17).
     */

    /**
     * Legacy {@code RenderingHandler.lastItemHolder}: the entity currently
     * holding the item being rendered, or null. Set at a render site and
     * cleared afterwards; {@link #setLastItemHolder(Entity)} only takes when
     * the slot is empty (matching legacy's null-guard) so a nested render
     * never clobbers the outer holder.
     */
    private static Entity lastItemHolder;

    /**
     * Legacy {@code RenderingHandler.itemTransformType}: the camera transform
     * of the item currently being rendered (GUI / GROUND / FIRST/THIRD-person
     * hands / FIXED). Mirrors the {@link ModelTransformationMode} the modern
     * {@code DynamicItemRenderer.render} callback is invoked with.
     */
    public static ModelTransformationMode itemTransformType;

    /**
     * Legacy {@code setLastItemHolder} — first non-null holder wins until it is
     * reset (the null-guard is load-bearing: RenderItem re-entered itself).
     */
    public static void setLastItemHolder(Entity entity)
    {
        if (lastItemHolder == null)
        {
            lastItemHolder = entity;
        }
    }

    /**
     * Legacy {@code resetLastItemHolder} — clears the holder only if it is the
     * same entity that set it (paired with {@link #setLastItemHolder(Entity)}).
     */
    public static void resetLastItemHolder(Entity entity)
    {
        if (lastItemHolder == entity)
        {
            lastItemHolder = null;
        }
    }

    public static Entity getLastItemHolder()
    {
        return lastItemHolder;
    }

    /**
     * Legacy {@code setTSRTTransform} equivalent — records the transform mode
     * of the item currently being rendered so morph renderers can query the
     * global. In the port this is set from the {@code DynamicItemRenderer}
     * render entry with the mode parameter (no ASM).
     */
    public static void setTSRTTransform(ModelTransformationMode type)
    {
        itemTransformType = type;
    }

    /**
     * Render the Bedrock ("Snowstorm") particle emitters — roadmap P153.
     *
     * <p>Invoked from {@link mchorse.blockbuster.mixin.client.WorldRendererParticlesMixin}
     * immediately after vanilla {@code ParticleManager.renderParticles}, mirroring
     * the 1.12.2 ASM patch on {@code EntityRenderer.renderWorldPass}. Faithful port
     * of legacy {@code RenderingHandler.renderParticles(float)}:</p>
     *
     * <ul>
     *   <li>optional farthest-first <b>emitter-level</b> depth sort behind
     *       {@link Blockbuster#snowstormDepthSorting} (particle-level sort is the
     *       emitter's own {@code depthSorting()}), squared distance to camera;</li>
     *   <li>the {@link #emitterIsIterating} guard so an emitter that spawns/attaches
     *       another emitter during its own render defers the add;</li>
     *   <li>the per-frame {@code emitter.running = emitter.sanityTicks < 2}
     *       keep-alive contract with {@code SnowstormMorph} (S14): an attached
     *       emitter is re-added every frame, resetting {@code sanityTicks}; two
     *       frames without a re-add auto-kills it;</li>
     *   <li>{@link #addEmitters()} drains the deferred-add queue afterwards.</li>
     * </ul>
     */
    public static void renderParticles(float partialTicks)
    {
        if (!emitters.isEmpty())
        {
            if (Blockbuster.snowstormDepthSorting.get())
            {
                emitters.sort((a, b) ->
                {
                    double ad = a.getDistanceSq();
                    double bd = b.getDistanceSq();

                    if (ad < bd)
                    {
                        return 1;
                    }
                    else if (ad > bd)
                    {
                        return -1;
                    }

                    return 0;
                });
            }

            emitterIsIterating = true;

            for (BedrockEmitter emitter : emitters)
            {
                emitter.render(partialTicks);
                emitter.running = emitter.sanityTicks < 2;
            }

            addEmitters();

            emitterIsIterating = false;
        }
    }

    /**
     * Lit-particle render hook — kept as an empty method for parity. The 1.12.2
     * {@code RenderingHandler.renderLitParticles(float)} was likewise an empty
     * stub (reserved for morph-based rendering); the mixin calls it right after
     * vanilla {@code ParticleManager.renderLitParticles} so the hook pair is in
     * place for later use.
     */
    public static void renderLitParticles(float partialTicks)
    {}

    /* ------------------------------------------------------------------ *
     * P203: green-screen ("chroma") sky + alpha capture                   *
     * ------------------------------------------------------------------ */

    /**
     * Real-GL implementation of {@link ChromaSky.Gl} — the core-profile
     * translation of the legacy {@code GlStateManager.clearColor} /
     * {@code GlStateManager.clear} / {@code glDisable(GL_FOG)} trio. Static
     * singleton so {@link #renderGreenSky()} allocates nothing per frame.
     */
    private static final ChromaSky.Gl CHROMA_GL = new ChromaSky.Gl()
    {
        @Override
        public void clearColor(float r, float g, float b, float a)
        {
            RenderSystem.clearColor(r, g, b, a);
        }

        @Override
        public void clearColorBuffer()
        {
            RenderSystem.clear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
        }

        @Override
        public void setShaderFogColor(float r, float g, float b, float a)
        {
            RenderSystem.setShaderFogColor(r, g, b, a);
        }
    };

    /**
     * Legacy {@code RenderingHandler.isGreenSky()} (1.12.2 line 182):
     * {@code Blockbuster.chromaSky.get()}. Consulted by {@code WorldRendererMixin}
     * on both {@code renderSky} and {@code renderClouds} (clouds are skipped
     * entirely when green sky is on).
     */
    public static boolean isGreenSky()
    {
        if (!Blockbuster.chromaSky.get())
        {
            return false;
        }

        /* S21 P272.1: a shader pack owns sky rendering. The HEAD-cancel still
         * applies under Iris, but the clear would land on the pack's multi-target
         * gbuffer framebuffer (flooding colortex0..N with the chroma colour) and
         * the pack re-derives the sky in its deferred/composite programs anyway,
         * so the flat colour never reaches the final image. Refusing with one
         * logged warning beats a corrupted gbuffer and a green screen that is not
         * green. No pack ⇒ this is a constant true and the legacy behaviour is
         * bit-identical. */
        return ShaderPackVideoCompat.isChromaSkyAvailable();
    }

    /**
     * Legacy {@code RenderingHandler.renderGreenSky()} (1.12.2 line 165): unpack
     * {@code Blockbuster.chromaSkyColor} as ARGB (alpha included) and clear the
     * colour buffer to it, then pin the shader fog colour (core-profile stand-in
     * for {@code glDisable(GL_FOG)}). Called from the {@code renderSky} mixin,
     * which cancels the rest of the vanilla sky render so nothing composites over
     * the flat chroma colour (translucent world geometry still does — parity).
     */
    public static void renderGreenSky()
    {
        ChromaSky.render(CHROMA_GL, Blockbuster.chromaSkyColor.get());
    }

    /*
     * P203's "capture HUD gate" used to live here as
     * {@code captureHidingHud} + {@code isCaptureHidingHud()} +
     * {@code setCaptureHidingHud(boolean)}, read by an {@code InGameHudMixin}
     * HEAD-cancel and written by nobody. It was removed in S22 (batch V-H)
     * because the feature it gated is provided twice over already and the seam
     * could never have been observable:
     *
     * - Both capture paths ({@code VideoCaptureWiring}, {@code
     *   ScreenshotKeyHandler}) read the framebuffer on
     *   {@code WorldRenderEvents.LAST}, which Fabric fires inside
     *   {@code WorldRenderer.render} — before {@code GameRenderer.render}
     *   reaches {@code InGameHud.render}. No HUD pixel exists yet at readback
     *   time, so cancelling the HUD cannot change a captured frame.
     * - Legacy's actual HUD discipline was not capture-scoped at all: Aperture
     *   1.8.2 set {@code mc.gameSettings.hideGUI = true} in
     *   {@code GuiCameraEditor.updateCameraEditor} and restored it in
     *   {@code closeScreen} (plus the Forge-only
     *   {@code GuiIngameForge.renderHotbar}/{@code renderCrosshairs} flags), so
     *   the HUD was hidden for the whole editor session — and recording is only
     *   startable from the editor's Minema panel. That is ported verbatim as
     *   {@code mc.options.hudHidden} at {@code GuiCameraEditor:1100/1527}.
     *
     * Wiring the seam would therefore have added behaviour 1.12.2 never had.
     * Pinned by {@code CaptureHudDisciplineTest}.
     */

    /* P231 — record-synced GIF animation (legacy onPreRenderEntity /
     * onPostRenderEntity, RenderingHandler.java:672-682) */

    /**
     * Legacy {@code onPreRenderEntity(RenderLivingEvent.Pre)}: pin
     * {@link mchorse.blockbuster.client.textures.GifTexture#entityTick} to the
     * rendered entity's age so a GIF skin animates against <i>that entity's</i>
     * clock instead of the global one. For a replayed actor the age is the
     * record's own tick, which is the whole point: two actors playing the same
     * record show the same frame, and a scrubbed/paused scene freezes the
     * animation with it.
     *
     * <p>The override is process-global (one entity sets it for every gif drawn
     * inside its render pass) — that is exactly how 1.12.2's record-sync worked;
     * preserve it.</p>
     *
     * <p>Driven by {@code LivingEntityRendererGifMixin}; 1.12.2 used Forge's
     * {@code RenderLivingEvent.Pre}/{@code .Post}, which has no Fabric API
     * counterpart on 1.20.4.</p>
     */
    public static void onPreRenderEntity(Entity entity)
    {
        GifTexture.entityTick = entity.age;
    }

    /**
     * Legacy {@code onPostRenderEntity(RenderLivingEvent.Post)}: release the
     * override so anything drawn outside an entity render (GUI previews, the
     * world's own quads) is back on the global clock.
     */
    public static void onPostRenderEntity()
    {
        GifTexture.entityTick = -1;
    }
}
