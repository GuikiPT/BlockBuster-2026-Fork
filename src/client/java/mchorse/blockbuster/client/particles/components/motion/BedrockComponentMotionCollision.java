package mchorse.blockbuster.client.particles.components.motion;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import mchorse.blockbuster.client.particles.components.BedrockComponentBase;
import mchorse.blockbuster.client.particles.components.IComponentParticleUpdate;
import mchorse.blockbuster.client.particles.emitter.BedrockEmitter;
import mchorse.blockbuster.client.particles.emitter.BedrockParticle;
import mchorse.blockbuster.client.particles.emitter.RandomProvider;
import mchorse.blockbuster.utils.EntityTransformationUtils;
import mchorse.mclib.math.Operation;
import mchorse.mclib.math.molang.MolangException;
import mchorse.mclib.math.molang.MolangParser;
import mchorse.mclib.math.molang.expressions.MolangExpression;
import mchorse.mclib.utils.MathUtils;
import mchorse.metamorph.api.MorphUtils;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;

import org.jetbrains.annotations.Nullable;

import javax.vecmath.Vector3d;
import javax.vecmath.Vector3f;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * {@code minecraft:particle_motion_collision} (roadmap P151).
 *
 * <p>The most extended Snowstorm component: swept-AABB block collision, entity
 * collision (with momentum and 2-tick-history inertia), split particles, random
 * bounciness, damping, expire-on-impact and drag. Ported operation-for-operation
 * from the 1.12.2 engine (author: Chryfi). Runs as a particle updater at sorting
 * index {@code 50}.</p>
 *
 * <p><b>Key spellings are a disk contract</b> — the mixed camelCase/snake_case
 * ({@code entityCollision}, {@code realisticCollision}, {@code preserveEnergy},
 * {@code expirationDelay} vs {@code realistic_collision_drag},
 * {@code split_particle_count}, {@code split_particle_speedThreshold},
 * {@code coefficient_of_restitution}, …) is exactly how authored files are
 * written; no normalization. {@code preserveEnergy} accepts a JSON boolean
 * <b>or</b> a MoLang primitive.</p>
 *
 * <p>Yarn mapping notes: 1.12's {@code AxisAlignedBB} maps to
 * {@link net.minecraft.util.math.Box}; {@code AxisAlignedBB#expand} (the swept
 * grow) maps to {@link Box#stretch}; {@code EnumFacing.Axis} to
 * {@link Direction.Axis}. The {@code calculateX/Y/Z Offset} sweep helpers were
 * removed from {@code Box} in 1.20.4, so they are reimplemented privately with
 * <b>literal 1.12 semantics</b> (kept in Y, X, Z resolution order, with the
 * 10-block tunneling guard) rather than adapting to {@code VoxelShape} sweeps.
 * The world queries sit behind {@code protected} seams so headless physics tests
 * can inject a synthetic floor without a real {@code World}.</p>
 */
public class BedrockComponentMotionCollision extends BedrockComponentBase implements IComponentParticleUpdate
{
    public MolangExpression enabled = MolangParser.ONE;
    public boolean preserveEnergy = false;
    public boolean entityCollision;
    public boolean momentum;
    public float collisionDrag = 0;
    public float bounciness = 1;
    public float randomBounciness = 0;
    public float randomDamp = 0;
    public float damp = 0; // should be like in Blender
    public int splitParticleCount;
    public float splitParticleSpeedThreshold; // threshold to activate the split
    public float radius = 0.01F;
    public boolean expireOnImpact;
    public MolangExpression expirationDelay = MolangParser.ZERO;
    public boolean realisticCollision;
    public boolean realisticCollisionDrag;
    public float rotationCollisionDrag;

    /* Runtime options */
    private Vector3d previous = new Vector3d();
    private Vector3d current = new Vector3d();
    private BlockPos.Mutable pos = new BlockPos.Mutable();

    public static float getComponent(Vector3f vector, Direction.Axis component)
    {
        if (component == Direction.Axis.X)
        {
            return vector.x;
        }
        else if (component == Direction.Axis.Y)
        {
            return vector.y;
        }

        return vector.z;
    }

    public static void setComponent(Vector3f vector, Direction.Axis component, float value)
    {
        if (component == Direction.Axis.X)
        {
            vector.x = value;
        }
        else if (component == Direction.Axis.Y)
        {
            vector.y = value;
        }
        else
        {
            vector.z = value;
        }
    }

    public static void negateComponent(Vector3f vector, Direction.Axis component)
    {
        setComponent(vector, component, -getComponent(vector, component));
    }

    public static double getComponent(Vector3d vector, Direction.Axis component)
    {
        if (component == Direction.Axis.X)
        {
            return vector.x;
        }
        else if (component == Direction.Axis.Y)
        {
            return vector.y;
        }

        return vector.z;
    }

    public static void setComponent(Vector3d vector, Direction.Axis component, double value)
    {
        if (component == Direction.Axis.X)
        {
            vector.x = value;
        }
        else if (component == Direction.Axis.Y)
        {
            vector.y = value;
        }
        else
        {
            vector.z = value;
        }
    }

    public static void negateComponent(Vector3d vector, Direction.Axis component)
    {
        setComponent(vector, component, -getComponent(vector, component));
    }

    @Override
    public BedrockComponentBase fromJson(JsonElement elem, MolangParser parser) throws MolangException
    {
        if (!elem.isJsonObject()) return super.fromJson(elem, parser);

        JsonObject element = elem.getAsJsonObject();

        if (element.has("enabled")) this.enabled = parser.parseJson(element.get("enabled"));
        if (element.has("entityCollision")) this.entityCollision = element.get("entityCollision").getAsBoolean();
        if (element.has("momentum")) this.momentum = element.get("momentum").getAsBoolean();
        if (element.has("realistic_collision_drag")) this.realisticCollisionDrag = element.get("realistic_collision_drag").getAsBoolean();
        if (element.has("collision_drag")) this.collisionDrag = element.get("collision_drag").getAsFloat();
        if (element.has("coefficient_of_restitution")) this.bounciness = element.get("coefficient_of_restitution").getAsFloat();
        if (element.has("bounciness_randomness")) this.randomBounciness = element.get("bounciness_randomness").getAsFloat();
        if (element.has("collision_rotation_drag")) this.rotationCollisionDrag = element.get("collision_rotation_drag").getAsFloat();
        if (element.has("preserveEnergy") && element.get("preserveEnergy").isJsonPrimitive())
        {
            JsonPrimitive energy = element.get("preserveEnergy").getAsJsonPrimitive();

            if (energy.isBoolean())
            {
                this.preserveEnergy = energy.getAsBoolean();
            }
            else
            {
                this.preserveEnergy = MolangExpression.isOne(parser.parseJson(energy));
            }
        }
        if (element.has("damp")) this.damp = element.get("damp").getAsFloat();
        if (element.has("random_damp")) this.randomDamp = element.get("random_damp").getAsFloat();
        if (element.has("split_particle_count")) this.splitParticleCount = element.get("split_particle_count").getAsInt();
        if (element.has("split_particle_speedThreshold")) this.splitParticleSpeedThreshold = element.get("split_particle_speedThreshold").getAsFloat();
        if (element.has("collision_radius")) this.radius = element.get("collision_radius").getAsFloat();
        if (element.has("expire_on_contact")) this.expireOnImpact = element.get("expire_on_contact").getAsBoolean();
        if (element.has("expirationDelay")) this.expirationDelay = parser.parseJson(element.get("expirationDelay"));
        if (element.has("realisticCollision")) this.realisticCollision = element.get("realisticCollision").getAsBoolean();

        return super.fromJson(element, parser);
    }

    @Override
    public JsonElement toJson()
    {
        JsonObject object = new JsonObject();

        if (!MolangExpression.isOne(this.enabled)) object.add("enabled", this.enabled.toJson());
        if (this.realisticCollision) object.addProperty("realisticCollision", true);
        if (this.entityCollision) object.addProperty("entityCollision", true);
        if (this.momentum) object.addProperty("momentum", true);
        if (this.realisticCollisionDrag) object.addProperty("realistic_collision_drag", true);
        if (this.collisionDrag != 0) object.addProperty("collision_drag", this.collisionDrag);
        if (this.bounciness != 1) object.addProperty("coefficient_of_restitution", this.bounciness);
        if (this.rotationCollisionDrag != 0) object.addProperty("collision_rotation_drag", this.rotationCollisionDrag);
        if (this.randomBounciness != 0) object.addProperty("bounciness_randomness", this.randomBounciness);
        if (this.preserveEnergy) object.addProperty("preserveEnergy", this.preserveEnergy);
        if (this.damp != 0) object.addProperty("damp", this.damp);
        if (this.randomDamp != 0) object.addProperty("random_damp", this.randomDamp);
        if (this.splitParticleCount != 0) object.addProperty("split_particle_count", this.splitParticleCount);
        if (this.splitParticleSpeedThreshold != 0) object.addProperty("split_particle_speedThreshold", this.splitParticleSpeedThreshold);
        if (this.radius != 0.01F) object.addProperty("collision_radius", this.radius);
        if (this.expireOnImpact) object.addProperty("expire_on_contact", true);
        if (!MolangExpression.isZero(this.expirationDelay)) object.add("expirationDelay", this.expirationDelay.toJson());

        return object;
    }

    /* World-query seams (overridable so headless tests can inject collisions). */

    protected boolean hasWorld(BedrockEmitter emitter)
    {
        return emitter.world != null;
    }

    protected boolean isLoaded(BedrockEmitter emitter)
    {
        return emitter.world.isChunkLoaded(this.pos);
    }

    protected List<Entity> getEntities(BedrockEmitter emitter, Box box)
    {
        return emitter.world.getOtherEntities(null, box);
    }

    protected List<Box> getCollisionBoxes(BedrockEmitter emitter, Box box)
    {
        List<Box> list = new ArrayList<Box>();

        for (VoxelShape shape : emitter.world.getBlockCollisions(null, box))
        {
            list.addAll(shape.getBoundingBoxes());
        }

        return list;
    }

    @Override
    public void update(BedrockEmitter emitter, BedrockParticle particle)
    {
        particle.realisticCollisionDrag = this.realisticCollisionDrag;

        if (!this.hasWorld(emitter))
        {
            return;
        }

        float r = this.radius;

        this.previous.set(particle.getGlobalPosition(emitter, particle.prevPosition));
        this.current.set(particle.getGlobalPosition(emitter));

        Vector3d prev = this.previous;
        Vector3d now = this.current;

        double x = now.x - prev.x;
        double y = now.y - prev.y;
        double z = now.z - prev.z;
        boolean veryBig = Math.abs(x) > 10 || Math.abs(y) > 10 || Math.abs(z) > 10;

        this.pos.set(now.x, now.y, now.z);

        if (veryBig || !this.isLoaded(emitter))
        {
            return;
        }

        Box aabb = new Box(prev.x - r, prev.y - r, prev.z - r, prev.x + r, prev.y + r, prev.z + r);

        double d0 = y;
        double origX = x;
        double origZ = z;

        List<Entity> entities = this.getEntities(emitter, aabb.stretch(x, y, z));
        HashMap<Entity, Box> entityAABBs = new HashMap<Entity, Box>();
        HashMap<Entity, CollisionOffset> staticEntityAABBs = new HashMap<>(); //for newtons first law
        /* for own hitbox implementation: check for hitbox expanded for the previous position - prevent fast moving tunneling */
        List<Box> list = this.getCollisionBoxes(emitter, aabb.stretch(x, y, z));

        if ((!list.isEmpty() || (!entities.isEmpty() && this.entityCollision)) && !particle.intersected)
        {
            particle.firstIntersection = particle.age;
            particle.intersected = true;
        }

        if (!particle.manual && !Operation.equals(this.enabled.get(), 0))
        {
            if (this.entityCollision)
            {
                for (Entity entity : entities)
                {
                    Box aabb2 = new Box(prev.x - r, prev.y - r, prev.z - r, prev.x + r, prev.y + r, prev.z + r);
                    Box entityAABB = entity.getBoundingBox();

                    double y2 = y, x2 = x, z2 = z;

                    y2 = calculateYOffset(entityAABB, aabb2, y2);
                    aabb2 = aabb2.offset(0.0D, y2, 0.0D);

                    x2 = calculateXOffset(entityAABB, aabb2, x2);
                    aabb2 = aabb2.offset(x2, 0.0D, 0.0D);

                    z2 = calculateZOffset(entityAABB, aabb2, z2);
                    aabb2 = aabb2.offset(0.0D, 0.0D, z2);

                    if (d0 == y2 && origX == x2 && origZ == z2)
                    {
                        entityAABBs.put(entity, entityAABB); //Note to myself: maybe start already here with collision response?
                    }
                    else
                    {
                        list.add(entityAABB);
                        staticEntityAABBs.put(entity, new CollisionOffset(entityAABB, x2, y2, z2));

                        if (this.momentum && d0 == y2)
                        {
                            momentum(particle, entity);
                        }
                    }
                }
            }

            CollisionOffset offsetData = calculateOffsets(aabb, list, x, y, z);
            aabb = offsetData.aabb;
            x = offsetData.x;
            y = offsetData.y;
            z = offsetData.z;

            if (d0 != y || origX != x || origZ != z)
            {
                this.collision(particle, emitter, prev);

                now.set(aabb.minX + r, aabb.minY + r, aabb.minZ + r);

                if (d0 != y)
                {
                    if (d0 < y) now.y = aabb.minY;
                    else now.y = aabb.maxY;

                    now.y += d0 < y ? r : -r;

                    this.collisionHandler(particle, emitter, Direction.Axis.Y, now, prev);

                    /* here comes inertia */
                    /* remove unecessary elements from collisionTime*/
                    particle.entityCollisionTime.keySet().retainAll(staticEntityAABBs.keySet());

                    for (HashMap.Entry<Entity, CollisionOffset> entry : staticEntityAABBs.entrySet())
                    {
                        CollisionOffset offsetData2 = entry.getValue();
                        Entity collidingEntity = entry.getKey();

                        if (d0 != offsetData2.y && origX == offsetData2.x && origZ == offsetData2.z)
                        {
                            inertia(particle, collidingEntity, now);
                        }

                        if (particle.entityCollisionTime.containsKey(collidingEntity))
                        {
                            particle.entityCollisionTime.get(collidingEntity).y = particle.age;
                        }
                        else
                        {
                            particle.entityCollisionTime.put(entry.getKey(), new Vector3f(-1F, particle.age, -1F));
                        }
                    }
                }

                if (origX != x)
                {
                    if (origX < x) now.x = aabb.minX;
                    else now.x = aabb.maxX;

                    now.x += origX < x ? r : -r;

                    collisionHandler(particle, emitter, Direction.Axis.X, now, prev);
                }

                if (origZ != z)
                {
                    if (origZ < z) now.z = aabb.minZ;
                    else now.z = aabb.maxZ;

                    now.z += origZ < z ? r : -r;

                    collisionHandler(particle, emitter, Direction.Axis.Z, now, prev);
                }

                particle.position.set(now);

                drag(particle);
            }
            else if (entityAABBs.isEmpty() && this.realisticCollisionDrag) //no collision - reset collision drag
            {
                particle.dragFactor = 0;
            }
            else
            {
                particle.rotationCollisionDrag = 0;
            }


            for (HashMap.Entry<Entity, Box> entry : entityAABBs.entrySet())
            {
                Box entityAABB = entry.getValue();
                Entity entity = entry.getKey();

                Vector3f speedEntity = new Vector3f((float) (entity.getX() - entity.prevX), (float) (entity.getY() - entity.prevY), (float) (entity.getZ() - entity.prevZ));
                Vector3f ray;

                if (speedEntity.x != 0 || speedEntity.y != 0 || speedEntity.z != 0)
                {
                    ray = speedEntity;
                }
                else
                {
                    /* fixes the issue of particles falling through the entity
                     * when they lie on the surface while the hitbox changes
                     * downside: the position is not always accurate depending on the movement*/
                    continue;
                }

                Vector3d frac = intersect(ray, particle.getGlobalPosition(emitter), entityAABB);

                if (frac != null)
                {
                    particle.position.add(frac);

                    Box aabb2 = new Box(particle.position.x - r, particle.position.y - r, particle.position.z - r, particle.position.x + r, particle.position.y + r, particle.position.z + r);

                    collision(particle, emitter, prev);

                    if ((aabb2.minX < entityAABB.maxX && aabb2.maxX > entityAABB.maxX) || (aabb2.maxX > entityAABB.minX && aabb2.minX < entityAABB.minX))
                    {
                        entityCollision(particle, emitter, entity, Direction.Axis.X, prev);
                    }

                    if ((aabb2.minY < entityAABB.maxY && aabb2.maxY > entityAABB.maxY) || (aabb2.maxY > entityAABB.minY && aabb2.minY < entityAABB.minY))
                    {
                        entityCollision(particle, emitter, entity, Direction.Axis.Y, prev);
                    }

                    if ((aabb2.minZ < entityAABB.maxZ && aabb2.maxZ > entityAABB.maxZ) || (aabb2.maxZ > entityAABB.minZ && aabb2.minZ < entityAABB.minZ))
                    {
                        entityCollision(particle, emitter, entity, Direction.Axis.Z, prev);
                    }
                }
            }

            if (!entityAABBs.isEmpty())
            {
                this.drag(particle);
            }
        }
    }

    public void collision(BedrockParticle particle, BedrockEmitter emitter, Vector3d prev)
    {
        if (this.expireOnImpact)
        {
            double expirationDelay = this.expirationDelay.get();

            if (expirationDelay != 0 && !particle.collided)
            {
                particle.setExpirationDelay(expirationDelay);
            }
            else if (expirationDelay == 0 && !particle.collided)
            {
                particle.dead = true;

                return;
            }
        }

        if (particle.relativePosition)
        {
            particle.relativePosition = false;
            particle.prevPosition.set(prev);
        }

        particle.rotationCollisionDrag = this.rotationCollisionDrag;
        particle.collided = true;
    }

    public void entityCollision(BedrockParticle particle, BedrockEmitter emitter, Entity entity, Direction.Axis component, Vector3d prev)
    {
        Vector3f entitySpeed = new Vector3f((float) (entity.getX() - entity.prevX), (float) (entity.getY() - entity.prevY), (float) (entity.getZ() - entity.prevZ));
        Vector3d entityPosition = new Vector3d(entity.getX(), entity.getY(), entity.getZ());

        if (this.momentum)
        {
            momentum(particle, entity);
        }

        /* collisionTime should be not changed - otherwise the particles will stop when moving against moving entites */
        float tmpTime = getComponent(particle.collisionTime, component);
        double delta = getComponent(particle.position, component) - getComponent(entityPosition, component);

        setComponent(particle.position, component, getComponent(particle.position, component) + (delta > 0 ? this.radius : -this.radius));

        collisionHandler(particle, emitter, component, particle.position, prev);

        /* collisionTime should not change or otherwise particles will lose their speed although they should be reflected */
        setComponent(particle.collisionTime, component, tmpTime);

        if (delta > 0 && component == Direction.Axis.Y) //particle is above
        {
            inertia(particle, entity, null);
        }

        /* particle speed is always switched (realistcCollision==true), as it always collides with the entity, but it should only have one correct direction */
        if (getComponent(particle.speed, component) > 0)
        {
            if (getComponent(entitySpeed, component) < 0) negateComponent(particle.speed, component);
        }
        else if (getComponent(particle.speed, component) < 0)
        {
            if (getComponent(entitySpeed, component) > 0) negateComponent(particle.speed, component);
        }

        /* otherwise particles would stick on the body and get reflected when entity stops */
        /* note to myself: when particle lies on top and you fly up it floats weirdly - need to redo this system a little bit*/
        setComponent(particle.position, component, getComponent(particle.position, component) + getComponent(particle.speed, component) / 20F);
    }

    public void collisionHandler(BedrockParticle particle, BedrockEmitter emitter, Direction.Axis component, Vector3d now, Vector3d prev)
    {
        float collisionTime = getComponent(particle.collisionTime, component);
        float speed = getComponent(particle.speed, component);
        float accelerationFactor = getComponent(particle.accelerationFactor, component);

        /* realistic collision */
        if (this.realisticCollision)
        {
            if (collisionTime != (particle.age - 1))
            {
                if (this.bounciness != 0)
                {
                    setComponent(particle.speed, component, -speed * this.bounciness);
                }
            }
            else if (collisionTime == (particle.age - 1))
            {
                setComponent(particle.speed, component, 0); //particle laid on that surface since last tick
            }
        }
        else
        {
            setComponent(particle.accelerationFactor, component, accelerationFactor * -this.bounciness);
        }

        if (collisionTime != (particle.age - 1))
        {
            /* random bounciness */
            if (this.randomBounciness != 0 /* && Math.round(particle.speed.x) != 0 */)
            {
                particle.speed = this.randomBounciness(particle.speed, component, this.randomBounciness);
            }

            /* split particles */
            if (this.splitParticleCount != 0)
            {
                this.splitParticle(particle, emitter, component, now, prev);
            }

            /* damping */
            if (damp != 0)
            {
                particle.speed = this.damping(particle.speed);
            }
        }

        if (collisionTime != particle.age - 1)
        {
            particle.bounces++;
        }

        setComponent(particle.collisionTime, component, particle.age);
    }

    public void inertia(BedrockParticle particle, Entity entity, @Nullable Vector3d now)
    {
        if (this.collisionDrag == 0)
        {
            return;
        }

        Vector3d entitySpeed = new Vector3d((entity.getX() - entity.prevX), (entity.getY() - entity.prevY), (entity.getZ() - entity.prevZ));

        double prevPrevPosX = EntityTransformationUtils.getPrevPrevPosX(entity);
        double prevPrevPosY = EntityTransformationUtils.getPrevPrevPosY(entity);
        double prevPrevPosZ = EntityTransformationUtils.getPrevPrevPosZ(entity);

        Vector3d prevEntitySpeed = new Vector3d(entity.prevX - prevPrevPosX, entity.prevY - prevPrevPosY, entity.prevZ - prevPrevPosZ);

        /* for first collision from the inertial system of the particle it is acceleration from zero to current velocity */
        if (!particle.entityCollisionTime.containsKey(entity))
        {
            prevEntitySpeed.scale(0);
        }
        else
        {
            /* stick the particle on top of the entity */
            particle.offset.x = entitySpeed.x;
            particle.offset.z = entitySpeed.z;

            if (now == null)
            {
                particle.position.x += entitySpeed.x;
                particle.position.z += entitySpeed.z;
            }
            else
            {
                now.x += entitySpeed.x;
                now.z += entitySpeed.z;
            }
        }

        particle.speed.x += Math.round((prevEntitySpeed.x - entitySpeed.x) * 1000D) / 250D; //scale it up so it gets more noticable
        particle.speed.y += Math.round((prevEntitySpeed.y - entitySpeed.y) * 1000D) / 250D;
        particle.speed.z += Math.round((prevEntitySpeed.z - entitySpeed.z) * 1000D) / 250D;
    }

    public void momentum(BedrockParticle particle, Entity entity)
    {
        particle.speed.x += 2 * (entity.getX() - entity.prevX);
        particle.speed.y += 2 * (entity.getY() - entity.prevY);
        particle.speed.z += 2 * (entity.getZ() - entity.prevZ);
    }

    public void drag(BedrockParticle particle)
    {
        /* only apply drag when speed is almost not zero and randombounciness and realisticCollision are off
         * prevent particles from accelerating away when randomBounciness is active */
        if (!((this.randomBounciness != 0 || this.realisticCollision) && Math.round(particle.speed.x * 10000) == 0 && Math.round(particle.speed.y * 10000) == 0 && Math.round(particle.speed.z * 10000) == 0))
        {
            particle.dragFactor = this.collisionDrag;
        }
    }

    public Vector3f damping(Vector3f vector)
    {
        float random = (float) (this.randomDamp * (RandomProvider.nextDouble() * 2 - 1));
        float clampedValue = MathUtils.clamp((1 - this.damp) + random, 0, 1);

        vector.scale(clampedValue);

        return vector;
    }

    public void splitParticle(BedrockParticle particle, BedrockEmitter emitter, Direction.Axis component, Vector3d now, Vector3d prev)
    {
        float speed = getComponent(particle.speed, component);

        if (!(Math.abs(speed) > Math.abs(this.splitParticleSpeedThreshold)))
        {
            return;
        }

        for (int i = 0; i < this.splitParticleCount; i++)
        {
            BedrockParticle splitParticle = emitter.createParticle(false);

            particle.softCopy(splitParticle);

            splitParticle.position.set(now);
            splitParticle.prevPosition.set(prev);
            splitParticle.morph.setDirect(MorphUtils.copy(particle.morph.get()));

            splitParticle.bounces = 1;

            double splitPosition = getComponent(splitParticle.position, component);

            setComponent(splitParticle.collisionTime, component, particle.age);
            setComponent(splitParticle.position, component, splitPosition);

            Vector3f randomSpeed = this.randomBounciness(particle.speed, component, (this.randomBounciness != 0) ? this.randomBounciness : 10);

            randomSpeed.scale(1.0f / this.splitParticleCount);
            splitParticle.speed.set(randomSpeed);

            if (this.damp != 0)
            {
                splitParticle.speed = this.damping(splitParticle.speed);
            }

            emitter.splitParticles.add(splitParticle);
        }

        particle.dead = true;
    }

    public Vector3f randomBounciness(Vector3f vector0, Direction.Axis component, float randomness)
    {
        if (randomness != 0)
        {
            /* don't change the vector0 - pointer behaviour not wanted here */
            Vector3f vector = new Vector3f(vector0);
            /* scale down the vector components not involved in the collision reflection */
            float randomfactor = 0.25F;
            float prevLength = vector.length();
            randomness *= 0.1F;
            float random1 = (float) RandomProvider.nextDouble() * randomness;
            float random2 = (float) (randomness * randomfactor * (RandomProvider.nextDouble() * 2 - 1));
            float random3 = (float) (randomness * randomfactor * (RandomProvider.nextDouble() * 2 - 1));

            float vectorValue = getComponent(vector, component);

            if (component == Direction.Axis.X)
            {
                vector.y += random2;
                vector.z += random3;
            }
            else if (component == Direction.Axis.Y)
            {
                vector.x += random2;
                vector.z += random3;
            }
            else
            {
                vector.y += random2;
                vector.x += random3;
            }

            if (this.bounciness != 0)
            {
                setComponent(vector, component, vectorValue + ((vectorValue < 0) ? -random1 : random1));
                vector.scale(prevLength / vector.length()); //scale back to original length
            }
            else if (vector.x != 0 || vector.y != 0 || vector.z != 0)
            {
                /* if bounciness=0 then the speed of a specific component wont't affect the particles movement
                 * so the particles speed needs to be scaled back without taking that component into account
                 * when bounciness=0 the energy of that component gets absorbed by the collision block and therefore is lost for the particle
                 */
                if (this.preserveEnergy)
                {
                    setComponent(vector, component, 0);
                }

                /* if the vector is now zero... don't execute 1/vector.length() -> 1/0 not possible */
                if (vector.x != 0 || vector.y != 0 || vector.z != 0)
                {
                    vector.scale(prevLength / vector.length());
                }

                setComponent(vector, component, vectorValue);
            }
            else /* bounciness == 0 and vector is zero (rare case, but not impossible) */
            {
                /* if you don't want particles to stop, while others randomly slide away,
                 * when bounciness==0, then return vector0 */
                return vector0;
            }

            return vector;
        }

        return vector0;
    }

    public Vector3d intersect(Vector3f ray, Vector3d orig, Box aabb)
    {
        double tmin = (aabb.minX - orig.x) / ray.x;
        double tmax = (aabb.maxX - orig.x) / ray.x;

        if (tmin > tmax)
        {
            double tminTmp = tmin;
            tmin = tmax;
            tmax = tminTmp;
        }

        double tymin = (aabb.minY - orig.y) / ray.y;
        double tymax = (aabb.maxY - orig.y) / ray.y;

        if (tymin > tymax)
        {
            double tyminTmp = tymin;
            tymin = tymax;
            tymax = tyminTmp;
        }

        if (tmin > tymax || tymin > tmax)
            return null;

        if (tymin > tmin)
            tmin = tymin;

        if (tymax < tmax)
            tmax = tymax;

        double tzmin = (aabb.minZ - orig.z) / ray.z;
        double tzmax = (aabb.maxZ - orig.z) / ray.z;

        if (tzmin > tzmax)
        {
            double tzminTmp = tzmin;
            tzmin = tzmax;
            tzmax = tzminTmp;
        }

        if (tmin > tzmax || tzmin > tmax)
            return null;

        if (tzmax < tmax)
            tmax = tzmax;

        Vector3d ray1 = new Vector3d(ray);

        ray1.scale(tmax);

        return ray1;
    }

    /**
     * @param aabb main particle box
     * @param list List of collision target boxes
     * @param x origin
     * @param y origin
     * @param z origin
     * @return CollisionOffset which includes aabb, x, y, z
     */
    public CollisionOffset calculateOffsets(Box aabb, List<Box> list, double x, double y, double z)
    {
        for (Box axisalignedbb : list)
        {
            y = calculateYOffset(axisalignedbb, aabb, y);
        }

        aabb = aabb.offset(0.0D, y, 0.0D);

        for (Box axisalignedbb1 : list)
        {
            x = calculateXOffset(axisalignedbb1, aabb, x);
        }

        aabb = aabb.offset(x, 0.0D, 0.0D);

        for (Box axisalignedbb2 : list)
        {
            z = calculateZOffset(axisalignedbb2, aabb, z);
        }

        aabb = aabb.offset(0.0D, 0.0D, z);

        return new CollisionOffset(aabb, x, y, z);
    }

    /**
     * Legacy {@code AxisAlignedBB#calculateXOffset} (removed from yarn {@code Box}).
     * If {@code box} (this) and {@code other} overlap in Y and Z, clamp {@code offsetX}
     * to the gap between them; otherwise return {@code offsetX} unchanged.
     */
    private static double calculateXOffset(Box box, Box other, double offsetX)
    {
        if (other.maxY > box.minY && other.minY < box.maxY && other.maxZ > box.minZ && other.minZ < box.maxZ)
        {
            if (offsetX > 0.0D && other.maxX <= box.minX)
            {
                double d1 = box.minX - other.maxX;

                if (d1 < offsetX)
                {
                    offsetX = d1;
                }
            }
            else if (offsetX < 0.0D && other.minX >= box.maxX)
            {
                double d0 = box.maxX - other.minX;

                if (d0 > offsetX)
                {
                    offsetX = d0;
                }
            }

            return offsetX;
        }
        else
        {
            return offsetX;
        }
    }

    private static double calculateYOffset(Box box, Box other, double offsetY)
    {
        if (other.maxX > box.minX && other.minX < box.maxX && other.maxZ > box.minZ && other.minZ < box.maxZ)
        {
            if (offsetY > 0.0D && other.maxY <= box.minY)
            {
                double d1 = box.minY - other.maxY;

                if (d1 < offsetY)
                {
                    offsetY = d1;
                }
            }
            else if (offsetY < 0.0D && other.minY >= box.maxY)
            {
                double d0 = box.maxY - other.minY;

                if (d0 > offsetY)
                {
                    offsetY = d0;
                }
            }

            return offsetY;
        }
        else
        {
            return offsetY;
        }
    }

    private static double calculateZOffset(Box box, Box other, double offsetZ)
    {
        if (other.maxX > box.minX && other.minX < box.maxX && other.maxY > box.minY && other.minY < box.maxY)
        {
            if (offsetZ > 0.0D && other.maxZ <= box.minZ)
            {
                double d1 = box.minZ - other.maxZ;

                if (d1 < offsetZ)
                {
                    offsetZ = d1;
                }
            }
            else if (offsetZ < 0.0D && other.minZ >= box.maxZ)
            {
                double d0 = box.maxZ - other.minZ;

                if (d0 > offsetZ)
                {
                    offsetZ = d0;
                }
            }

            return offsetZ;
        }
        else
        {
            return offsetZ;
        }
    }

    @Override
    public int getSortingIndex()
    {
        return 50;
    }

    public class CollisionOffset
    {
        public Box aabb;
        public double x;
        public double y;
        public double z;

        public CollisionOffset(Box aabb, double x, double y, double z)
        {
            this.aabb = aabb;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
