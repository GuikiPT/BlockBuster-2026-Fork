package mchorse.blockbuster.common;

import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.PacketOpenGui;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Screen-opening plumbing (roadmap P100) — the Fabric replacement for Forge's
 * {@code IGuiHandler} / {@code EntityPlayer#openGui}.
 *
 * <p>Port of 1.12.2 {@code common/GuiHandler.java}. The int constants
 * ({@link #PLAYBACK 0}, {@link #ACTOR 1}, {@link #MODEL_BLOCK 3}) are the wire
 * contract of the legacy {@code openGui} call: id 2 was historically removed
 * and is intentionally absent — <b>never renumber</b> (config files/scripts in
 * the wild referenced these ids). Forge routed the open through its own GUI
 * packet; here the same is achieved with a single S2C {@link PacketOpenGui}.</p>
 *
 * <p>{@link #open(PlayerEntity, int, int, int, int)} keeps the legacy
 * {@code openGui(player, ID, x, y, z)} shape and picks the side:</p>
 * <ul>
 * <li><b>Server</b> (e.g. actor-config right-click): send {@link PacketOpenGui}
 * to that client — the {@code getClientGuiElement} switch now runs on the
 * receiving client.</li>
 * <li><b>Client</b> (e.g. model-block right-click, a client-side-direct open in
 * 1.12): route immediately through {@link #route}.</li>
 * </ul>
 *
 * <p>Screen factories are registered per id in {@link #FACTORIES} (mirrors the
 * legacy {@code getClientGuiElement} switch) so S12/S15 can swap the stubs in
 * without touching the routing. The concrete factories live in the client
 * source set and are installed at client init; this common class never
 * references a client-only screen. Routing is total — an unknown id (including
 * the removed id 2) logs a warning and no-ops, never crashes.</p>
 *
 * Legacy source: blockbuster-1.12/src/main/java/mchorse/blockbuster/common/GuiHandler.java
 */
public class GuiHandler
{
    /* GUI ids — wire contract; id 2 intentionally absent (historically removed). */
    public static final int PLAYBACK = 0;
    public static final int ACTOR = 1;
    public static final int MODEL_BLOCK = 3;

    private static final Logger LOGGER = LoggerFactory.getLogger("blockbuster");

    /**
     * Client screen factories keyed by GUI id — the routing table mirroring the
     * legacy {@code getClientGuiElement} switch. Populated at client init (see
     * {@code GuiHandlerClient}); empty on a dedicated server / headless, where
     * {@link #route} then simply warns.
     */
    private static final Map<Integer, GuiFactory> FACTORIES = new HashMap<>();

    /**
     * Client-installed gate for the model-block right-click open (P209). Legacy
     * {@code BlockModel.onBlockActivated} opened the model dashboard client-side
     * only, gated by {@code !EntityUtils.isAdventureMode(player)} and an async
     * {@code PermissionUtils.hasPermission(player, editModelBlock, …)} check.
     * That logic touches client-only singletons ({@code MinecraftClient},
     * {@code PermissionUtils}), so it is installed from the client entrypoint
     * ({@code GuiHandlerClient}) and reached from the common {@link BlockModel}
     * through {@link #openModelBlock}. Null on a dedicated server / headless,
     * where the client branch of {@code onUse} never runs anyway.
     */
    private static ModelBlockOpener modelBlockOpener;

    /**
     * The client-side model-block open gate (adventure-mode + {@code
     * blockbuster.model_block.edit} permission), installed by the client
     * entrypoint. Kept separate from {@link GuiFactory} because it carries the
     * player reference the permission check needs.
     */
    @FunctionalInterface
    public interface ModelBlockOpener
    {
        void open(PlayerEntity player, int x, int y, int z);
    }

    /**
     * One id's screen opener. Receives the raw {@code (x, y, z)} triple exactly
     * as it rode the packet (for {@link #ACTOR}, {@code x} is the entity id);
     * the factory resolves the entity / block entity and opens the screen.
     */
    @FunctionalInterface
    public interface GuiFactory
    {
        void open(int x, int y, int z);
    }

    /**
     * Register (or replace) the screen factory for a GUI id. Called from the
     * client entrypoint; later stages (S12/S15) re-register the same ids with
     * their real screens without touching {@link #open}/{@link #route}.
     */
    public static void register(int id, GuiFactory factory)
    {
        FACTORIES.put(id, factory);
    }

    /** Test/reset hook: drop every registered factory. */
    public static void clearFactories()
    {
        FACTORIES.clear();
    }

    /**
     * Install (or replace) the client-side model-block open gate. Called from
     * the client entrypoint; {@code null} clears it (test/reset hook).
     */
    public static void setModelBlockOpener(ModelBlockOpener opener)
    {
        modelBlockOpener = opener;
    }

    /**
     * Legacy {@code BlockModel.onBlockActivated} client branch (P209). Runs the
     * installed {@link ModelBlockOpener} gate (adventure-mode + {@code
     * blockbuster.model_block.edit} permission, then the actual open). Total: no
     * gate installed (dedicated server / headless) → warn + no-op, never a
     * crash. Only ever reached from the client side of {@link BlockModel#onUse}.
     */
    public static void openModelBlock(PlayerEntity player, int x, int y, int z)
    {
        if (modelBlockOpener == null)
        {
            LOGGER.warn("GuiHandler.openModelBlock: no client opener installed — ignored");

            return;
        }

        modelBlockOpener.open(player, x, y, z);
    }

    /**
     * Legacy {@code openGui(player, ID, x, y, z)} shortcut. Server-side sends the
     * S2C open packet to the player; client-side routes directly. Preserves the
     * ACTOR quirk of passing the entity id through {@code x}.
     */
    public static void open(PlayerEntity player, int id, int x, int y, int z)
    {
        if (player.getWorld().isClient)
        {
            route(id, x, y, z);
        }
        else if (player instanceof ServerPlayerEntity serverPlayer)
        {
            Dispatcher.sendTo(new PacketOpenGui(id, x, y, z), serverPlayer);
        }
        else
        {
            LOGGER.warn("GuiHandler.open: player is neither a client nor a server player (id={}) — dropped", id);
        }
    }

    /**
     * Dispatch an open request to the registered client factory (invoked by the
     * client receiver of {@link PacketOpenGui}, or directly for a client-side
     * open). Total: an unknown id — including the removed id 2 — logs a warning
     * and no-ops.
     */
    public static void route(int id, int x, int y, int z)
    {
        GuiFactory factory = FACTORIES.get(id);

        if (factory == null)
        {
            LOGGER.warn("GuiHandler: no screen factory registered for GUI id {} — ignored", id);

            return;
        }

        factory.open(x, y, z);
    }
}
