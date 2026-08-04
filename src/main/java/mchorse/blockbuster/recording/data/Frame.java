package mchorse.blockbuster.recording.data;

import mchorse.blockbuster.aperture.CameraHandler;
import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.blockbuster.recording.scene.Replay;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Hand;
import org.jetbrains.annotations.Nullable;

/**
 * Recording frame class — 1:1 port of the 2.7.2 per-tick state DTO
 * (roadmap P101).
 *
 * <p>Yarn 1.20.4 mapping table (legacy MCP → yarn):
 * {@code posX/Y/Z → getX()/getY()/getZ()}, {@code rotationYaw → getYaw()},
 * {@code rotationPitch → getPitch()}, {@code rotationYawHead → getHeadYaw()},
 * {@code renderYawOffset → bodyYaw} field, {@code motionX/Y/Z →
 * getVelocity()/setVelocity}, {@code isAirBorne → velocityDirty},
 * {@code isElytraFlying() → isFallFlying()}, {@code getFoodStats() →
 * getHungerManager()}, {@code inventory.currentItem →
 * getInventory().selectedSlot}, {@code experienceTotal → totalExperience}.
 * The legacy FLAGS reflection hack is replaced by an access-widened
 * {@code Entity.setFlag} (see blockbuster.accesswidener).</p>
 *
 * <p><b>S22 P294 — {@code isAirBorne} is {@code velocityDirty}, not
 * {@code velocityModified}.</b> The two are adjacent public booleans on
 * {@code Entity} and both port-compile, but they drive different halves of the
 * entity tracker, and picking the wrong one costs replay smoothness:</p>
 *
 * <pre>
 *   legacy  Entity.isAirBorne      →  Entity.velocityDirty
 *   legacy  Entity.velocityChanged →  Entity.velocityModified
 * </pre>
 *
 * <p>{@code velocityDirty} is the flag 1.12.2's {@code EntityTrackerEntry}
 * read as {@code isAirBorne} in its sync gate
 * ({@code updateCounter % updateFrequency == 0 || trackedEntity.isAirBorne ||
 * dataManager.isDirty()}, line 198, cleared at line 318) and yarn 1.20.4's
 * {@code EntityTrackerEntry.tick} reads in exactly the same position
 * ({@code trackingTick % tickInterval == 0 || entity.velocityDirty ||
 * getDataTracker().isDirty()}, cleared right after) — so an actor whose frame
 * carries {@code isAirBorne} gets a position packet <b>that tick</b>, bypassing
 * the actor's {@code trackingTickInterval(3)}. {@code velocityModified} is the
 * {@code velocityChanged} flag that only makes the server resend an
 * {@code EntityVelocityUpdateS2CPacket} — traffic 1.12.2 never generated for
 * actors (registered with {@code sendVelocityUpdates = false}) and which does
 * nothing for the client's position lerp. Verified with {@code javap -c}
 * against the loom-cache named jar; pinned by
 * {@code VelocityFlagTranslationTest}.</p>
 *
 * <p><b>Load-bearing quirk:</b> {@link #toNBT} writes {@code motionX} into
 * all three of {@code MX}/{@code MY}/{@code MZ} — old files were saved this
 * way and byte-parity depends on reproducing it.</p>
 */
public class Frame
{
    /* Position */
    public double x;
    public double y;
    public double z;

    /* Rotation */
    public float yaw;
    public float yawHead;
    public float pitch;

    public boolean hasBodyYaw;
    public float bodyYaw;

    /* Mount's data */
    public float mountYaw;
    public float mountPitch;

    public boolean isMounted;

    /* Motion */
    public double motionX;
    public double motionY;
    public double motionZ;

    /* Fall distance */
    public float fallDistance;

    /* Entity flags */
    public boolean isAirBorne;
    public boolean isSneaking;
    public boolean isSprinting;
    public boolean onGround;
    public boolean flyingElytra;

    /* Client data */
    public float roll;

    /* Active hand */
    public int activeHands;

    private int hotbarSlot;
    private int foodLevel;
    private int totalExperience;

    /* Methods for retrieving/applying state data */

