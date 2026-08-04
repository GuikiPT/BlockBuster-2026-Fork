package mchorse.blockbuster.mixin;

import mchorse.blockbuster.recording.capturing.ActionHandler;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * S22 P294 — the <b>client</b> half of Forge's {@code PlayerTickEvent} END
 * phase, which legacy {@code ActionHandler.onPlayerTick} relied on to advance a
 * player-attached {@link mchorse.blockbuster.recording.RecordPlayer} once per
 * client tick.
 *
 * <h2>Why this class exists</h2>
 *
 * <p>Forge posts {@code PlayerTickEvent} from {@code EntityPlayer.onUpdate()},
 * a class that exists on both sides, so legacy's handler ran once per player
 * entity per tick <b>on the client too</b>. The port had only wired the server
 * seam ({@code ServerPlayerEntityMixin} on {@code playerTick}), so for every
 * player-driven replay — {@code /record play} on yourself, and a scene
 * {@code Replay} cast onto a fake player — the client never called
 * {@code RecordPlayer.next()}. The visible cost is the whole client-side
 * smoothing path in {@code Record.applyFrame}: recorded rotations, and for a
 * {@code realPlayer} playback the recorded position with the
 * {@code prevX}/{@code lastRenderX} pair the renderer interpolates between.
 * Without it the entity only moved when a server correction packet landed.</p>
 *
 * <h2>Why {@code PlayerEntity.tick} and not a client tick event</h2>
 *
 * <p>{@code PlayerEntity.tick()} is the yarn counterpart of
 * {@code EntityPlayer.onUpdate()}, so its TAIL is Forge's injection point
 * exactly — inside the entity's own tick, after everything vanilla does for it,
 * and (for the local player) inside {@code ClientPlayerEntity.tick}'s
 * {@code isChunkLoaded} gate, which 1.12.2 had verbatim as
 * {@code EntityPlayerSP.onUpdate}'s {@code isBlockLoaded} gate. A
 * {@code ClientTickEvents} listener would fire outside both and would not see
 * remote players at all.</p>
 *
 * <p>It also gets the pause behaviour right for free, which is the other reason
 * not to use a tick event here: this injection is reached through
 * {@code ClientWorld.tickEntities()}, which {@code MinecraftClient.tick()}
 * guards with {@code paused} — and legacy's client {@code PlayerTickEvent} could
 * not fire while the game was paused either, for the same structural reason
 * ({@code WorldClient.updateEntities()} behind {@code Minecraft.runTick}'s
 * {@code if (!this.isGamePaused)}). See {@code ClientTickPauseGateTest} for the
 * census of that asymmetry; Fabric's {@code ClientTickEvents} fire regardless
 * and would have needed the gate restated by hand.</p>
 *
 * <h2>Why the {@code isClient} guard is mandatory</h2>
 *
 * <p>Verified with {@code javap -c} against the loom-cache named jar, 1.20.4
 * kept 1.12.2's split exactly: {@code ServerPlayerEntity.tick()} does
 * <b>not</b> call {@code super.tick()} (legacy {@code EntityPlayerMP.onUpdate}
 * did not either), and {@code ServerPlayerEntity.playerTick()} reaches
 * {@code PlayerEntity.tick()} through {@code invokespecial} (legacy
 * {@code onUpdateEntity} → {@code super.onUpdate()}). So on a server this
 * injection fires from inside the very method {@code ServerPlayerEntityMixin}
 * already brackets — the guard in
 * {@link ActionHandler#onClientPlayerTick(PlayerEntity)} is what stops the
 * playback being advanced twice per server tick (double-speed replay).</p>
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityPlaybackTickMixin
{
    @Inject(method = "tick", at = @At("TAIL"))
    private void blockbuster$onClientPlayerTick(CallbackInfo info)
    {
        ActionHandler.onClientPlayerTick((PlayerEntity) (Object) this);
    }
}
