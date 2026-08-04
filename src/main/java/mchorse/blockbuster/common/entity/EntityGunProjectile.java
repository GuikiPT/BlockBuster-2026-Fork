package mchorse.blockbuster.common.entity;

import java.util.Optional;
import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.common.GunProps;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.guns.PacketGunProjectile;
import mchorse.blockbuster.network.common.guns.PacketGunProjectileSpawnData;
import mchorse.blockbuster.network.common.guns.PacketGunProjectileVanish;
import mchorse.blockbuster.network.common.guns.PacketGunStuck;
import mchorse.metamorph.api.Morph;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.MorphUtils;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Gun projectile entity (P195).
 *
 * <p>Faithful port of 1.12.2 {@code EntityGunProjectile} (extended
 * {@code EntityThrowable implements IEntityAdditionalSpawnData}). It flies with
 * a custom raytrace + friction/gravity integration, carries a morph, and on
 * impact bounces/sticks/penetrates/damages/knocks-back/runs commands. On yarn
 * 1.20.4 it extends {@link ProjectileEntity} (technique borrowed from the BBS
 * reference; behaviour is the 1.12.2 one, not BBS's redesign).</p>
 *
 * <h2>Port deltas (parity notes)</h2>
 * <ul>
 *   <li>Forge's {@code IEntityAdditionalSpawnData} has no yarn equivalent — the
 *       server pushes a dedicated {@link PacketGunProjectileSpawnData} (byte
 *       layout mirrors the legacy {@code writeSpawnData}) to tracking players on
 *       spawn + on {@code onStartedTrackingBy}.</li>
 *   <li>{@code GunProps} still stores morphs as raw NBT (P193 deferred the real
 *       morph swap to P197); this class resolves impact morphs through
 *       {@link MorphManager} at the use site.</li>
 *   <li>The render-side morph animation update ({@code props.getEntity(this)} +
 *       dummy-actor plumbing) is a P197 concern — SEAM'd out here (server tick
 *       needs no morph animation for physics/commands).</li>
 *   <li>Nether-portal traversal on block hit is not reproduced (rare edge; the
 *       legacy {@code setPortal} call has no safe 1.20.4 analogue that we can
 *       verify headlessly) — a NETHER_PORTAL block hit is simply ignored rather
 *       than impacted. Documented delta.</li>
 * </ul>
 */
public class EntityGunProjectile extends ProjectileEntity
{
    public GunProps props;
    public AbstractMorph original;
    public Morph morph = new Morph();
    public int hits;
    public int impact;
    public boolean vanish;
    public int vanishDelay;
    public boolean stuck;

    public int ticksExisted;

    /* Syncing on the client side the position */
    public int updatePos;
    public double targetX;
    public double targetY;
    public double targetZ;

    public double initMX;
    public double initMY;
    public double initMZ;

    public boolean setInit;

    public EntityGunProjectile(EntityType<? extends EntityGunProjectile> type, World world)
    {
        super(type, world);

        this.impact = -1;
    }

    public EntityGunProjectile(World world, GunProps props, AbstractMorph morph)
    {
        this(Blockbuster.GUN_PROJECTILE, world);

        this.props = props;
        this.morph.setDirect(morph);
        this.original = this.morph.copy();

        if (props != null)
        {
            this.setHitbox(props.hitboxX, props.hitboxY);
        }
    }

    /**
     * Legacy {@code setSize(hitboxX, hitboxY)} — 1.20.4 entity dimensions are
     * immutable per type, so the runtime resize is a documented no-op seam
     * (dimensions default to 0.25×0.25 at registration). SEAM(P197): if a
     * per-instance resize proves visually necessary, override
     * {@code getDimensions()} + {@code calculateDimensions()}.
     */
    private void setHitbox(float width, float height)
    {
        this.calculateDimensions();
    }