    /**
     * Set frame fields from player entity.
     */
    public void fromPlayer(PlayerEntity player)
    {
        Entity mount = player.hasVehicle() ? player.getVehicle() : player;

        /* Position and rotation */
        this.x = mount.getX();
        this.y = player.hasVehicle() && player.getVehicle().getY() > player.getY() ? player.getY() : mount.getY();
        this.z = mount.getZ();

        this.yaw = player.getYaw();
        this.yawHead = player.getHeadYaw();
        this.pitch = player.getPitch();

        this.hasBodyYaw = true;
        this.bodyYaw = player.bodyYaw;

        /* Mount information */
        this.isMounted = mount != player;

        if (this.isMounted)
        {
            this.mountYaw = mount.getYaw();
            this.mountPitch = mount.getPitch();
        }

        /* Motion and fall distance */
        this.motionX = mount.getVelocity().x;
        this.motionY = mount.getVelocity().y;
        this.motionZ = mount.getVelocity().z;

        this.fallDistance = mount.fallDistance;

        /* States */
        this.isSprinting = mount.isSprinting();
        this.isSneaking = player.isSneaking();
        this.flyingElytra = player.isFallFlying();

        /* P294: legacy `mount.isAirBorne`, which is yarn `velocityDirty` — see
         * the class javadoc for why `velocityModified` is the wrong half. */
        this.isAirBorne = mount.velocityDirty;
        this.onGround = mount.isOnGround();

        /* Active hands */
        this.activeHands = player.isUsingItem() ? (player.getActiveHand() == Hand.OFF_HAND ? 2 : 1) : 0;

        if (player.getWorld().isClient)
        {
            this.fromPlayerClient(player);
        }

        this.hotbarSlot = player.getInventory().selectedSlot;
        this.foodLevel = player.getHungerManager().getFoodLevel();
        this.totalExperience = player.totalExperience;
    }

    /**
     * Legacy captured roll only for the local client player. The
     * local-player check moves into the client wiring when the S15 camera
     * lands; the seam returns 0 until then.
     */
    private void fromPlayerClient(PlayerEntity player)
    {
        this.roll = CameraHandler.getRoll();
    }

    /**
     * Apply frame properties on actor. Different actions will be made
     * depending on which side this method was invoked.
     *
     * Use second argument to force things to be cool.
     */
    public void apply(LivingEntity actor, boolean force)
    {
        this.apply(actor, null, force);
    }

    /**
     * @param actor
     * @param replay the replay that is being used for this record - used for playback configuration
     * @param force
     */
    public void apply(LivingEntity actor, @Nullable Replay replay, boolean force)
    {
        boolean isRemote = actor.getWorld().isClient;

        Entity mount = actor.hasVehicle() ? actor.getVehicle() : actor;

        /* Actors never delegate the mount transform to their vehicle — the
         * actor entity itself carries the recorded transform. */
        if (mount instanceof EntityActor)
        {
            mount = actor;
        }

        if (actor instanceof EntityActor)
        {
            EntityActor theActor = (EntityActor) actor;

            theActor.isMounted = this.isMounted;
            theActor.roll = this.roll;
        }

        /* This is most important part of the code that makes the recording
         * super smooth.
         *
         * By the way, this code is useful only on the client side, for more
         * reference see renderer classes (they use prev* and lastTick* stuff
         * for interpolation).
         */
        if (this.isMounted)
        {
            mount.prevYaw = mount.getYaw();
            mount.prevPitch = mount.getPitch();
        }

        actor.prevYaw = actor.getYaw();
        actor.prevPitch = actor.getPitch();
        actor.prevHeadYaw = actor.getHeadYaw();

        /* Inject frame's values into actor */
        if (!isRemote || force)
        {
            mount.setPosition(this.x, this.y, this.z);
        }

        /* Rotation */
        if (isRemote || force)
        {
            if (this.isMounted)
            {
                mount.setYaw(this.mountYaw);
                mount.setPitch(this.mountPitch);

                if (actor == mount)
                {
                    actor.setPosition(this.x, this.y, this.z);
                }
            }

            actor.setYaw(this.yaw);
            actor.setPitch(this.pitch);
            actor.setHeadYaw(this.yawHead);

            /* Keep the tracker's standing rotation targets on the frame just
             * applied. 1.20.4's ClientPlayNetworkHandler.onEntity re-arms the
             * rotation lerp on every position-only move packet with
             * getLerpTargetYaw()/getLerpTargetPitch() — the last rotation the
             * SERVER sent, which for playback is permanently stale (the server
             * half above deliberately skips rotation) — so tickMovement pulled
             * the head toward frame 0 every tick and the per-tick snap-back
             * read as jitter, worsening as the recording turned away from its
             * first frame. 1.12.2's handleEntityMovement passed the entity's
             * current client rotation instead, which is why legacy needed no
             * counterpart to this. Verified with javap -c against the
             * loom-cache named jar (onEntity: getLerpTargetYaw/Pitch before
             * updateTrackedPositionAndAngles). Fields access-widened. */
            if (isRemote)
            {
                actor.serverYaw = this.yaw;
                actor.serverPitch = this.pitch;
                actor.serverHeadYaw = this.yawHead;
            }
        }

        /* Motion and fall distance */
        mount.setVelocity(this.motionX, this.motionY, this.motionZ);

        mount.fallDistance = this.fallDistance;

        /* Booleans */
        if (!isRemote || force)
        {
            mount.setSprinting(this.isSprinting);
            actor.setSneaking(this.isSneaking);

            this.setFlag(actor, Entity.FALL_FLYING_FLAG_INDEX, this.flyingElytra);
        }

        /* P294: legacy `mount.isAirBorne = this.isAirBorne`. yarn's counterpart
         * is `velocityDirty` — the entity-tracker sync gate, which is what makes
         * an airborne frame push a position packet the same tick instead of
         * waiting out the actor's 3-tick tracking interval. */
        mount.velocityDirty = this.isAirBorne;
        mount.setOnGround(this.onGround);

        if (!isRemote)
        {
            if (this.activeHands > 0 && !actor.isUsingItem())
            {
                actor.setCurrentHand(this.activeHands == 1 ? Hand.MAIN_HAND : Hand.OFF_HAND);
            }
            else if (this.activeHands == 0 && actor.isUsingItem())
            {
                actor.stopUsingItem();
            }
        }

        if (actor instanceof PlayerEntity)
        {
            PlayerEntity player = (PlayerEntity) actor;
            player.getInventory().selectedSlot = this.hotbarSlot;

            if (replay != null && replay.playBackXPFood)
            {
                player.getHungerManager().setFoodLevel(this.foodLevel);
                player.addExperience(this.totalExperience - player.totalExperience);
            }
        }
    }

