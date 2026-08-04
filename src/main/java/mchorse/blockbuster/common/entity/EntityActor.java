package mchorse.blockbuster.common.entity;

import com.mojang.authlib.GameProfile;
import javax.vecmath.Vector3d;
import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.client.render.IRenderLast;
import mchorse.blockbuster.client.render.RenderLastSort;
import mchorse.blockbuster.common.GuiHandler;
import mchorse.blockbuster.common.item.ItemActorConfig;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.PacketActorSpawnData;
import mchorse.blockbuster.network.common.PacketModifyActor;
import mchorse.blockbuster.network.common.recording.PacketSyncTick;
import mchorse.blockbuster.recording.RecordPlayer;
import mchorse.blockbuster.recording.data.Frame;
import mchorse.blockbuster.recording.data.Record;
import mchorse.metamorph.api.Morph;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.MorphUtils;
import mchorse.metamorph.api.models.IMorphProvider;
import mchorse.metamorph.api.morphs.AbstractMorph;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Queue;

/**
 * Actor entity (P93, first slice) — the no-AI puppet that recordings play
 * back on. Port of 1.12.2 {@code common/entity/EntityActor.java}
 * (extends {@code EntityCreature} → {@link PathAwareEntity}).
 *
 * <p>Landed in this slice: registration surface (see
 * {@code Blockbuster.registerContent}), the no-AI/no-despawn puppet
 * behavior, the legacy entity-NBT key set ({@code Morph} written only when
 * non-empty, {@code Invisible}, {@code EnableBurning}, {@code WasAttached})
 * and the default {@code blockbuster.steve} morph on initial spawn.</p>
 *
 * <p>Deferred (marked where relevant):
 * the frame-driven fall-damage/
 * footstep logic arrive with RecordPlayer (P110/P119) — the body-yaw
 * override ({@code turnHead}) and spawn-frame alignment ({@code baseTick})
 * are in; riding +
 * item-vacuum + config gates (`actor_tracking_range`, `actor_fall_damage`,
 * …) complete in P93 proper once S1 config values exist; spawn-data
 * networking waits on S2.</p>
 */
public class EntityActor extends PathAwareEntity implements IMorphProvider, IRenderLast
{
    /**
     * Skips model rendering (legacy actor config "invisible" flag; not
     * vanilla invisibility).
     */
    public boolean invisible = false;

    /** Legacy: actors only visually burn when this is enabled. */
    public boolean enableBurning = true;

    /** Whether this actor was attached to a director block / scene. */
    public boolean wasAttached = false;

    /**
     * Whether this actor should be rendered in the "render last" (translucent
     * pass) phase. Runtime-only flag applied by {@link mchorse.blockbuster.recording.scene.Replay#apply}
     * (P130); not persisted in entity NBT, exactly like 1.12.2.
     */
    public boolean renderLast = false;

    /**
     * Legacy {@code EntityActor.getRenderLastPos} (1.12.2 lines 148-153): the
     * <b>partial-tick-lerped</b> position, i.e. where the actor is being drawn
     * this frame, not where it was at the last tick boundary.
     *
     * <p>Legacy reached for {@code Minecraft.getRenderPartialTicks()} inside the
     * method because its comparator passed nothing; the port takes them as a
     * parameter (see {@link IRenderLast}). Lerping between {@code prevX} and
     * {@code getX()} is legacy-exact: note that vanilla's own entity draw lerps
     * {@code lastRenderX} instead, and the tail pass reproduces <i>that</i> for
     * the draw. Legacy sorted on one pair of fields and drew with the other, and
     * so does this — the two only diverge for an entity teleported mid-tick.</p>
     */
    @Override
    public Vector3d getRenderLastPos(float partialTicks)
    {
        return RenderLastSort.lerpPos(
            this.prevX, this.getX(), this.prevY, this.getY(), this.prevZ, this.getZ(), partialTicks);
    }

    /**
     * Metamorph's morph for this actor (legacy NBT key {@code Morph}).
     *
     * <p>The {@link Morph} wrapper is load-bearing rather than a convenience:
     * {@link Morph#set} returns {@code false} when the incoming morph
     * <em>merged</em> into the live instance ({@code canMerge}), which is what
     * keeps an actor's animation state continuous across a morph switch, while
     * {@link Morph#setDirect} replaces the instance outright. Legacy picks
     * between them deliberately at every call site and the port does the
     * same.</p>
     */
    public Morph morph = new Morph();

    /**
     * Paused-morph state (legacy {@code pauseOffset}/{@code pausePreviousMorph}/
     * {@code pausePreviousOffset}/{@code forceMorph}) — driven by the P167
     * {@code MorphAction.applyWithOffset} scrub/seek path
     * ({@link #morphPause}/{@link #applyPause}). {@code forceMorph} also records
     * whether the current morph was applied forcibly (skip the merge machinery).
     */
    public int pauseOffset = -1;
    public AbstractMorph pausePreviousMorph;
    public int pausePreviousOffset = -1;
    public boolean forceMorph;

    /**
     * Incoming {@link PacketModifyActor} queue, drained in {@link #turnHead}.
     *
     * <p>Legacy queues instead of applying on the network thread because
     * {@code applyPause} mutates the live morph, and doing that off-tick races
     * the renderer. Same reason here — the client handler enqueues, the tick
     * applies.</p>
     */
    public final Queue<PacketModifyActor> modify = new ArrayDeque<PacketModifyActor>();

