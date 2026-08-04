package mchorse.blockbuster.client.particles.emitter;

import mchorse.blockbuster.client.particles.components.appearance.BedrockComponentCollisionAppearance;
import mchorse.blockbuster.client.particles.components.appearance.BedrockComponentCollisionTinting;
import mchorse.mclib.math.molang.expressions.MolangExpression;
import mchorse.mclib.utils.DummyEntity;
import mchorse.mclib.utils.MathUtils;
import mchorse.mclib.utils.MatrixUtils;
import mchorse.metamorph.api.Morph;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.world.World;

import javax.vecmath.Matrix3f;
import javax.vecmath.SingularMatrixException;
import javax.vecmath.Vector3d;
import javax.vecmath.Vector3f;
import java.util.HashMap;
import java.util.Map;

/**
 * Bedrock ("Snowstorm") particle simulation state — roadmap P149.
 *
 * <p>Ported operation-for-operation from the 1.12.2 legacy engine
 * ({@code mchorse.blockbuster.client.particles.emitter.BedrockParticle}). The
 * physics integrator, collision bookkeeping, {@code softCopy} inclusion/exclusion
 * set and matrix setup are load-bearing (see S13 plan quirks); do not "fix" the
 * apparent oddities (the {@code offset * 6} inertia scale, the double {@code /20}
 * on rotation acceleration, the accelerationFactor bounce channel).</p>
 *
 * <p>Determinism: all {@code Math.random()} draws route through
 * {@link RandomProvider} so the P156 golden can seed them.</p>
 */
public class BedrockParticle
{
    /* Randoms */
    public float random1 = (float) RandomProvider.nextDouble();
    public float random2 = (float) RandomProvider.nextDouble();
    public float random3 = (float) RandomProvider.nextDouble();
    public float random4 = (float) RandomProvider.nextDouble();

    public Morph morph = new Morph();
    private DummyEntity dummy;

    /* States */
    public int age;
    public int lifetime;
    public boolean dead;
    public boolean relativePosition;
    public boolean relativeRotation;
    public boolean relativeDirection;
    public boolean relativeScale;
    public boolean relativeScaleBillboard;
    public boolean relativeAcceleration;
    public boolean realisticCollisionDrag;
    public float linearVelocity;
    public float angularVelocity;
    public boolean gravity;
    public boolean manual;

    /* Age when the particle should expire */
    private int expireAge = -1;
    /* Used to determine lifetime when expirationDelay is on */
    private int expirationDelay = -1;

    /**
     * This is used to estimate whether an object is only bouncing or lying on a surface
     *
     * CollisionTime won't work when e.g. the particle bounces of the surface and directly in the next
     * update cycle hits the same surface side, like from top of the block to bottom of the block...
     * I think this probably never happens in practice
     */
    public Vector3f collisionTime = new Vector3f(-2f, -2f, -2f);
    public HashMap<Entity, Vector3f> entityCollisionTime = new HashMap<>();
    public boolean collided;
    public int bounces;
    /**
     * Is set by collision component when there is a collision an drag should happen.
     */
    public float rotationCollisionDrag = 0;

    /**
     * For collision Appearance needed for animation
     */
    public int firstIntersection = -1;
    public boolean intersected;

    /* Rotation */
    public float rotation;
    public float initialRotation;
    public float prevRotation;

    public float rotationVelocity;
    public float rotationAcceleration;
    public float rotationDrag;

    /* for transforming into intertial systems (currently just used for inertia)*/
    public Vector3d offset = new Vector3d();
    /* Position */
    public Vector3d position = new Vector3d();
    public Vector3d initialPosition = new Vector3d();
    public Vector3d prevPosition = new Vector3d();
    public Matrix3f matrix = new Matrix3f();
    private boolean matrixSet;

    public Vector3f speed = new Vector3f();
    public Vector3f acceleration = new Vector3f();
    public Vector3f accelerationFactor = new Vector3f(1, 1, 1);
    public float drag = 0;
    /**
     * Is set by collision component when there is a collision an drag should happen.
     */
    public float dragFactor = 0;

    /* Color */
    public float r = 1;
    public float g = 1;
    public float b = 1;
    public float a = 1;

    private Vector3d global = new Vector3d();

