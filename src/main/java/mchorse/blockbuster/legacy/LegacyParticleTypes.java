package mchorse.blockbuster.legacy;

import mchorse.blockbuster.Blockbuster;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.ParticleTypes;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Legacy 1.12.2 particle-name → modern {@link ParticleType} table (roadmap
 * P163, sits beside the P71 id-translation shim {@link LegacyIdMap}).
 *
 * <p>{@code ParticleMorph} stores the 1.12.2 particle name string in its
 * {@code Type} NBT key (e.g. {@code explode}, {@code reddust},
 * {@code iconcrack}). Those strings are a disk contract, so on 1.20.4 they must
 * be resolved through this table rather than parsed by the vanilla registry
 * (whose ids differ: {@code explode} → {@code poof}, {@code reddust} →
 * {@code dust}, etc.).</p>
 *
 * <p>Total reader: an unknown or removed 1.12.2 name resolves to {@code null}
 * (logged once) and the caller silently skips emission — exactly matching
 * legacy {@code EnumParticleTypes.getByName} returning {@code null}. A small set
 * of names that had no 1.13+ equivalent ({@link #PLACEHOLDERS}) are documented
 * as intentional no-ops rather than mapping errors.</p>
 *
 * <p>Legacy source: {@code net.minecraft.util.EnumParticleTypes} (1.12.2) +
 * {@code mchorse.blockbuster_pack.morphs.ParticleMorph} (Type key handling).</p>
 */
public final class LegacyParticleTypes
{
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    /**
     * 1.12.2 particle names whose particle was removed in the 1.13 flattening
     * and has no faithful modern equivalent. Resolving these yields {@code null}
     * (skip emission) without a "missing mapping" warning — the absence is
     * intentional, not a port gap.
     */
    public static final Set<String> PLACEHOLDERS;

    /**
     * 1.12.2 → 1.20.4 particle-type map. {@link LinkedHashMap} so
     * {@link #legacyNames()} keeps a stable order for tests.
     */
    private static final Map<String, ParticleType<?>> MAP = new LinkedHashMap<String, ParticleType<?>>();

    static
    {
        /* Simple (no-argument) particles */
        MAP.put("explode", ParticleTypes.POOF);
        MAP.put("largeexplode", ParticleTypes.EXPLOSION);
        MAP.put("hugeexplosion", ParticleTypes.EXPLOSION_EMITTER);
        MAP.put("fireworksSpark", ParticleTypes.FIREWORK);
        MAP.put("bubble", ParticleTypes.BUBBLE);
        MAP.put("splash", ParticleTypes.SPLASH);
        MAP.put("wake", ParticleTypes.FISHING);
        MAP.put("suspended", ParticleTypes.UNDERWATER);
        MAP.put("depthsuspend", ParticleTypes.UNDERWATER);
        MAP.put("crit", ParticleTypes.CRIT);
        MAP.put("magicCrit", ParticleTypes.ENCHANTED_HIT);
        MAP.put("smoke", ParticleTypes.SMOKE);
        MAP.put("largesmoke", ParticleTypes.LARGE_SMOKE);
        MAP.put("spell", ParticleTypes.EFFECT);
        MAP.put("instantSpell", ParticleTypes.INSTANT_EFFECT);
        MAP.put("mobSpell", ParticleTypes.ENTITY_EFFECT);
        MAP.put("mobSpellAmbient", ParticleTypes.AMBIENT_ENTITY_EFFECT);
        MAP.put("witchMagic", ParticleTypes.WITCH);
        MAP.put("dripWater", ParticleTypes.DRIPPING_WATER);
        MAP.put("dripLava", ParticleTypes.DRIPPING_LAVA);
        MAP.put("angryVillager", ParticleTypes.ANGRY_VILLAGER);
        MAP.put("happyVillager", ParticleTypes.HAPPY_VILLAGER);
        MAP.put("townaura", ParticleTypes.MYCELIUM);
        MAP.put("note", ParticleTypes.NOTE);
        MAP.put("portal", ParticleTypes.PORTAL);
        MAP.put("enchantmenttable", ParticleTypes.ENCHANT);
        MAP.put("flame", ParticleTypes.FLAME);
        MAP.put("lava", ParticleTypes.LAVA);
        MAP.put("cloud", ParticleTypes.CLOUD);
        MAP.put("snowballpoof", ParticleTypes.ITEM_SNOWBALL);
        MAP.put("slime", ParticleTypes.ITEM_SLIME);
        MAP.put("heart", ParticleTypes.HEART);
        MAP.put("droplet", ParticleTypes.RAIN);
        MAP.put("mobappearance", ParticleTypes.ELDER_GUARDIAN);
        MAP.put("dragonbreath", ParticleTypes.DRAGON_BREATH);
        MAP.put("endRod", ParticleTypes.END_ROD);
        MAP.put("damageIndicator", ParticleTypes.DAMAGE_INDICATOR);
        MAP.put("sweepAttack", ParticleTypes.SWEEP_ATTACK);
        MAP.put("totem", ParticleTypes.TOTEM_OF_UNDYING);
        MAP.put("spit", ParticleTypes.SPIT);

        /* Parameterised particles (built from Args via LegacyIdMap) */
        MAP.put("reddust", ParticleTypes.DUST);
        MAP.put("iconcrack", ParticleTypes.ITEM);
        MAP.put("blockcrack", ParticleTypes.BLOCK);
        MAP.put("blockdust", ParticleTypes.BLOCK);
        MAP.put("fallingdust", ParticleTypes.FALLING_DUST);

        Set<String> placeholders = new LinkedHashSet<String>();

        /* Removed in the 1.13 flattening; intentionally emit nothing */
        placeholders.add("footstep");     /* FOOTSTEP — removed */
        placeholders.add("snowshovel");   /* SNOW_SHOVEL — removed */
        placeholders.add("barrier");      /* BARRIER — became a block marker */
        placeholders.add("take");         /* ITEM_TAKE — never rendered a particle */

        PLACEHOLDERS = Collections.unmodifiableSet(placeholders);
    }

    private LegacyParticleTypes()
    {}

    /**
     * The registered 1.12.2 particle names (mapped ones only, in registration
     * order). Does not include {@link #PLACEHOLDERS}.
     */
    public static Set<String> legacyNames()
    {
        return Collections.unmodifiableSet(MAP.keySet());
    }

    /**
     * Resolve a 1.12.2 particle name to its modern {@link ParticleType}, or
     * {@code null} for an unknown/removed name.
     */
    public static ParticleType<?> type(String legacyName)
    {
        return MAP.get(legacyName);
    }

    /**
     * Build a spawnable {@link ParticleEffect} from a 1.12.2 particle name and
     * its {@code Args} int array (mirrors 1.12.2's {@code int... parameters}).
     * Returns {@code null} when the name is unknown/removed — the caller skips
     * emission, never crashes.
     */
    public static ParticleEffect create(String legacyName, int[] args)
    {
        if (legacyName == null || PLACEHOLDERS.contains(legacyName))
        {
            return null;
        }

        ParticleType<?> type = MAP.get(legacyName);

        if (type == null)
        {
            warn(legacyName);

            return null;
        }

        if (type == ParticleTypes.ITEM)
        {
            int id = args != null && args.length > 0 ? args[0] : 1;
            int meta = args != null && args.length > 1 ? args[1] : 0;
            Item item = LegacyIdMap.item(id, meta);

            return new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(item));
        }

        if (type == ParticleTypes.BLOCK || type == ParticleTypes.FALLING_DUST)
        {
            /* 1.12.2 packed Block.getStateId(state) = id | (meta << 12) */
            int stateId = args != null && args.length > 0 ? args[0] : 0;
            BlockState state = LegacyIdMap.blockState(stateId & 4095, stateId >>> 12);

            @SuppressWarnings("unchecked")
            ParticleType<BlockStateParticleEffect> blockType = (ParticleType<BlockStateParticleEffect>) type;

            return new BlockStateParticleEffect(blockType, state);
        }

        if (type == ParticleTypes.DUST)
        {
            /* 1.12.2 encoded reddust colour in the velocity args, not Args;
             * fall back to vanilla red dust. */
            return new DustParticleEffect(DustParticleEffect.RED, 1.0F);
        }

        return (ParticleEffect) type;
    }

    private static void warn(String legacyName)
    {
        if (WARNED.add(legacyName))
        {
            Blockbuster.LOGGER.warn("No modern particle mapping for legacy particle name '{}'; skipping emission", legacyName);
        }
    }
}