    /**
     * Set entity flags. Legacy located the FLAGS DataParameter by reflection;
     * the port calls the access-widened {@link Entity#setFlag}.
     */
    private void setFlag(LivingEntity actor, int i, boolean flag)
    {
        actor.setFlag(i, flag);
    }

    /**
     * Create a copy of this frame
     */
    public Frame copy()
    {
        Frame frame = new Frame();

        frame.x = this.x;
        frame.y = this.y;
        frame.z = this.z;

        frame.yaw = this.yaw;
        frame.yawHead = this.yawHead;
        frame.pitch = this.pitch;

        frame.hasBodyYaw = this.hasBodyYaw;
        frame.bodyYaw = this.bodyYaw;

        frame.isMounted = this.isMounted;

        if (frame.isMounted)
        {
            frame.mountYaw = this.mountYaw;
            frame.mountPitch = this.mountPitch;
        }

        frame.motionX = this.motionX;
        frame.motionY = this.motionY;
        frame.motionZ = this.motionZ;

        frame.fallDistance = this.fallDistance;

        frame.isAirBorne = this.isAirBorne;
        frame.isSneaking = this.isSneaking;
        frame.isSprinting = this.isSprinting;
        frame.onGround = this.onGround;
        frame.flyingElytra = this.flyingElytra;

        frame.activeHands = this.activeHands;

        frame.roll = this.roll;

        frame.hotbarSlot = this.hotbarSlot;
        frame.foodLevel = this.foodLevel;
        frame.totalExperience = this.totalExperience;

        return frame;
    }

    /* Save/load frame instance */
    public void toBytes(PacketByteBuf buf)
    {
        buf.writeDouble(this.x);
        buf.writeDouble(this.y);
        buf.writeDouble(this.z);

        buf.writeFloat(this.yaw);
        buf.writeFloat(this.yawHead);
        buf.writeFloat(this.pitch);

        buf.writeBoolean(this.hasBodyYaw);

        if (this.hasBodyYaw)
        {
            buf.writeFloat(this.bodyYaw);
        }

        buf.writeBoolean(this.isMounted);

        if (this.isMounted)
        {
            buf.writeFloat(this.mountYaw);
            buf.writeFloat(this.mountPitch);
        }

        buf.writeFloat((float) this.motionX);
        buf.writeFloat((float) this.motionY);
        buf.writeFloat((float) this.motionZ);

        buf.writeFloat(this.fallDistance);

        buf.writeBoolean(this.isAirBorne);
        buf.writeBoolean(this.isSneaking);
        buf.writeBoolean(this.isSprinting);
        buf.writeBoolean(this.onGround);
        buf.writeBoolean(this.flyingElytra);

        buf.writeByte(this.activeHands);

        buf.writeFloat(this.roll);

        buf.writeInt(this.hotbarSlot);
        buf.writeInt(this.foodLevel);
        buf.writeInt(this.totalExperience);
    }

