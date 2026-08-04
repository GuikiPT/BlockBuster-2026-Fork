package mchorse.aperture.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import mchorse.aperture.camera.CameraAPI;
import mchorse.mclib.utils.OpHelper;
import mchorse.mclib.utils.resources.RLUtils;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server command {@code /aperture} — port of legacy
 * {@code mchorse.aperture.commands.CommandAperture} (S15 P186).
 *
 * <p>Legacy was a vanilla {@code CommandBase} registered from
 * {@code Aperture.serverLoad} ({@code event.registerServerCommand}) with
 * required permission level 2. Only one sub-command exists:
 * {@code /aperture play <profile> [player]} — plays the given camera profile
 * (a {@link mchorse.mclib.utils.resources.ResourceLocation}) either to the
 * command sender or to the named player, routed through {@link CameraAPI}. The
 * profile argument suggests the world-save server profiles
 * ({@link CameraAPI#getServerProfiles()}); the player argument (legacy
 * {@code isUsernameIndex} tab completion) resolves an online player.</p>
 *
 * <p>Ported to the Brigadier seam directly (no McCommandBase bridge — legacy
 * extended vanilla {@code CommandBase}, not McLib's base).</p>
 *
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/commands/CommandAperture.java
 */
public class CommandAperture
{
    /**
     * Suggestion provider over the world-save server camera profiles (legacy
     * {@code getTabCompletions} args.length == 1 case; the {@code "play"}
     * literal is a Brigadier literal here, not a completion entry).
     */
    public static final SuggestionProvider<ServerCommandSource> PROFILE_SUGGESTIONS =
        (context, builder) -> CommandSource.suggestMatching(CameraAPI.getServerProfiles(), builder);

    public String getName()
    {
        return "aperture";
    }

    public int getRequiredPermissionLevel()
    {
        return OpHelper.VANILLA_OP_LEVEL;
    }

    /**
     * Mount {@code /aperture play <profile> [player]} into the dispatcher.
     */
    public void register(CommandDispatcher<ServerCommandSource> dispatcher)
    {
        dispatcher.register(CommandManager.literal(this.getName())
            .requires(source -> source.hasPermissionLevel(this.getRequiredPermissionLevel()))
            .then(CommandManager.literal("play")
                .then(CommandManager.argument("profile", StringArgumentType.string())
                    .suggests(PROFILE_SUGGESTIONS)
                    .executes(this::playSelf)
                    .then(CommandManager.argument("player", EntityArgumentType.player())
                        .executes(this::playOther)))));
    }

    /**
     * {@code /aperture play <profile>} — legacy args.length &lt;= 2 branch:
     * play to the command sender (must be a player).
     */
    private int playSelf(CommandContext<ServerCommandSource> context) throws CommandSyntaxException
    {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();

        CameraAPI.playCameraProfile(player, RLUtils.create(StringArgumentType.getString(context, "profile")));

        return 1;
    }

    /**
     * {@code /aperture play <profile> <player>} — legacy args.length &gt; 2
     * branch: play to the named player.
     */
    private int playOther(CommandContext<ServerCommandSource> context) throws CommandSyntaxException
    {
        ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");

        CameraAPI.playCameraProfile(player, RLUtils.create(StringArgumentType.getString(context, "profile")));

        return 1;
    }
}
