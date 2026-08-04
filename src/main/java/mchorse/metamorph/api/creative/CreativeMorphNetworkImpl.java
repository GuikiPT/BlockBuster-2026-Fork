package mchorse.metamorph.api.creative;

import mchorse.metamorph.Metamorph;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.network.Dispatcher;
import mchorse.metamorph.network.common.creative.PacketAcquireMorph;
import mchorse.metamorph.network.common.creative.PacketClearAcquired;
import mchorse.metamorph.network.common.creative.PacketMorph;
import mchorse.metamorph.network.common.creative.PacketSyncMorph;
import mchorse.metamorph.network.common.survival.PacketFavorite;
import mchorse.metamorph.network.common.survival.PacketKeybind;
import mchorse.metamorph.network.common.survival.PacketRemoveMorph;
import mchorse.metamorph.network.common.survival.PacketSelectMorph;
import net.minecraft.entity.player.PlayerEntity;

/**
 * The real {@link ICreativeMorphNetwork} — creative/survival morph dispatch over
 * the {@code metamorph} channel (roadmap P223, S22).
 *
 * <p>P54–P61 landed every creative/survival data-model class against the
 * {@link CreativeMorphNetwork#INSTANCE} seam, but the seam kept its
 * {@link ICreativeMorphNetwork.Noop} default in production: only tests ever
 * assigned it. The visible consequences were that {@code canUse} was always
 * {@code false} (so the creative-menu keybind and every category keybind did
 * nothing) and that six registered packets — {@code PacketSyncMorph},
 * {@code PacketSelectMorph}, {@code PacketRemoveMorph}, {@code PacketKeybind},
 * {@code PacketFavorite}, {@code PacketClearAcquired} — had no production send
 * site at all. This class is that send site;
 * {@code mchorse.metamorph.client.MetamorphCreativeWiring} installs it.</p>
 *
 * <p>Each method is the legacy call verbatim, taken from the classes that used
 * to hold the {@code Dispatcher.sendToServer(...)} lines:</p>
 * <ul>
 *   <li>{@link #canUse} — legacy {@code CommonProxy.canUse}:
 *       {@code player.isCreative() || allow_morphing_into_category_morphs}. A
 *       {@code null} player is not creative and falls back to the config alone
 *       (the keybind walk passes the client player, which can be null between
 *       worlds).</li>
 *   <li>{@link #morph} — {@code MorphCategory.morph}.</li>
 *   <li>{@link #selectMorph} — {@code AcquiredCategory.morph} and the demorph
 *       keybind, which sends index {@code -1}; the wire deliberately does not
 *       clamp it.</li>
 *   <li>{@link #acquireMorph} — {@code AcquiredCategory.addMorph}, with
 *       {@code notify = false} (the acquire toast is only raised by the server's
 *       own grant path, never by the player adding a morph to their own list).</li>
 *   <li>{@link #clearAcquired}/{@link #syncMorph}/{@link #removeMorph} —
 *       {@code AcquiredCategory.clear}/{@code edit}/{@code remove}.</li>
 *   <li>{@link #keybind}/{@link #favorite} — {@code GuiSurvivalScreen}.</li>
 * </ul>
 *
 * <p>Client-only in practice: {@link Dispatcher#sendToServer} routes through
 * McLib's {@code clientSender} seam, which stays null on a dedicated server (the
 * send is logged and dropped rather than crashing). The class itself is
 * common-side so the seam it fills stays common-side, exactly like the legacy
 * {@code CommonProxy.canUse} it replaces.</p>
 *
 * <p>Legacy sources:
 * {@code .tools/legacy-src/metamorph/.../CommonProxy.java},
 * {@code .../api/creative/categories/AcquiredCategory.java},
 * {@code .../api/creative/categories/MorphCategory.java},
 * {@code .../client/gui/survival/GuiSurvivalScreen.java},
 * {@code .../client/KeyboardHandler.java}</p>
 */
public class CreativeMorphNetworkImpl implements ICreativeMorphNetwork
{
    @Override
    public boolean canUse(PlayerEntity player)
    {
        return (player != null && player.isCreative()) || Metamorph.allowMorphingIntoCategoryMorphs.get();
    }

    @Override
    public void morph(AbstractMorph morph)
    {
        Dispatcher.sendToServer(new PacketMorph(morph));
    }

    @Override
    public void selectMorph(int index)
    {
        Dispatcher.sendToServer(new PacketSelectMorph(index));
    }

    @Override
    public void acquireMorph(AbstractMorph morph)
    {
        Dispatcher.sendToServer(new PacketAcquireMorph(morph, false));
    }

    @Override
    public void clearAcquired()
    {
        Dispatcher.sendToServer(new PacketClearAcquired());
    }

    @Override
    public void syncMorph(AbstractMorph morph, int index)
    {
        Dispatcher.sendToServer(new PacketSyncMorph(morph, index));
    }

    @Override
    public void removeMorph(int index)
    {
        Dispatcher.sendToServer(new PacketRemoveMorph(index));
    }

    @Override
    public void keybind(int index, int keybind)
    {
        Dispatcher.sendToServer(new PacketKeybind(index, keybind));
    }

    @Override
    public void favorite(int index)
    {
        Dispatcher.sendToServer(new PacketFavorite(index));
    }
}
