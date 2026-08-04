package mchorse.aperture.camera;

import com.mojang.authlib.GameProfile;
import mchorse.aperture.Aperture;
import mchorse.aperture.ClientProxy;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/**
 * Outside (detached camera) mode handler (P177).
 *
 * Port notes: legacy spawned an {@code EntityOtherPlayerMP} named
 * {@code "Camera"} and juggled {@code renderViewEntity} across
 * FogColors/FogDensity/RenderPlayer/RenderOverlay events; on 1.20.4 a
 * client-side {@link OtherClientPlayerEntity} plus
 * {@code MinecraftClient.setCameraEntity} is honored natively by the
 * fog/particle/sky paths, so the event juggling collapses into the P178
 * mixin set.
 *
 * <p><b>S22 P252/P253 (batch V-K)</b> undid two deviations that had crept in
 * with that collapse:</p>
 * <ul>
 * <li>The camera entity is now pinned to the dummy for the whole time outside
 * mode is attached ({@link #onFrame()} re-asserts it, which is what legacy's
 * {@code FogColors} handler did every frame). It is <b>never</b> swapped for
 * {@code mc.player} on the {@code sky} option — that option owns the sky, and
 * only the sky ({@link #skyColorPosition(Vec3d)}).</li>
 * <li>The dummy is constructed but <b>never added to the client world</b>,
 * exactly as in 1.12.2, so nothing that enumerates world entities can see it.
 * See {@link #start()}.</li>
 * </ul>
 *
 * <p><b>S22 P271 (batch W-F)</b> supplied the half of {@code hide_player} that
 * 1.20.4 had put out of reach: with the toggle <b>off</b>, legacy put the local
 * player back on screen around its own draw ({@code onPreRenderPlayer}), and
 * 1.20.4 makes that decision in the {@code WorldRenderer.render} entity loop
 * where no renderer-level hook can reach it. See {@link #showsLocalPlayer()}
 * and {@link #drawsLocalPlayer(boolean, boolean, boolean, boolean)}.</p>
 *
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/camera/CameraOutside.java
 */
public class CameraOutside
{
    public boolean active;
    public OtherClientPlayerEntity camera;

