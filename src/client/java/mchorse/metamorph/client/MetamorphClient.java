package mchorse.metamorph.client;

import mchorse.mclib.utils.OpHelper;
import mchorse.metamorph.Metamorph;
import mchorse.metamorph.client.gui.survival.GuiSurvivalScreen;
import mchorse.metamorph.client.render.EntityMorphBodyPartPass;
import mchorse.metamorph.client.render.MorphRenderPipeline;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.io.File;

/**
 * Client-side Metamorph runtime wiring installed at client init (roadmap
 * P54.1).
 *
 * <p>Creates the {@link EntityModelHandler} singleton and loads {@code
 * config/metamorph/selectors.json} so the entity-selector render substitution
 * is live, and installs the morph render pipeline (P54) — the morph-state
 * provider, the draw seam and the per-morph renderer dispatch.</p>
 */
public final class MetamorphClient
{
    /**
     * The cached survival morph menu (roadmap P61) — legacy kept exactly one
     * instance on {@code ClientProxy} and reopened it, which is what lets the
     * menu keep its scroll position and selection between openings. The
     * conditional section rebuild lives in
     * {@link GuiSurvivalScreen#open()}.
     */
    private static GuiSurvivalScreen survivalScreen;

    private MetamorphClient()
    {}

    public static void init()
    {
        EntityModelHandler.INSTANCE = new EntityModelHandler();
        EntityModelHandler.INSTANCE.loadSelectors(selectorsFile());

        /* P290: legacy `EntityModelHandler.onUpdateEntity(LivingUpdateEvent)`,
         * the once-per-tick client driver for selector morphs. The port had
         * relocated it into the render path, which turned a per-tick animation
         * clock into a per-frame one (see EntityModelHandler.renderEntity). */
        ClientTickEvents.END_CLIENT_TICK.register(client ->
        {
            if (client.world == null || client.isPaused())
            {
                return;
            }

            EntityModelHandler.INSTANCE.updateEntities(client.world.getEntities());
        });

        /* P60: the client keybind family (action / creative / selector /
         * survival / demorph) + the global morph-keybind dispatch. */
        KeyboardHandler.HANDLER.register();

        /* P54: fill the three render seams (morph state provider, draw seam,
         * AbstractMorph render dispatch) and register Metamorph's own morph
         * renderers. Blockbuster's pack renderers register separately. */
        MorphRenderPipeline.install();

        /* P223: the creative/survival morph network seam + the user section's
         * acquired-morph supplier. Until this line the eight creative morph
         * operations were no-ops and the "user" category was always empty. */
        MetamorphCreativeWiring.install();

        /* P225: the two HUD surfaces — the acquired-morph toast and the
         * squid-air replacement bubble bar. Until this line GuiOverlay, GuiHud
         * and ClientMorphFlow were all orphaned and neither ever drew. */
        MetamorphHudWiring.install();

        /* P80.2 (S22 P227): the entity-morph body-part pass. Until this line a
         * body part attached to a mob disguise was invisible — the morph drew,
         * the parts did not. */
        EntityMorphBodyPartPass.install();

        /* P55.1: the server-plugin custom payload channel (metamorph:plugin_morph),
         * which lets a Bukkit/Spigot/proxy plugin force-morph named players on
         * this client without the mod being installed server-side. */
        NetworkHandler.register();
    }

    /**
     * Legacy {@code ClientProxy.getSurvivalScreen()} — the lazily built, then
     * cached, survival morph menu. Callers still have to {@code open()} it,
     * which is where the creative/config-driven section rebuild happens.
     */
    public static GuiSurvivalScreen getSurvivalScreen(MinecraftClient mc)
    {
        if (survivalScreen == null)
        {
            survivalScreen = new GuiSurvivalScreen(mc);
        }

        return survivalScreen;
    }

    public static File selectorsFile()
    {
        File config = FabricLoader.getInstance().getConfigDir().toFile();

        return new File(new File(config, "metamorph"), "selectors.json");
    }

    /**
     * Legacy {@code ClientProxy.canEditSelectors()} — the gate on both the
     * selector-menu keybind and the creative screen's selector-panel toggle:
     * the syncable {@code entity_selectors} OP-access value <b>and</b> the local
     * player being OP. Pure, so the gate is testable without a client.
     */
    public static boolean canEditSelectors()
    {
        return Metamorph.opEntitySelector.get() && OpHelper.isPlayerOp();
    }
}
