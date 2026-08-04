package mchorse.aperture.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import mchorse.aperture.Aperture;
import mchorse.aperture.ClientProxy;
import mchorse.aperture.camera.CameraControl;
import mchorse.aperture.camera.CameraProfile;
import mchorse.aperture.camera.data.Position;
import mchorse.mclib.utils.OpHelper;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;

/**
 * Bundled Aperture client commands (S15 P186) — {@code /camera} (+ its
 * sub-commands) and {@code /load_chunks}, registered through Fabric's
 * {@link ClientCommandRegistrationCallback}.
 *
 * <p>Legacy structure: {@code CommandCamera} (a McLib {@code SubCommandBase})
 * with the sub-commands {@code start/stop/reload/to_tp} and the control
 * commands {@code step/rotate/roll/fov/default}; {@code CommandLoadChunks} was
 * a separate client command. Both were client-side (Forge
 * {@code ClientCommandHandler}). This port folds the sub-command bodies into a
 * single Brigadier tree, each sub-command taking a legacy-style
 * {@code greedyString} tail split on spaces so the {@code '~'}-relative
 * coordinate parsing and loose optional args survive 1:1.</p>
 *
 * <p>The command name is the configurable {@code command_name}
 * ({@link Aperture#cameraCommandName()}, sanitized with the legacy regex,
 * empty → {@code "camera"}), applied at registration.</p>
 *
 * Legacy sources:
 *   .tools/legacy-src/aperture/src/main/java/mchorse/aperture/commands/CommandCamera.java
 *   .tools/legacy-src/aperture/.../commands/camera/*.java
 *   .tools/legacy-src/aperture/.../commands/CommandLoadChunks.java
 */
public class CameraCommands
{
    private static final String[] EMPTY = new String[0];

