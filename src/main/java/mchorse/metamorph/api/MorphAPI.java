package mchorse.metamorph.api;

import mchorse.metamorph.Metamorph;
import mchorse.metamorph.api.events.AcquireMorphEvent;
import mchorse.metamorph.api.events.MorphEvent;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import mchorse.metamorph.network.Dispatcher;
import mchorse.metamorph.network.common.creative.PacketAcquireMorph;
import mchorse.metamorph.network.common.creative.PacketMorph;
import mchorse.metamorph.network.common.survival.PacketMorphPlayer;
import mchorse.metamorph.network.common.survival.PacketMorphState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Morph API (roadmap P48/P55 — the firing owner of the morph packet set).
 *
 * <p>Public API for morphing a player. Acquired morphs and favorites are sent
 * only to the owning player. Use on the server side.</p>
 *
 * <p>Port notes: the legacy Forge event bus posts become invocations on the
 * bundled {@link MetamorphEvents} array-backed events (P56); {@code isRemote}
 * becomes {@code getWorld().isClient}; the tight-space status message maps to
 * {@link PlayerEntity#sendMessage(Text, boolean)} with {@code overlay=true};
 * packets flow over the bundled McLib S2 dispatcher (P23/P55). The load-bearing
 * legacy quirk is preserved verbatim: the outgoing {@link PacketMorph} /
 * {@link PacketMorphPlayer} carry the <b>original</b> {@code morph} argument even
 * though the capability applies {@code event.morph} (a Pre handler may have
 * replaced it).</p>
 *
 * <p>Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/api/MorphAPI.java</p>
 */
public class MorphAPI
{
    /**
     * Demorph given player
     */
    public static boolean demorph(PlayerEntity player)
    {
        return morph(player, null, false);
    }

    /**
     * Morph a player into given morph with given force flag.
     *
     * @return true, if player was morphed successfully
     */
    public static boolean morph(PlayerEntity player, AbstractMorph morph, boolean force)
    {
        IMorphing morphing = Morphing.get(player);

        if (morphing == null)
        {
            return false;
        }

        if (!force && !player.noClip && !Metamorph.morphInTightSpaces.get() && !EntityUtils.canPlayerMorphFit(player, morphing.getCurrentMorph(), morph))
        {
            if (!player.getWorld().isClient)
            {
                player.sendMessage(Text.translatable("metamorph.gui.status.tight_space"), true);
            }

            return false;
        }

        MorphEvent.Pre event = new MorphEvent.Pre(player, morph, force);

        if (MetamorphEvents.MORPH_PRE.invoker().onMorph(event))
        {
            return false;
        }

        boolean morphed = morphing.setCurrentMorph(event.morph, player, event.force);

        if (!player.getWorld().isClient && morphed)
        {
            Dispatcher.sendTo(new PacketMorph(morph), (ServerPlayerEntity) player);
            Dispatcher.sendToTracked(player, new PacketMorphPlayer(player.getId(), morph));
            Dispatcher.sendTo(new PacketMorphState(player, morphing), (ServerPlayerEntity) player);
        }

        if (morphed)
        {
            MetamorphEvents.MORPH_POST.invoker().accept(new MorphEvent.Post(player, event.morph, force));
        }

        return morphed;
    }

    public static boolean acquire(PlayerEntity player, AbstractMorph morph)
    {
        return acquire(player, morph, true);
    }

    /**
     * Make given player acquire a given morph. Usable on both sides, but it's
     * better to use it on the server.
     *
     * @return true, if player has acquired a morph
     */
    public static boolean acquire(PlayerEntity player, AbstractMorph morph, boolean notify)
    {
        if (morph == null)
        {
            return false;
        }

        AcquireMorphEvent.Pre event = new AcquireMorphEvent.Pre(player, morph);

        if (MetamorphEvents.ACQUIRE_MORPH_PRE.invoker().onAcquire(event))
        {
            return false;
        }

        boolean acquired = Morphing.get(player).acquireMorph(event.morph);

        if (notify && !player.getWorld().isClient && acquired)
        {
            Dispatcher.sendTo(new PacketAcquireMorph(event.morph), (ServerPlayerEntity) player);
        }

        if (acquired)
        {
            MetamorphEvents.ACQUIRE_MORPH_POST.invoker().accept(new AcquireMorphEvent.Post(player, event.morph));
        }

        return acquired;
    }
}
