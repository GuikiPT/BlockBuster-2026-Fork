package mchorse.vanilla_pack;

import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.GhastEntity;
import net.minecraft.entity.mob.GiantEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MagmaCubeEntity;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.BatEntity;
import net.minecraft.entity.passive.SquidEntity;

/**
 * Entity-morph categorization rule for the Metamorph section (roadmap P57).
 *
 * <p>Extracted, headless-testable core of legacy
 * {@code mchorse.vanilla_pack.MetamorphSection#addMorph} — the switch that
 * decides which creative category a living entity morph lands in. The legacy
 * decision operates on the entity instance via {@code instanceof}; here the
 * same decision is expressed over the entity's class so it can be exercised
 * without spawning entities in a headless test.</p>
 *
 * <p>Legacy 1.12.2 → yarn 1.20.4 class map (verified against the loom-cache
 * named jar):</p>
 * <ul>
 *   <li>boss: {@code EntityDragon}/{@code EntityWither}/{@code EntityGiantZombie}
 *       → {@link EnderDragonEntity}/{@link WitherEntity}/{@link GiantEntity}
 *       (the giant zombie has no 1.20.4 spawn egg but the type exists — kept in
 *       boss).</li>
 *   <li>animal: {@code EntityAnimal} + {@code minecraft:bat} +
 *       {@code minecraft:squid} → {@link AnimalEntity} + {@link BatEntity} +
 *       {@link SquidEntity}.</li>
 *   <li>hostile: {@code EntityMob} + ghast/magma_cube/slime/shulker →
 *       {@link HostileEntity} + {@link GhastEntity}/{@link MagmaCubeEntity}/
 *       {@link SlimeEntity}/{@link ShulkerEntity}.</li>
 * </ul>
 *
 * <p>The order (boss → animal → hostile → generic) is load-bearing and matches
 * legacy. Third-party (non-{@code minecraft:}) mob ids are grouped by their
 * mod-id namespace prefix.</p>
 *
 * <p>SEAM(P50/P52/S14): the full {@code MetamorphSection} — which iterates the
 * entity registry, instantiates each {@code LivingEntity} to strip its NBT into
 * an {@code EntityMorph} via {@code MetamorphFactory}, seeds {@code generic}
 * with {@code BlockMorph}/{@code ItemMorph}/{@code LabelMorph} and the "Notch"
 * player easter egg, drops empty categories and sorts each
 * case-insensitively — lands once {@code EntityMorph}, the factory and the
 * vanilla-pack morphs are in the tree. That class must delegate its per-entity
 * category choice to {@link #getCategory(String, Class)}.</p>
 */
public class MetamorphCategories
{
    /** Category id for miscellaneous / uncategorized entity morphs. */
    public static final String GENERIC = "generic";

    /**
     * Choose the creative category id for an entity morph.
     *
     * @param name the (remapped) entity id, e.g. {@code minecraft:cow} or
     *             {@code mymod:golem}
     * @param cls  the entity class (never instantiated here)
     * @return one of {@code boss}/{@code animal}/{@code hostile}/{@code generic}
     *         for vanilla entities, or the mod-id namespace prefix for
     *         third-party entities
     */
    public static String getCategory(String name, Class<?> cls)
    {
        /* Category for third-party modded mobs — grouped by mod-id prefix */
        if (!name.startsWith("minecraft:"))
        {
            return name.substring(0, name.indexOf(":"));
        }

        if (isAssignable(cls, EnderDragonEntity.class)
            || isAssignable(cls, WitherEntity.class)
            || isAssignable(cls, GiantEntity.class))
        {
            return "boss";
        }

        if (isAssignable(cls, AnimalEntity.class)
            || name.equals("minecraft:bat")
            || name.equals("minecraft:squid"))
        {
            return "animal";
        }

        if (isAssignable(cls, HostileEntity.class)
            || name.equals("minecraft:ghast")
            || name.equals("minecraft:magma_cube")
            || name.equals("minecraft:slime")
            || name.equals("minecraft:shulker"))
        {
            return "hostile";
        }

        return GENERIC;
    }

    private static boolean isAssignable(Class<?> cls, Class<?> parent)
    {
        return cls != null && parent.isAssignableFrom(cls);
    }
}
