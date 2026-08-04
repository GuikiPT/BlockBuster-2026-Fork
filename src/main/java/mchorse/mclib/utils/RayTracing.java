package mchorse.mclib.utils;

import net.minecraft.entity.Entity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.List;
import java.util.Optional;

/**
 * Port of McLib 2.4.3's RayTracing (roadmap P13) to yarn 1.20.4 types.
 *
 * Legacy source: .tools/legacy-src/mclib/src/main/java/mchorse/mclib/utils/RayTracing.java
 *
 * API mapping (1.12.2 -> yarn 1.20.4):
 * - eye position vec -> {@link Entity#getCameraPosVec(float)}
 * - {@code getLook(partialTicks)} -> {@link Entity#getRotationVec(float)}
 * - {@code world.rayTraceBlocks(..., stopOnLiquid=false, ...)} ->
 *   {@code world.raycast(new RaycastContext(..., ShapeType.COLLIDER, FluidHandling.NONE, entity))}
 * - {@code getEntitiesInAABBexcluding} -> {@code world.getOtherEntities}
 * - 1.12 {@code AxisAlignedBB.expand} (directional) -> {@link Box#stretch},
 *   1.12 {@code grow} (symmetric) -> {@link Box#expand}
 * - {@code calculateIntercept} -> {@link Box#raycast(Vec3d, Vec3d)}
 * - {@code canBeCollidedWith()} -> {@link Entity#canHit()}
 * - {@code getCollisionBorderSize()} -> {@link Entity#getTargetingMargin()}
 * - {@code getLowestRidingEntity()} -> {@link Entity#getRootVehicle()}
 */
public class RayTracing
{
    /**
     * Get the entity at which given player is looking at
     */
    public static Entity getTargetEntity(Entity input, double maxReach)
    {
        HitResult result = rayTraceWithEntity(input, maxReach);

        return result.getType() == HitResult.Type.ENTITY ? ((EntityHitResult) result).getEntity() : null;
    }

    /**
     * Kind of like rayTrace method, but as well it takes into account entity
     * ray tracing
     */
    public static HitResult rayTraceWithEntity(Entity input, double maxReach)
    {
        double blockDistance = maxReach;

        HitResult result = rayTrace(input, maxReach, 1.0F);
        Vec3d eyes = input.getCameraPosVec(1.0F);

        if (result.getType() != HitResult.Type.MISS)
        {
            blockDistance = result.getPos().distanceTo(eyes);
        }

        Vec3d look = input.getRotationVec(1.0F);
        Vec3d max = eyes.add(look.x * maxReach, look.y * maxReach, look.z * maxReach);
        Vec3d hit = null;
        Entity target = null;

        float area = 1.0F;

        List<Entity> list = input.getWorld().getOtherEntities(input, input.getBoundingBox().stretch(look.x * maxReach, look.y * maxReach, look.z * maxReach).expand(area, area, area), (entity) ->
        {
            return entity != null && entity.canHit();
        });

        double entityDistance = blockDistance;

        for (int i = 0; i < list.size(); ++i)
        {
            Entity entity = list.get(i);

            if (entity == input)
            {
                continue;
            }

            Box aabb = entity.getBoundingBox().expand(entity.getTargetingMargin());
            Optional<Vec3d> intercept = aabb.raycast(eyes, max);

            if (aabb.contains(eyes))
            {
                if (entityDistance >= 0.0D)
                {
                    hit = intercept.isPresent() ? intercept.get() : eyes;
                    target = entity;
                    entityDistance = 0.0D;
                }
            }
            else if (intercept.isPresent())
            {
                double eyesDistance = eyes.distanceTo(intercept.get());

                if (eyesDistance < entityDistance || entityDistance == 0.0D)
                {
                    if (entity.getRootVehicle() == input.getRootVehicle() && !canRiderInteract(input))
                    {
                        if (entityDistance == 0.0D)
                        {
                            hit = intercept.get();
                            target = entity;
                        }
                    }
                    else
                    {
                        hit = intercept.get();
                        target = entity;
                        entityDistance = eyesDistance;
                    }
                }
            }
        }

        if (target != null)
        {
            result = new EntityHitResult(target, hit);
        }

        return result;
    }

    /**
     * This method is extracted from {@link Entity} class, because it was marked
     * as client side only code.
     */
    public static HitResult rayTrace(Entity input, double blockReachDistance, float partialTicks)
    {
        Vec3d eyePos = input.getCameraPosVec(partialTicks);
        Vec3d eyeDir = input.getRotationVec(partialTicks);
        Vec3d eyeReach = eyePos.add(eyeDir.x * blockReachDistance, eyeDir.y * blockReachDistance, eyeDir.z * blockReachDistance);

        return input.getWorld().raycast(new RaycastContext(eyePos, eyeReach, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, input));
    }

    /**
     * 1.12's {@code Entity.canRiderInteract()} (a Forge extension) defaulted
     * to false for every vanilla entity; modern Entity has no equivalent, so
     * the legacy ridden-entity exclusion keeps its shape with the default.
     */
    private static boolean canRiderInteract(Entity input)
    {
        return false;
    }
}