    /**
     * Record player which defines the current state of the actor (position,
     * actions, etc.) — attached by {@code RecordManager.play} via
     * {@code EntityUtils.setRecordPlayer}.
     */
    public RecordPlayer playback;

    /**
     * Whether the tick loop should NOT advance {@link #playback} itself
     * (BB gun / manual scrubbing, S10+).
     */
    public boolean manual = false;

    /**
     * Whether this actor is mounted — legacy hack for hacking the riding pose
     * for 3rd-party sit-able mods (CFM/Quark). Set from {@code Frame.isMounted}
     * every applied frame; consumed by the actor renderer (P119.1).
     */
    public boolean isMounted;

    /**
     * Camera-roll of the actor (recorded per frame). {@link #roll} is the
     * current value written by {@code Frame.apply}; {@link #prevRoll} is the
     * previous-frame value written by {@code Record.applyFrame} for render
     * interpolation ({@code EntityUtils.getRoll}).
     */
    public float roll;
    public float prevRoll;

    /**
     * Fake player used in some of methods like onBlockActivated to avoid
     * NullPointerException (and some math like the direction in which to open
     * the fence or something) — see {@link EntityFakePlayer}.
     *
     * <p>Legacy built this eagerly in the constructor and the port does too, but
     * behind a guard: a fake player is a whole {@code PlayerEntity}, and if its
     * construction ever fails this field stays {@code null} and every call site
     * skips the interaction rather than taking the actor down with it.</p>
     */
    public EntityFakePlayer fakePlayer;

    public EntityActor(EntityType<? extends EntityActor> type, World world)
    {
        super(type, world);

        /* Legacy canDespawn() == false */
        this.setPersistent();

        this.createFakePlayer(world);
    }

    /**
     * Legacy constructor body: {@code this.fakePlayer = new EntityFakePlayer(…);
     * this.fakePlayer.capabilities.isCreativeMode = true;}
     */
    private void createFakePlayer(World world)
    {
        try
        {
            this.fakePlayer = new EntityFakePlayer(world, this, EntityFakePlayer.profile());
            this.fakePlayer.getAbilities().creativeMode = true;
        }
        catch (Exception e)
        {
            this.fakePlayer = null;

            Blockbuster.LOGGER.warn("Failed to create an actor's fake player — actor interaction actions will be skipped", e);
        }
    }

    /**
     * Legacy convenience constructor ({@code new EntityActor(world)}) —
     * command code (P120) creates actors this way.
     */
    public EntityActor(World world)
    {
        this(Blockbuster.ACTOR, world);
    }

    /** {@link IMorphProvider} — legacy {@code getMorph()}. */
    @Override
    public AbstractMorph getMorph()
    {
        return this.morph.get();
    }

    /**
     * Legacy {@code applyModifyPacket}: drain one queued S→C modify packet.
     *
     * <p>Two branches on {@code offset}: a non-negative offset is a paused
     * (scrubbed) morph and goes through {@link #applyPause}, then — when the
     * packet was a <em>forced</em> morph — immediately clears the pause state
     * back to the sentinels it just wrote, which is how legacy makes a forced
     * switch resume playing rather than stay frozen. A negative offset is an
     * ordinary {@link #modify} with {@code notify == false} (the client must
     * not echo the broadcast back).</p>
     */
    public void applyModifyPacket(PacketModifyActor message)
    {
        this.forceMorph = message.forceMorph;

        if (message.offset >= 0)
        {
            this.invisible = message.invisible;
            this.applyPause(message.morph, message.offset, message.previous, message.previousOffset, this.forceMorph);

            if (this.forceMorph)
            {
                this.pauseOffset = -1;
                this.pausePreviousMorph = null;
                this.pausePreviousOffset = -1;
                this.forceMorph = false;
            }
        }
        else
        {
            this.modify(message.morph, message.invisible, false);
        }
    }

    /**
     * Legacy {@code modify(AbstractMorph, boolean, boolean)}: set morph +
     * invisibility and notify trackers.
     *
     * <p>The {@code forceMorph} branch is the load-bearing part:
     * {@link Morph#setDirect} bypasses the merge machinery so a forced morph
     * really replaces the instance, while the normal path uses
     * {@link Morph#set} and may keep the old instance alive (merged) for a
     * seamless animation hand-over.</p>
     */
    public void modify(AbstractMorph morph, boolean invisible, boolean notify)
    {
        if (this.forceMorph)
        {
            this.morph.setDirect(morph);
        }
        else
        {
            this.morph.set(morph);
        }

        this.invisible = invisible;

        if (!this.getWorld().isClient && notify)
        {
            this.notifyPlayers();
        }
    }

    /**
     * Legacy {@code morph(AbstractMorph, boolean force)} — the entry point the
     * P167 {@link mchorse.blockbuster.recording.actions.MorphAction} and the
     * {@link mchorse.blockbuster.recording.scene.Replay#apply} actor branch
     * (P130) drive. Clears any paused-morph state and records the force flag.
     */
    public void morph(AbstractMorph morph, boolean force)
    {
        this.pauseOffset = -1;
        this.pausePreviousMorph = null;
        this.pausePreviousOffset = -1;
        this.forceMorph = force;

        this.morph.set(morph);
    }