    public BedrockParticle()
    {
        this.speed.set((float) RandomProvider.nextDouble() - 0.5F, (float) RandomProvider.nextDouble() - 0.5F, (float) RandomProvider.nextDouble() - 0.5F);
        this.speed.normalize();
        this.matrix.setIdentity();
    }

    public boolean isCollisionTexture(BedrockEmitter emitter)
    {
        return MolangExpression.isOne(emitter.scheme.getOrCreate(BedrockComponentCollisionAppearance.class).enabled) && this.intersected;
    }

    public boolean isCollisionTinting(BedrockEmitter emitter)
    {
        return MolangExpression.isOne(emitter.scheme.getOrCreate(BedrockComponentCollisionTinting.class).enabled) && this.intersected;
    }

    public int getExpireAge()
    {
        return this.expireAge;
    }

    public int getExpirationDelay()
    {
        return this.expirationDelay;
    }

    /**
     * Copy this particle to the given particle (it does not copy fields that are initialized by components)
     * @param to destiny to copy values to
     * @return copied particle
     */
    public BedrockParticle softCopy(BedrockParticle to)
    {
        to.age = this.age;
        to.expireAge = this.expireAge;
        to.expirationDelay = this.expirationDelay;
        to.realisticCollisionDrag = this.realisticCollisionDrag;
        to.collisionTime = (Vector3f) this.collisionTime.clone();
        to.entityCollisionTime = new HashMap<>();

        for (Map.Entry<Entity, Vector3f> entry : this.entityCollisionTime.entrySet())
        {
            to.entityCollisionTime.put(entry.getKey(), (Vector3f) entry.getValue().clone());
        }

        to.bounces = this.bounces;
        to.firstIntersection = this.firstIntersection;
        to.offset = (Vector3d) this.offset.clone();
        to.position = (Vector3d) this.position.clone();
        to.initialPosition = (Vector3d) this.initialPosition.clone();
        to.prevPosition = (Vector3d) this.prevPosition.clone();
        to.matrix = (Matrix3f) this.matrix.clone();
        to.matrixSet = this.matrixSet;
        to.speed = (Vector3f) this.speed.clone();
        to.acceleration = (Vector3f) this.acceleration.clone();
        to.accelerationFactor = (Vector3f) this.accelerationFactor.clone();
        to.dragFactor = this.dragFactor;
        to.global = (Vector3d) this.global.clone();

        return to;
    }

    public double getDistanceSq(BedrockEmitter emitter)
    {
        Vector3d pos = this.getGlobalPosition(emitter);

        double dx = emitter.cX - pos.x;
        double dy = emitter.cY - pos.y;
        double dz = emitter.cZ - pos.z;

        return dx * dx + dy * dy + dz * dz;
    }

    public double getAge(float partialTick)
    {
        return (this.age + partialTick) / 20.0;
    }

    public Vector3d getGlobalPosition(BedrockEmitter emitter)
    {
        return this.getGlobalPosition(emitter, this.position);
    }

    public Vector3d getGlobalPosition(BedrockEmitter emitter, Vector3d vector)
    {
        double px = vector.x;
        double py = vector.y;
        double pz = vector.z;

        if (this.relativePosition && this.relativeRotation)
        {
            Vector3f v = new Vector3f((float) px, (float) py, (float) pz);
            emitter.rotation.transform(v);

            px = v.x;
            py = v.y;
            pz = v.z;

            px += emitter.lastGlobal.x;
            py += emitter.lastGlobal.y;
            pz += emitter.lastGlobal.z;
        }

        this.global.set(px, py, pz);

        return this.global;
    }

