package mchorse.mclib.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.GameMode;

import java.util.function.Supplier;

/**
 * Port of McLib 2.4.3's client-side {@code EntityUtils} (roadmap P209 — the
 * gamemode/adventure gate consumed by GUI/editing entry points such as the
 * model-block right-click and camera-editor keybinds). Legacy resolved the
 * gamemode from the player-list entry ({@code NetworkPlayerInfo}), defaulting
 * to {@link GameMode#SURVIVAL} when no network info was present, and
 * {@code isAdventureMode} returned true only for {@link GameMode#ADVENTURE}.
 *
 * <p>Port decision (consistent with the rest of the port, e.g.
 * {@code CameraControl.getGameMode()} and
 * {@code ClientHandlerCameraProfile}, which already replaced legacy
 * {@code EntityUtils.isAdventureMode} with the local client gamemode): the
 * gamemode is read from {@code MinecraftClient.interactionManager} — the local
 * player is the only entity these client-side gates ever query. The result is
 * observably identical to the legacy network-info lookup for the local player.</p>
 *
 * <p>Headless seam: {@link #gameModeProvider} defaults to the live
 * {@code MinecraftClient} probe (which yields {@code null} under JUnit, folded
 * to {@link GameMode#SURVIVAL} by {@link #getGameMode()} — i.e. "not adventure",
 * the legacy no-network-info default). Tests install a fake provider via
 * {@link #setGameModeProvider(Supplier)} to drive the truth table.</p>
 *
 * <p>Legacy source: .tools/legacy-src/mclib/src/main/java/mchorse/mclib/utils/EntityUtils.java</p>
 */
public class EntityUtils
{
    /**
     * Client seam supplying the local player's raw gamemode ({@code null} when
     * unavailable). Defaults to the live {@code MinecraftClient} probe; headless
     * tests override it.
     */
    private static Supplier<GameMode> gameModeProvider = EntityUtils::localGameMode;

    /**
     * Install the client-side gamemode provider. Passing {@code null} restores
     * the live {@code MinecraftClient} probe — used by headless tests to reset
     * state.
     */
    public static void setGameModeProvider(Supplier<GameMode> provider)
    {
        gameModeProvider = provider == null ? EntityUtils::localGameMode : provider;
    }

    private static GameMode localGameMode()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc != null && mc.interactionManager != null)
        {
            return mc.interactionManager.getCurrentGameMode();
        }

        return null;
    }

    /**
     * Legacy {@code getGameMode()} — the local player's gamemode, defaulting to
     * {@link GameMode#SURVIVAL} when unavailable (legacy's no-network-info
     * default).
     */
    public static GameMode getGameMode()
    {
        GameMode mode = gameModeProvider.get();

        return mode == null ? GameMode.SURVIVAL : mode;
    }

    /**
     * Legacy {@code getGameMode(EntityPlayer)}. The port resolves the local
     * client gamemode (the only player these client gates query); the argument
     * is kept for call-site parity.
     */
    public static GameMode getGameMode(PlayerEntity player)
    {
        return getGameMode();
    }

    /**
     * Legacy {@code isAdventureMode(EntityPlayer)} — true only in
     * {@link GameMode#ADVENTURE}.
     */
    public static boolean isAdventureMode(PlayerEntity player)
    {
        return getGameMode(player) == GameMode.ADVENTURE;
    }
}
