package mchorse.metamorph.network.client;

import mchorse.mclib.network.ClientMessageHandler;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.network.common.PacketSettings;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Client handler installing the server's active morph-settings map (roadmap
 * P55).
 *
 * <p>Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/network/client/ClientHandlerSettings.java</p>
 */
public class ClientHandlerSettings extends ClientMessageHandler<PacketSettings>
{
    @Override
    public void run(ClientPlayerEntity player, PacketSettings message)
    {
        MorphManager.INSTANCE.setActiveSettings(message.settings);
    }
}