    /**
     * Legacy {@code morphPause}: store the data for a paused morph. Does not
     * itself run the {@link MorphUtils#pause}/{@link MorphUtils#resume}
     * machinery — that is {@link #applyPause}. Uses {@code setDirect}: a scrub
     * must land on exactly the instance the caller paused.
     */
    public void morphPause(AbstractMorph morph, int offset, AbstractMorph previous, int previousOffset, boolean resume)
    {
        this.pauseOffset = offset;
        this.pausePreviousMorph = previous;
        this.pausePreviousOffset = previousOffset;
        this.forceMorph = resume;

        this.morph.setDirect(morph);
    }

    /**
     * Legacy {@code applyPause}: store the paused state, <b>then</b> pause the
     * previous and incoming morph (and resume when requested).
     *
     * <p>The order is legacy's and it matters: the field holds the live
     * {@link AbstractMorph} instance that {@link MorphUtils#pause} mutates, so
     * storing first means the pause lands on the morph the actor is actually
     * wearing.</p>
     */
    public void applyPause(AbstractMorph morph, int offset, AbstractMorph previous, int previousOffset, boolean resume)
    {
        this.morphPause(morph, offset, previous, previousOffset, resume);

        MorphUtils.pause(previous, null, previousOffset);
        MorphUtils.pause(morph, previous, offset);

        if (resume)
        {
            MorphUtils.resume(morph);
        }
    }

    /**
     * Legacy {@code notifyPlayers()}: re-broadcast this actor's morph/state to
     * tracking clients through the record player's tracker set.
     *
     * <p>Legacy gates on {@code !manual && playback != null} — an actor with no
     * playback (or one being scrubbed by hand) has no tracker broadcast at all;
     * its state reaches clients through the spawn data instead.</p>
     */
    public void notifyPlayers()
    {
        if (!this.manual && this.playback != null)
        {
            this.playback.sendToTracked(new PacketModifyActor(this));
        }
    }