    public void fromBytes(PacketByteBuf buf)
    {
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();

        this.yaw = buf.readFloat();
        this.yawHead = buf.readFloat();
        this.pitch = buf.readFloat();

        if (buf.readBoolean())
        {
            this.hasBodyYaw = true;
            this.bodyYaw = buf.readFloat();
        }

        this.isMounted = buf.readBoolean();

        if (this.isMounted)
        {
            this.mountYaw = buf.readFloat();
            this.mountPitch = buf.readFloat();
        }

        this.motionX = buf.readFloat();
        this.motionY = buf.readFloat();
        this.motionZ = buf.readFloat();

        this.fallDistance = buf.readFloat();

        this.isAirBorne = buf.readBoolean();
        this.isSneaking = buf.readBoolean();
        this.isSprinting = buf.readBoolean();
        this.onGround = buf.readBoolean();
        this.flyingElytra = buf.readBoolean();

        this.activeHands = buf.readByte();

        this.roll = buf.readFloat();
        this.hotbarSlot = buf.readInt();
        this.foodLevel = buf.readInt();
        this.totalExperience = buf.readInt();
    }

    /**
     * Write frame data to NBT tag. Used for saving the frame on the disk.
     *
     * Statement order matches legacy exactly — compound key insertion order
     * is part of the byte-parity contract (P118).
     */
    public void toNBT(NbtCompound tag)
    {
        tag.putDouble("X", this.x);
        tag.putDouble("Y", this.y);
        tag.putDouble("Z", this.z);

        /* Legacy bug, load-bearing: motionX lands in all three keys */
        tag.putFloat("MX", (float) this.motionX);
        tag.putFloat("MY", (float) this.motionX);
        tag.putFloat("MZ", (float) this.motionX);

        tag.putFloat("RX", this.yaw);
        tag.putFloat("RY", this.pitch);
        tag.putFloat("RZ", this.yawHead);

        if (this.hasBodyYaw)
        {
            tag.putFloat("RW", this.bodyYaw);
        }

        if (this.isMounted)
        {
            tag.putFloat("MRX", this.mountYaw);
            tag.putFloat("MRY", this.mountPitch);
        }

        tag.putFloat("Fall", this.fallDistance);

        tag.putBoolean("Airborne", this.isAirBorne);
        tag.putBoolean("Elytra", this.flyingElytra);
        tag.putBoolean("Sneaking", this.isSneaking);
        tag.putBoolean("Sprinting", this.isSprinting);
        tag.putBoolean("Ground", this.onGround);

        if (this.activeHands > 0)
        {
            tag.putByte("Hands", (byte) this.activeHands);
        }

        if (this.roll != 0)
        {
            tag.putFloat("Roll", this.roll);
        }

        tag.putInt("HotbarSlot", this.hotbarSlot);
        tag.putInt("FoodLevel", this.foodLevel);
        tag.putInt("TotalExperience", this.totalExperience);
    }

    /**
     * Read frame data from NBT tag. Used for loading frame from disk.
     */
    public void fromNBT(NbtCompound tag)
    {
        this.x = tag.getDouble("X");
        this.y = tag.getDouble("Y");
        this.z = tag.getDouble("Z");

        this.motionX = tag.getFloat("MX");
        this.motionY = tag.getFloat("MY");
        this.motionZ = tag.getFloat("MZ");

        this.yaw = tag.getFloat("RX");
        this.pitch = tag.getFloat("RY");
        this.yawHead = tag.getFloat("RZ");

        if (tag.contains("RW"))
        {
            this.hasBodyYaw = true;
            this.bodyYaw = tag.getFloat("RW");
        }

        if (tag.contains("MRX") && tag.contains("MRY"))
        {
            this.isMounted = true;
            this.mountYaw = tag.getFloat("MRX");
            this.mountPitch = tag.getFloat("MRY");
        }

        this.fallDistance = tag.getFloat("Fall");

        this.isAirBorne = tag.getBoolean("Airborne");
        this.flyingElytra = tag.getBoolean("Elytra");
        this.isSneaking = tag.getBoolean("Sneaking");
        this.isSprinting = tag.getBoolean("Sprinting");
        this.onGround = tag.getBoolean("Ground");

        if (tag.contains("Hands"))
        {
            this.activeHands = tag.getByte("Hands");
        }

        if (tag.contains("Roll"))
        {
            this.roll = tag.getFloat("Roll");
        }

        this.hotbarSlot = tag.contains("HotbarSlot") ? tag.getInt("HotbarSlot") : this.hotbarSlot;
        this.foodLevel = tag.contains("FoodLevel") ? tag.getInt("FoodLevel") : this.foodLevel;
        this.totalExperience = tag.contains("TotalExperience") ? tag.getInt("TotalExperience") : this.totalExperience;
    }

    public enum RotationChannel
    {
        HEAD_YAW,
        HEAD_PITCH,
        BODY_YAW
    }
}
