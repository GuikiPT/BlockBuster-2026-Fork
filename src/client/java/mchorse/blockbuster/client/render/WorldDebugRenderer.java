package mchorse.blockbuster.client.render;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.client.gui.dashboard.panels.recording_editor.RecordTimelineColors;
import mchorse.blockbuster.common.OrientedBB;
import mchorse.blockbuster.recording.data.Frame;
import mchorse.blockbuster.recording.data.Record;
import mchorse.mclib.client.render.RenderingUtilsClient;
import mchorse.mclib.utils.Color;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

import javax.vecmath.Vector3d;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The world-debug render tail (roadmap P80.3, debug half).
 *
 * <p>Port of the second half of 1.12.2's
 * {@code RenderingHandler.onRenderLast(RenderWorldLastEvent)} — the part that
 * draws <b>debug</b> geometry into the world once the frame's entities are
 * down: the coloured actor record paths (legacy {@code recordsToRender} +
 * {@code renderPaths}, lines 521-541 and 566-632) and, eventually, the
 * oriented-bounding-box outlines ({@code obbsToRender}, lines 544-551).</p>
 *
 * <p>The <i>other</i> half of that legacy handler — the sorted render-last
 * entity pass ({@code renderLastEntities}) — is deliberately not here; it is
 * its own phase and keeps living on {@link mchorse.blockbuster.client.RenderingHandler}.</p>
 *
 * <h2>Why {@code AFTER_TRANSLUCENT}</h2>
 *
 * <p>Legacy's {@code RenderWorldLastEvent} fired after the world was drawn and
 * after the render-last pass, so Tail B always composited <b>over</b> Tail A.
 * On 1.20.4 that ordering is what has to be preserved, not the literal name:
 * the record set is filled during the <i>entity</i> pass, so any event from
 * {@code AFTER_ENTITIES} on can see it, and the render-last pass sits at
 * {@code BEFORE_DEBUG_RENDER}. {@code AFTER_TRANSLUCENT} is the first stage
 * after both, and it is the in-tree precedent for "draw lines into the world
 * late" (Aperture's camera-profile path renderer registers there). {@code LAST}
 * would be marginally closer in wall-clock position but it is where the video/
 * screenshot capture hooks read the framebuffer, and putting debug lines into
 * the same stage makes their inclusion in a capture depend on listener
 * registration order — at {@code AFTER_TRANSLUCENT} they are simply part of the
 * world, as they were in 1.12.2.</p>
 *
 * <p>{@link WorldRenderContext#consumers()} is <b>null</b> at that stage (as it
 * is at {@code LAST}), so this class falls back to the client's own
 * {@code VertexConsumerProvider.Immediate} and flushes it itself — the same
 * thing {@code CameraRenderer.onLastRender} does.</p>
 *
 * <h2>OBB outlines (P75.2, landed by batch V-M)</h2>
 *
 * <p>The second drain: {@link mchorse.blockbuster.common.OrientedBB} now
 * carries its legacy geometry (basis, {@code rotation}/{@code scale} matrices,
 * {@code Corner[8]}, {@code buildCorners()}) and
 * {@code ModelCustomRenderer.updateObbs} rebuilds every drawn limb's boxes
 * per frame, which is what registers them here (legacy's
 * {@code RenderingHandler.obbsToRender.add(this)}, through the
 * {@code OrientedBB.debugSink} seam this class installs).</p>
 *
 * <p><b>Its gate is not the paths' gate.</b> Legacy line 544 is
 * {@code showDebugInfo && !obbsToRender.isEmpty()} — no config term. The
 * {@code record_render_debug_paths} option is a <i>record path</i> option
 * (its name says so, and its legacy read site is the paths branch only), so
 * folding the OBBs into it would be a new behaviour, not a port.</p>
 *
 * <p><b>Corners are absolute world coordinates</b> — {@code center} is the
 * entity's lerped world position and {@code offset} is entity-relative — so
 * the draw pushes {@code translate(-camera)}, which is the 1.20.4 spelling of
 * legacy's {@code builder.setTranslation(-playerX, -playerY, -playerZ)}.
 * Nothing here is camera-relative already, so nothing needs rebasing.</p>
 *
 * <p>Legacy source:
 * {@code blockbuster-1.12/src/main/java/mchorse/blockbuster/client/RenderingHandler.java}
 * lines 516-631.</p>
 */
