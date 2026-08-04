package mchorse.blockbuster.client.gui;

import mchorse.blockbuster.BlockbusterClient;
import mchorse.blockbuster.aperture.CameraHandler;
import mchorse.blockbuster.client.gui.dashboard.GuiBlockbusterPanels;
import mchorse.blockbuster.common.BlockbusterPermissions;
import mchorse.blockbuster.common.GuiHandler;
import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.blockbuster.common.tileentity.TileEntityModel;
import mchorse.blockbuster.recording.scene.SceneLocation;
import mchorse.mclib.client.gui.mclib.GuiDashboard;
import mchorse.mclib.client.gui.utils.ScreenOpener;
import mchorse.mclib.permissions.PermissionUtils;
import mchorse.mclib.utils.EntityUtils;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * Client-side screen routing for {@link GuiHandler} (roadmap P100, live-wired
 * in S22/P228) — the port of 1.12.2 {@code GuiHandler.getClientGuiElement}.
 * Lives in the client source set (it touches client-only screens);
 * {@link #install()} registers one factory per GUI id into the common
 * {@link GuiHandler} routing table at client init, so the common code never
 * references a client class.
 *
 * <p>The legacy switch, branch for branch:</p>
 * <ul>
 * <li>{@link GuiHandler#PLAYBACK} → guarded by
 * {@link CameraHandler#isApertureLoaded()} (a constant-true shim now that
 * Aperture is bundled), then {@code new GuiPlayback()}. The port reaches that
 * screen through the already-installed {@link CameraHandler#attach} opener
 * (see {@code CameraHandlerClient}) instead of constructing a second copy of
 * it here — see {@link #openPlayback()} for the one deviation (empty vs. null
 * location).</li>
 * <li>{@link GuiHandler#ACTOR} → resolve {@code world.getEntityById(x)} as an
 * {@link EntityActor} and open {@link GuiActor}.</li>
 * <li>{@link GuiHandler#MODEL_BLOCK} → resolve the {@link TileEntityModel} at
 * {@code (x, y, z)}, make the dashboard's model panel active, hand it the
 * block, and display the dashboard — legacy order exactly.</li>
 * </ul>
 *
 * <p>All three resolve their target through the {@link #entityResolver} /
 * {@link #blockEntityResolver} seams and display through {@link ScreenOpener}
 * / {@link CameraHandler#attach}, so a headless test can drive
 * {@code GuiHandler.route(...)} end to end and capture what came up. Every
 * branch stays total: an unresolvable target logs a warning and no-ops, where
 * legacy returned {@code null} (its MODEL_BLOCK branch actually cast blindly
 * and would NPE on a missing tile entity — the port warns instead, per the
 * "readers are total" rule; no working setup can observe the difference).</p>
 *
 * Legacy source: blockbuster-1.12/src/main/java/mchorse/blockbuster/common/GuiHandler.java
 */
public final class GuiHandlerClient
{
    private static final Logger LOGGER = LoggerFactory.getLogger("blockbuster");

    /**
     * {@code mc.world.getEntityById(id)} — the ACTOR route's target lookup.
     * A seam because {@link MinecraftClient#getInstance()} is {@code null}
     * headless; tests hand the route a prepared entity.
     */
    public static IntFunction<Entity> entityResolver = GuiHandlerClient::resolveEntity;

    /**
     * {@code mc.world.getBlockEntity(pos)} — the MODEL_BLOCK route's target
     * lookup. Same seam rationale as {@link #entityResolver}.
     */
    public static Function<BlockPos, BlockEntity> blockEntityResolver = GuiHandlerClient::resolveBlockEntity;

    private GuiHandlerClient()
    {}

    /** Test hook: restore the production world lookups. */
    public static void resetSeams()
    {
        entityResolver = GuiHandlerClient::resolveEntity;
        blockEntityResolver = GuiHandlerClient::resolveBlockEntity;
    }

    private static Entity resolveEntity(int id)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        return mc == null || mc.world == null ? null : mc.world.getEntityById(id);
    }

    private static BlockEntity resolveBlockEntity(BlockPos pos)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        return mc == null || mc.world == null ? null : mc.world.getBlockEntity(pos);
    }

    /** Install the three legacy GUI-id factories into the routing table. */
    public static void install()
    {
        GuiHandler.register(GuiHandler.PLAYBACK, (x, y, z) ->
        {
            if (!CameraHandler.isApertureLoaded())
            {
                return;
            }

            openPlayback();
        });

        GuiHandler.register(GuiHandler.ACTOR, (x, y, z) ->
        {
            Entity entity = entityResolver.apply(x);

            if (!(entity instanceof EntityActor))
            {
                LOGGER.warn("GuiHandler ACTOR open: entity {} is not an EntityActor — ignored", x);

                return;
            }

            openActor((EntityActor) entity);
        });

        GuiHandler.register(GuiHandler.MODEL_BLOCK, (x, y, z) ->
        {
            BlockEntity be = blockEntityResolver.apply(new BlockPos(x, y, z));

            if (!(be instanceof TileEntityModel))
            {
                LOGGER.warn("GuiHandler MODEL_BLOCK open: no model block entity at ({}, {}, {}) — ignored", x, y, z);

                return;
            }

            openModelBlock((TileEntityModel) be);
        });

        /* P209: the model-block right-click gate. Legacy
         * BlockModel.onBlockActivated (client branch): skip in adventure mode,
         * then run the async blockbuster.model_block.edit permission check and
         * open the MODEL_BLOCK screen only when granted. On the integrated
         * server / null client the permission check resolves true synchronously
         * (see PermissionUtils), matching 1.12.2 singleplayer. */
        GuiHandler.setModelBlockOpener((player, x, y, z) ->
        {
            if (EntityUtils.isAdventureMode(player))
            {
                return;
            }

            PermissionUtils.hasPermission(player, BlockbusterPermissions.editModelBlock, (allowed) ->
            {
                if (allowed)
                {
                    GuiHandler.route(GuiHandler.MODEL_BLOCK, x, y, z);
                }
            });
        });
    }

    /**
     * Legacy {@code getPlayback()} — {@code new GuiPlayback()}, which Forge
     * displayed because the handler returned it.
     *
     * <p>Deviation (documented): the port opens it through the installed
     * {@link CameraHandler#attach} opener, which always calls
     * {@code GuiPlayback.setLocation}. Legacy's PLAYBACK branch left
     * {@code location} <b>null</b>, so the screen's "Done" button sent a
     * {@code PacketPlaybackButton} with a null location. An empty
     * {@link SceneLocation} is the same thing to every consumer
     * ({@code isEmpty() == true}, wire type 0) minus the null. Note this GUI id
     * has <b>no call site</b> in 2.7.2 or in the port (the playback-button item
     * travels via {@code PacketPlaybackButton} instead), so the branch is
     * parity plumbing.</p>
     */
    public static void openPlayback()
    {
        CameraHandler.attach(new SceneLocation(), Collections.emptyList());
    }

    /** Legacy {@code new GuiActor(Minecraft.getMinecraft(), actor)}. */
    public static void openActor(EntityActor actor)
    {
        GuiActor.open(MinecraftClient.getInstance(), actor);
    }

    /**
     * Legacy MODEL_BLOCK branch, in order: activate the dashboard's model
     * panel, hand it the block ({@code openModelBlock}), then display the
     * dashboard. Total — a dashboard-less/panel-less client (never the case in
     * game, always the case in a bare headless JVM) warns and no-ops.
     */
    public static void openModelBlock(TileEntityModel model)
    {
        GuiDashboard dashboard = GuiDashboard.get();
        GuiBlockbusterPanels panels = BlockbusterClient.panels;

        if (dashboard == null || panels == null || panels.modelPanel == null)
        {
            LOGGER.warn("GuiHandler MODEL_BLOCK open: the dashboard model panel is not available — ignored");

            return;
        }

        dashboard.panels.setPanel(panels.modelPanel);
        panels.modelPanel.openModelBlock(model);

        ScreenOpener.open(dashboard);
    }
}
