package mchorse.aperture.capabilities.camera;

import mchorse.aperture.capabilities.CameraAccessor;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;

/**
 * Default implementation of {@link ICamera} (P181).
 *
 * Port notes: the Forge capability provider/storage pair collapses into
 * mixin-attached data — {@code ServerPlayerEntityCameraMixin} holds a
 * {@code Camera} instance per player and persists it into player NBT via
 * {@link #serializeNBT()}/{@link #deserializeNBT(NbtCompound)} (only the
 * legacy {@code "Profile"} string key; the timestamp is runtime-only, so a
 * server restart forgets staleness and resends — preserved behavior).
 *
 * Legacy sources:
 * .tools/legacy-src/aperture/src/main/java/mchorse/aperture/capabilities/camera/Camera.java
 * .tools/legacy-src/aperture/src/main/java/mchorse/aperture/capabilities/camera/CameraStorage.java
 */
public class Camera implements ICamera
{
    public ItemInfo camera = new ItemInfo();

    public static ICamera get(PlayerEntity player)
    {
        return player instanceof CameraAccessor ? ((CameraAccessor) player).aperture$getCamera() : null;
    }

    @Override
    public String currentProfile()
    {
        return this.camera.filename;
    }

    @Override
    public long currentProfileTimestamp()
    {
        return this.camera.timestamp;
    }

    @Override
    public boolean hasProfile()
    {
        return !this.camera.filename.isEmpty();
    }

    @Override
    public void setCurrentProfile(String filename)
    {
        this.camera.filename = filename;
    }

    @Override
    public void setCurrentProfileTimestamp(long timestamp)
    {
        this.camera.timestamp = timestamp;
    }

    /* NBT persistence (legacy CameraStorage: only "Profile" is saved) */

    public NbtCompound serializeNBT()
    {
        NbtCompound tag = new NbtCompound();

        tag.putString("Profile", this.currentProfile());

        return tag;
    }

    public void deserializeNBT(NbtCompound tag)
    {
        this.setCurrentProfile(tag.getString("Profile"));
    }

    /**
     * Item information class
     *
     * Instance of this class is responsible for storing information about a
     * file item like camera profile or recording with timestamp of when
     * it was changed.
     */
    public static class ItemInfo
    {
        public String filename;
        public long timestamp;

        public ItemInfo()
        {
            this("", -1);
        }

        public ItemInfo(String filename, long timestamp)
        {
            this.filename = filename;
            this.timestamp = timestamp;
        }
    }
}
