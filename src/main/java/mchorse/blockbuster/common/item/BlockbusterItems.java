package mchorse.blockbuster.common.item;

import mchorse.blockbuster.Blockbuster;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Blockbuster hand-item registry holder (P98).
 *
 * <p>Ports the item half of legacy {@code CommonProxy.registerItems}. The
 * <b>registration order matters</b>: the creative tab's generic item run
 * iterates items in registration order, so these are registered in the exact
 * legacy sequence — {@code register}, {@code playback}, {@code actor_config},
 * {@code gun} (see {@code blockbuster-1.12} {@code CommonProxy} lines 130-133).</p>
 *
 * <p>The 16 model-block items and the two green {@code BlockItem}s are
 * registered by their owning block phases (P95 / P97); the actor spawn egg by
 * P93. This holder only owns the four plain hand items.</p>
 */
public final class BlockbusterItems
{
    public static ItemRegister REGISTER;
    public static ItemPlayback PLAYBACK;
    public static ItemActorConfig ACTOR_CONFIG;
    public static ItemGun GUN;

    private BlockbusterItems()
    {}

    /**
     * Idempotent — safe to call from both the mod initializer and headless
     * tests (registries are not frozen in the fabric-loader-junit environment).
     */
    public static synchronized void register()
    {
        if (REGISTER != null)
        {
            return;
        }

        REGISTER = register("register", new ItemRegister());
        PLAYBACK = register("playback", new ItemPlayback());
        ACTOR_CONFIG = register("actor_config", new ItemActorConfig());
        GUN = register("gun", new ItemGun());
    }

    private static <T extends Item> T register(String name, T item)
    {
        return Registry.register(Registries.ITEM, new Identifier(Blockbuster.MOD_ID, name), item);
    }
}
