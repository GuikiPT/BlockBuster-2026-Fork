package mchorse.blockbuster.network.server;

import java.util.Map;

import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.network.common.PacketDamageControlCheck;
import mchorse.blockbuster.recording.capturing.DamageControl;
import mchorse.blockbuster.recording.capturing.DamageControl.BlockEntry;
import mchorse.blockbuster.recording.scene.Scene;
import mchorse.mclib.network.ServerMessageHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Server handler for {@link PacketDamageControlCheck} (roadmap P131.2). <b>No
 * permission check</b> — any player may ask which scene owns a block.
 *
 * <p>Guard: {@code pointPos != null && !world.isAir(pointPos)}. Iterates
 * {@code CommonProxy.damage.damage}, skipping non-{@link Scene} keys. Match
 * order: (1) any recorded {@link BlockEntry} whose {@code pos} equals the query;
 * (2) else a per-axis <b>box</b> test
 * {@code |target.pos − pointPos| <= maxDistance} on each of X/Y/Z (not a
 * radius). Replies via actionbar (overlay) with
 * {@code blockbuster.info.damage_control.message} carrying the scene id; no
 * reply when unowned. 1:1 behavior port of 1.12.2
 * {@code ServerHandlerDamageControlCheck.java}
 * ({@code EntityPlayerMP.posX/Y/Z} → yarn {@code LivingEntity.getX/Y/Z}).</p>
 */
public class ServerHandlerDamageControlCheck extends ServerMessageHandler<PacketDamageControlCheck>
{
    @Override
    public void run(ServerPlayerEntity player, PacketDamageControlCheck packet)
    {
        if (packet.pointPos != null && !player.getWorld().getBlockState(packet.pointPos).isAir())
        {
            Scene target = null;

            for (Map.Entry<Object, DamageControl> entry : CommonProxy.damage.damage.entrySet())
            {
                if (!(entry.getKey() instanceof Scene))
                {
                    continue;
                }

                DamageControl control = entry.getValue();

                for (BlockEntry block : control.blocks)
                {
                    if (block.pos.equals(packet.pointPos))
                    {
                        target = (Scene) entry.getKey();
                        break;
                    }
                }

                if (target != null)
                {
                    break;
                }

                double x = Math.abs(control.target.getX() - (double) packet.pointPos.getX());
                double y = Math.abs(control.target.getY() - (double) packet.pointPos.getY());
                double z = Math.abs(control.target.getZ() - (double) packet.pointPos.getZ());

                if (x <= control.maxDistance && y <= control.maxDistance && z <= control.maxDistance)
                {
                    target = (Scene) entry.getKey();
                    break;
                }
            }

            if (target != null)
            {
                player.sendMessage(Text.translatable(
                    "blockbuster.info.damage_control.message", target.getId()), true);
            }
        }
    }
}
