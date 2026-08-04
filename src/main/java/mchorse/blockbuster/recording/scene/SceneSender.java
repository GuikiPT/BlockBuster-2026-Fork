package mchorse.blockbuster.recording.scene;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Scene command sender (roadmap P128) — the source used to run a scene's
 * {@code startCommand}/{@code stopCommand}.
 *
 * <p>Legacy source:
 * {@code blockbuster-1.12/src/main/java/mchorse/blockbuster/recording/scene/SceneSender.java},
 * an {@code ICommandSender} named {@code "SceneSender(<id>)"}, positioned at
 * {@code BlockPos.ORIGIN}, {@code canUseCommand} always true (permission level
 * 4), {@code sendCommandFeedback() == false} (silent), no backing entity — so
 * scene commands bypass permissions and produce no feedback.</p>
 *
 * <p>On 1.20.4 the equivalent is a {@link ServerCommandSource}. This holder
 * keeps the scene reference and builds the source lazily ({@link #create()}),
 * because a scene's {@code world} is bound after construction (in
 * {@code SceneManager.get}) and may be null in tests until then; a null world
 * yields a null source and {@code Scene.sendCommand} then no-ops (legacy
 * always had a real world).</p>
 */
public class SceneSender
{
    public final Scene scene;

    public SceneSender(Scene scene)
    {
        this.scene = scene;
    }

    public String getName()
    {
        return "SceneSender(" + this.scene.getId() + ")";
    }

    /**
     * Build the backing {@link ServerCommandSource}: {@code Vec3d.ZERO}
     * position (legacy {@code BlockPos.ORIGIN}), permission level 4 (legacy
     * {@code canUseCommand} always true), silent (legacy
     * {@code sendCommandFeedback() == false}), no entity, world = the scene's
     * {@link ServerWorld}. Returns null when the scene has no server world yet.
     */
    public ServerCommandSource create()
    {
        World world = this.scene.getWorld();

        if (!(world instanceof ServerWorld serverWorld))
        {
            return null;
        }

        MinecraftServer server = serverWorld.getServer();
        String name = this.getName();

        return new ServerCommandSource(server, Vec3d.ZERO, Vec2f.ZERO, serverWorld, 4, name, Text.literal(name), server, null).withSilent();
    }
}
