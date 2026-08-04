package mchorse.mclib.network.mclib.client;

import mchorse.mclib.config.Config;
import mchorse.mclib.config.ConfigManager;
import mchorse.mclib.network.ClientMessageHandler;
import mchorse.mclib.network.mclib.Dispatcher;
import mchorse.mclib.network.mclib.common.PacketConfig;
import net.minecraft.client.network.ClientPlayerEntity;

import java.util.function.Consumer;

/**
 * Full port of McLib 2.4.3's ClientHandlerConfig (roadmap P26).
 *
 * <p>{@code overwrite=true} → the server pushed syncable values: overlay them
 * onto the local module ({@code copyServer}). {@code overwrite=false} → an
 * op requested the server-side config for editing; legacy stored it into the
 * open {@code GuiDashboard}'s config panel — that GUI is S3 P44, so the
 * store goes through the {@link #storeServerConfig} seam (the S3 dashboard
 * installs the "only when the dashboard screen is open" behavior; the
 * headless default is a no-op, matching legacy's behavior with no dashboard
 * open).</p>
 *
 * <p>Legacy source: .tools/legacy-src/mclib/build/sources/main/java/mchorse/mclib/network/mclib/client/ClientHandlerConfig.java</p>
 */
public class ClientHandlerConfig extends ClientMessageHandler<PacketConfig>
{
    /** TODO(S3 P44): {@code GuiDashboard.config.storeServerConfig(...)} gated on the open screen. */
    public static Consumer<Config> storeServerConfig = config -> {};

    @Override
    public void run(ClientPlayerEntity player, PacketConfig message)
    {
        if (message.overwrite)
        {
            ConfigManager manager = Dispatcher.configs.get();
            Config present = manager == null ? null : manager.modules.get(message.config.id);

            if (present != null)
            {
                present.copyServer(message.config);
            }
        }
        else
        {
            storeServerConfig.accept(message.config);
        }
    }
}