    /**
     * Assign the shooter as this projectile's owner.
     *
     * <p>Legacy 1.12.2 did this inside its {@code shoot(Entity, …)} override,
     * unwrapping an {@code EntityActor.EntityFakePlayer} to its backing actor
     * so the actor (not the fake player) counts as the thrower. The owner is
     * load-bearing: the entity-collision sweep excludes {@code getOwner()} so
     * the shooter cannot hit their own projectile, and the thrown damage source
     * attributes death messages to the owner.</p>
     */
    public void shootFrom(Entity thrower)
    {
        /* Legacy: this.thrower = ((EntityActor.EntityFakePlayer) thrower).actor
         * — the actor, never its fake player, is the owner. */
        if (thrower instanceof EntityActor.EntityFakePlayer fake && fake.actor != null)
        {
            thrower = fake.actor;
        }

        this.setOwner(thrower);
    }

    public void setInitialMotion()
    {
        Vec3d v = this.getVelocity();

        this.initMX = v.x;
        this.initMY = v.y;
        this.initMZ = v.z;
    }

    /**
     * Server-side spawn-data broadcast — the replacement for Forge's
     * {@code writeSpawnData}. Called by {@code ItemGun} right after spawn and by
     * {@link #onStartedTrackingBy}.
     */
    public PacketGunProjectileSpawnData buildSpawnData()
    {
        NbtCompound propsTag = this.props == null ? null : this.props.toNBT();
        NbtCompound morphTag = this.morph.get() == null ? null : this.morph.toNBT();

        return new PacketGunProjectileSpawnData(this.getId(), propsTag, morphTag, this.initMX, this.initMY, this.initMZ);
    }

    /** Client-side handler entry: replicate the legacy {@code readSpawnData}. */
    public void applySpawnData(PacketGunProjectileSpawnData message)
    {
        if (message.props != null)
        {
            this.props = new GunProps(message.props);
            this.setHitbox(this.props.hitboxX, this.props.hitboxY);
        }

        if (message.morph != null)
        {
            this.morph.fromNBT(message.morph);
        }

        this.initMX = message.initMX;
        this.initMY = message.initMY;
        this.initMZ = message.initMZ;
    }

    @Override
    public void onStartedTrackingBy(ServerPlayerEntity player)
    {
        super.onStartedTrackingBy(player);

        Dispatcher.sendTo(this.buildSpawnData(), player);
    }

    @Override
    protected void initDataTracker()
    {}