    public void update(BedrockEmitter emitter)
    {
        this.prevRotation = this.rotation;
        this.prevPosition.set(this.position);

        this.setupMatrix(emitter);

        if (!this.manual)
        {
            //this.position.add(this.offset);

            /*if (this.realisticCollisionDrag && Math.round(this.speed.x*10000) == 0 && Math.round(this.speed.y*10000) == 0 && Math.round(this.speed.z*10000) == 0)
            {
                this.dragFactor = 0;
                this.speed.scale(0);
            }*/

            /* lazy fix for transforming from moving intertial system back to global space */
            if (this.entityCollisionTime.isEmpty())
            {
                transformOffsetToGlobal();
            }
            else
            {
                for (HashMap.Entry<Entity, Vector3f> entry : this.entityCollisionTime.entrySet())
                {
                    if (entry.getValue().y != this.age)
                    {
                        transformOffsetToGlobal();
                    }
                }
            }

            //TODO drag force usually takes velocity^2 -> this cuts off higher velocities much quicker but takes longer with slower velocities. maybe two separate options?
            float rotationDrag = (this.rotationDrag + this.rotationCollisionDrag) * this.rotationVelocity;
            float rotationAcceleration = this.rotationAcceleration / 20F;
            /* clamp drag so it doesn't make rotation velocity explode*/
            this.rotationVelocity = MathUtils.clamp(this.rotationVelocity - rotationDrag / 20F,
                    Math.min(this.rotationVelocity, 0), Math.max(this.rotationVelocity, 0));
            this.rotationVelocity += rotationAcceleration / 20F;
            this.rotation += this.rotationVelocity;

            /* Position */
            if (this.age == 0)
            {
                if (this.relativeDirection)
                {
                    emitter.rotation.transform(this.speed);
                }

                if (this.linearVelocity != 0)
                {
                    Vector3f v = new Vector3f(emitter.lastGlobal);
                    v.x -= emitter.prevGlobal.x;
                    v.y -= emitter.prevGlobal.y;
                    v.z -= emitter.prevGlobal.z;

                    this.speed.x += v.x * this.linearVelocity;
                    this.speed.y += v.y * this.linearVelocity;
                    this.speed.z += v.z * this.linearVelocity;
                }

                if (this.angularVelocity != 0)
                {
                    Matrix3f rotation1 = new Matrix3f(emitter.rotation);
                    Matrix3f identity = new Matrix3f();

                    identity.setIdentity();

                    try
                    {
                        Matrix3f rotation0 = new Matrix3f(emitter.prevRotation);

                        rotation0.invert();
                        rotation1.mul(rotation0);

                        Vector3f angularV = MatrixUtils.getAngularVelocity(rotation1);

                        Vector3f radius = new Vector3f(emitter.translation);
                        radius.x += this.position.x - emitter.lastGlobal.x;
                        radius.y += this.position.y - emitter.lastGlobal.y;
                        radius.z += this.position.z - emitter.lastGlobal.z;

                        Vector3f v = new Vector3f();

                        v.cross(angularV, radius);

                        this.speed.x += v.x * this.angularVelocity;
                        this.speed.y += v.y * this.angularVelocity;
                        this.speed.z += v.z * this.angularVelocity;
                    }
                    catch (SingularMatrixException e) {} //maybe check if determinant is zero
                }
            }

            if (this.relativeAcceleration)
            {
                emitter.rotation.transform(this.acceleration);
            }

            if (this.gravity)
            {
                this.acceleration.y -= 9.81;
            }

            Vector3f drag = new Vector3f(this.speed);
            drag.scale(-(this.drag + this.dragFactor));
            /* apply drag separately so we can clamp it - high drag values shouldn't accelerate particles again */
            drag.scale(1 / 20F);
            if (this.speed.length() - drag.length() <= 0) {
                this.speed.scale(0);
            } else {
                this.speed.add(drag);
            }

            this.acceleration.scale(1 / 20F);
            this.speed.add(this.acceleration);

            Vector3f speed0 = new Vector3f(this.speed);
            speed0.x *= this.accelerationFactor.x;
            speed0.y *= this.accelerationFactor.y;
            speed0.z *= this.accelerationFactor.z;

            if (this.relativePosition || this.relativeRotation)
            {
                this.matrix.transform(speed0);
            }

            this.position.x += speed0.x / 20F;
            this.position.y += speed0.y / 20F;
            this.position.z += speed0.z / 20F;

            if (!this.morph.isEmpty())
            {
                /* P230: per-particle morph rendering-dummy tick (legacy verbatim).
                 * {@code dummy.age += 1} is legacy {@code dummy.ticksExisted += 1}. */
                LivingEntity dummy = this.getDummy(emitter);

                if (dummy != null)
                {
                    this.morph.get().update(dummy);

                    dummy.age += 1;
                }
            }
        }

        if (this.lifetime >= 0 &&
            (this.age >= this.lifetime || (this.age >= this.expireAge && this.expireAge != -1)) )
        {
            this.dead = true;
        }

        this.age ++;
        this.acceleration.set(0, 0, 0);
    }

