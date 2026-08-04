package mchorse.chameleon.client;

import mchorse.chameleon.Chameleon;
import mchorse.chameleon.client.gui.ChameleonMorphEditors;
import mchorse.chameleon.client.render.ChameleonMorphRenderer;
import mchorse.chameleon.lib.MolangHelper;
import mchorse.chameleon.mclib.ChameleonConfigButtons;
import mchorse.chameleon.mclib.ChameleonTree;
import mchorse.chameleon.metamorph.ChameleonMorph;
import mchorse.mclib.utils.files.GlobalTree;
import mchorse.metamorph.client.render.MorphRendererRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.io.File;

/**
 * Chameleon's client init — the client half of legacy {@code ClientProxy.preInit}
 * plus the three seams the port's source-set split introduces.
 *
 * <p>Called once from {@code BlockbusterClient.onInitializeClient}. Everything
 * here is idempotent, so a re-run (test isolation) is harmless.</p>
 *
 * <p>Legacy source: chameleon/.../ClientProxy.java</p>
 */
public class ChameleonClient
{
    private ChameleonClient()
    {}

    /**
     * The Chameleon config folder ({@code config/chameleon}) — legacy
     * {@code new File(event.getModConfigurationDirectory(), "chameleon")}. The
     * folder name is the bare {@code "chameleon"}, not the {@code chameleon_morph}
     * mod id, so an existing 1.12.2 install's models drop straight in.
     */
    public static File configRoot()
    {
        return FabricLoader.getInstance().getConfigDir().resolve("chameleon").toFile();
    }

    public static void init()
    {
        File modelsFile = new File(configRoot(), "models");

        /* Legacy ClientProxy.preInit: models folder, scan, resource pack, file
         * tree — in that order (the scan populates the folders the tree lists). */
        ChameleonModelLoader.install(modelsFile);

        ChameleonPack.INSTANCE = new ChameleonPack(modelsFile);

        if (Chameleon.tree == null)
        {
            Chameleon.tree = new ChameleonTree(modelsFile);

            GlobalTree.TREE.register(Chameleon.tree);
        }

        /* Port seam: the three client-only values MolangHelper needs (see that
         * class for why the rest of it is common). */
        MolangHelper.context = new ClientContext();

        /* Port seam (P54): the morph's two @SideOnly(CLIENT) render bodies. */
        MorphRendererRegistry.register(ChameleonMorph.class, new ChameleonMorphRenderer());

        /* Port seam (P165-registry): the morph editor. */
        ChameleonMorphEditors.register();

        /* Port seam (P210): the config panel's button row. */
        ChameleonConfigButtons.register();
    }

    /**
     * {@link MolangHelper.IClientContext} over the live client.
     *
     * <p>Every accessor is total: before the first frame, or on a client with no
     * world, the queries read 0 / the origin rather than throwing — an animation
     * evaluated at an odd moment must not break the frame.</p>
     */
    public static class ClientContext implements MolangHelper.IClientContext
    {
        @Override
        public float partialTick()
        {
            MinecraftClient mc = MinecraftClient.getInstance();

            return mc == null ? 0F : mc.getTickDelta();
        }

        /**
         * Legacy {@code mc.world.loadedEntityList.size()}. Yarn's
         * {@code ClientWorld.getRegularEntityCount()} is the same population.
         */
        @Override
        public int actorCount(World world)
        {
            return world instanceof ClientWorld
                ? ((ClientWorld) world).getRegularEntityCount()
                : 0;
        }

        @Override
        public Vec3d cameraPosition()
        {
            MinecraftClient mc = MinecraftClient.getInstance();
            Camera camera = mc == null || mc.gameRenderer == null ? null : mc.gameRenderer.getCamera();

            return camera == null ? Vec3d.ZERO : camera.getPos();
        }
    }
}
