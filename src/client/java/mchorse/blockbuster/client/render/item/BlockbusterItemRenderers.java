package mchorse.blockbuster.client.render.item;

import mchorse.blockbuster.client.render.tileentity.TileEntityModelItemStackRenderer;
import mchorse.blockbuster.common.item.BlockbusterItems;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.item.Item;

/**
 * P83: registration seam for Blockbuster's builtin item renderers.
 *
 * <p>Ports the item-renderer half of legacy {@code ClientProxy.preLoad}: one
 * {@code TileEntityGunItemStackRenderer} for the gun and one <b>shared</b>
 * {@code TileEntityModelItemStackRenderer} across all 16 model-block
 * light-variant items (legacy looped
 * {@code modelBlockItem.setTileEntityItemStackRenderer(teModelItemSR)} with a
 * single shared instance). On 1.20.4 the equivalent is
 * {@link BuiltinItemRendererRegistry#register(net.minecraft.util.ItemConvertible, BuiltinItemRendererRegistry.DynamicItemRenderer)}.</p>
 *
 * <p>The gun item exists now ({@link BlockbusterItems#GUN}), so {@link
 * #register()} wires it immediately. The 16 model-block items exist only once
 * their block phase has registered them, so {@code BlockbusterClient} scans the
 * item registry and calls {@link #registerModelBlockItem(Item)} for each variant
 * it finds, all sharing {@link #MODEL} — the modern equivalent of the legacy
 * shared-instance loop.</p>
 *
 * <p><b>P229.</b> {@link #MODEL} is the {@code client/render/tileentity}
 * renderer — the one that was always the registered implementation. The
 * near-duplicate that used to live in this package (and that this facade used to
 * hold instead) has been deleted; there is exactly one model-block item renderer
 * class now, and exactly one place that registers it.</p>
 */
public final class BlockbusterItemRenderers
{
    /** The single shared model-block item renderer (all 16 variants use it). */
    public static final TileEntityModelItemStackRenderer MODEL = new TileEntityModelItemStackRenderer();

    /** The gun item renderer. */
    public static final TileEntityGunItemStackRenderer GUN = new TileEntityGunItemStackRenderer();

    private BlockbusterItemRenderers()
    {}

    /**
     * Register the renderers for the plain hand items — that is the gun; the
     * model-block variants come through {@link #registerModelBlockItem(Item)}
     * from {@code BlockbusterClient.registerModelBlockRendering}.
     *
     * <p>Idempotent per item: {@link BuiltinItemRendererRegistry} rejects a
     * <b>second</b> registration for the same item with an
     * {@code IllegalArgumentException} (its backing map uses
     * {@code putIfAbsent}), so every registration here is guarded by a
     * {@link BuiltinItemRendererRegistry#get(net.minecraft.util.ItemConvertible)}
     * null-check — re-running (client init + a headless test in the same JVM)
     * is a no-op rather than a crash.</p>
     */
    public static void register()
    {
        /* Ensure the plain hand items (incl. the gun) exist before wiring. */
        BlockbusterItems.register();

        registerIfAbsent(BlockbusterItems.GUN, GUN);
    }

    /**
     * Register the shared model-block renderer for one model-block item.
     * Called once per light variant from {@code BlockbusterClient}, exactly as
     * legacy set the shared TEISR on each of {@code Blockbuster.modelBlockItems}.
     */
    public static void registerModelBlockItem(Item item)
    {
        registerIfAbsent(item, MODEL);
    }

    private static void registerIfAbsent(Item item, BuiltinItemRendererRegistry.DynamicItemRenderer renderer)
    {
        if (item != null && BuiltinItemRendererRegistry.INSTANCE.get(item) == null)
        {
            BuiltinItemRendererRegistry.INSTANCE.register(item, renderer);
        }
    }
}