    /**
     * Sets the expirationDelay and expireAge - the smallest expire age wins. Negative expiration delays always overwrite/win.
     */
    public void setExpirationDelay(double delay)
    {
        int expirationDelay = (int) delay;

        if (this.age + expirationDelay < this.expireAge || this.expireAge == -1)
        {
            this.expirationDelay = Math.abs(expirationDelay);
            this.expireAge = this.age + this.expirationDelay;
        }
    }

    public void setupMatrix(BedrockEmitter emitter)
    {
        if (this.relativePosition)
        {
            if (this.relativeRotation)
            {
                this.matrix.setIdentity();
            }
            else if (!this.matrixSet)
            {
                this.matrix.set(emitter.rotation);
                this.matrixSet = true;
            }
        }
        else if (this.relativeRotation)
        {
            this.matrix.set(emitter.rotation);
        }
    }

    /**
     * This method adds the offset to the speed to transform from a moving inertial system to the global space
     * (especially for inertia)
     */
    public void transformOffsetToGlobal()
    {
        this.offset.scale(6); //scale it up so it gets more noticeable (artistic choice)

        this.speed.x += this.offset.x;
        this.speed.y += this.offset.y;
        this.speed.z += this.offset.z;

        this.offset.scale(0);
    }

    /**
     * Lazily create and reset the per-particle morph rendering dummy (P230).
     *
     * <p>Legacy body, verbatim: the dummy is created once against the emitter's
     * world and then, on <b>every</b> fetch, snapped to the particle's global
     * position with all prev/last-tick position state collapsed onto it and all
     * four rotations (yaw, pitch, head yaw, body yaw) plus their prev twins
     * zeroed. That "no interpolation, no rotation" reset is what makes the morph
     * follow the particle exactly rather than lagging a frame behind it.</p>
     *
     * <p>Yarn 1.20.4 names: {@code prevPosX/Y/Z} → {@code prevX/Y/Z},
     * {@code lastTickPosX/Y/Z} → {@code lastRenderX/Y/Z},
     * {@code rotationYaw/Pitch} → {@code setYaw/setPitch} + {@code prevYaw/prevPitch},
     * {@code rotationYawHead} → {@code headYaw}, {@code renderYawOffset} →
     * {@code bodyYaw}.</p>
     *
     * <p>Deviation from legacy's {@code new DummyEntity(Minecraft.getMinecraft().world)}:
     * the world comes from {@link BedrockEmitter#world} (the target entity's world —
     * the same client world on a real client) and falls back to the client world,
     * which keeps the method callable headlessly. {@code DummyEntity} needs an
     * {@link net.minecraft.entity.EntityType} on 1.20.4; an armor stand is the
     * same host the rest of the port uses.</p>
     */
    public LivingEntity getDummy(BedrockEmitter emitter)
    {
        if (this.dummy == null)
        {
            this.dummy = new DummyEntity(EntityType.ARMOR_STAND, dummyWorld(emitter));
        }

        Vector3d pos = this.getGlobalPosition(emitter);

        this.dummy.setPosition(pos.x, pos.y, pos.z);
        this.dummy.prevX = this.dummy.getX();
        this.dummy.prevY = this.dummy.getY();
        this.dummy.prevZ = this.dummy.getZ();
        this.dummy.lastRenderX = this.dummy.getX();
        this.dummy.lastRenderY = this.dummy.getY();
        this.dummy.lastRenderZ = this.dummy.getZ();
        this.dummy.setYaw(0);
        this.dummy.prevYaw = 0;
        this.dummy.setPitch(0);
        this.dummy.prevPitch = 0;
        this.dummy.headYaw = this.dummy.prevHeadYaw = 0;
        this.dummy.bodyYaw = this.dummy.prevBodyYaw = 0;

        return this.dummy;
    }

    private static World dummyWorld(BedrockEmitter emitter)
    {
        if (emitter != null && emitter.world != null)
        {
            return emitter.world;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        return mc == null ? null : mc.world;
    }
}