public class WorldDebugRenderer
{
    /**
     * Legacy {@code RenderingHandler.recordsToRender}: every record whose actor
     * drew this frame. Filled by {@link RenderActor#render}, drained (and
     * always cleared) by {@link #onWorldRender}.
     *
     * <p>Legacy used a {@code HashSet}; this is a {@code LinkedHashSet} so the
     * draw order of a multi-actor scene is the order the actors rendered in
     * rather than hash order. Nothing observable depends on it — every path is
     * an opaque line list — but it makes the tail deterministic to test.</p>
     */
    private static final Set<Record> recordsToRender = new LinkedHashSet<Record>();

    /**
     * Legacy {@code RenderingHandler.obbsToRender}: every oriented bounding box
     * whose limb drew this frame. Filled through the
     * {@link OrientedBB#debugSink} seam by {@code OrientedBB.buildCorners()}
     * (and by its constructor, as in 1.12.2), drained and always cleared by
     * {@link #onWorldRender}.
     *
     * <p>Legacy used a plain {@code HashSet} on the render thread. This one is
     * {@code synchronized} because in the port an {@code OrientedBB} can also
     * be <b>constructed</b> off the render thread — model JSON is parsed
     * wherever the model arrives — and legacy's construction-time registration
     * is kept. Iteration copies under the lock.</p>
     */
    private static final Set<OrientedBB> obbsToRender = Collections.synchronizedSet(new LinkedHashSet<OrientedBB>());

    /** Legacy's {@code final int delta = 2} — every second frame is a vertex. */
    public static final int PATH_DELTA = 2;

    /** Legacy {@code GlStateManager.glLineWidth(2F)}. */
    public static final double PATH_LINE_WIDTH = 2D;

    /** Legacy {@code OrientedBB.render}'s {@code glLineWidth(3F)}. */
    public static final double OBB_LINE_WIDTH = 3D;

    /** Legacy {@code OrientedBB.renderAxes}'s {@code glLineWidth(2F)}. */
    public static final double OBB_AXIS_LINE_WIDTH = 2D;

    /**
     * Legacy {@code renderAxes(..., 4, true, false)} — half-length in
     * Minecraft pixels, so a 0.25-block cross ({@code 4/32} either way).
     */
    public static final double OBB_AXIS_LENGTH = 4D;

    private static boolean installed;

    private WorldDebugRenderer()
    {}

    /**
     * Install the tail. Idempotent — the world-render hook registers once.
     */
    public static void install()
    {
        if (installed)
        {
            return;
        }

        installed = true;

        /* Legacy's RenderingHandler.obbsToRender.add(this), from the common
         * source set: without this the OBB half of the tail is dead. */
        OrientedBB.debugSink = WorldDebugRenderer::addObb;

        WorldRenderEvents.AFTER_TRANSLUCENT.register(WorldDebugRenderer::onWorldRender);
    }

    /** Whether {@link #install()} has already run. */
    public static boolean isInstalled()
    {
        return installed;
    }

    /**
     * Legacy {@code RenderingHandler.recordsToRender.add(entity.playback.record)}
     * at the tail of {@code RenderActor.doRender}.
     *
     * <p>Because legacy registered at the <b>tail</b>, an invisible actor —
     * which returns early — never registered its record and never drew a path.
     * That quirk is preserved by the call site, not here.</p>
     */
    public static void addRecord(Record record)
    {
        if (record != null)
        {
            recordsToRender.add(record);
        }
    }

    /** Test/teardown seam: the pending set, unmodifiable. */
    public static Set<Record> pendingRecords()
    {
        return Collections.unmodifiableSet(recordsToRender);
    }

    /** Drop the pending set without drawing (world unload, tests). */
    public static void clearRecords()
    {
        recordsToRender.clear();
    }

    /**
     * Legacy {@code RenderingHandler.obbsToRender.add(this)} — the sink
     * {@link #install()} hands to {@link OrientedBB#debugSink}.
     */
    public static void addObb(OrientedBB obb)
    {
        if (obb != null)
        {
            obbsToRender.add(obb);
        }
    }

    /** Test/teardown seam: the pending OBB set, unmodifiable. */
    public static Set<OrientedBB> pendingObbs()
    {
        return Collections.unmodifiableSet(obbsToRender);
    }

    /** Drop the pending OBBs without drawing (world unload, tests). */
    public static void clearObbs()
    {
        obbsToRender.clear();
    }

