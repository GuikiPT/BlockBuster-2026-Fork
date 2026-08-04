package mchorse.blockbuster.client.render.item;

import mchorse.blockbuster.Blockbuster;

/**
 * P83: the default, inert {@link ItemStackDrawer}.
 *
 * <p>It draws nothing and logs at most once, so a Blockbuster item registered
 * with a {@code DynamicItemRenderer} renders empty rather than crashing before
 * anything is installed — the totality ground rule on the render side.</p>
 *
 * <p><b>P229.</b> Both users of this default —
 * {@code TileEntityModelItemStackRenderer.liveDrawer} and {@code staticDrawer}
 * — are assigned by {@code ModelBlockRenderWiring.install()} at client init, so
 * on a running client this is only the pre-install placeholder (and the value
 * tests restore). Seeing its log line in a real session means the wiring never
 * ran.</p>
 */
public final class InertDrawer implements ItemStackDrawer
{
    public static final InertDrawer INSTANCE = new InertDrawer();

    private boolean warned;

    private InertDrawer()
    {}

    @Override
    public void draw(ItemRenderContext context)
    {
        if (!this.warned)
        {
            this.warned = true;

            Blockbuster.LOGGER.debug("Blockbuster item render seam is inert (morph/gun render lands in S8/S17); item renders empty for now.");
        }
    }
}
