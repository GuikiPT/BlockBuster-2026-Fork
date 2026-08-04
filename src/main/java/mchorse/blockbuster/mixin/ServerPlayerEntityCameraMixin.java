package mchorse.blockbuster.mixin;

import mchorse.aperture.capabilities.CameraAccessor;
import mchorse.aperture.capabilities.camera.Camera;
import mchorse.aperture.capabilities.camera.ICamera;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bundled Aperture per-player camera "capability" (S15 P181): replaces the
 * Forge {@code CameraProvider}/{@code AttachCapabilitiesEvent} attachment
 * with mixin-attached data. Persists only the legacy {@code "Profile"}
 * string, wrapped in the namespaced container key {@code "aperture:camera"}
 * (S4 attached-data convention — container namespaced, key name inside
 * preserved); the timestamp stays runtime-only, exactly like legacy
 * {@code CameraStorage}.
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityCameraMixin implements CameraAccessor
{
    @Unique
    private final Camera aperture$camera = new Camera();

    @Override
    public ICamera aperture$getCamera()
    {
        return this.aperture$camera;
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void aperture$writeCamera(NbtCompound nbt, CallbackInfo info)
    {
        nbt.put("aperture:camera", this.aperture$camera.serializeNBT());
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void aperture$readCamera(NbtCompound nbt, CallbackInfo info)
    {
        if (nbt.contains("aperture:camera", NbtElement.COMPOUND_TYPE))
        {
            this.aperture$camera.deserializeNBT(nbt.getCompound("aperture:camera"));
        }
    }
}