    /**
     * Legacy's OBB gate, verbatim: {@code mc.gameSettings.showDebugInfo &&
     * !obbsToRender.isEmpty()} ({@code RenderingHandler.java:544}).
     *
     * <p>Two conditions, not three — the {@code record_render_debug_paths}
     * config is <b>not</b> consulted here. It gates record paths only, in both
     * 1.12.2 and this port; an author who wants to see an OBB should not have
     * to turn on actor path lines.</p>
     */
    public static boolean shouldRenderObbs(boolean showDebugInfo, boolean obbsNonEmpty)
    {
        return showDebugInfo && obbsNonEmpty;
    }

    /**
     * Legacy's three-way gate, verbatim: {@code mc.gameSettings.showDebugInfo
     * && !recordsToRender.isEmpty() && Blockbuster.recordRenderDebugPaths.get()}.
     *
     * <p>Note what is <b>not</b> in it: the clear. Legacy cleared the set on the
     * next line, outside the {@code if}, so a frame that fails any of the three
     * conditions still discards the records that were registered during it —
     * the set is a per-frame register, never a backlog.</p>
     */
    public static boolean shouldRenderPaths(boolean showDebugInfo, boolean recordsNonEmpty, boolean configOn)
    {
        return showDebugInfo && recordsNonEmpty && configOn;
    }

    /**
     * The live read of {@code record_render_debug_paths}. Split out so a test
     * can prove the config value is what the gate consults — the config existed
     * in the port for a while with no reader at all.
     */
    public static boolean debugPathsEnabled()
    {
        return Blockbuster.recordRenderDebugPaths.get();
    }

    /**
     * Legacy's per-record path colour, reproduced exactly:
     *
     * <pre>
     * random.setSeed(record.filename.hashCode());
     * random.setSeed(random.nextLong());
     * MathHelper.hsvToRGB(random.nextFloat(), 1F, 1F);
     * </pre>
     *
     * <p>The <b>double seeding</b> is not a typo — the filename hash seeds a
     * generator whose first {@code long} seeds the generator that picks the hue.
     * {@code java.util.Random} is specified down to the LCG constants, so
     * {@code filename → hue → 0xRRGGBB} is exact and stable across JVMs and
     * sessions; that stability is the feature (an actor keeps its colour).</p>
     *
     * <p>Port hardening: legacy would NPE on a record with a null filename.
     * A null name hashes as 0 here (the same colour an empty name gets),
     * because a debug overlay must never take the frame down.</p>
     */
    public static int pathColor(String filename)
    {
        Random random = new Random();

        random.setSeed(filename == null ? 0 : filename.hashCode());
        random.setSeed(random.nextLong());

        return RecordTimelineColors.hsvToRgb(random.nextFloat(), 1F, 1F);
    }

    /**
     * The raw {@code GL_LINES} vertex stream for one record's path: consecutive
     * pairs form one segment, so frame {@code i - delta} → frame {@code i}.
     *
     * <p>Legacy quirks kept: a record with fewer than {@code delta + 1} frames
     * is skipped entirely (with the stock delta of 2 that is
     * {@code frames.size() < 3}), the walk starts at {@code i = delta} and
     * strides by {@code delta} — so the path is a decimated polyline, not one
     * vertex per tick — and every vertex sits <b>one block above</b> the
     * recorded position ({@code frame.y + 1F}), which is roughly the actor's
     * eye height and keeps the line out of the floor.</p>
     */
    public static List<Vector3d> buildPathVertices(Record record, int delta)
    {
        List<Vector3d> vertices = new ArrayList<Vector3d>();

        if (record == null || record.frames == null || delta < 1)
        {
            return vertices;
        }

        int length = record.frames.size();

        if (length < delta + 1)
        {
            return vertices;
        }

        for (int i = delta; i < length; i += delta)
        {
            Frame prev = record.frames.get(i - delta);
            Frame current = record.frames.get(i);

            if (prev == null || current == null)
            {
                continue;
            }

            vertices.add(new Vector3d(prev.x, prev.y + 1F, prev.z));
            vertices.add(new Vector3d(current.x, current.y + 1F, current.z));
        }

        return vertices;
    }

