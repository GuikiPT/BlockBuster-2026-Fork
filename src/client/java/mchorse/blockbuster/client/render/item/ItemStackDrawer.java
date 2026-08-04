package mchorse.blockbuster.client.render.item;

/**
 * P83: the render seam an item {@code DynamicItemRenderer} delegates to.
 *
 * <p>P83 built the registration + context plumbing; P229 installed the two
 * concrete drawers the model-block item needs — the live morph render (legacy
 * {@code ClientProxy.modelRenderer.render}) and the {@code model_static} sprite
 * (legacy's {@code ItemMeshDefinition} swap). The gun's draw never used this
 * seam: {@code TileEntityGunItemStackRenderer} routes through
 * {@code GunItemContextRouter} + {@code GunPropsRenderer} directly. Where a
 * drawer is not installed the default is inert (logs once, no-ops), so the item
 * renders nothing but never crashes — the "totality" ground rule applied to the
 * render side.</p>
 *
 * <p>Keeping the draw behind a functional seam is also what makes the
 * config-switch and context plumbing headless-testable: a test installs a
 * counting stub drawer and asserts which path ran and that the
 * {@link ItemRenderContext} (mode + holder) reached it unchanged, with no GL
 * involved.</p>
 */
@FunctionalInterface
public interface ItemStackDrawer
{
    /**
     * Draw the item's configured content. The {@link ItemRenderContext}
     * carries the resolved holder and the camera transform mode; the matrices
     * and vertex consumers are the live render targets (null in headless
     * tests, which supply an inert stub drawer).
     */
    void draw(ItemRenderContext context);
}
