package mchorse.blockbuster.client.model;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Optional static-limb geometry cache (roadmap P86).
 *
 * <p>The 1.12.2 renderer cached each box limb's geometry in a GL display list
 * ({@code ModelCustomRenderer.displayList}, lazily compiled on first render,
 * freed via {@code delete()}); OBJ/VOX limbs and the extruded-2D layer kept
 * their own per-(renderer[, texture]) list caches with matching
 * {@code clear}/{@code clearByTexture}/{@code clearByModel} eviction. On core
 * profile there are no display lists — geometry is streamed into a
 * {@link VertexConsumer} every frame (see {@link ModelCustomRenderer#emit}).</p>
 *
 * <p>This class is the <b>optional</b> re-introduction of that "compile once"
 * behavior for <em>static</em> box limbs, mirroring the legacy display-list
 * <em>lifecycle</em> (lazy bake, per-limb identity keying, model-scoped and
 * global eviction) rather than its GL mechanism. It is:</p>
 *
 * <ul>
 *   <li><b>Off by default.</b> Gated on {@link #isEnabled()} (initial value read
 *       from the {@code blockbuster.render.vbo_cache} system property). When
 *       disabled the renderer's direct per-frame emit runs unchanged, so output
 *       is bit-identical to the P85 goldens — this is the "cache force-disabled"
 *       done-condition.</li>
 *   <li><b>Output-preserving.</b> The cache stores only the <em>local</em> baked
 *       vertex geometry ({@code [x,y,z,u,v,nx,ny,nz]} in pixel units) — exactly
 *       {@link ModelCustomRenderer#baked}. Color / light / overlay stay per-draw
 *       arguments and the per-frame pose matrix is still applied at emit, so the
 *       replayed vertex stream is byte-identical to the direct path (the
 *       "cache-enabled produces identical geometry" done-condition, asserted by
 *       {@code LimbGeometryCacheTest}). Because color is never baked into the
 *       cache, morph {@code LimbProperties} colour / opacity changes need no
 *       invalidation.</li>
 *   <li><b>Droppable.</b> Nothing depends on the cache existing; deleting this
 *       class and its two call sites in {@link ModelCustomRenderer} restores the
 *       pure streaming renderer.</li>
 * </ul>
 *
 * <p><b>Scope.</b> Only plain box {@link ModelCustomRenderer} limbs are cached
 * ({@link #isCacheable}). {@code is3D} extruded limbs (animated skins, P79) and
 * OBJ shape-key limbs (P77) are dynamic and always stream; OBJ/VOX subclasses
 * override {@link ModelCustomRenderer#emit} and never reach the cache branch.</p>
 *
 * <p><b>GPU residency.</b> An actual {@code net.minecraft.client.gl.VertexBuffer}
 * upload (drawing the cached geometry entirely GPU-side) is deliberately
 * <em>not</em> wired into the default emit path: a raw {@code VertexBuffer.draw}
 * flushes immediately and would reorder against the translucent-batch, changing
 * output and interacting badly with Sodium (an S21 concern). The load-bearing,
 * testable part — the allocate-once / free-on-the-render-thread <em>lifecycle</em>
 * and the eviction triggers — is modelled here via a pluggable
 * {@link CachedLimb#gpuResource} + render-thread disposer, so the real
 * {@code VertexBuffer} can be slotted in for the S20 acceptance pass without
 * re-deriving the invalidation graph.</p>
 */
public final class LimbGeometryCache
{
    /** System property that seeds {@link #enabled} at class load. */
    public static final String FLAG_PROPERTY = "blockbuster.render.vbo_cache";

    private static boolean enabled = Boolean.getBoolean(FLAG_PROPERTY);

    /** Identity-keyed per-limb cache (mirrors the legacy per-renderer list). */
    private static final Map<ModelCustomRenderer, CachedLimb> CACHE = new IdentityHashMap<ModelCustomRenderer, CachedLimb>();

    /** Bake-call counter — instrumentation for the invalidation tests. */
    private static int bakeCount;

    /**
     * Render-thread executor seam. Legacy freed GL resources via
     * {@code Minecraft.addScheduledTask} because deleting off the render thread
     * kicked the player with a GL state exception; on 1.20.4 that is
     * {@code MinecraftClient.execute}. Injectable so lifecycle is unit-testable
     * without a {@code MinecraftClient}.
     */
    private static Executor renderThread = defaultRenderThread();

    private LimbGeometryCache()
    {
    }

    public static boolean isEnabled()
    {
        return enabled;
    }

    public static void setEnabled(boolean value)
    {
        enabled = value;
    }

    public static void setRenderThread(Executor executor)
    {
        renderThread = executor == null ? defaultRenderThread() : executor;
    }

    /** Number of live cache entries (test/debug visibility). */
    public static int size()
    {
        return CACHE.size();
    }

    /** Total number of bakes performed since load (invalidation instrumentation). */
    public static int bakeCount()
    {
        return bakeCount;
    }

    /** Reset the bake counter (test instrumentation only). */
    public static void resetBakeCount()
    {
        bakeCount = 0;
    }

    /**
     * A limb is cacheable only when its geometry is genuinely static: a plain
     * box {@link ModelCustomRenderer} (not an OBJ/VOX subclass, whose
     * {@link ModelCustomRenderer#emit} overrides never consult the cache) whose
     * limb is not an {@code is3D} extruded layer (dynamic, per-frame skin
     * dependent). Conservative by design — the "must not change output" bar
     * means when in doubt, stream.
     */
    public static boolean isCacheable(ModelCustomRenderer renderer)
    {
        if (renderer == null || renderer.getClass() != ModelCustomRenderer.class)
        {
            return false;
        }

        return renderer.limb == null || !renderer.limb.is3D;
    }

    /**
     * Fetch (baking on demand) the cached geometry for a limb. Mirrors the
     * legacy lazy display-list compile timing: the first call bakes, later calls
     * reuse. Callers must have already checked {@link #isCacheable}.
     */
    public static CachedLimb get(ModelCustomRenderer renderer)
    {
        CachedLimb cached = CACHE.get(renderer);

        if (cached == null)
        {
            cached = bake(renderer);
            CACHE.put(renderer, cached);
        }

        return cached;
    }

    private static CachedLimb bake(ModelCustomRenderer renderer)
    {
        if (!renderer.compiled)
        {
            renderer.bake();
        }

        /* Deep-copy the baked local geometry so a later re-bake of the renderer
         * (e.g. after addBox) cannot silently mutate a live cache entry — the
         * cache owns its own immutable snapshot, exactly like a compiled list. */
        float[][][] source = renderer.baked;
        float[][][] copy = new float[source.length][][];

        for (int i = 0; i < source.length; i++)
        {
            float[][] box = source[i];
            float[][] boxCopy = new float[box.length][];

            for (int j = 0; j < box.length; j++)
            {
                boxCopy[j] = box[j].clone();
            }

            copy[i] = boxCopy;
        }

        bakeCount++;

        return new CachedLimb(copy);
    }

    /**
     * Replay a cached limb's geometry into a {@link VertexConsumer}. This loop
     * is a byte-for-byte mirror of {@link ModelCustomRenderer#emit}: same pose /
     * normal matrices, same {@code * scale} factor, same per-vertex call order
     * and the same pre-multiplied colour / light / overlay arguments — so the
     * emitted stream is identical whether the cache is on or off.
     */
    public static void emit(ModelCustomRenderer renderer, MatrixStack matrices, VertexConsumer consumer, float scale, float r, float g, float b, float a, int light, int overlay)
    {
        CachedLimb cached = get(renderer);

        MatrixStack.Entry entry = matrices.peek();
        Matrix4f pose = entry.getPositionMatrix();

        for (float[][] box : cached.geometry)
        {
            for (float[] v : box)
            {
                consumer.vertex(pose, v[0] * scale, v[1] * scale, v[2] * scale)
                    .color(r, g, b, a)
                    .texture(v[3], v[4])
                    .overlay(overlay)
                    .light(light)
                    .normal(entry.getNormalMatrix(), v[5], v[6], v[7])
                    .next();
            }
        }
    }

    /**
     * Evict a single limb, freeing any GPU residency on the render thread.
     * Called from {@link ModelCustomRenderer#delete()} — which
     * {@link ModelCustom#delete()} fans out over every limb, so model removal
     * ({@code ModelClientHandler.removeModel} → scheduled {@code model.delete()})
     * drops the model's entries exactly as the legacy display-list frees did.
     */
    public static void invalidate(ModelCustomRenderer renderer)
    {
        CachedLimb cached = CACHE.remove(renderer);

        if (cached != null)
        {
            cached.dispose(renderThread);
        }
    }

    /**
     * Evict every limb of a model. Convenience mirror of the legacy
     * {@code ModelExtrudedLayer.clearByModel(ModelCustom)} loop; safe to call in
     * addition to the per-limb {@link #invalidate} from {@code delete()}.
     */
    public static void clearByModel(ModelCustom model)
    {
        if (model == null || model.limbs == null)
        {
            return;
        }

        for (ModelCustomRenderer renderer : model.limbs)
        {
            invalidate(renderer);
        }
    }

    /** Global eviction (legacy {@code clear()}); frees all GPU residency. */
    public static void clear()
    {
        for (CachedLimb cached : CACHE.values())
        {
            cached.dispose(renderThread);
        }

        CACHE.clear();
    }

    private static Executor defaultRenderThread()
    {
        return task ->
        {
            MinecraftClient mc = MinecraftClient.getInstance();

            if (mc != null)
            {
                mc.execute(task);
            }
            else
            {
                task.run();
            }
        };
    }

    /**
     * One cached limb: the immutable local geometry snapshot plus an optional
     * GPU residency handle. The handle is a plain {@link AutoCloseable} seam so
     * the allocate-once / free-on-render-thread lifecycle is real and testable
     * without a fragile headless {@code VertexBuffer}; the S20 acceptance pass
     * plugs a {@code net.minecraft.client.gl.VertexBuffer} in here.
     */
    public static final class CachedLimb
    {
        /** Local baked geometry: {@code [box][vertex][x,y,z,u,v,nx,ny,nz]}. */
        public final float[][][] geometry;

        private AutoCloseable gpuResource;

        CachedLimb(float[][][] geometry)
        {
            this.geometry = geometry;
        }

        /**
         * Attach a GPU residency handle (e.g. an uploaded {@code VertexBuffer}).
         * Idempotent-safe: replacing a live handle disposes the old one.
         */
        public void setGpuResource(AutoCloseable resource, Executor renderThread)
        {
            if (this.gpuResource != null && this.gpuResource != resource)
            {
                dispose(this.gpuResource, renderThread);
            }

            this.gpuResource = resource;
        }

        public boolean hasGpuResource()
        {
            return this.gpuResource != null;
        }

        int vertexCount()
        {
            int count = 0;

            for (float[][] box : this.geometry)
            {
                count += box.length;
            }

            return count;
        }

        void dispose(Executor renderThread)
        {
            AutoCloseable resource = this.gpuResource;
            this.gpuResource = null;

            if (resource != null)
            {
                dispose(resource, renderThread);
            }
        }

        private static void dispose(AutoCloseable resource, Executor renderThread)
        {
            renderThread.execute(() ->
            {
                try
                {
                    resource.close();
                }
                catch (Exception e)
                {
                    /* GPU free failures must never crash the render loop. */
                }
            });
        }
    }
}