    @Override
    public void tick()
    {
        this.ticksExisted++;

        if (this.vanish)
        {
            if (!this.getWorld().isClient && this.vanishDelay <= 0)
            {
                this.discard();
            }

            if (this.vanishDelay > 0)
            {
                this.vanishDelay--;
            }
        }

        if (!this.getWorld().isChunkLoaded(this.getBlockPos()))
        {
            this.discard();
        }

        if (this.getWorld().isClient && !this.setInit)
        {
            this.setInit = true;
            this.setVelocity(this.initMX, this.initMY, this.initMZ);
        }

        if (!this.stuck)
        {
            Vec3d velocity = this.getVelocity();
            Vec3d position = this.getPos();
            Vec3d next = position.add(velocity);
            Entity entity = null;

            HitResult result = this.getWorld().raycast(new RaycastContext(position, next, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));

            if (result != null && result.getType() != HitResult.Type.MISS)
            {
                next = result.getPos();
            }
            else
            {
                result = null;
            }

            if (this.props != null && !this.props.ignoreEntities)
            {
                Box searchBox = this.getBoundingBox().stretch(velocity).expand(1.0D);
                double dist = 0.0D;

                for (Entity current : this.getWorld().getOtherEntities(this, searchBox, e -> !(e instanceof EntityGunProjectile) && e != this.getOwner() && e.canHit()))
                {
                    Box box = current.getBoundingBox().expand(0.30000001192092896D);
                    Optional<Vec3d> ray = box.raycast(position, next);

                    if (ray.isPresent())
                    {
                        double d1 = position.squaredDistanceTo(ray.get());

                        if (d1 < dist || dist == 0.0D)
                        {
                            entity = current;
                            dist = d1;
                        }
                    }
                }
            }

            if (entity != null)
            {
                result = new EntityHitResult(entity);
            }

            /* Update position */
            this.setPos(position.x + velocity.x, position.y + velocity.y, position.z + velocity.z);

            if (result != null)
            {
                boolean portal = result.getType() == HitResult.Type.BLOCK
                    && this.getWorld().getBlockState(((BlockHitResult) result).getBlockPos()).isOf(Blocks.NETHER_PORTAL);

                if (!portal)
                {
                    this.onImpact(result);
                }
            }

            velocity = this.getVelocity();
            float distance = MathHelper.sqrt((float) (velocity.x * velocity.x + velocity.z * velocity.z));

            this.setYaw((float) (MathHelper.atan2(velocity.x, velocity.z) * (180D / Math.PI)));
            this.setPitch((float) (MathHelper.atan2(velocity.y, distance) * (180D / Math.PI)));
            this.setPitch(updateRotation(this.prevPitch, this.getPitch()));
            this.setYaw(updateRotation(this.prevYaw, this.getYaw()));

            float friction = this.props == null ? 1F : this.props.friction;

            if (this.isTouchingWater())
            {
                for (int j = 0; j < 4; ++j)
                {
                    this.getWorld().addParticle(ParticleTypes.BUBBLE, this.getX() - velocity.x * 0.25D, this.getY() - velocity.y * 0.25D, this.getZ() - velocity.z * 0.25D, velocity.x, velocity.y, velocity.z);
                }

                friction *= 0.8F;
            }

            if (this.isOnGround())
            {
                friction *= 0.9F;
            }

            double mx = velocity.x * friction;
            double my = velocity.y * friction;
            double mz = velocity.z * friction;

            if (!this.hasNoGravity())
            {
                my -= this.getGravityVelocity();
            }

            this.setVelocity(mx, my, mz);

            if (this.props != null && this.hits < this.props.hits)
            {
                double diff = mx * mx + my * my + mz * mz;

                if (diff < 100 * 100)
                {
                    this.noClip = this.props.ignoreBlocks;
                    this.move(MovementType.SELF, new Vec3d(mx, my, mz));
                }
                else
                {
                    this.discard();
                }
            }
            else
            {
                this.setPosition(this.getX(), this.getY(), this.getZ());
            }
        }

        this.updateProjectile();
    }

    /**
     * Legacy {@code getGravityVelocity()} — props gravity (default 0.03).
     */
    private float getGravityVelocity()
    {
        return this.props == null ? 0.03F : this.props.gravity;
    }

    private void updateProjectile()
    {
        if (this.getWorld().isClient && this.updatePos > 0)
        {
            double d0 = this.getX() + (this.targetX - this.getX()) / this.updatePos;
            double d1 = this.getY() + (this.targetY - this.getY()) / this.updatePos;
            double d2 = this.getZ() + (this.targetZ - this.getZ()) / this.updatePos;

            this.updatePos--;
            this.setPosition(d0, d1, d2);
        }

        /* P290: legacy ran this on BOTH sides, above the isRemote return —
         * it is the only thing that advances the projectile morph's
         * Animation.progress, so without it a projectile morph with "Animates"
         * ticked never animated at all. The dummy actor the morph is ticked
         * against is the same one the renderer draws with.
         *
         * Deviation, and the only one: legacy dereferenced this.props here
         * *before* the null check below, so a projectile whose props failed to
         * resolve NPE'd every tick. Guarded (CROSS_CUTTING: total readers). */
        AbstractMorph projectileMorph = this.morph.get();

        if (projectileMorph != null && this.props != null)
        {
            this.props.createEntity(this.getWorld());

            LivingEntity dummy = this.props.getEntity(this);

            if (dummy != null)
            {
                /* Legacy also assigned posX/posY/posZ here; getEntity already
                 * does exactly that (plus prevPos), so it is one call, not two. */
                projectileMorph.update(dummy);
            }
        }

        if (this.props == null || this.getWorld().isClient)
        {
            return;
        }

        if (this.ticksExisted > this.props.lifeSpan)
        {
            this.discard();

            this.executeCommand(this.props.vanishCommand);
        }

        if (this.props.ticking > 0 && this.ticksExisted % this.props.ticking == 0)
        {
            this.executeCommand(this.props.tickCommand);
        }

        if (this.impact >= 0)
        {
            if (this.impact == 0)
            {
                AbstractMorph original = MorphUtils.copy(this.original);

                this.morph.set(original);

                Dispatcher.sendToTracked(this, new PacketGunProjectile(this.getId(), original));
            }

            this.impact--;
        }
    }

