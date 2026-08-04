package mchorse.metamorph.network.server.creative;

import mchorse.mclib.network.ServerMessageHandler;
import mchorse.metamorph.Metamorph;
import mchorse.metamorph.api.MorphAPI;
import mchorse.metamorph.network.common.creative.PacketMorph;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server handler for a creative morph request (roadmap P55).
 *
 * <p>Gated by {@code canUse} — the legacy {@code Metamorph.proxy.canUse} check
 * ({@code creative || allow_morphing_into_category_morphs}); the force flag is
 * that same config.</p>
 *
 * <p>Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/network/server/creative/ServerHandlerMorph.java</p>
 */
public class ServerHandlerMorph extends ServerMessageHandler<PacketMorph>
{
    @Override
    public void run(ServerPlayerEntity player, PacketMorph message)
    {
        if (player.isCreative() || Metamorph.allowMorphingIntoCategoryMorphs.get())
        {
            MorphAPI.morph(player, message.morph, Metamorph.allowMorphingIntoCategoryMorphs.get());
        }
    }
}
