package mchorse.metamorph.client;

import mchorse.metamorph.api.creative.CreativeMorphNetwork;
import mchorse.metamorph.api.creative.CreativeMorphNetworkImpl;
import mchorse.metamorph.api.creative.sections.UserSection;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Collections;
import java.util.List;

/**
 * Installs the two client-side creative-morph seams (roadmap P223, S22).
 *
 * <p>Both were shipped with safe defaults and never assigned outside tests:</p>
 * <ul>
 *   <li>{@link CreativeMorphNetwork#INSTANCE} defaulted to
 *       {@link mchorse.metamorph.api.creative.ICreativeMorphNetwork.Noop}, so
 *       {@code canUse} was always {@code false} and all eight morph operations
 *       were inert — six registered packets had no send site because of it. It
 *       now points at {@link CreativeMorphNetworkImpl}.</li>
 *   <li>{@link UserSection#acquiredMorphsSupplier} defaulted to
 *       {@code Collections::emptyList}, so the "user" creative category could
 *       never show an acquired morph. It now reads the client player's morphing
 *       component, which is what legacy's
 *       {@code Morphing.get(Minecraft.getMinecraft().player)} did inline inside
 *       {@code UserSection.update}.</li>
 * </ul>
 *
 * <p>Client-only on purpose: both seams are read from the client
 * (picker/keybinds/survival menu) and the network half only ever sends to the
 * server. A dedicated server keeps the {@code Noop}, which is what legacy's
 * client-proxy-only wiring amounted to.</p>
 */
public final class MetamorphCreativeWiring
{
    private MetamorphCreativeWiring()
    {}

    public static void install()
    {
        CreativeMorphNetwork.INSTANCE = new CreativeMorphNetworkImpl();
        UserSection.acquiredMorphsSupplier = MetamorphCreativeWiring::acquiredMorphs;
    }

    /**
     * The installed supplier: the local player's acquired morphs. Headless (or
     * between worlds) there is no client player, and the empty list keeps
     * {@code UserSection.update} behaving exactly like the old default.
     */
    public static List<AbstractMorph> acquiredMorphs()
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity player = mc == null ? null : mc.player;

        return acquiredMorphsOf(player == null ? null : Morphing.get(player));
    }

    /**
     * Legacy {@code UserSection.update}'s {@code morphing == null ?
     * Collections.emptyList() : morphing.getAcquiredMorphs()} — split out so the
     * null contract is testable without a client player.
     */
    public static List<AbstractMorph> acquiredMorphsOf(IMorphing morphing)
    {
        return morphing == null ? Collections.emptyList() : morphing.getAcquiredMorphs();
    }
}
