package mchorse.blockbuster.mixin.client;

import mchorse.aperture.camera.CameraOutside;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * S15 P177 / S22 P271 — {@code outside → hide_player} <b>off</b> draws the
 * local player again.
 *
 * <p><b>Legacy.</b> 1.12.2 hid the client's own player in outside mode by
 * default: {@code RenderPlayer.doRender} drew it only when
 * {@code renderManager.renderViewEntity == entity}, and outside mode had pointed
 * that at the detached {@code "Camera"} dummy. With {@code hide_player} off,
 * {@code CameraOutside.onPreRenderPlayer} ({@code RenderPlayerEvent.Pre}, LOW
 * priority — legacy source :121-128) put {@code renderManager.renderViewEntity}
 * back to {@code mc.player} for the duration of that one draw, and
 * {@code onPostRenderPlayer} :133-141 restored the dummy. Forge posts
 * {@code RenderPlayerEvent.Pre} <i>before</i> the {@code isUser()} guard, which
 * is the only reason the trick worked.</p>
 *
 * <p><b>1.20.4 has no such hook.</b> The decision moved upstream, into the
 * {@code WorldRenderer.render} entity loop, and is made before any renderer is
 * reached — so the P177 cancel on {@code PlayerEntityRenderer.render} (which is
 * the {@code hide_player} <i>on</i> half) is downstream of a player vanilla has
 * already skipped and can never put one back. Read out of the loom-cache named
 * jar with {@code javap}, the loop's gate is</p>
 *
 * <pre>(entity != camera.getFocusedEntity()          // 861
 *   || camera.isThirdPerson()                       // 869
 *   || camera.getFocusedEntity() instanceof LivingEntity
 *      &amp;&amp; ((LivingEntity) camera.getFocusedEntity()).isSleeping())   // 877/888/894
 * &amp;&amp; (!(entity instanceof ClientPlayerEntity)      // 905
 *   || camera.getFocusedEntity() == entity)         // 913</pre>
 *
 * <p>With the detached camera focused, the first clause is already true for the
 * local player; the <b>second</b> is what drops it. Redirecting the
 * {@code getFocusedEntity()} call at offset 913 to {@code mc.player} makes that
 * clause pass, and the player is drawn exactly where the entity loop would have
 * drawn any other entity — same culling, same buffers, same
 * {@code regularEntityCount}. Nothing else in the frame observes the redirect;
 * the real focused entity is untouched.</p>
 *
 * <p><b>Cost.</b> This looks like an injection into the hottest loop in the game
 * and is not one. Offset 913 sits <i>inside</i> the {@code instanceof
 * ClientPlayerEntity} branch (the {@code ifeq} at 908 jumps past it), and there
 * is exactly one {@code ClientPlayerEntity} in a client world — so the handler
 * runs <b>once per frame</b>, not once per entity. When outside mode is not
 * attached it is three field loads and a branch
 * ({@link CameraOutside#showsLocalPlayer()}) before the original call.</p>
 *
 * <p><b>Why the slice and not {@code ordinal = 3}.</b> {@code getFocusedEntity}
 * is called five times in {@code render}; the one we want is the fourth. A bare
 * ordinal would silently retarget if a future Minecraft dropped a clause — it
 * would land on the unrelated fifth call and quietly change behaviour.
 * {@code LivingEntity.isSleeping()} is called exactly once in the method,
 * immediately before the clause, so slicing from it makes {@code ordinal = 0}
 * mean "the {@code getFocusedEntity} that belongs to the
 * {@code ClientPlayerEntity} clause". If that shape ever changes the slice fails
 * to apply and the mixin config (`required`/`defaultRequire: 1`) refuses to
 * load, instead of reverting the behaviour in silence.
 * {@code CameraOutsideShowPlayerTest} pins the same shape headlessly.</p>
 */
@Mixin(WorldRenderer.class)
public class WorldRendererOutsidePlayerMixin
{
    @Redirect(
        method = "render(Lnet/minecraft/client/util/math/MatrixStack;FJZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;)V",
        slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;isSleeping()Z")),
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;getFocusedEntity()Lnet/minecraft/entity/Entity;", ordinal = 0)
    )
    private Entity blockbuster$focusedEntityForLocalPlayer(Camera camera)
    {
        /* Ordered so the off path never touches the config Value or the client. */
        if (CameraOutside.showsLocalPlayer())
        {
            return MinecraftClient.getInstance().player;
        }

        return camera.getFocusedEntity();
    }
}