    /**
     * Start outside mode: build the detached {@code "Camera"} dummy and point
     * the view at it (legacy {@code CameraOutside.start}, :49-60).
     *
     * <p><b>The dummy is deliberately not added to the world.</b> Legacy built
     * an {@code EntityOtherPlayerMP} and handed it straight to
     * {@code setRenderViewEntity} — it never reached {@code World.spawnEntity}
     * or {@code loadedEntityList}, so no tick, no render pass, no entity
     * enumeration ever saw it. The port briefly called
     * {@code mc.world.addEntity(...)}, which made it a genuine client-world
     * entity; it stayed invisible only because vanilla's
     * {@code WorldRenderer.render} skips {@code camera.getFocusedEntity()} —
     * an incidental guarantee, not a designed one, and no guarantee at all for
     * F3 entity counts, particle/AI targeting, morph selectors or other mods.
     * Nothing needs the membership: the dummy is only ever (a) the argument to
     * {@code setCameraEntity}, (b) the thing {@code CameraRunner
     * .setCameraPosition} moves instead of the player, and (c) the entity
     * {@code Camera.update} seeds position/rotation from — and all three work
     * off the entity's own fields, which the constructor's {@code ClientWorld}
     * reference already backs.</p>
     */
    public void start()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null || mc.world == null || mc.player == null)
        {
            return;
        }

        this.camera = new OtherClientPlayerEntity(mc.world, new GameProfile(UUID.randomUUID(), "Camera"));
        this.camera.refreshPositionAndAngles(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYaw(), mc.player.getPitch());

        mc.setCameraEntity(this.camera);

        this.active = true;
    }

    public void stop()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc != null)
        {
            if (this.camera != null)
            {
                this.camera.discard();
            }

            mc.setCameraEntity(mc.player);
        }

        this.camera = null;
        this.active = false;
    }

    /**
     * Per-frame re-assertion that the detached camera is the view entity
     * (legacy {@code CameraOutside.onFogColor}, :78-85, which ran
     * {@code this.mc.setRenderViewEntity(this.camera)} unconditionally on every
     * {@code FogColors} event — i.e. every frame, before anything that keys off
     * the view entity rendered).
     *
     * <p>Called from {@code ApertureClient.frame} rather than from the runner:
     * outside mode is attached and detached independently of playback
     * ({@code GuiConfigCameraOptions}' live toggle, the camera editor, and
     * Blockbuster's {@code CameraHandler.attachOutside} all attach it with the
     * runner stopped), which is exactly the split legacy had — the handler was
     * registered by {@code start()}, not by the runner.</p>
     *
     * <p>Identity-guarded because {@code setCameraEntity} is not free:
     * {@code GameRenderer.onCameraEntitySet} closes and drops the active
     * post-effect processor on every call.</p>
     */
    public void onFrame()
    {
        if (!this.active || this.camera == null)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc != null && mc.getCameraEntity() != this.camera)
        {
            mc.setCameraEntity(this.camera);
        }
    }

    /* --------------------------------------------------------------------- */
    /* `outside → sky` (legacy Aperture.outsideSky)                           */
    /* --------------------------------------------------------------------- */

    /**
     * Position the sky/fog colour is sampled at, given the one vanilla would
     * have used (the render camera's).
     *
     * <p><b>Legacy mechanism.</b> 1.12.2's {@code EntityRenderer.updateFogColor}
     * ran {@code world.getSkyColor(this.mc.getRenderViewEntity(), partialTicks)}
     * and {@code CameraRunner.onRenderTick} (:236-239) had just set that entity
     * to {@code outsideSky ? camera : mc.player}. The swap survived exactly one
     * call: {@code CameraOutside.onFogColor}, the {@code FogColors} handler
     * posted at the <i>end</i> of the same method, put the view entity back to
     * the camera before the world (sky plane included) was drawn. So the option
     * moved one input — where the fog/clear colour is sampled — and could never
     * reach player visibility, which the view entity also drove.</p>
     *
     * <p><b>Port.</b> 1.20.4 samples the sky colour from a <i>position</i>
     * ({@code ClientWorld.getSkyColor(Vec3d, float)}, biome-sampled at that
     * point), and the position {@code BackgroundRenderer.render} passes is
     * {@code camera.getPos()} — which the P178 {@code CameraMixin} has already
     * overridden to the fixture position. Swapping the camera <i>entity</i>
     * therefore cannot move the sky on 1.20.4; all it moves is which entity
     * {@code WorldRenderer} skips. The option is honoured where it actually
     * lives instead: {@code BackgroundRendererOutsideSkyMixin} substitutes this
     * position into that one call, the same single call site legacy's swap
     * reached.</p>
     */
    public static Vec3d skyColorPosition(Vec3d cameraPos)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        CameraRunner runner = ClientProxy.runner;

        if (mc == null || mc.player == null || runner == null)
        {
            return cameraPos;
        }

        return skyFollowsPlayer(runner.outside.active, Aperture.outsideSky.get())
            ? mc.player.getPos()
            : cameraPos;
    }

    /**
     * Pure core of {@link #skyColorPosition(Vec3d)} (house precedent:
     * {@link #shouldHidePlayer(boolean, boolean, boolean)}).
     *
     * <p>{@code sky} on (legacy default, {@code Aperture.java:158}) means "the
     * detached camera drives the sky", which on 1.20.4 is what vanilla already
     * does — nothing to substitute. Only {@code sky} off, and only while
     * outside mode is attached, pulls the sample back to the player.</p>
     */
    public static boolean skyFollowsPlayer(boolean outsideActive, boolean skyFromCamera)
    {
        return outsideActive && !skyFromCamera;
    }

    /* --------------------------------------------------------------------- */
    /* `outside → hide_player` (legacy Aperture.outsideHidePlayer)            */
    /* --------------------------------------------------------------------- */

    /**
     * Whether the local player's own model must be suppressed this frame.
     *
     * <p><b>Legacy mechanism.</b> 1.12.2 never "hid" the player explicitly:
     * {@code RenderPlayer.doRender} only drew the client's own player when
     * {@code renderManager.renderViewEntity == entity}
     * (RenderPlayer.java:60), and outside mode had already pointed that field
     * at the detached {@code "Camera"} entity — so the local player fell out of
     * the frame <i>by default</i>. What legacy's {@code RenderPlayerEvent.Pre
     * /Post} handlers did (CameraOutside.java:124 and :136) was the
     * <b>opposite</b>: with {@code hide_player} <b>off</b> they briefly put
     * {@code renderViewEntity} back to {@code mc.player} around the player's
     * own draw so it <i>would</i> render, restoring the camera entity
     * afterwards. {@code CameraRenderer.java:111}'s {@code thirdPersonView = 1}
     * force is the same "keep me visible" half, and needs no port: 1.20.4 does
     * not route the local player's visibility through a first/third-person
     * flag the way {@code RenderGlobal.renderEntities} did.</p>
     *
     * <p><b>Port.</b> 1.20.4's {@code WorldRenderer.render} entity loop keeps
     * legacy's default without any help from us — {@code !(entity instanceof
     * ClientPlayerEntity) || camera.getFocusedEntity() == entity} is the exact
     * modern shape of the {@code RenderPlayer.doRender} guard, and the focused
     * entity is the detached camera. This hook is therefore <b>defence in
     * depth</b> rather than the mechanism: it catches any path that reaches
     * {@code PlayerEntityRenderer.render} with {@code mc.player} without going
     * through that loop. The mechanism for the <i>other</i> half — putting the
     * player back when {@code hide_player} is off, legacy's
     * {@code onPreRenderPlayer} — is {@link #showsLocalPlayer()} (P271), and the
     * two are disjoint by construction: this one needs the toggle <b>on</b>,
     * that one needs it <b>off</b>.</p>
     */
    public static boolean hidesPlayer(PlayerEntity player)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        CameraRunner runner = ClientProxy.runner;

        return shouldHidePlayer(
            runner != null && runner.outside.active,
            Aperture.outsideHidePlayer.get(),
            mc != null && player != null && player == mc.player);
    }

    /**
     * Pure core of {@link #hidesPlayer(PlayerEntity)}, extracted so the
     * config → render-decision path is headless-testable (same shape as
     * {@code RenderActor.shouldRender(boolean, boolean)}).
     *
     * <p>Only the client's own player is ever affected — legacy's handlers were
     * gated on {@code event.getEntityPlayer() == this.mc.player}, so other
     * players and actors keep rendering — and only while outside mode is
     * actually attached, which the runner does for the duration of playback
     * ({@code CameraRunner.attachOutside}/{@code detachOutside}). Together that
     * is legacy's "whether player should be hidden during playback of the
     * camera if outside mode is enabled".</p>
     */
    public static boolean shouldHidePlayer(boolean outsideActive, boolean hidePlayer, boolean localPlayer)
    {
        return outsideActive && hidePlayer && localPlayer;
    }

    /* --------------------------------------------------------------------- */
    /* `outside → hide_player` OFF: putting the player back (P271)            */
    /* --------------------------------------------------------------------- */

    /**
     * Live entry for the two redirects that restore legacy's
     * {@code hide_player} <b>off</b> behaviour: is the client's own player being
     * force-drawn from the detached camera this frame?
     *
     * <p><b>This is the hot one.</b> It is read from inside
     * {@code WorldRenderer.render} and {@code LivingEntityRenderer.hasLabel},
     * so it must cost nothing in the overwhelmingly common state — outside mode
     * not attached. It is three plain field loads
     * ({@code ClientProxy.runner} → {@code outside} → {@code active}) and a
     * branch; the config {@code Value} is only touched once outside mode is
     * actually attached, which is exactly how legacy ordered it (the handlers
     * that read {@code Aperture.outsideHidePlayer} were only <i>registered</i>
     * while outside mode was attached — {@code CameraOutside.start()}
     * :59). No map lookup, no config parse, no {@code MinecraftClient}
     * dereference on the off path.</p>
     */
    public static boolean showsLocalPlayer()
    {
        CameraRunner runner = ClientProxy.runner;

        return runner != null && runner.outside.active && !Aperture.outsideHidePlayer.get();
    }

    /**
     * Pure core of {@link #showsLocalPlayer()} — legacy's
     * {@code onPreRenderPlayer} gate, minus the {@code == mc.player} test that
     * the injection sites make structurally (both of them are only reached for
     * the client's own player).
     */
    public static boolean forcesLocalPlayer(boolean outsideActive, boolean hidePlayer)
    {
        return outsideActive && !hidePlayer;
    }

    /**
     * The whole player-visibility decision, as pure logic: <b>is the client's
     * own player model drawn this frame?</b>
     *
     * <p>This is vanilla's {@code WorldRenderer.render} clause with the P271
     * substitution folded in, specialised to {@code entity == mc.player}
     * (verified against the real client jar by
     * {@code CameraOutsideShowPlayerTest}):</p>
     *
     * <pre>(entity != focused || thirdPerson || focused.isSleeping())
     *   &amp;&amp; (!(entity instanceof ClientPlayerEntity) || focused == entity)</pre>
     *
     * <p>While outside mode is attached the focused entity is the detached
     * {@code "Camera"} dummy, never the player, so the first clause is
     * unconditionally true and the <b>F5 view drops out of the decision
     * entirely</b>. That is legacy's behaviour too, by a different route:
     * 1.12.2 decided this in {@code RenderPlayer.doRender}'s
     * {@code renderManager.renderViewEntity == entity} test, and Aperture
     * overwrote {@code gameSettings.thirdPersonView} itself every frame
     * ({@code CameraOutside.onFogColor} :81 forced 0,
     * {@code CameraRenderer.onCameraOrient} :113 forced 1 during playback), so
     * the F5 key could not reach the outcome there either.</p>
     *
     * @param outsideActive  outside mode attached — the "camera mode" input
     * @param hidePlayer     {@code Aperture.outsideHidePlayer}
     * @param thirdPerson    {@code camera.isThirdPerson()} — the F5 view
     * @param focusedSleeping the focused entity is a sleeping {@code LivingEntity}
     */
    public static boolean drawsLocalPlayer(boolean outsideActive, boolean hidePlayer, boolean thirdPerson, boolean focusedSleeping)
    {
        /* Outside mode attached ⇒ the focused entity is the dummy, not us. */
        boolean focusedIsPlayer = !outsideActive;

        boolean notTheFirstPersonSkip = !focusedIsPlayer || thirdPerson || focusedSleeping;
        boolean clientPlayerClause = focusedIsPlayer || forcesLocalPlayer(outsideActive, hidePlayer);

        return notTheFirstPersonSkip && clientPlayerClause;
    }
}
