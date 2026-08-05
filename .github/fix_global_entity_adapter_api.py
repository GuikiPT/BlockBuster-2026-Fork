from pathlib import Path

root = Path.cwd()
compat_dir = root / "src/main/java/mchorse/metamorph/compat"
utils_path = root / "src/main/java/mchorse/metamorph/api/EntityUtils.java"
compat_dir.mkdir(parents=True, exist_ok=True)

utils = utils_path.read_text()
compat_import = "import mchorse.metamorph.compat.EntityMorphCompatibility;\n"
if compat_import not in utils:
    anchor = "import mchorse.metamorph.Metamorph;\n"
    if anchor not in utils:
        raise SystemExit("EntityUtils import anchor not found")
    utils = utils.replace(anchor, anchor + compat_import, 1)

old = "return java.util.Objects.equals(a, b);"
new = "return EntityMorphCompatibility.compareIdentity(a, b);"
if new not in utils:
    if old not in utils:
        raise SystemExit("EntityUtils deep comparison anchor not found")
    utils = utils.replace(old, new, 1)
utils_path.write_text(utils)

(compat_dir / "EntityMorphAdapter.java").write_text(r'''package mchorse.metamorph.compat;

import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

/**
 * Extension point for entities whose identity or render animation is not fully
 * represented by vanilla entity NBT, pose and limb animation state.
 *
 * <p>External mods or compatibility addons may register an implementation with
 * {@link EntityMorphCompatibility#register(EntityMorphAdapter)} during their
 * initializer, or expose it through Java's {@link java.util.ServiceLoader}.</p>
 */
public interface EntityMorphAdapter
{
    /** Stable adapter id used to replace duplicate registrations. */
    String id();

    /** Whether this adapter handles the supplied live/dummy entity. */
    boolean supports(LivingEntity entity);

    /** Whether this adapter recognizes this saved entity-data shape. */
    default boolean supportsIdentity(NbtCompound entityData)
    {
        return false;
    }

    /**
     * Return an identity-only copy used when deciding whether two acquired
     * morphs are equivalent. The original saved NBT remains untouched and is
     * still used to reconstruct the rendered entity.
     */
    default NbtCompound normalizeIdentity(NbtCompound entityData)
    {
        return entityData.copy();
    }

    /** Restore client/spawn state that ordinary Entity.readNbt did not restore. */
    default void applyPersistentState(LivingEntity entity, NbtCompound entityData, World world)
    {}

    /** Mirror a custom animation controller after vanilla state was copied. */
    default void updateAnimation(LivingEntity entity, LivingEntity target)
    {}
}
''')

(compat_dir / "EntityMorphCompatibility.java").write_text(r'''package mchorse.metamorph.compat;

import mchorse.metamorph.Metamorph;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;

/** Global registry for optional modded-entity morph adapters. */
public final class EntityMorphCompatibility
{
    private static final CopyOnWriteArrayList<EntityMorphAdapter> ADAPTERS = new CopyOnWriteArrayList<>();

    static
    {
        register(new CobblemonEntityMorphAdapter());

        try
        {
            ServiceLoader.load(EntityMorphAdapter.class).forEach(EntityMorphCompatibility::register);
        }
        catch (Throwable throwable)
        {
            Metamorph.LOGGER.warn("Unable to discover entity morph adapters", throwable);
        }
    }

    private EntityMorphCompatibility()
    {}

    /** Register or replace an adapter with the same stable id. */
    public static void register(EntityMorphAdapter adapter)
    {
        Objects.requireNonNull(adapter, "adapter");
        ADAPTERS.removeIf(existing -> existing.id().equals(adapter.id()));
        ADAPTERS.add(adapter);
        Metamorph.LOGGER.info("Registered entity morph adapter: {}", adapter.id());
    }

    public static void unregister(String id)
    {
        ADAPTERS.removeIf(adapter -> adapter.id().equals(id));
    }

    public static List<EntityMorphAdapter> adapters()
    {
        return List.copyOf(ADAPTERS);
    }

    /**
     * Deep NBT equality is the generic default. An adapter can normalize only
     * its identity-bearing fields, preventing volatile nested data from either
     * collapsing every variant or creating one morph per individual entity.
     */
    public static boolean compareIdentity(NbtCompound a, NbtCompound b)
    {
        if (a == null || b == null)
        {
            return a == b;
        }

        for (EntityMorphAdapter adapter : ADAPTERS)
        {
            boolean left = adapter.supportsIdentity(a);
            boolean right = adapter.supportsIdentity(b);

            if (left || right)
            {
                if (left != right)
                {
                    return false;
                }

                try
                {
                    return Objects.equals(adapter.normalizeIdentity(a), adapter.normalizeIdentity(b));
                }
                catch (Throwable throwable)
                {
                    Metamorph.LOGGER.warn("Entity morph adapter {} failed to normalize identity", adapter.id(), throwable);
                    return Objects.equals(a, b);
                }
            }
        }

        return Objects.equals(a, b);
    }

    public static void applyPersistentState(LivingEntity entity, NbtCompound entityData, World world)
    {
        if (entity == null || entityData == null || world == null)
        {
            return;
        }

        for (EntityMorphAdapter adapter : ADAPTERS)
        {
            try
            {
                if (adapter.supports(entity))
                {
                    adapter.applyPersistentState(entity, entityData, world);
                }
            }
            catch (Throwable throwable)
            {
                Metamorph.LOGGER.warn("Entity morph adapter {} failed to restore persistent state", adapter.id(), throwable);
            }
        }
    }

    public static void updateAnimation(LivingEntity entity, LivingEntity target)
    {
        if (entity == null || target == null)
        {
            return;
        }

        for (EntityMorphAdapter adapter : ADAPTERS)
        {
            try
            {
                if (adapter.supports(entity))
                {
                    adapter.updateAnimation(entity, target);
                }
            }
            catch (Throwable throwable)
            {
                Metamorph.LOGGER.warn("Entity morph adapter {} failed to update animation", adapter.id(), throwable);
            }
        }
    }
}
''')

