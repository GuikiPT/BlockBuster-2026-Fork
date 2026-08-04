package mchorse.metamorph.api;

import java.util.List;
import java.util.Optional;
import mchorse.metamorph.Metamorph;
import mchorse.metamorph.api.models.IMorphProvider;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import mchorse.metamorph.mixin.EntityTypeAccessor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.AbstractNbtNumber;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Entity utilities (roadmap P53).
 *
 * <p>Methods related to {@link mchorse.metamorph.api.morphs.EntityMorph}. The
 * two load-bearing ones are {@link #stripEntityNBT(NbtCompound)} (an identity
 * contract used for acquired-morph dedup) and {@link #compareData} (deliberately
 * shallow, primitives/strings only).</p>
 *
 * <p>Port note: the helpers landed with their consuming phases — the
 * capability-driven {@code getMorph} and {@code forceUpdateSize}/
 * {@code canPlayerMorphFit} with morphing storage (P52) and player size (P54),
 * the server-safe picking pair {@link #getTargetEntity}/{@link #rayTrace} with
 * the vanilla-pack actions (P49.1, consumed by {@code shulker_bullet}).</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/api/EntityUtils.java
 */
public class EntityUtils
{
    /**
     * Strip some common {@link net.minecraft.entity.Entity} related tags, so
     * there won't be interference with comparing two tags on morph acquiring.
     *
     * <p><b>Identity contract</b>: acquired-morph dedup compares the stripped
     * {@code EntityData}. The legacy 1.12 removal list is copied verbatim; the
     * only additions are the modern volatile keys that replace the pre-flattening
     * ones — 1.20 stores the entity UUID as a single {@code UUID} int-array tag
     * instead of {@code UUIDLeast}/{@code UUIDMost}, so both forms are stripped
     * (old fixtures carry the legacy pair, new captures carry the int-array).</p>
     */
    public static NbtCompound stripEntityNBT(NbtCompound tag)
    {
        /* Meta stuff */
        tag.remove("Dimension");
        tag.remove("HurtTime");
        tag.remove("DeathTime");
        tag.remove("HurtByTimestamp");
        tag.remove("Health");
        tag.remove("PortalCooldown");
        tag.remove("Leashed");
        tag.remove("Air");
        tag.remove("id");
        tag.remove("Invulnerable");

        /* Inventory and equipment */
        tag.remove("ArmorDropChances");
        tag.remove("HandDropChances");
        tag.remove("HandItems");
        tag.remove("Inventory");
        tag.remove("LeftHanded");
        tag.remove("CanPickUpLoot");

        /* Space data */
        tag.remove("Pos");
        tag.remove("Motion");
        tag.remove("Rotation");
        tag.remove("FallDistance");
        tag.remove("FallFlying");
        tag.remove("OnGround");
        tag.remove("Fire");
        tag.remove("ArmorItems");

        /* UUID (legacy pair + modern int-array) */
        tag.remove("UUIDLeast");
        tag.remove("UUIDMost");
        tag.remove("UUID");

        /* Attributes */
        tag.remove("Attributes");

        /* Shulker tags stripping */
        tag.remove("Peek");
        tag.remove("AttachFace");
        tag.remove("APX");
        tag.remove("APY");
        tag.remove("APZ");

        /* Zombie pigmen stripping */
        tag.remove("Anger");
        tag.remove("HurtBy");

        /* Chicken de-egging */
        tag.remove("EggLayTime");

        return tag;
    }

    /**
     * Compare two {@link NbtCompound}s for morphing acquiring.
     *
     * <p><b>Deliberately shallow</b>: compares only top-level primitive (number)
     * and string tags. Lists and nested compounds are intentionally ignored —
     * do not deep-compare (legacy quirk).</p>
     */
    public static boolean compareData(NbtCompound a, NbtCompound b)
    {
        if (a == null || b == null)
        {
            return a == b;
        }

        /* Different count of tags? They're different */
        if (a.getSize() != b.getSize())
        {
            return false;
        }

        for (String key : a.getKeys())
        {
            NbtElement aTag = a.get(key);
            NbtElement bTag = b.get(key);

            /* Supporting condition for size check above, in case if the size is
             * the same, but different keys are missing */
            if (bTag == null)
            {
                return false;
            }

            /* We check only strings and primitives, lists and compounds aren't
             * concern of mine */
            if (!(aTag instanceof AbstractNbtNumber) && !(aTag instanceof NbtString))
            {
                continue;
            }

            if (!aTag.equals(bTag))
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Create a dummy entity of the given type — the morph-side replacement for
     * legacy {@code EntityList.createEntityByIDFromName(rl, world)} (roadmap
     * P252, S22).
     *
     * <p>1.20.4's {@code EntityType.create(World)} is gated:</p>
     *
     * <pre>return !this.isEnabled(world.getEnabledFeatures()) ? null : this.factory.create(this, world);</pre>
     *
     * <p>so a type behind an experimental feature flag comes back {@code null}
     * in an ordinary world even though {@code Registries.ENTITY_TYPE} always
     * contains it. On 1.20.4 that is {@code minecraft:breeze} (and
     * {@code minecraft:wind_charge}), both {@code FeatureFlags.UPDATE_1_21} —
     * which is why the creative picker logged
     * "{@code Couldn't add morph minecraft:breeze, because it's null!}" every
     * session and silently dropped the mob.</p>
     *
     * <p><b>The legacy rule is registry membership</b> — 1.12.2's
     * {@code EntityList} had no feature-flag concept, so every registered living
     * entity was constructible and therefore morphable. Feature flags gate world
     * <i>content</i> (spawning, spawn eggs, {@code /summon}, recipes); a morph is
     * a costume whose entity is never added to the world, and its renderer and
     * model layers are registered unconditionally. So a feature-disabled type
     * falls back to {@link mchorse.metamorph.mixin.EntityTypeAccessor the raw
     * factory}, which is the same constructor call the enabled path makes.</p>
     *
     * <p>Total: any other {@code null} (notably {@code minecraft:player}, whose
     * factory itself answers {@code null}) and any exception come back as
     * {@code null} for the caller to warn-and-skip, never as a crash.</p>
     *
     * @return the created entity, or {@code null} if the type genuinely declines
     *         to build
     */
    @SuppressWarnings("unchecked")
    public static Entity createEntity(World world, EntityType<?> type)
    {
        if (world == null || type == null)
        {
            return null;
        }

        try
        {
            Entity created = type.create(world);

            if (created != null)
            {
                return created;
            }

            /* Not the feature gate — the type itself declined (player). */
            if (type.isEnabled(world.getEnabledFeatures()))
            {
                return null;
            }

            return ((EntityTypeAccessor) type).metamorph$getFactory().create(type, world);
        }
        catch (Exception e)
        {
            Metamorph.LOGGER.warn("Couldn't create an entity of type " + Registries.ENTITY_TYPE.getId(type), e);

            return null;
        }
    }

    /**
     * Whether this entity type is hidden behind an experimental feature flag in
     * a default world (1.20.4: {@code minecraft:breeze}/{@code minecraft:wind_charge}
     * behind {@code UPDATE_1_21}). Such types are still registered, still
     * morphable, and are built through {@link #createEntity}'s fallback.
     */
    public static boolean isFeatureGated(EntityType<?> type)
    {
        return type != null && !type.isEnabled(FeatureFlags.VANILLA_FEATURES);
    }

    /**
     * Get morph from an entity.
     *
     * <p>Faithful port of the legacy accessor: an {@link IMorphProvider} (e.g.
     * a morphed actor) yields its inner morph directly; otherwise the player's
     * {@link IMorphing} capability current morph is returned. Non-player,
     * non-provider entities have no morph (returns {@code null}).</p>
     */
    public static AbstractMorph getMorph(LivingEntity entity)
    {
        if (entity instanceof IMorphProvider)
        {
            return ((IMorphProvider) entity).getMorph();
        }
        else if (entity instanceof PlayerEntity player)
        {
            IMorphing cap = Morphing.get(player);

            if (cap != null)
            {
                return cap.getCurrentMorph();
            }
        }

        return null;
    }

    /**
     * Get slot for given index of the entity's equipment-and-armor iteration
     * order. Mirrors 1.12's {@code getEquipmentAndArmor()} ordering:
     * mainhand, offhand, feet, legs, chest, head.
     */
    public static EquipmentSlot slotForIndex(int index)
    {
        switch (index)
        {
            case 1:
                return EquipmentSlot.OFFHAND;
            case 2:
                return EquipmentSlot.FEET;
            case 3:
                return EquipmentSlot.LEGS;
            case 4:
                return EquipmentSlot.CHEST;
            case 5:
                return EquipmentSlot.HEAD;
            default:
                return EquipmentSlot.MAINHAND;
        }
    }

    /**
     * Get string pose for an entity based on its attributes or a custom pose.
     * Returns one of {@code "flying"}, {@code "riding"}, {@code "sneaking"},
     * {@code "standing"}, with custom-pose override rules.
     */
    public static String getPose(LivingEntity entity, String custom, boolean sneak)
    {
        boolean empty = custom.isEmpty();

        if (!empty && !sneak)
        {
            return custom;
        }

        if (entity.isFallFlying())
        {
            return "flying";
        }
        else if (entity.hasVehicle())
        {
            return "riding";
        }
        else if (entity.isSneaking())
        {
            return sneak && !empty ? custom : "sneaking";
        }

        return "standing";
    }

    /**
     * Force the player's bounding box to match the given morph (or the vanilla
     * default box when {@code morph} is null) — used by the tight-space fit
     * check before a non-forced morph.
     *
     * <p>Port note: the legacy hard-coded vanilla box sizes are preserved
     * verbatim ({@code isElytraFlying}→{@link PlayerEntity#isFallFlying()},
     * {@code isPlayerSleeping}→{@link PlayerEntity#isSleeping()}). The actual
     * box mutation rides {@code AbstractMorph.updateSize*} (the P54 size
     * handler).</p>
     */
    public static void forceUpdateSize(PlayerEntity player, AbstractMorph morph)
    {
        if (morph != null)
        {
            morph.updateSize(player, morph.getWidth(player), morph.getHeight(player));
        }
        else
        {
            float width;
            float height;

            if (player.isFallFlying())
            {
                width = 0.6F;
                height = 0.6F;
            }
            else if (player.isSleeping())
            {
                width = 0.2F;
                height = 0.2F;
            }
            else if (player.isSneaking())
            {
                width = 0.6F;
                height = 1.65F;
            }
            else
            {
                width = 0.6F;
                height = 1.8F;
            }

            AbstractMorph.updateSizeDefault(player, width, height);
        }
    }

    /**
     * Whether the player would fit if morphed into {@code newMorph}: temporarily
     * resize to the new morph's box, test for collision-free space, then restore
     * the current morph's box — exactly like legacy.
     *
     * <p>1.12's {@code world.getCollisionBoxes(player, box).isEmpty()} maps to
     * yarn 1.20.4 {@code World.isSpaceEmpty(Entity, Box)}.</p>
     */
    public static boolean canPlayerMorphFit(PlayerEntity player, AbstractMorph currentMorph, AbstractMorph newMorph)
    {
        boolean canFit;

        forceUpdateSize(player, newMorph);
        canFit = player.getWorld().isSpaceEmpty(player, player.getBoundingBox());
        forceUpdateSize(player, currentMorph);

        return canFit;
    }

    /**
     * Get the entity at which given player is looking at (roadmap P49.1 — the
     * {@code shulker_bullet} action's target picker). Taken from
     * {@code EntityRenderer}.
     *
     * <p>That's a big method... Why Minecraft has lots of these big
     * methods?</p>
     *
     * <p>API translation: {@code getEntitiesInAABBexcluding} →
     * {@link net.minecraft.world.World#getOtherEntities}; {@code canBeCollidedWith()}
     * → {@code canHit()}; {@code AxisAlignedBB.expand(x,y,z)} → {@code Box.stretch};
     * {@code AxisAlignedBB.grow(a,a,a)} → {@code Box.expand(a)};
     * {@code getCollisionBorderSize()} → {@code getTargetingMargin()};
     * {@code aabb.calculateIntercept(a, b)} → {@code Box.raycast(a, b)} (an
     * {@link java.util.Optional} of the hit vector instead of a ray-trace
     * result); {@code getLowestRidingEntity()} → {@code getRootVehicle()}.</p>
     *
     * <p>Deviation (documented): 1.12's {@code Entity.canRiderInteract()} has no
     * 1.20.4 counterpart. It returned {@code false} for everything except boats,
     * and the only caller here passes a morphed player, so the legacy
     * {@code !input.canRiderInteract()} term is folded to a constant
     * {@code true}.</p>
     */
    public static Entity getTargetEntity(Entity input, double maxReach)
    {
        double blockDistance = maxReach;

        BlockHitResult result = rayTrace(input, maxReach, 1.0F);
        Vec3d eyes = new Vec3d(input.getX(), input.getY() + input.getStandingEyeHeight(), input.getZ());

        if (result != null && result.getType() != HitResult.Type.MISS)
        {
            blockDistance = result.getPos().distanceTo(eyes);
        }

        Vec3d look = input.getRotationVec(1.0F);
        Vec3d max = eyes.add(look.x * maxReach, look.y * maxReach, look.z * maxReach);
        Entity target = null;

        float area = 1.0F;

        List<Entity> list = input.getWorld().getOtherEntities(input,
            input.getBoundingBox().stretch(look.x * maxReach, look.y * maxReach, look.z * maxReach).expand(area, area, area),
            (entity) -> entity != null && entity.canHit());

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
                    target = entity;
                    entityDistance = 0.0D;
                }
            }
            else if (intercept.isPresent())
            {
                double eyesDistance = eyes.distanceTo(intercept.get());

                if (eyesDistance < entityDistance || entityDistance == 0.0D)
                {
                    /* Legacy: entity.getLowestRidingEntity() ==
                     * input.getLowestRidingEntity() && !input.canRiderInteract()
                     * — the second term is constant true here (see javadoc). */
                    if (entity.getRootVehicle() == input.getRootVehicle())
                    {
                        if (entityDistance == 0.0D)
                        {
                            target = entity;
                        }
                    }
                    else
                    {
                        target = entity;
                        entityDistance = eyesDistance;
                    }
                }
            }
        }

        return target;
    }

    /**
     * This method is extracted from {@code Entity} class, because it was marked
     * as client side only code (roadmap P49.1).
     *
     * <p>1.12's {@code world.rayTraceBlocks(from, to, stopOnLiquid = false,
     * ignoreBlockWithoutBoundingBox = false, returnLastUncollidableBlock = true)}
     * is {@code RaycastContext(from, to, ShapeType.COLLIDER, FluidHandling.NONE,
     * entity)}. 1.20.4 never returns {@code null}: a miss comes back as a
     * {@link net.minecraft.util.hit.BlockHitResult} of type
     * {@code MISS} at the end position, which callers must check.</p>
     */
    public static BlockHitResult rayTrace(Entity input, double blockReachDistance, float partialTicks)
    {
        Vec3d eyePos = new Vec3d(input.getX(), input.getY() + input.getStandingEyeHeight(), input.getZ());
        Vec3d eyeDir = input.getRotationVec(partialTicks);
        Vec3d eyeReach = eyePos.add(eyeDir.x * blockReachDistance, eyeDir.y * blockReachDistance, eyeDir.z * blockReachDistance);

        return input.getWorld().raycast(new RaycastContext(eyePos, eyeReach,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE, input));
    }
}