    /**
     * Legacy {@code onImpact(RayTraceResult)} — bounce/stick/penetration on
     * block hits, damage/knockback/commands on entity hits, morph swap.
     */
    protected void onImpact(HitResult result)
    {
        if (this.stuck || this.vanish || this.props == null)
        {
            return;
        }

        this.hits++;

        boolean shouldDie = this.props.vanish && this.hits >= this.props.hits && !this.props.sticks;
        boolean impactMorph = false;
        boolean isBlock = result.getType() == HitResult.Type.BLOCK;
        boolean isEntity = result.getType() == HitResult.Type.ENTITY;

        if (isBlock && !this.props.ignoreBlocks)
        {
            BlockHitResult block = (BlockHitResult) result;
            Direction side = block.getSide();
            Direction.Axis axis = side.getAxis();
            float factor = (this.props.bounce && this.hits <= this.props.hits ? -1 : 0);

            Vec3d v = this.getVelocity();
            double mx = v.x;
            double my = v.y;
            double mz = v.z;

            if (axis == Direction.Axis.X) mx *= factor;
            if (axis == Direction.Axis.Y) my *= factor;
            if (axis == Direction.Axis.Z) mz *= factor;

            mx *= this.props.bounceFactor;
            my *= this.props.bounceFactor;
            mz *= this.props.bounceFactor;

            this.setVelocity(mx, my, mz);

            Vec3d hit = block.getPos();
            double px = hit.x + this.getWidth() / 2 * side.getOffsetX();
            double py = hit.y - this.getHeight() * (side == Direction.DOWN ? 1 : 0);
            double pz = hit.z + this.getWidth() / 2 * side.getOffsetZ();

            this.setPos(px, py, pz);

            if (this.props.sticks)
            {
                this.stuck = true;

                if (!this.getWorld().isClient)
                {
                    if (side == Direction.WEST || side == Direction.EAST) px += this.props.penetration * side.getOffsetX();
                    else if (side == Direction.UP || side == Direction.DOWN) py += this.props.penetration * side.getOffsetY();
                    else if (side == Direction.NORTH || side == Direction.SOUTH) pz += this.props.penetration * side.getOffsetZ();

                    this.setPos(px, py, pz);

                    Dispatcher.sendToTracked(this, new PacketGunStuck(this.getId(), (float) px, (float) py, (float) pz));
                }
            }
        }

        if (!this.getWorld().isClient)
        {
            if (isBlock && !this.props.ignoreBlocks)
            {
                if (!this.props.impactCommand.isEmpty())
                {
                    BlockHitResult block = (BlockHitResult) result;
                    String command = this.props.impactCommand;
                    int x = block.getBlockPos().getX();
                    int y = block.getBlockPos().getY();
                    int z = block.getBlockPos().getZ();

                    command = command.replaceAll("\\$\\{x\\}", String.valueOf(x));
                    command = command.replaceAll("\\$\\{y\\}", String.valueOf(y));
                    command = command.replaceAll("\\$\\{z\\}", String.valueOf(z));

                    this.executeCommand(command);
                }

                impactMorph = true;
            }

            if (isEntity && !this.props.ignoreEntities)
            {
                Entity target = ((EntityHitResult) result).getEntity();

                this.executeCommand(this.props.impactEntityCommand);

                if (this.props.damage > 0)
                {
                    if (target instanceof MobEntity)
                    {
                        MobEntity living = (MobEntity) target;

                        target.damage(this.getDamageSources().thrown(this, this.getOwner()), 0);
                        living.setHealth(living.getHealth() - this.props.damage);
                    }
                    else
                    {
                        target.damage(this.getDamageSources().thrown(this, this.getOwner()), this.props.damage);
                    }
                }

                if (this.props.knockbackHorizontal != 0 && target instanceof LivingEntity)
                {
                    Vec3d v = this.getVelocity();

                    ((LivingEntity) target).takeKnockback(Math.abs(this.props.knockbackHorizontal), -v.x, -v.z);

                    if (this.props.knockbackHorizontal < 0)
                    {
                        Vec3d tv = target.getVelocity();
                        target.setVelocity(tv.x * -1, tv.y, tv.z * -1);
                    }
                }

                Vec3d tv = target.getVelocity();
                target.setVelocity(tv.x, tv.y + this.props.knockbackVertical, tv.z);

                impactMorph = true;
            }

            if (shouldDie)
            {
                this.vanish = true;
                this.vanishDelay = this.props.vanishDelay;

                if (this.vanishDelay > 0)
                {
                    Dispatcher.sendToTracked(this, new PacketGunProjectileVanish(this.getId(), this.vanishDelay));
                }

                return;
            }

            if (impactMorph && this.props.impactDelay > 0)
            {
                AbstractMorph morph = MorphManager.INSTANCE.morphFromNBT(this.props.impactMorph);

                this.morph.set(morph);
                this.impact = this.props.impactDelay;

                Dispatcher.sendToTracked(this, new PacketGunProjectile(this.getId(), morph));
            }
        }
    }

