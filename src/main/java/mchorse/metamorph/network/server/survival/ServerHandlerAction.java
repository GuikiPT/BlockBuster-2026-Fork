package mchorse.metamorph.network.server.survival;

import mchorse.mclib.network.ServerMessageHandler;
import mchorse.metamorph.api.MetamorphEvents;
import mchorse.metamorph.api.events.MorphActionEvent;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import mchorse.metamorph.network.common.survival.PacketAction;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server handler executing a morph action (roadmap P55).
 *
 * <p>Requires a morphed, non-spectator player; runs the morph's action and
 * fires {@link MorphActionEvent} through the bundled event bus (legacy posted it
 * on the Forge bus).</p>
 *
 * <p>Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/network/server/survival/ServerHandlerAction.java</p>
 */
public class ServerHandlerAction extends ServerMessageHandler<PacketAction>
{
    @Override
    public void run(ServerPlayerEntity player, PacketAction message)
    {
        IMorphing capability = Morphing.get(player);

        if (capability != null && capability.isMorphed() && !player.isSpectator())
        {
            AbstractMorph morph = capability.getCurrentMorph();

            morph.action(player);
            MetamorphEvents.MORPH_ACTION.invoker().accept(new MorphActionEvent(player, morph.getSettings().action, morph));
        }
    }
}
