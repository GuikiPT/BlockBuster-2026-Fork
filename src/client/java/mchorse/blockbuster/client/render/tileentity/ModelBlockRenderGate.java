package mchorse.blockbuster.client.render.tileentity;

/**
 * Pure model-block render-decision predicate (roadmap P96).
 *
 * <p>Extraction of the legacy {@code TileEntityModelRenderer.render} gate:</p>
 * <pre>!te.morph.isEmpty()
 *     &amp;&amp; (!Blockbuster.modelBlockDisableRendering.get() || teSettings.isRenderAlways())
 *     &amp;&amp; teSettings.isEnabled()</pre>
 *
 * <p>Load-bearing quirk (P96): a per-TE {@code renderAlways} flag <b>overrides</b>
 * the global {@code model_block_disable_rendering} kill-switch — a block with
 * "render always" set still draws even when the config disables model-block
 * rendering globally.</p>
 *
 * <p>P80.3 added the {@code deferred} clause, legacy's other gate on the same
 * block: {@code TileEntityModel.shouldRenderInPass} returned
 * {@code super.shouldRenderInPass(pass) && !(settings.isRenderLast() &&
 * RenderingHandler.addRenderLast(this))}. 1.20.4 has no render-pass hook on the
 * block entity, so the two gates merge here — {@code deferred} is that
 * {@code renderLast && addRenderLast(...)} term, already evaluated by the
 * caller (it has a side effect: it enqueues).</p>
 */
public final class ModelBlockRenderGate
{
    private ModelBlockRenderGate()
    {}

    /**
     * @param morphEmpty        {@code te.morph.isEmpty()}
     * @param disableRendering  the global {@code model_block_disable_rendering} config
     * @param renderAlways      the TE's {@code renderAlways} setting (overrides the kill-switch)
     * @param enabled           the TE's {@code enabled} setting
     * @param deferred          the P80.3 tail pass has taken this block for this frame
     * @return whether the morph should be rendered this frame
     */
    public static boolean shouldRender(boolean morphEmpty, boolean disableRendering, boolean renderAlways, boolean enabled, boolean deferred)
    {
        return !morphEmpty && (!disableRendering || renderAlways) && enabled && !deferred;
    }
}
