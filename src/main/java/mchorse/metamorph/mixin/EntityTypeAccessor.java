package mchorse.metamorph.mixin;

import net.minecraft.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Access to {@link EntityType}'s private entity factory (roadmap P252, S22).
 *
 * <p>1.20.4's {@code EntityType.create(World)} is <b>not</b> a plain
 * constructor call — it is</p>
 *
 * <pre>return !this.isEnabled(world.getEnabledFeatures()) ? null : this.factory.create(this, world);</pre>
 *
 * <p>so a type behind an experimental feature flag ({@code minecraft:breeze} and
 * {@code minecraft:wind_charge} require {@code FeatureFlags.UPDATE_1_21} on
 * 1.20.4) answers {@code null} in every ordinary world, even though it is
 * unconditionally present in {@code Registries.ENTITY_TYPE}. 1.12.2's
 * {@code EntityList.createEntityByIDFromName} had no such gate — a registered
 * living entity was always constructible — and Metamorph's rule was
 * <i>registry membership</i>, not "is this content enabled in this world".</p>
 *
 * <p>Feature flags gate <b>world content</b> (spawning, spawn eggs, {@code /summon},
 * recipes); a morph is a costume that is never added to the world. Reaching the
 * factory directly is therefore the modern spelling of the legacy behaviour, and
 * it is the only one available: {@code getEntityFromNbt},
 * {@code loadEntityWithPassengers} and {@code spawn} all funnel through the same
 * gated {@code create}. Accessor only, no injection — nothing vanilla does
 * changes.</p>
 *
 * <p>Used exclusively by {@code mchorse.metamorph.api.EntityUtils#createEntity},
 * which tries the vanilla path first and falls back here only when the type is
 * feature-disabled.</p>
 */
@Mixin(EntityType.class)
public interface EntityTypeAccessor
{
    @SuppressWarnings("rawtypes")
    @Accessor("factory")
    EntityType.EntityFactory metamorph$getFactory();
}
