package mchorse.metamorph.network.server.survival;

import java.util.List;

import mchorse.mclib.network.ServerMessageHandler;
import mchorse.metamorph.api.MorphAPI;
import mchorse.metamorph.api.MorphUtils;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import mchorse.metamorph.network.common.survival.PacketSelectMorph;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;

/**
 * Server handler selecting an acquired morph by index (roadmap P55).
 *
 * <p>Blocked in ADVENTURE mode. An out-of-range index (e.g. {@code -1} from the
 * demorph key) resolves to {@code null} — i.e. a demorph. A <b>copy</b> of the
 * stored morph is applied (mutation safety), never the stored instance.</p>
 *
 * <p>Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/network/server/survival/ServerHandlerSelectMorph.java</p>
 */
public class ServerHandlerSelectMorph extends ServerMessageHandler<PacketSelectMorph>
{
    @Override
    public void run(ServerPlayerEntity player, PacketSelectMorph message)
    {
        if (player.interactionManager.getGameMode() == GameMode.ADVENTURE)
        {
            return;
        }

        int index = message.index;

        IMorphing capability = Morphing.get(player);
        List<AbstractMorph> morphs = capability.getAcquiredMorphs();
        AbstractMorph morph = null;

        if (!morphs.isEmpty() && index >= 0 && index < morphs.size())
        {
            morph = morphs.get(index);
        }

        MorphAPI.morph(player, MorphUtils.copy(morph), false);
    }
}
