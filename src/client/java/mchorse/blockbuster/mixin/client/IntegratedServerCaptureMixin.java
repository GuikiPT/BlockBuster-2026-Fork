package mchorse.blockbuster.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mchorse.blockbuster.client.video.CaptureClock;
import net.minecraft.server.integrated.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.BooleanSupplier;

/**
 * S22 <b>P295</b> — the integrated server's half of the fixed-timestep capture
 * clock.
 *
 * <h2>The defect</h2>
 *
 * <p>P199 shipped only the client half. {@code RenderTickCounterMixin} makes
 * every rendered frame worth exactly {@code 20 / captureFps} ticks of
 * <i>client</i> game time, but nothing gated the integrated server: it kept
 * ticking the world at wall-clock 20 TPS while the render clock advanced game
 * time at {@code renderFps / captureFps} of real speed. The server world — and
 * with it {@code SceneManager} playback, actors, and every
 * {@code END_SERVER_TICK} driver — therefore ran at a completely different rate
 * from the frames being encoded, and {@code PacketSyncTick} snapped client
 * playback forward to match. Reported as "it doesn't throttle/slow-mo to put the
 * footage together smoothly — basically it doesn't work correctly".</p>
 *
 * <h2>The seam</h2>
 *
 * <p>There is no legacy Forge/ASM hook to port: 1.12.2 delegated capture timing
 * to the external Minema mod, whose {@code TimeManipulator} replaced
 * {@code Minecraft.timer} — a field that on 1.12.2 drove <i>both</i> sides,
 * because the integrated server's tick loop and the client's render loop
 * consumed the same {@code Timer.elapsedTicks}. 1.20.4 split them:
 * {@code RenderTickCounter} paces the client, and {@code MinecraftServer.runServer}
 * paces the server off its own {@code Util.getMeasuringTimeMs()} loop. One legacy
 * hook therefore becomes two modern seams, and this is the second.</p>
 *
 * <p><b>Why wrap the {@code MinecraftServer.tick} call, and not something
 * else.</b> {@code IntegratedServer.tick(BooleanSupplier)} contains exactly one
 * {@code invokespecial MinecraftServer.tick(BooleanSupplier)} (verified with
 * {@code javap -c} against the loom named jar, offset 113), and that call
 * <i>is</i> one server tick: everything before it in the method is
 * integrated-server bookkeeping that must keep happening once per pass at wall
 * speed (reading {@code MinecraftClient.isPaused}, the pause autosave, the
 * time-update packets, the view/simulation-distance sync). Wrapping the call
 * alone is what lets the tick count vary while that bookkeeping does not.
 * Cancelling {@code tick} at HEAD instead would run <b>at most</b> one tick per
 * pass, which starves the clock whenever the render rate outruns wall time
 * (240 fps into a 30 fps video advances game time 8× faster than the server loop
 * iterates); and it would also skip the bookkeeping. Redirecting the vanilla
 * server loop's sleep would trade a bounded, per-tick decision for a global
 * timing change.</p>
 *
 * <p>BBS solves the same problem at the same call
 * ({@code mchorse.bbs_mod.mixin.client.IntegratedServerMixin}) — the sanctioned
 * modern-API reference. The technique is shared; the accounting is not. BBS keeps
 * its {@code lastServerTicks} counter as a public field on the recorder and does
 * the {@code while (last < total)} bookkeeping inside the mixin. Here the debt
 * lives in the GL-free timing core ({@code CaptureTiming.claimServerTicks()},
 * reached through {@link CaptureClock#claimServerTicks()}), so it is headlessly
 * testable exactly like the rest of P199 and this class stays a thin adapter with
 * no state of its own.</p>
 *
 * <h2>Edges</h2>
 *
 * <ul>
 * <li><b>Not recording</b> — {@link CaptureClock#claimServerTicks()} answers
 * {@link CaptureClock#NOT_CAPTURING} and the original call is made once,
 * unchanged. A strict no-op: no allocation, no extra call, no behaviour drift.
 * This is also the state a dedicated server would be in, though it can never
 * reach this class at all — {@code IntegratedServer} is client-only, so the
 * mixin lives in {@code blockbuster.client.mixins.json}.</li>
 * <li><b>Started mid-tick</b> — {@code CaptureClock.start} builds a fresh timing
 * core with both counters at 0, so the first pass after the start claims 0 and
 * the world waits for the first captured frame rather than inheriting a debt
 * from wall time.</li>
 * <li><b>Stopped with ticks still owed</b> — the very next claim answers
 * {@link CaptureClock#NOT_CAPTURING}, so the server is back on wall time
 * immediately and the debt is dropped rather than burst through (see
 * {@link CaptureClock#stop()}). Nothing can strand the server frozen: a frozen
 * world would require a claim of 0 forever, and only a live timing core can
 * return 0.</li>
 * <li><b>Pause menu</b> — vanilla returns from {@code tick} <i>before</i> the
 * wrapped call while {@code paused} and a network handler exists, so a paused
 * integrated server consumes nothing and this code does not run. That is
 * unreachable during a take anyway ({@code CapturePauseReachabilityTest}: a
 * capture can only be started from the camera editor, whose
 * {@code shouldPause()} is false, and closing it stops the capture).</li>
 * <li><b>Multiplayer</b> — a remote server has no client-side clock to lockstep
 * to. Recording a joined server therefore captures whatever the server sends, as
 * Minema also did; only single-player and LAN-hosted takes are frame-locked.</li>
 * </ul>
 */
@Mixin(IntegratedServer.class)
public class IntegratedServerCaptureMixin
{
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;tick(Ljava/util/function/BooleanSupplier;)V"))
    private void blockbuster$lockstepToCaptureClock(IntegratedServer server, BooleanSupplier shouldKeepTicking, Operation<Void> original)
    {
        int authorized = CaptureClock.claimServerTicks();

        if (authorized == CaptureClock.NOT_CAPTURING)
        {
            /* Not recording: one ordinary wall-clock server tick, untouched. */
            original.call(server, shouldKeepTicking);

            return;
        }

        /* Recording: exactly as many ticks as the render clock has paid for —
         * possibly none, possibly several. */
        for (int i = 0; i < authorized; i++)
        {
            original.call(server, shouldKeepTicking);
        }
    }
}