(compat_dir / "CobblemonEntityMorphAdapter.java").write_text(r'''package mchorse.metamorph.compat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import mchorse.metamorph.Metamorph;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.World;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/** Built-in optional adapter; it has no compile-time dependency on Cobblemon. */
public final class CobblemonEntityMorphAdapter implements EntityMorphAdapter
{
    private static final String ENTITY_CLASS = "com.cobblemon.mod.common.entity.pokemon.PokemonEntity";
    private static final String POKEMON_CLASS = "com.cobblemon.mod.common.pokemon.Pokemon";
    private static final String POSE_CLASS = "com.cobblemon.mod.common.entity.PoseType";

    private static final String[] IDENTITY_KEYS = {
        "Species", "FormId", "Gender", "ScaleModifier", "Shiny",
        "PokemonData", "ForcedAspects", "Features", "PersistentData",
        "TeraType", "DmaxLevel", "GmaxFactor"
    };

    @Override
    public String id()
    {
        return "cobblemon";
    }

    @Override
    public boolean supports(LivingEntity entity)
    {
        for (Class<?> type = entity.getClass(); type != null; type = type.getSuperclass())
        {
            if (ENTITY_CLASS.equals(type.getName()))
            {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean supportsIdentity(NbtCompound entityData)
    {
        return entityData.contains("Pokemon", NbtElement.COMPOUND_TYPE)
            && entityData.getCompound("Pokemon").contains("Species", NbtElement.STRING_TYPE);
    }

    @Override
    public NbtCompound normalizeIdentity(NbtCompound entityData)
    {
        NbtCompound normalized = new NbtCompound();
        NbtCompound source = entityData.getCompound("Pokemon");
        NbtCompound identity = new NbtCompound();

        for (String key : IDENTITY_KEYS)
        {
            NbtElement value = source.get(key);

            if (value != null)
            {
                identity.put(key, value.copy());
            }
        }

        normalized.put("Pokemon", identity);
        return normalized;
    }

    @Override
    public void applyPersistentState(LivingEntity entity, NbtCompound entityData, World world)
    {
        NbtCompound pokemonTag = entityData.getCompound("Pokemon");

        if (pokemonTag.isEmpty())
        {
            Metamorph.LOGGER.warn("Cobblemon morph data contained no Pokemon compound");
            return;
        }

        Object pokemon = null;

        /* Cobblemon's entity loader uses CLIENT_CODEC in a client world, but a
         * saved entity contains the full server CODEC. A private morph dummy
         * also never receives Cobblemon's SpawnPokemonPacket. */
        if (world.isClient())
        {
            pokemon = decodePokemon(entity, pokemonTag, world);

            if (pokemon != null)
            {
                invokeSetter(entity, "setPokemon", pokemon);
            }
        }

        syncIdentityTrackers(entity, pokemonTag, pokemon);
    }

    @Override
    public void updateAnimation(LivingEntity entity, LivingEntity target)
    {
        boolean moving = target.limbAnimator.speed > 0.01F
            || target.getVelocity().horizontalLengthSquared() > 0.000025D;
        boolean inWater = target.isSwimming() || target.isSubmergedInWater();
        boolean flying = target.isFallFlying();

        if (target instanceof PlayerEntity player)
        {
            flying = flying || player.getAbilities().flying;
        }

        String pose = inWater
            ? (moving ? "SWIM" : "FLOAT")
            : flying
                ? (moving ? "FLY" : "HOVER")
                : moving ? "WALK" : "STAND";

        setTracked(entity, "MOVING", moving);

        try
        {
            Class<?> poseClass = Class.forName(POSE_CLASS, false, entity.getClass().getClassLoader());

            if (poseClass.isEnum())
            {
                @SuppressWarnings({"rawtypes", "unchecked"})
                Object value = Enum.valueOf((Class<? extends Enum>) poseClass.asSubclass(Enum.class), pose);
                setTracked(entity, "POSE_TYPE", value);
            }
        }
        catch (Throwable throwable)
        {
            Metamorph.LOGGER.debug("Unable to mirror Cobblemon pose {}", pose, throwable);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object decodePokemon(LivingEntity entity, NbtCompound pokemonTag, World world)
    {
        try
        {
            Class<?> pokemonClass = Class.forName(POKEMON_CLASS, false, entity.getClass().getClassLoader());
            Object codecObject = staticValue(pokemonClass, "CODEC");

            if (!(codecObject instanceof Codec<?>))
            {
                return null;
            }

            Codec<Object> codec = (Codec<Object>) codecObject;
            DataResult<Object> decoded = codec.parse(world.getRegistryManager().getOps(NbtOps.INSTANCE), pokemonTag);
            Optional<Object> result = decoded.result();

            if (result.isEmpty())
            {
                decoded.error().ifPresent(error ->
                    Metamorph.LOGGER.warn("Failed to decode saved Cobblemon Pokemon: {}", error.message()));
            }

            return result.orElse(null);
        }
        catch (Throwable throwable)
        {
            Metamorph.LOGGER.warn("Failed to restore the saved Cobblemon Pokemon", throwable);
            return null;
        }
    }

    private static void syncIdentityTrackers(LivingEntity entity, NbtCompound pokemonTag, Object pokemon)
    {
        String species = pokemonTag.getString("Species");

        if (!species.isBlank())
        {
            setTracked(entity, "SPECIES", species);
        }

        Set<String> aspects = reflectedSet(pokemon, "getAspects");

        if (aspects == null)
        {
            aspects = fallbackAspects(pokemonTag);
        }

        setTracked(entity, "ASPECTS", aspects);

        if (pokemonTag.contains("Level", NbtElement.NUMBER_TYPE))
        {
            setTracked(entity, "LABEL_LEVEL", pokemonTag.getInt("Level"));
        }

        if (pokemonTag.contains("Friendship", NbtElement.NUMBER_TYPE))
        {
            setTracked(entity, "FRIENDSHIP", pokemonTag.getInt("Friendship"));
        }

        String caughtBall = pokemonTag.getString("CaughtBall");

        if (!caughtBall.isBlank())
        {
            setTracked(entity, "CAUGHT_BALL", caughtBall);
        }

        Metamorph.LOGGER.debug("Restored Cobblemon morph identity {} {}", species, aspects);
    }

    private static Set<String> fallbackAspects(NbtCompound pokemonTag)
    {
        Set<String> aspects = new LinkedHashSet<>();

        if (pokemonTag.getBoolean("Shiny"))
        {
            aspects.add("shiny");
        }

        if (pokemonTag.contains("ForcedAspects", NbtElement.LIST_TYPE))
        {
            NbtList list = pokemonTag.getList("ForcedAspects", NbtElement.STRING_TYPE);

            for (int i = 0; i < list.size(); i++)
            {
                aspects.add(list.getString(i));
            }
        }

        return aspects;
    }

    private static Set<String> reflectedSet(Object target, String getter)
    {
        if (target == null)
        {
            return null;
        }

        try
        {
            Object value = target.getClass().getMethod(getter).invoke(target);

            if (value instanceof Set<?> set)
            {
                Set<String> result = new LinkedHashSet<>();

                for (Object item : set)
                {
                    if (item != null)
                    {
                        result.add(item.toString());
                    }
                }

                return result;
            }
        }
        catch (Throwable throwable)
        {
            Metamorph.LOGGER.debug("Unable to read external entity aspects", throwable);
        }

        return null;
    }

    private static void invokeSetter(Object target, String name, Object value)
    {
        if (target == null || value == null)
        {
            return;
        }

        try
        {
            for (Method method : target.getClass().getMethods())
            {
                if (method.getName().equals(name)
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isInstance(value))
                {
                    method.invoke(target, value);
                    return;
                }
            }
        }
        catch (Throwable throwable)
        {
            Metamorph.LOGGER.warn("Failed to install external entity state", throwable);
        }
    }

    @SuppressWarnings("unchecked")
    private static void setTracked(LivingEntity entity, String name, Object value)
    {
        if (value == null)
        {
            return;
        }

        try
        {
            Object accessor = staticValue(entity.getClass(), name);

            if (accessor instanceof TrackedData<?> tracked)
            {
                entity.getDataTracker().set((TrackedData<Object>) tracked, value);
            }
        }
        catch (Throwable throwable)
        {
            Metamorph.LOGGER.debug("Unable to set external tracked value {}", name, throwable);
        }
    }

    private static Object staticValue(Class<?> owner, String name) throws ReflectiveOperationException
    {
        try
        {
            Field field = owner.getField(name);

            if (Modifier.isStatic(field.getModifiers()))
            {
                return field.get(null);
            }
        }
        catch (NoSuchFieldException ignored)
        {}

        for (String getter : new String[] {"get" + name, name})
        {
            try
            {
                Method method = owner.getMethod(getter);

                if (Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 0)
                {
                    return method.invoke(null);
                }
            }
            catch (NoSuchMethodException ignored)
            {}
        }

        throw new NoSuchFieldException(owner.getName() + "." + name);
    }
}
''')

print("Installed global entity morph adapter API and optional Cobblemon adapter")