    /**
     * Legacy {@code onRenderLast}'s debug half. The set is cleared in a
     * {@code finally} whether or not anything drew — legacy's unconditional
     * {@code recordsToRender.clear()}.
     */
    public static void onWorldRender(WorldRenderContext context)
    {
        try
        {
            MinecraftClient mc = MinecraftClient.getInstance();

            if (mc == null || context == null || context.camera() == null || context.matrixStack() == null)
            {
                return;
            }

            boolean showDebugInfo = mc.options != null && mc.options.debugEnabled;

            if (shouldRenderPaths(showDebugInfo, !recordsToRender.isEmpty(), debugPathsEnabled()))
            {
                renderPaths(mc, context);
            }

            if (shouldRenderObbs(showDebugInfo, !obbsToRender.isEmpty()))
            {
                renderObbs(mc, context);
            }
        }
        finally
        {
            recordsToRender.clear();
            obbsToRender.clear();
        }
    }

    /**
     * Legacy {@code renderPaths}. The 1.12.2 version unbound the active shader
     * program, killed lighting and texturing and pushed raw
     * {@code POSITION_COLOR} lines through the tessellator with a
     * {@code setTranslation(-playerX, -playerY, -playerZ)}; here the lines
     * layer already <i>is</i> untextured, unlit {@code POSITION_COLOR}, and the
     * translation is the camera-relative shift every 1.20.4 world draw applies.
     *
     * <p>Depth stays <b>on</b>: legacy disabled lighting and texturing but never
     * touched the depth test, so a path behind terrain is hidden by it.</p>
     */
    private static void renderPaths(MinecraftClient mc, WorldRenderContext context)
    {
        VertexConsumerProvider consumers = context.consumers();

        if (consumers == null)
        {
            /* AFTER_TRANSLUCENT (like LAST) carries no consumers — build our own. */
            consumers = mc.getBufferBuilders().getEntityVertexConsumers();
        }

        MatrixStack matrices = context.matrixStack();
        Vec3d camera = context.camera().getPos();
        Color color = new Color();

        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        for (Record record : recordsToRender)
        {
            List<Vector3d> vertices = buildPathVertices(record, PATH_DELTA);

            if (vertices.isEmpty())
            {
                continue;
            }

            color.set(pathColor(record.filename), false);

            RenderingUtilsClient.renderLines(matrices, consumers, vertices, color, PATH_LINE_WIDTH, true);
        }

        matrices.pop();

        if (consumers instanceof VertexConsumerProvider.Immediate immediate)
        {
            immediate.draw();
        }
    }

    /**
     * Legacy {@code OrientedBB.render(RenderWorldLastEvent)} +
     * {@code renderAxes(...)}, once per pending box.
     *
     * <p>Everything 1.12.2 did with ambient GL state is a {@code RenderLayer}
     * property here: {@code disableTexture2D}/{@code disableLighting} are what
     * the untextured, unlit {@code POSITION_COLOR} lines layer already is,
     * {@code glLineWidth(3F)}/{@code (2F)} are the layer's line width, and the
     * {@code glUseProgram(0)} dance (legacy unbinding a shader-pack program so
     * its fixed-function lines would show) has no analogue and needs none.</p>
     *
     * <p><b>Depth differs between the two draws, on purpose.</b> The box keeps
     * the depth test (legacy never touched it), so an OBB inside geometry is
     * occluded by it; the axis cross is drawn with the test off (legacy's
     * {@code renderAxes(..., depth = false)} → {@code disableDepth()}), so the
     * anchor is findable even when the box is buried.</p>
     *
     * <p>Colour is legacy's flat opaque white for both.</p>
     */
    private static void renderObbs(MinecraftClient mc, WorldRenderContext context)
    {
        VertexConsumerProvider consumers = context.consumers();

        if (consumers == null)
        {
            consumers = mc.getBufferBuilders().getEntityVertexConsumers();
        }

        MatrixStack matrices = context.matrixStack();
        Vec3d camera = context.camera().getPos();
        Color color = new Color(1F, 1F, 1F, 1F);
        List<OrientedBB> pending;

        synchronized (obbsToRender)
        {
            pending = new ArrayList<OrientedBB>(obbsToRender);
        }

        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        for (OrientedBB obb : pending)
        {
            RenderingUtilsClient.renderLines(matrices, consumers, obb.wireframeVertices(), color, OBB_LINE_WIDTH, true);
            RenderingUtilsClient.renderLines(matrices, consumers, obb.axesVertices(OBB_AXIS_LENGTH), color, OBB_AXIS_LINE_WIDTH, false);
        }

        matrices.pop();

        if (consumers instanceof VertexConsumerProvider.Immediate immediate)
        {
            immediate.draw();
        }
    }
}