    public static void register()
    {
        ClientCommandRegistrationCallback.EVENT.register(CameraCommands::registerCommands);
    }

    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registry)
    {
        String name = Aperture.cameraCommandName();

        dispatcher.register(ClientCommandManager.literal(name)
            .then(sub("start", CameraCommands::start))
            .then(sub("stop", CameraCommands::stop))
            .then(sub("reload", CameraCommands::reload))
            .then(sub("to_tp", CameraCommands::toTP))
            .then(sub("step", CameraCommands::step))
            .then(sub("rotate", CameraCommands::rotate))
            .then(sub("roll", CameraCommands::roll))
            .then(sub("fov", CameraCommands::fov))
            .then(sub("default", CameraCommands::defaults)));

        dispatcher.register(ClientCommandManager.literal("load_chunks")
            .executes(context ->
            {
                LoadChunks.run(context.getSource());

                return 1;
            }));
    }

    /**
     * A sub-command literal that runs {@code handler} with no args, or with the
     * space-split tail of a trailing {@code greedyString} (legacy string-array
     * command parsing).
     */
    private static LiteralArgumentBuilder<FabricClientCommandSource> sub(String literal, SubHandler handler)
    {
        return ClientCommandManager.literal(literal)
            .executes(context ->
            {
                handler.run(context.getSource(), EMPTY);

                return 1;
            })
            .then(ClientCommandManager.argument("args", StringArgumentType.greedyString())
                .executes(context ->
                {
                    handler.run(context.getSource(), StringArgumentType.getString(context, "args").split(" "));

                    return 1;
                }));
    }

    private interface SubHandler
    {
        void run(FabricClientCommandSource source, String[] args);
    }

    /* /camera start [tick] */
    private static void start(FabricClientCommandSource source, String[] args)
    {
        if (!ClientProxy.canUseCameraEditor())
        {
            return;
        }

        int tick = args.length == 0 || args[0].isEmpty() ? 0 : parseIntSafe(args[0], 0);

        ClientProxy.runner.start(ClientProxy.control.currentProfile, tick);
        source.sendFeedback(Text.translatable("aperture.info.profile.start"));
    }

    /* /camera stop */
    private static void stop(FabricClientCommandSource source, String[] args)
    {
        if (!ClientProxy.canUseCameraEditor())
        {
            return;
        }

        ClientProxy.runner.stop();
        source.sendFeedback(Text.translatable("aperture.info.profile.stop"));
    }

    /* /camera reload — drops the cached editor instance */
    private static void reload(FabricClientCommandSource source, String[] args)
    {
        ClientProxy.cameraEditor = null;
    }

    /* /camera to_tp [x y z] — generate per-tick tp lines to the clipboard */
    private static void toTP(FabricClientCommandSource source, String[] args)
    {
        CameraProfile profile = ClientProxy.control.currentProfile;

        if (profile == null)
        {
            source.sendFeedback(Text.literal("No camera profile selected..."));
            return;
        }

        double[] origin = null;

        if (args.length >= 3)
        {
            origin = new double[] {parseDoubleSafe(args[0]), parseDoubleSafe(args[1]), parseDoubleSafe(args[2])};
        }

        MinecraftClient.getInstance().keyboard.setClipboard(generateToTP(profile, origin));
        source.sendFeedback(Text.literal("Copied the thing to your clipboard!"));
    }

    /**
     * Legacy {@code SubCommandCameraToTP} line generation — one
     * {@code tp @s[scores={var=N}] x y z yaw pitch} line per tick of the
     * profile duration, {@code N} 1-indexed. When {@code origin} is non-null
     * every point is re-based so the first frame lands at {@code origin}.
     * Extracted for headless testing.
     */
    public static String generateToTP(CameraProfile profile, double[] origin)
    {
        Position position = new Position();
        Position first = new Position();
        StringBuilder tps = new StringBuilder();

        profile.applyProfile(0, 0F, first);

        for (int i = 0, c = (int) profile.getDuration(); i < c; i++)
        {
            profile.applyProfile(i, 0F, position);

            if (origin != null)
            {
                position.point.set(origin[0] + (position.point.x - first.point.x),
                    origin[1] + (position.point.y - first.point.y),
                    origin[2] + (position.point.z - first.point.z));
            }

            tps.append("tp @s[scores={var=").append(i + 1).append("}] ")
                .append(position.point.x).append(" ")
                .append(position.point.y).append(" ")
                .append(position.point.z).append(" ")
                .append(position.angle.yaw).append(" ")
                .append(position.angle.pitch).append("\n");
        }

        return tps.toString();
    }

    /* /camera step [x] [y] [z] — OP-gated player shift (absolute or ~relative) */
    private static void step(FabricClientCommandSource source, String[] args)
    {
        if (!OpHelper.isPlayerOp())
        {
            return;
        }

        ClientPlayerEntity player = source.getPlayer();

        if (player == null)
        {
            return;
        }

        double x = args.length > 0 && !args[0].isEmpty() ? parseRelativeDouble(args[0], player.getX()) : player.getX();
        double y = args.length > 1 && !args[1].isEmpty() ? parseRelativeDouble(args[1], player.getY()) : player.getY();
        double z = args.length > 2 && !args[2].isEmpty() ? parseRelativeDouble(args[2], player.getZ()) : player.getZ();

        player.refreshPositionAndAngles(x, y, z, player.getYaw(), player.getPitch());
        player.setVelocity(0, 0, 0);
    }

    /* /camera rotate [yaw] [pitch] — player rotation (absolute or ~relative) */
    private static void rotate(FabricClientCommandSource source, String[] args)
    {
        ClientPlayerEntity player = source.getPlayer();

        if (player == null)
        {
            return;
        }

        double x = args.length > 0 && !args[0].isEmpty() ? parseRelativeDouble(args[0], player.getYaw()) : player.getYaw();
        double y = args.length > 1 && !args[1].isEmpty() ? parseRelativeDouble(args[1], player.getPitch()) : player.getPitch();

        player.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), (float) x, (float) y);
        player.setVelocity(0, 0, 0);

        ClientProxy.renderer.smooth.set((float) x, (float) y);
    }

    /* /camera roll [roll] — get/set client roll */
    private static void roll(FabricClientCommandSource source, String[] args)
    {
        CameraControl control = ClientProxy.control;

        if (args.length == 0 || args[0].isEmpty())
        {
            source.sendFeedback(Text.translatable("aperture.info.camera.roll", control.roll));
        }
        else
        {
            control.setRoll((float) parseDoubleSafe(args[0]));
        }
    }

    /* /camera fov [fov] — get/set client FOV */
    private static void fov(FabricClientCommandSource source, String[] args)
    {
        if (args.length == 0 || args[0].isEmpty())
        {
            source.sendFeedback(Text.translatable("aperture.info.camera.fov", Aperture.currentFov.get()));
        }
        else
        {
            ClientProxy.control.setFOV((float) parseDoubleSafe(args[0]));
        }
    }

    /* /camera default — reset roll + FOV */
    private static void defaults(FabricClientCommandSource source, String[] args)
    {
        ClientProxy.control.resetRoll();
        ClientProxy.control.resetFOV();
    }

    /* Legacy vanilla-parity numeric helpers */

    /**
     * Legacy {@code SubCommandCameraRotate.parseRelativeDouble}: a leading
     * {@code '~'} means "relative to {@code base}"; a bare {@code '~'} is
     * {@code base} itself.
     */
    public static double parseRelativeDouble(String input, double base)
    {
        if (input.equals("~"))
        {
            return base;
        }

        boolean relative = input.startsWith("~");
        String number = relative ? input.substring(1) : input;
        double value = Double.parseDouble(number);

        return relative ? base + value : value;
    }

    private static int parseIntSafe(String input, int min)
    {
        int value = Integer.parseInt(input);

        return Math.max(value, min);
    }

    private static double parseDoubleSafe(String input)
    {
        return Double.parseDouble(input);
    }
}
