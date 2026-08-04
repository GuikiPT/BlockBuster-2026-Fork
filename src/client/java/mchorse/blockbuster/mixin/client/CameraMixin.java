package mchorse.blockbuster.mixin.client;

import mchorse.aperture.camera.data.Position;
import mchorse.aperture.client.ApertureClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * S15 P178: THE camera position/rotation override point, replacing 1.12.2's
 * {@code EntityViewRenderEvent.CameraSetup} + {@code renderViewEntity}
 * machinery. Target signature verified via javap against the loom-cache
 * named jar: {@code update(BlockView, Entity, boolean, boolean, float)},
 * {@code setPos(DDD)}, {@code setRotation(FF)}.
 *
 * <p>Also serves as the per-frame runner drive (legacy RenderTickEvent
 * Phase.START — {@code ApertureClient.frame} runs the runner before
 * reading its position). The legacy CFM world-to-texture guard (yaw-angle
 * heuristic) translates to context-flag gating: the override applies only
 * while a camera context is active, never on angle heuristics.</p>
 */
@Mixin(Camera.class)
public abstract class CameraMixin
{
    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract void setPos(double x, double y, double z);

    @Inject(method = "update", at = @At("RETURN"))
    private void blockbuster$onUpdate(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo info)
    {
        Position position = ApertureClient.frame(tickDelta);

        if (position != null)
        {
            this.setPos(position.point.x, position.point.y, position.point.z);
            this.setRotation(position.angle.yaw, position.angle.pitch);
        }
    }
}
