package mchorse.aperture.capabilities;

import mchorse.aperture.capabilities.camera.ICamera;

/**
 * Mixin-attached data accessor (P181) — implemented on
 * {@code ServerPlayerEntity} by {@code ServerPlayerEntityCameraMixin}
 * (the port's replacement for Forge's {@code CameraProvider} capability
 * attachment).
 */
public interface CameraAccessor
{
    ICamera aperture$getCamera();
}
