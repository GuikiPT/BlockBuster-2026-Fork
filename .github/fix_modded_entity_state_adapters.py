from pathlib import Path

root = Path.cwd()
entity_morph = root / "src/main/java/mchorse/metamorph/api/morphs/EntityMorph.java"
entity_utils = root / "src/main/java/mchorse/metamorph/api/EntityUtils.java"
compat = root / "src/main/java/mchorse/metamorph/compat/EntityMorphCompatibility.java"

text = entity_morph.read_text()

import_anchor = "import mchorse.metamorph.api.MorphSettings;\n"
compat_import = "import mchorse.metamorph.compat.EntityMorphCompatibility;\n"
if compat_import not in text:
    if import_anchor not in text:
        raise SystemExit("EntityMorph import anchor not found")
    text = text.replace(import_anchor, import_anchor + compat_import, 1)

load_anchor = """        living.deathTime = 0;
        living.hurtTime = 0;
        living.setFireTicks(0);
"""
load_patch = """        /* Optional compatibility adapters run after the ordinary NBT reader.
         * Some mods use a reduced client codec rather than their full saved
         * entity codec, so a private morph dummy needs the missing spawn-state
         * bridge explicitly. */
        EntityMorphCompatibility.applyPersistentState(living, this.entityData, world);

        living.deathTime = 0;
        living.hurtTime = 0;
        living.setFireTicks(0);
"""
if "EntityMorphCompatibility.applyPersistentState" not in text:
    if load_anchor not in text:
        raise SystemExit("EntityMorph persistent-state anchor not found")
    text = text.replace(load_anchor, load_patch, 1)

animation_anchor = """        this.entity.setPose(target.getPose());

        if (this.entity instanceof MobEntity)
"""
animation_patch = """        this.entity.setPose(target.getPose());

        /* Vanilla state is the common baseline. Optional adapters may mirror
         * a mod's own tracked animation controller after it. */
        EntityMorphCompatibility.updateAnimation(this.entity, target);

        if (this.entity instanceof MobEntity)
"""
if "EntityMorphCompatibility.updateAnimation" not in text:
    if animation_anchor not in text:
        raise SystemExit("EntityMorph animation anchor not found")
    text = text.replace(animation_anchor, animation_patch, 1)

entity_morph.write_text(text)

utils = entity_utils.read_text()
utils = utils.replace("import net.minecraft.nbt.AbstractNbtNumber;\n", "")
utils = utils.replace("import net.minecraft.nbt.NbtString;\n", "")
utils = utils.replace(
    "and {@link #compareData} (deliberately\n * shallow, primitives/strings only).",
    "and {@link #compareData}, which performs a complete nested comparison so\n * mod-defined species/form data participates in acquired-morph identity.",
)

old_compare = """    /**
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
"""
new_compare = """    /**
     * Compare two stripped entity-data compounds for acquired-morph identity.
     *
     * <p>The legacy shallow comparison made modern entities whose identity is
     * stored in a nested compound look identical. Cobblemon, for example, uses
     * one entity type for every Pokemon and stores Species/FormId/features
     * below {@code Pokemon}. NBT equality is recursive and order-independent,
     * after {@link #stripEntityNBT(NbtCompound)} has removed volatile fields.</p>
     */
    public static boolean compareData(NbtCompound a, NbtCompound b)
    {
        return java.util.Objects.equals(a, b);
    }
"""
if "return java.util.Objects.equals(a, b);" not in utils:
    if old_compare not in utils:
        raise SystemExit("EntityUtils.compareData anchor not found")
    utils = utils.replace(old_compare, new_compare, 1)
entity_utils.write_text(utils)

compat.parent.mkdir(parents=True, exist_ok=True)
compat.write_text(r'''package mchorse.metamorph.compat;

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

/** Optional adapters for entity mods with non-vanilla identity or animation state. */
public final class EntityMorphCompatibility
{
    private static final String COBBLEMON_ENTITY = "com.cobblemon.mod.common.entity.pokemon.PokemonEntity";
    private static final String COBBLEMON_POKEMON = "com.cobblemon.mod.common.pokemon.Pokemon";
    private static final String COBBLEMON_POSE = "com.cobblemon.mod.common.entity.PoseType";

    private EntityMorphCompatibility()
    {}

    public static void applyPersistentState(LivingEntity entity, NbtCompound entityData, World world)
    {
        if (entity == null || entityData == null || world == null || !isCobblemon(entity))
        {
            return;
        }

        NbtCompound pokemonTag = entityData.getCompound("Pokemon");

        if (pokemonTag.isEmpty())
        {
            Metamorph.LOGGER.warn("EntityMorph: Cobblemon entity data contained no Pokemon compound");
            return;
        }

        Object pokemon = null;

        /* PokemonEntity.load() selects CLIENT_CODEC in a client world, while
         * saved entity NBT contains Pokemon.CODEC. Outside Cobblemon's custom
         * spawn packet that mismatch can fall back to a randomized Pokemon. */
        if (world.isClient())
        {
            pokemon = decodeCobblemonPokemon(entity, pokemonTag, world);

            if (pokemon != null)
            {
                invokeSetter(entity, "setPokemon", pokemon);
            }
        }

        /* A morph dummy never receives Cobblemon's SpawnPokemonPacket, so seed
         * the tracked values its client delegate normally gets from that packet. */
        syncCobblemonIdentity(entity, pokemonTag, pokemon);
    }

    public static void updateAnimation(LivingEntity entity, LivingEntity target)
    {
        if (entity == null || target == null || !isCobblemon(entity))
        {
            return;
        }

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
            Class<?> poseClass = Class.forName(COBBLEMON_POSE, false, entity.getClass().getClassLoader());

            if (poseClass.isEnum())
            {
                @SuppressWarnings({"rawtypes", "unchecked"})
                Object value = Enum.valueOf((Class<? extends Enum>) poseClass.asSubclass(Enum.class), pose);
                setTracked(entity, "POSE_TYPE", value);
            }
        }
        catch (Throwable throwable)
        {
            Metamorph.LOGGER.debug("EntityMorph: unable to mirror Cobblemon pose " + pose, throwable);
        }
    }

    private static boolean isCobblemon(LivingEntity entity)
    {
        return COBBLEMON_ENTITY.equals(entity.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    private static Object decodeCobblemonPokemon(LivingEntity entity, NbtCompound pokemonTag, World world)
    {
        try
        {
            Class<?> pokemonClass = Class.forName(COBBLEMON_POKEMON, false, entity.getClass().getClassLoader());
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
                    Metamorph.LOGGER.warn("EntityMorph: failed to decode saved Cobblemon Pokemon: " + error.message()));
            }

            return result.orElse(null);
        }
        catch (Throwable throwable)
        {
            Metamorph.LOGGER.warn("EntityMorph: failed to restore the saved Cobblemon Pokemon", throwable);
            return null;
        }
    }

    private static void syncCobblemonIdentity(LivingEntity entity, NbtCompound pokemonTag, Object pokemon)
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

        Metamorph.LOGGER.debug("EntityMorph: restored Cobblemon morph identity " + species + " " + aspects);
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
            Metamorph.LOGGER.debug("EntityMorph: unable to read external entity aspects", throwable);
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
            Metamorph.LOGGER.warn("EntityMorph: failed to install external entity state", throwable);
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
            Metamorph.LOGGER.debug("EntityMorph: unable to set external tracked value " + name, throwable);
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

print("Applied nested morph identity and optional modded-entity adapters")