    /**
     * Legacy actors have no AI tasks at all — pure puppet.
     */
    @Override
    protected void initGoals()
    {}

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared)
    {
        return false;
    }

    /**
     * Render-distance culling parity (roadmap P86). Port of 1.12.2
     * {@code EntityActor.isInRangeToRenderDist(distance)}: the actor's visible
     * range scales with the {@code actor_rendering_range} config (default 256,
     * range 64–1024) instead of vanilla's fixed {@code 64 *
     * renderDistanceMultiplier}. The yarn override point is
     * {@code Entity.shouldRender(double)}, which the engine calls with the
     * squared eye-to-entity distance.
     *
     * <p>This is a deliberate parity fix, not a no-op: without it, actors would
     * use the vanilla range and disappear far sooner than in 1.12.2. The
     * always-render config is the <em>frustum</em> gate and lives on the
     * renderer ({@code RenderActor.shouldRender}); it and this distance gate are
     * independent, exactly as in 1.12.2 (always-render bypasses the frustum
     * check but the config range still bounds distance).</p>
     */
    @Override
    public boolean shouldRender(double distance)
    {
        double edge = this.getBoundingBox().getAverageSideLength();

        if (Double.isNaN(edge))
        {
            edge = 1.0D;
        }

        return inRenderRange(edge, Blockbuster.actorRenderingRange.get(), distance);
    }

    /**
     * Pure render-range decision (headless-testable): {@code distanceSquared <
     * (averageEdge * range)^2}. Mirrors the legacy
     * {@code isInRangeToRenderDist} formula exactly.
     *
     * @param averageEdge     the entity bounding-box average edge length
     * @param range           {@code actor_rendering_range} config value
     * @param distanceSquared squared eye-to-entity distance (as the engine
     *                        passes it to {@code shouldRender(double)})
     */
    public static boolean inRenderRange(double averageEdge, int range, double distanceSquared)
    {
        double d = averageEdge * range;

        return distanceSquared < d * d;
    }

    /**
     * Legacy: {@code isBurning()} is gated by {@code enableBurning}.
     */
    @Override
    public boolean isOnFire()
    {
        return this.enableBurning && super.isOnFire();
    }

    /**
     * Right-click behavior — port of 1.12.2 {@code processInteract} (the yarn
     * override point is {@code interactMob}). An empty hand mounts the actor;
     * an {@link ItemActorConfig} in hand opens the actor-config GUI.
     *
     * <p>The GUI open is <b>server-triggered</b> (P100): the server-side
     * {@code interactMob} calls {@link GuiHandler#open}, which sends the S2C
     * {@link mchorse.blockbuster.network.common.PacketOpenGui} — the entity id
     * rides in the GUI x coordinate ({@code getId()}), preserving the legacy
     * {@code openGui(..., ACTOR, world, getEntityId(), 0, 0)} overload. Both
     * branches return {@code SUCCESS} on the client so the interaction is
     * swallowed and forwarded to the server (matching the legacy {@code true}).</p>
     *
     * <p>P241: the empty-hand branch mounts the player onto the actor, exactly
     * like legacy — server side only, skipped entirely when
     * {@code Blockbuster.actorDisableRiding} is on, and skipped for a sneaking
     * player (that is how you right-click an actor without mounting it). The
     * click is still swallowed ({@code SUCCESS}) in every empty-hand case,
     * including the two skip paths, because legacy returned {@code true}
     * unconditionally there.</p>
     */
    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand)
    {
        ItemStack stack = player.getStackInHand(hand);

        if (stack.isEmpty())
        {
            if (shouldMount(this.getWorld().isClient, Blockbuster.actorDisableRiding.get(), player.isSneaking()))
            {
                player.startRiding(this);
            }

            return ActionResult.SUCCESS;
        }
        else if (stack.getItem() instanceof ItemActorConfig)
        {
            if (!this.getWorld().isClient)
            {
                GuiHandler.open(player, GuiHandler.ACTOR, this.getId(), 0, 0);
            }

            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
    }

    /**
     * The empty-hand riding decision of {@link #interactMob} (P241), extracted
     * so the truth table is testable headlessly — {@code MobEntity}'s
     * constructor dereferences the world, so no {@link EntityActor} instance
     * exists in a unit test.
     *
     * <p>Legacy {@code processInteract}:
     * {@code if (!this.world.isRemote && !Blockbuster.actorDisableRiding.get())
     * { if (!player.isSneaking()) player.startRiding(this); }}. All three
     * conditions are load-bearing: mounting is a server-side decision (the
     * client's own click is swallowed and forwarded), the config is the kill
     * switch a scene builder flips so actors stay un-mountable props, and
     * sneak-right-click is the escape hatch for interacting with an actor
     * without climbing on it.</p>
     *
     * @param client        {@code world.isClient} — legacy {@code world.isRemote}.
     * @param disableRiding {@code Blockbuster.actorDisableRiding}.
     * @param sneaking      {@code player.isSneaking()}.
     */
    public static boolean shouldMount(boolean client, boolean disableRiding, boolean sneaking)
    {
        return !client && !disableRiding && !sneaking;
    }

    /**
     * Drive the actor's {@link EntityFakePlayer} mirror.
     *
     * <p>1.12.2 got this for free: {@code RecordPlayer.checkAndSpawn} appended
     * the fake player to {@code world.loadedEntityList} and vanilla ticked it
     * from then on. 1.20.4 has no such list — the only way in is
     * {@code World.spawnEntity}, which would make the fake player a real,
     * networked, client-visible player — so the actor ticks it directly
     * instead. See {@link EntityFakePlayer#tick()}.</p>
     */
    @Override
    public void tick()
    {
        super.tick();

        if (this.fakePlayer != null)
        {
            this.fakePlayer.tick();
        }
    }

    /**
     * Apply the current frame on a freshly spawned client-side actor
     * (legacy {@code onEntityUpdate}) — aligns rotation and body yaw during
     * the first two client ticks so actors don't face north until the next
     * frame lands.
     */
    @Override
    public void baseTick()
    {
        boolean spawn = this.getWorld().isClient && this.age < 2;
        Record record = this.playback == null ? null : this.playback.record;

        spawn &= record != null && record.getFrame(0) != null;

        if (spawn)
        {
            this.playback.applyFrame(this.playback.tick - 1, this, true);

            Frame frame = record.getFrameSafe(this.playback.tick - record.preDelay - 1);

            if (frame.hasBodyYaw)
            {
                this.bodyYaw = frame.bodyYaw;
            }
        }

        super.baseTick();

        if (spawn && this.playback.playing)
        {
            this.playback.applyFrame(this.playback.tick, this, true);

            Frame frame = record.getFrameSafe(this.playback.tick - record.preDelay);

            if (frame.hasBodyYaw)
            {
                this.bodyYaw = frame.bodyYaw;
            }
        }
    }

    /**
     * Playback driving — legacy {@code onLivingUpdate}, which deliberately
     * <b>never calls super</b> ("You can't use super.onLivingUpdate() …
     * it will distort actor's movement (make it more laggy)"): vanilla's
     * tracker interpolation ({@code bodyTrackingIncrements} lerping
     * position <i>and rotation</i> toward the server snapshot, plus the
     * head-yaw lerp toward {@code serverHeadYaw}) fights the rotations the
     * client-side playback applies every tick. Legacy replaces it with a
     * position-only lerp and calls {@code travel} with a position
     * save/restore ("fixes weird sliding"). Deferred: fake-player mirroring.
     */
    @Override
    public void tickMovement()
    {
        if (!this.getWorld().isClient && this.playback != null && this.playback.playing && !this.manual)
        {
            int tick = this.playback.tick;

            if (this.playback.isFinished() && !this.noClip)
            {
                this.playback.stopPlaying();
            }
            else if (tick != 0 && tick % Blockbuster.recordSyncRate.get() == 0)
            {
                Dispatcher.sendToTracked(this, new PacketSyncTick(this.getId(), tick));
            }
        }

        if (this.noClip && !this.getWorld().isClient)
        {
            if (this.playback != null)
            {
                this.playback.next();
            }

            return;
        }

        this.pickUpNearByItems();

        if (this.playback != null)
        {
            if (this.manual)
            {
                this.playback.applyFrame(this.playback.tick, this, true);
                this.playback.applyAction(this.playback.tick, this, true);
                this.playback.tick++;
            }
            else
            {
                this.playback.next();
            }
        }

        /* Legacy inline tracker lerp: position only — no yaw/pitch, and no
         * head-yaw lerp at all (rotations belong to the client playback) */
        if (this.getWorld().isClient && this.bodyTrackingIncrements > 0)
        {
            double d0 = this.getX() + (this.serverX - this.getX()) / this.bodyTrackingIncrements;
            double d1 = this.getY() + (this.serverY - this.getY()) / this.bodyTrackingIncrements;
            double d2 = this.getZ() + (this.serverZ - this.getZ()) / this.bodyTrackingIncrements;

            this.bodyTrackingIncrements--;
            this.setPosition(d0, d1, d2);
        }
        else if (!this.canMoveVoluntarily())
        {
            this.setVelocity(this.getVelocity().multiply(0.98D));
        }

        Vec3d velocity = this.getVelocity();
        double motionX = Math.abs(velocity.x) < 0.005D ? 0.0D : velocity.x;
        double motionY = Math.abs(velocity.y) < 0.005D ? 0.0D : velocity.y;
        double motionZ = Math.abs(velocity.z) < 0.005D ? 0.0D : velocity.z;

        this.setVelocity(motionX, motionY, motionZ);

        this.tickHandSwing();

        /* Make foot steps sound more player-like */
        if (!this.getWorld().isClient && this.isPlaying() && this.playback.tick < this.playback.record.frames.size() - 1 && !this.isSneaking() && this.isOnGround())
        {
            Frame current = this.playback.record.frames.get(this.playback.tick);
            Frame next = this.playback.record.frames.get(this.playback.tick + 1);

            double dx = next.x - current.x;
            double dy = next.y - current.y;
            double dz = next.z - current.z;

            this.horizontalSpeed = this.horizontalSpeed + (float) Math.sqrt(dx * dx + dz * dz) * 0.32F;
            this.distanceTraveled = this.distanceTraveled + (float) Math.sqrt(dx * dx + dy * dy + dz * dz) * 0.32F;
        }

        if (this.playback != null)
        {
            double posX = this.getX();
            double posY = this.getY();
            double posZ = this.getZ();
            double prevPosX = this.prevX;
            double prevPosY = this.prevY;
            double prevPosZ = this.prevZ;

            /* Trigger pressure playback */
            this.travel(new Vec3d(this.sidewaysSpeed, this.upwardSpeed, this.forwardSpeed));

            /* Restore the position from the playback which fixes weird sliding */
            this.setPosition(posX, posY, posZ);
            this.prevX = prevPosX;
            this.prevY = prevPosY;
            this.prevZ = prevPosZ;
        }
        else
        {
            /* Trigger pressure playback */
            this.travel(new Vec3d(this.sidewaysSpeed, this.upwardSpeed, this.forwardSpeed));
        }
    }

    /**
     * Pick up nearby items (P93) — legacy {@code pickUpNearByItems}: "Taken
     * from super implementation of onLivingUpdate. You can't use
     * super.onLivingUpdate() in onLivingUpdate(), because it will distort
     * actor's movement (make it more laggy)."
     *
     * <p><b>Load-bearing quirk</b>: the search box is 1.12's directional
     * {@code AxisAlignedBB.expand(1, 0, 1)} — which only grows toward {@code +x}
     * / {@code +z} — <i>not</i> vanilla's symmetric {@code grow(1, 0, 1)}.
     * mchorse used the wrong one and actors have picked up asymmetrically ever
     * since, so this ports to {@link Box#stretch(double, double, double)} (the
     * 1.20.4 name for the directional variant) rather than "fixing" it to
     * {@code expand}.</p>
     *
     * <p>1.20.4 mapping: {@code world.getEntitiesWithinAABB(EntityItem.class,
     * box)} → {@link World#getEntitiesByClass}; {@code entityitem.isDead} →
     * {@link Entity#isRemoved()}; {@code getItem() != null} → an
     * {@code isEmpty()} check (modern stacks are never null);
     * {@code onItemPickup(item, 1)} → {@link Entity#sendPickup(Entity, int)};
     * {@code setDead()} → {@link Entity#discard()}. The pickup is
     * cosmetic/audible only — legacy never put the stack in an inventory
     * either, it just plays the pickup effect and deletes the item.</p>
     */
    private void pickUpNearByItems()
    {
        /* P277 — this one ran the OTHER way. Legacy guards on `!this.dead`,
         * and `EntityLivingBase.dead` is a different field from `Entity.isDead`:
         * it is set in onDeath() and means "the death sequence has started",
         * i.e. health has hit zero, whether or not the entity is still in the
         * world. The port had translated it to isRemoved() (removal), so a dying
         * actor went on hoovering up items for the 20 ticks before it was
         * removed. 1.20.4 kept the field, same name and same meaning
         * (LivingEntity.dead, protected, assigned in onDeath), so the 1:1 port
         * is simply `this.dead`. */
        if (this.getWorld().isClient || this.dead)
        {
            return;
        }

        for (ItemEntity item : this.getWorld().getEntitiesByClass(ItemEntity.class, pickupBox(this.getBoundingBox()), entity -> true))
        {
            if (!item.isRemoved() && !item.getStack().isEmpty() && !item.cannotPickup())
            {
                this.sendPickup(item, 1);
                item.discard();
            }
        }
    }

    /**
     * The pickup search volume — legacy's directional
     * {@code getEntityBoundingBox().expand(1.0D, 0.0D, 1.0D)}, i.e. 1.20.4's
     * {@link Box#stretch}. Extracted so the asymmetry (grow toward {@code +x}
     * and {@code +z} only) is pinned headlessly and nobody "fixes" it into a
     * symmetric {@code expand}.
     */
    static Box pickupBox(Box bounds)
    {
        return bounds.stretch(1.0D, 0.0D, 1.0D);
    }

    /**
     * Update fall state — legacy {@code updateFallState} (yarn:
     * {@code fall}). Responsible for applying fall damage on the actor:
     * {@code move(...)} overrides the {@code onGround} property wrongly on the
     * server, so during playback (with {@code actor_fall_damage}) the recorded
     * {@code frames.get(tick - 1).onGround} is forced back in before super runs
     * — otherwise actors never take (or always take) fall damage.
     */
    @Override
    protected void fall(double heightDifference, boolean onGround, BlockState state, BlockPos landedPosition)
    {
        if (!this.getWorld().isClient && Blockbuster.actorFallDamage.get() && this.playback != null && this.playback.record != null)
        {
            int tick = this.playback.getTick();

            /* Override onGround field */
            if (tick >= 1 && tick < this.playback.record.frames.size())
            {
                onGround = this.playback.record.frames.get(tick - 1).onGround;
                this.setOnGround(onGround);
            }
        }

        super.fall(heightDifference, onGround, state, landedPosition);
    }

    /**
     * Legacy {@code isPlaying()} — whether the playback is driving this
     * actor right now.
     */
    public boolean isPlaying()
    {
        return this.playback != null && this.playback.playing && !this.playback.isFinished();
    }

    /**
     * Body-yaw handling — legacy {@code updateDistance} override (yarn:
     * {@code turnHead}). During playback the recorded {@code bodyYaw} drives
     * the body directly (gated by `actor_playback_body_yaw`); otherwise this
     * rolls back to {@link net.minecraft.entity.LivingEntity}'s auto-align
     * logic, copied inline exactly like legacy did — {@code super.turnHead}
     * would hit {@code MobEntity}'s body-control variant, which legacy
     * deliberately bypassed ("much superior renderYawOffset animation").
     */
    @Override
    protected float turnHead(float bodyRotation, float distance)
    {
        boolean shouldAutoAlign = true;

        if (Blockbuster.actorPlaybackBodyYaw.get() && this.playback != null && this.playback.record != null)
        {
            Frame previous = this.playback.record.getFrame(this.playback.getTick() - 1);
            Frame frame = this.playback.getCurrentFrame();

            if (frame != null && frame.hasBodyYaw)
            {
                this.bodyYaw = frame.bodyYaw;
                this.prevBodyYaw = previous == null || !this.playback.playing ? frame.bodyYaw : previous.bodyYaw;

                shouldAutoAlign = false;
            }
        }

        if (shouldAutoAlign)
        {
            float tempBodyYaw = MathHelper.wrapDegrees(bodyRotation - this.bodyYaw);
            this.bodyYaw += tempBodyYaw * 0.3F;
            tempBodyYaw = MathHelper.wrapDegrees(this.getYaw() - this.bodyYaw);
            boolean isBackwards = tempBodyYaw < -90.0F || tempBodyYaw >= 90.0F;

            if (tempBodyYaw < -75.0F)
            {
                tempBodyYaw = -75.0F;
            }

            if (tempBodyYaw >= 75.0F)
            {
                tempBodyYaw = 75.0F;
            }

            this.bodyYaw = this.getYaw() - tempBodyYaw;

            if (tempBodyYaw * tempBodyYaw > 2500.0F)
            {
                this.bodyYaw += tempBodyYaw * 0.2F;
            }

            if (isBackwards)
            {
                distance *= -1.0F;
            }
        }

        /* Legacy explanation, kept verbatim in intent: "Why do we update morph
         * here? Because for some reason the EntityMorph morphs don't turn
         * smoothly in onLivingUpdate method" */
        AbstractMorph morph = this.morph.get();

        if (morph != null)
        {
            morph.update(this);
        }

        while (!this.modify.isEmpty())
        {
            this.applyModifyPacket(this.modify.poll());
        }

        return distance;
    }

    /**
     * Legacy {@code onInitialSpawn}: freshly spawned actors default to the
     * {@code blockbuster.steve} morph.
     */
    @Override
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, @Nullable EntityData entityData, @Nullable NbtCompound entityNbt)
    {
        NbtCompound tag = new NbtCompound();

        tag.putString("Name", "blockbuster.steve");
        this.morph.setDirect(MorphManager.INSTANCE.morphFromNBT(tag));

        return super.initialize(world, difficulty, spawnReason, entityData, entityNbt);
    }

    /**
     * Push the spawn payload to a player that just started tracking this actor
     * — the port's replacement for Forge's {@code writeSpawnData}, which yarn
     * has no equivalent of.
     *
     * <p><b>P277 — the orphan sweep, restored.</b> Legacy opened
     * {@code writeSpawnData} with</p>
     * <pre>if (this.wasAttached &amp;&amp; this.playback == null) this.setDead();</pre>
     * <p>and that line is load-bearing, not a curiosity. {@code wasAttached} is
     * set by {@code Scene.collectActors} on every actor a scene creates, and it
     * is <b>persisted</b> ({@code WasAttached} in
     * {@link #writeCustomDataToNbt}) while {@link #playback} — a live
     * {@code RecordPlayer} — is not. So the pair means exactly "this entity was
     * spawned by a scene, and it has come back from disk with no scene driving
     * it": an orphan, and the first player to track it kills it. That is how
     * 1.12.2 got away with never despawning actors on shutdown.</p>
     *
     * <p>The port dropped the check with a comment claiming it "moved to the
     * scene lifecycle" — it had not moved anywhere; no other file in the tree
     * ever read {@code wasAttached}. Combined with a shutdown path that never
     * stopped scenes, that made scene actors <i>permanent</i>: saved into the
     * world on quit and immortal on every rejoin thereafter.
     * {@code SceneManager.stopAll()} now stops them being written in the first
     * place, but this stays as the recovery leg — it is the only thing that
     * cleans an actor out of a world saved by a pre-P277 build, or by a crash
     * or {@code kill -9} that never reached {@code SERVER_STOPPING} at all.</p>
     *
     * <p>Safe against the live case: {@code collectActors} hands the actor to
     * {@code RecordManager.play}, which assigns {@link #playback} <i>before</i>
     * {@code RecordPlayer.checkAndSpawn} ever adds it to the world, so a
     * freshly cast actor is never tracked with a null playback.</p>
     */
    @Override
    public void onStartedTrackingBy(ServerPlayerEntity player)
    {
        super.onStartedTrackingBy(player);

        if (this.wasAttached && this.playback == null)
        {
            this.discard();
        }

        Dispatcher.sendTo(new PacketActorSpawnData(this), player);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound tag)
    {
        super.readCustomDataFromNbt(tag);

        this.morph.setDirect(MorphManager.INSTANCE.morphFromNBT(tag.getCompound("Morph")));
        this.invisible = tag.getBoolean("Invisible");
        this.enableBurning = tag.getBoolean("EnableBurning");
        this.wasAttached = tag.getBoolean("WasAttached");

        if (!this.getWorld().isClient)
        {
            this.notifyPlayers();
        }
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound tag)
    {
        super.writeCustomDataToNbt(tag);

        if (!this.morph.isEmpty())
        {
            tag.put("Morph", this.morph.get().toNBT());
        }

        tag.putBoolean("Invisible", this.invisible);
        tag.putBoolean("EnableBurning", this.enableBurning);
        tag.putBoolean("WasAttached", this.wasAttached);
    }

    /**
     * Fake player used in some of methods like onBlockActivated to avoid
     * NullPointerException (and some math like the direction in which to open
     * the fence or something).
     *
     * <p>1:1 port of 1.12.2's {@code EntityActor.EntityFakePlayer}, kept as a
     * nested class of {@link EntityActor} exactly like legacy. It is
     * <b>never</b> added to the world: it exists only so the player-only
     * interaction API ({@code PlayerEntity#interact}, {@code Item#use},
     * {@code Item#useOnBlock}, {@code BlockState#onUse}, gun shooting) has a
     * player to run against while an actor replays a recording.</p>
     *
     * <h2>1.12.2 → 1.20.4 deltas</h2>
     * <ul>
     * <li>Legacy mutated {@code player.width}/{@code height}/{@code eyeHeight}
     * directly. Those are immutable/derived on 1.20.4, so the mirror is a
     * <b>pose override</b>: {@link #getDimensions(EntityPose)} and
     * {@link #getActiveEyeHeight(EntityPose, EntityDimensions)} defer to the
     * backing actor, and {@link net.minecraft.entity.Entity#calculateDimensions()}
     * republishes them. Both overrides are null-guarded because
     * {@code Entity}'s own constructor calls them before {@link #actor} is
     * assigned.</li>
     * <li>Legacy got its per-tick mirror for free by appending the fake player
     * to {@code world.loadedEntityList} in {@code RecordPlayer.checkAndSpawn}.
     * There is no such list on 1.20.4 (adding an entity means spawning and
     * networking it, which is exactly what must not happen), so
     * {@link EntityActor#tick()} drives {@link #tick()} instead. Consequence:
     * the mirror runs on both sides and from the actor's first tick, where
     * legacy only ran it server-side after {@code checkAndSpawn}. The body is
     * pure state mirroring, so this is strictly more consistent.</li>
     * <li>{@code displayGUIChest}/{@code closeScreen} become
     * {@link #openHandledScreen(NamedScreenHandlerFactory)} /
     * {@link #closeHandledScreen()}. Vanilla's {@code PlayerEntity} default for
     * the former is "do nothing"; legacy built a real container on the fake
     * player, so the port does too (the actor can open a chest and
     * {@code CloseContainerAction} can close it again).</li>
     * <li>{@code readFromNBT → setDead()} / {@code writeToNBT → no-op} become
     * {@link #readNbt(NbtCompound)} / {@link #writeNbt(NbtCompound)}: a fake
     * player must never be restored from or written to disk.</li>
     * </ul>
     *
     * <p>Legacy quirk kept verbatim: {@link #isCreative()} returns {@code false}
     * even though {@link EntityActor#EntityActor} switches the abilities'
     * {@code creativeMode} flag on. Interaction code branches on both, and the
     * combination is what 1.12.2 shipped.</p>
     */
    public static class EntityFakePlayer extends PlayerEntity
    {
        /**
         * Legacy fake-player name, verbatim — it is observable (scoreboards,
         * other mods' logs), so it is a compatibility surface.
         */
        public static final String NAME = "xXx_Fake_Player_420_xXx";

        /**
         * Legacy passed {@code new GameProfile(null, NAME)}. A null UUID is
         * fatal on 1.20.4 ({@code GameProfile} rejects it outright), so the
         * offline-mode UUID of the same name stands in — deterministic, and
         * derived from the one piece of identity legacy did supply.
         */
        public static GameProfile profile()
        {
            return new GameProfile(Uuids.getOfflinePlayerUuid(NAME), NAME);
        }

        public EntityActor actor;

        public EntityFakePlayer(World world, EntityActor actor, GameProfile profile)
        {
            super(world, BlockPos.ORIGIN, 0.0F, profile);

            this.actor = actor;
        }

        @Override
        public boolean isSpectator()
        {
            return false;
        }

        @Override
        public boolean isCreative()
        {
            return false;
        }

        /**
         * Legacy {@code onUpdate} — deliberately does <b>not</b> call super:
         * the fake player must not run any vanilla player tick logic, it only
         * mirrors the actor.
         */
        @Override
        public void tick()
        {
            if (this.actor == null)
            {
                return;
            }

            if (this.actor.isRemoved())
            {
                this.discard();

                return;
            }

            /* width/height/eyeHeight (legacy field writes) → republish the
             * overridden dimensions, then take the actor's box verbatim.
             * setPosition rebuilds the box from the dimensions, so the box
             * assignment has to come last to reach legacy's end state. */
            this.calculateDimensions();

            if (this.actor.getVehicle() != this)
            {
                this.setPosition(this.actor.getX(), this.actor.getY(), this.actor.getZ());
            }

            this.setBoundingBox(this.actor.getBoundingBox());

            this.setYaw(this.actor.getYaw());
            this.setPitch(this.actor.getPitch());

            /* Legacy compared with Objects.equals, and ItemStack overrides
             * neither equals() in 1.12.2 nor in 1.20.4 — so this is an identity
             * check on both, and the copy runs whenever the actor swapped
             * instances. Kept as-is rather than "fixed" into areEqual. */
            if (!Objects.equals(this.getEquippedStack(EquipmentSlot.MAINHAND), this.actor.getMainHandStack()))
            {
                this.equipStack(EquipmentSlot.MAINHAND, this.actor.getMainHandStack());
            }

            if (!Objects.equals(this.getEquippedStack(EquipmentSlot.OFFHAND), this.actor.getOffHandStack()))
            {
                this.equipStack(EquipmentSlot.OFFHAND, this.actor.getOffHandStack());
            }
        }

        /** Pose override — the actor's hitbox, not a player's. */
        @Override
        public EntityDimensions getDimensions(EntityPose pose)
        {
            return this.actor == null ? super.getDimensions(pose) : this.actor.getDimensions(pose);
        }

        /** Pose override — legacy {@code player.eyeHeight = actor.getEyeHeight()}. */
        @Override
        public float getActiveEyeHeight(EntityPose pose, EntityDimensions dimensions)
        {
            return this.actor == null ? super.getActiveEyeHeight(pose, dimensions) : this.actor.getEyeHeight(pose);
        }

        /**
         * Legacy {@code displayGUIChest}: close whatever is open, then build the
         * container on this fake player. The sync id stays {@code 0} — nothing
         * is ever sent to a client (there isn't one), so an
         * {@code OptionalInt.empty()} return is the honest answer, exactly as
         * vanilla's non-server players report.
         */
        @Override
        public OptionalInt openHandledScreen(NamedScreenHandlerFactory factory)
        {
            if (this.currentScreenHandler != this.playerScreenHandler)
            {
                this.closeHandledScreen();
            }

            ScreenHandler handler = factory == null ? null : factory.createMenu(0, this.getInventory(), this);

            if (handler != null)
            {
                this.currentScreenHandler = handler;
            }

            return OptionalInt.empty();
        }

        /**
         * Legacy {@code closeScreen}: fire the container's close callback (which
         * is what returns held/crafting items to the world) before vanilla drops
         * the reference.
         */
        @Override
        public void closeHandledScreen()
        {
            if (this.currentScreenHandler != null)
            {
                this.currentScreenHandler.onClosed(this);
            }

            super.closeHandledScreen();
        }

        /** Legacy {@code readFromNBT} → {@code setDead()}. */
        @Override
        public void readNbt(NbtCompound nbt)
        {
            this.discard();
        }

        /** Legacy {@code writeToNBT} → returns the tag untouched. */
        @Override
        public NbtCompound writeNbt(NbtCompound nbt)
        {
            return nbt;
        }

        /** A fake player is never persisted (legacy's empty {@code writeToNBT}). */
        @Override
        public boolean shouldSave()
        {
            return false;
        }
    }
}