    private void executeCommand(String command)
    {
        if (command != null && !command.isEmpty() && this.getServer() != null)
        {
            this.getServer().getCommandManager().executeWithPrefix(this.getCommandSource(), command);
        }
    }

    /**
     * ProjectileEntity requires this abstract hook; the legacy entity-sweep is
     * driven from {@link #tick()} → {@link #onImpact(HitResult)} directly, so
     * this delegates for parity if the base ever routes a collision here.
     */
    @Override
    protected void onEntityHit(EntityHitResult result)
    {
        this.onImpact(result);
    }

    /**
     * Don't restore the projectile from NBT — kill it immediately (legacy
     * {@code readEntityFromNBT} → {@code setDead}); a persistent projectile
     * would double-fire impact commands after a reload.
     */
    @Override
    public void readCustomDataFromNbt(NbtCompound nbt)
    {
        super.readCustomDataFromNbt(nbt);

        this.discard();
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt)
    {
        super.writeCustomDataToNbt(nbt);
    }

    /**
     * Client desync correction, gated by {@code bb_gun_sync_distance} (default
     * 0 → server corrections ignored entirely); never correct while stuck.
     */
    @Override
    public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int interpolationSteps)
    {
        if (this.stuck)
        {
            return;
        }

        double dx = this.getX() - x;
        double dy = this.getY() - y;
        double dz = this.getZ() - z;
        double dist = dx * dx + dy * dy + dz * dz;
        double syncDistance = Blockbuster.bbGunSyncDistance.get();

        if (syncDistance > 0 && dist > syncDistance * syncDistance)
        {
            this.updatePos = interpolationSteps;
            this.targetX = x;
            this.targetY = y;
            this.targetZ = z;
        }
    }
}
