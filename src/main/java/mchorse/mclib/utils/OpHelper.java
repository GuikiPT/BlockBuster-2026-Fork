package mchorse.mclib.utils;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.function.IntPredicate;
import java.util.function.IntSupplier;

/**
 * Port of McLib 2.4.3's OpHelper. The server-side half ({@link
 * #isPlayerOp(ServerPlayerEntity)}) landed with S2/P26 (its op gates on
 * {@code ServerHandlerConfig} / {@code ServerHandlerRequestConfigs} /
 * {@code ServerHandlerDropItem} are security-relevant and could not be
 * deferred); the client-side half ({@link #getPlayerOpLevel()} /
 * {@link #isPlayerOp()}) lands here with P21.
 *
 * <p>Legacy {@code isPlayerOp(EntityPlayerMP)} checked
 * {@code canSendCommands(profile)} then the ops-list entry level (falling
 * back to {@code server.getOpPermissionLevel()}); yarn 1.20.4's
 * {@code Entity.hasPermissionLevel(int)} (public, verified via javap) reduces
 * to the same predicate — permission level >= 2, with singleplayer
 * cheats-owner reporting level 4 like 1.12.2. This is the exact
 * implementation the S01 P21 plan prescribes.</p>
 *
 * <p><b>Client seam.</b> Legacy {@code getPlayerOpLevel()} read
 * {@code Minecraft.getMinecraft().player.getPermissionLevel()} under
 * {@code @SideOnly(Side.CLIENT)}. This class lives in the <i>common</i>
 * source set (server methods are called there), so it cannot touch
 * {@code MinecraftClient}. The local-player op level is therefore read
 * through the injectable {@link #clientOpLevelProvider} seam: the client
 * entrypoint ({@code mchorse.blockbuster.BlockbusterClient}) installs a
 * provider that recovers the exact level by probing
 * {@code ClientPlayerEntity#hasPermissionLevel(int)} downwards from 4 (yarn
 * keeps {@code getPermissionLevel()} protected, verified with javap);
 * headless tests install a fake provider to drive the truth table. When no
 * provider is installed the level is 0 (non-op), matching a null/absent
 * client player.</p>
 *
 * <p>Every client-side OP gate in the port calls this class — mclib
 * {@code GuiAbstractDashboard}/{@code GuiConfigPanel}, Aperture
 * {@code ClientProxy.canUseCameraEditor}/{@code GuiProfilesManager}/
 * {@code KeyboardHandler}/{@code CameraCommands}/{@code AbstractDestination
 * .serverDestinationCheck}, Metamorph {@code MetamorphClient}, Blockbuster
 * {@code KeyboardHandler}/{@code GuiScenePanel}/{@code GuiModelBlockPanel} —
 * exactly the legacy call graph.</p>
 *
 * <p>Legacy source: .tools/legacy-src/mclib/build/sources/main/java/mchorse/mclib/utils/OpHelper.java</p>
 */
public class OpHelper
{
    /**
     * Minimum OP level according to vanilla code
     */
    public static final int VANILLA_OP_LEVEL = 2;

    /**
     * Client seam supplying the local player's op level. Defaults to 0
     * (non-op) so the common/headless default matches an absent client
     * player; the client entrypoint overrides it via
     * {@link #setClientOpLevelProvider(IntSupplier)}.
     */
    private static IntSupplier clientOpLevelProvider = () -> 0;

    /**
     * Install the client-side op-level provider (called once from the client
     * entrypoint). Passing {@code null} restores the non-op default — used by
     * headless tests to reset state.
     */
    public static void setClientOpLevelProvider(IntSupplier provider)
    {
        clientOpLevelProvider = provider == null ? () -> 0 : provider;
    }

    /**
     * Highest vanilla op level (a level-4 owner in singleplayer-with-cheats).
     */
    public static final int MAX_OP_LEVEL = 4;

    /**
     * Recover an exact op level from the only public accessor yarn 1.20.4
     * leaves on the client player, the {@code hasPermissionLevel(int)}
     * predicate: the level is the largest {@code n} in {@code [1, 4]} the
     * predicate accepts, or 0. Extracted here (rather than inlined in the
     * client entrypoint) so the probe itself is headlessly testable.
     */
    public static int probeOpLevel(IntPredicate hasPermissionLevel)
    {
        if (hasPermissionLevel == null)
        {
            return 0;
        }

        for (int level = MAX_OP_LEVEL; level > 0; level--)
        {
            if (hasPermissionLevel.test(level))
            {
                return level;
            }
        }

        return 0;
    }

    /**
     * Legacy {@code @SideOnly(Side.CLIENT) getPlayerOpLevel()} — resolves the
     * local player's op level through the {@link #clientOpLevelProvider} seam.
     */
    public static int getPlayerOpLevel()
    {
        return clientOpLevelProvider.getAsInt();
    }

    /**
     * Legacy {@code @SideOnly(Side.CLIENT) isPlayerOp()} — whether the local
     * client player is op.
     */
    public static boolean isPlayerOp()
    {
        return isOp(getPlayerOpLevel());
    }

    public static boolean isPlayerOp(ServerPlayerEntity player)
    {
        if (player == null)
        {
            return false;
        }

        return player.hasPermissionLevel(VANILLA_OP_LEVEL);
    }

    public static boolean isOp(int opLevel)
    {
        return opLevel >= VANILLA_OP_LEVEL;
    }
}
