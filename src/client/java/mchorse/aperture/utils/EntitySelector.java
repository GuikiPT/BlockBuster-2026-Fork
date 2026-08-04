package mchorse.aperture.utils;

import com.google.common.base.Splitter;
import mchorse.aperture.Aperture;
import mchorse.blockbuster.legacy.LegacyIdMap;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client-side 1.12-style entity selector (P175).
 *
 * <p>Legacy Aperture bundled a copy of 1.12's
 * {@code net.minecraft.command.EntitySelector} because the vanilla one
 * didn't work client-side. 1.20.4's Brigadier {@code EntitySelector} is
 * server-command-bound, so this port reimplements the 1.12 argument set
 * ({@code r, rm, l, lm, x, y, z, dx, dy, dz, rx, rxm, ry, rym, c, m, team,
 * name, type, tag} + {@code score_*} accepted-but-unfiltered, exactly like
 * the legacy copy) against yarn {@code Entity} accessors, with 1.12
 * semantics: {@code @p/@a} match players only, {@code @r} shuffles,
 * {@code @p/@r} default {@code c=1}, closest-first sorting for
 * {@code @p/@a/@e}, negative {@code c} reverses.</p>
 *
 * <p>Notes:
 * <ul>
 * <li>The matcher core runs over an explicit candidate list (testable in
 * plain JUnit); the {@link #matchEntities(PlayerEntity, String, Class)}
 * entry point iterates the sender's (client) world.</li>
 * <li>{@code m=} (gamemode) requires server-side player data — like the
 * legacy client copy (which required {@code EntityPlayerMP}), it matches
 * nothing client-side.</li>
 * <li>{@code type=} resolves through {@link #resolveEntityType(String)}: a
 * registered id (modern <i>or</i> modded) wins directly — that is 1.12's
 * {@code EntityList.isRegistered(new ResourceLocation(s))} — and anything
 * else falls through to the P71 id-translation shim, so a camera profile
 * saved in 1.12.2 with {@code @e[type=zombie_pigman]} still targets
 * {@code minecraft:zombified_piglin}. Unresolvable → warning + no match,
 * never a crash.</li>
 * </ul></p>
 *
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/utils/EntitySelector.java
 */
public class EntitySelector
{
    /** This matches the at-tokens introduced for command blocks, including their arguments, if any. */
    private static final Pattern TOKEN_PATTERN = Pattern.compile("^@([pare])(?:\\[([^ ]*)\\])?$");
    private static final Splitter COMMA_SPLITTER = Splitter.on(',').omitEmptyStrings();
    private static final Splitter EQUAL_SPLITTER = Splitter.on('=').limit(2);
    private static final Set<String> VALID_ARGUMENTS = new HashSet<String>();

    static
    {
        for (String argument : new String[] {"r", "rm", "l", "lm", "x", "y", "z", "dx", "dy", "dz", "rx", "rxm", "ry", "rym", "c", "m", "team", "name", "type", "tag"})
        {
            VALID_ARGUMENTS.add(argument);
        }
    }

    /**
     * Whether the given string represents a selector.
     */
    public static boolean isSelector(String selectorStr)
    {
        return TOKEN_PATTERN.matcher(selectorStr).matches();
    }

    /**
     * Match entities in the sender's world (client-side entry point).
     */
    public static <T extends Entity> List<T> matchEntities(PlayerEntity sender, String token, Class<? extends T> targetClass) throws Exception
    {
        List<Entity> candidates = new ArrayList<Entity>();

        if (sender.getWorld() instanceof ClientWorld world)
        {
            for (Entity entity : world.getEntities())
            {
                candidates.add(entity);
            }
        }

        return matchEntities(sender, sender.getPos(), candidates, token, targetClass);
    }

    /**
     * Matcher core over an explicit candidate list.
     */
    public static <T extends Entity> List<T> matchEntities(Entity sender, Vec3d senderPos, Iterable<Entity> candidates, String token, Class<? extends T> targetClass) throws Exception
    {
        Matcher matcher = TOKEN_PATTERN.matcher(token);

        if (!matcher.matches())
        {
            return Collections.<T>emptyList();
        }

        Map<String, String> map = getArgumentMap(matcher.group(2));

        if (!isEntityTypeValid(map))
        {
            return Collections.<T>emptyList();
        }

        String type = matcher.group(1);
        BlockPos blockpos = getBlockPosFromArguments(map, senderPos == null ? BlockPos.ORIGIN : BlockPos.ofFloored(senderPos));
        Vec3d pos = getPosFromArguments(map, senderPos == null ? Vec3d.ZERO : senderPos);

        List<Predicate<Entity>> predicates = new ArrayList<Predicate<Entity>>();

        predicates.addAll(getTypePredicates(map, type));
        predicates.addAll(getXpLevelPredicates(map));
        predicates.addAll(getGamemodePredicates(map));
        predicates.addAll(getTeamPredicates(map));
        predicates.addAll(getNamePredicates(map));
        predicates.addAll(getTagPredicates(map));
        predicates.addAll(getRadiusPredicates(map, pos));
        predicates.addAll(getRotationsPredicates(map));

        List<T> matched = filterResults(map, targetClass, predicates, type, candidates, blockpos);

        return getEntitiesFromPredicates(matched, map, sender, targetClass, type, pos);
    }

    /**
     * Resolve a selector {@code type=} value to a 1.20.4 entity-type id.
     *
     * <p>1.12.2 did a single {@code EntityList.isRegistered(new
     * ResourceLocation(s))} check. The port keeps that as the first step —
     * so already-modern ids <b>and</b> modded ids that are actually
     * registered (e.g. {@code blockbuster:actor}) resolve exactly as they
     * did — and only then routes the string through the central P71
     * id-translation shim, which is what turns a pre-flattening id embedded
     * in a legacy camera profile ({@code zombie_pigman}, {@code
     * villager_golem}, {@code EntityHorse}, …) into its flattened
     * counterpart.</p>
     *
     * <p>Total by contract: an unparseable, unknown or unmapped id returns
     * {@code null} (the caller warns and matches nothing) instead of
     * throwing {@code InvalidIdentifierException} out of the selector.</p>
     *
     * @return the registered 1.20.4 entity-type id, or {@code null}
     */
    public static Identifier resolveEntityType(String id)
    {
        if (id == null)
        {
            return null;
        }

        /* Legacy step: the id as written, if it is registered right now.
         * tryParse is the total form of 1.12's ResourceLocation constructor. */
        Identifier direct = Identifier.tryParse(id);

        if (direct != null && Registries.ENTITY_TYPE.containsId(direct))
        {
            return direct;
        }

        /* P71: pre-flattening entity id embedded in a legacy camera profile */
        EntityType<?> type = LegacyIdMap.entityType(id).orElse(null);

        return type == null ? null : Registries.ENTITY_TYPE.getId(type);
    }

    private static boolean isEntityTypeValid(Map<String, String> params)
    {
        String s = params.get("type");

        if (s == null)
        {
            return true;
        }

        String id = s.startsWith("!") ? s.substring(1) : s;

        if (resolveEntityType(id) != null)
        {
            return true;
        }

        /* Legacy sent commands.generic.entity.invalidType to the sender; the
         * port has no command sender here (the selector is driven by camera
         * modifiers), so it logs — and, like legacy, matches nothing. */
        Aperture.LOGGER.warn("Invalid entity type in selector: '" + id + "'");

        return false;
    }

    private static List<Predicate<Entity>> getTypePredicates(Map<String, String> params, String type)
    {
        String s = params.get("type");

        if (s == null || !type.equals("e") && !type.equals("r"))
        {
            if (!type.equals("e"))
            {
                return Collections.<Predicate<Entity>>singletonList(entity -> entity instanceof PlayerEntity);
            }

            return Collections.<Predicate<Entity>>emptyList();
        }

        final boolean flag = s.startsWith("!");
        final Identifier id = resolveEntityType(flag ? s.substring(1) : s);

        if (id == null)
        {
            /* Unreachable in practice — isEntityTypeValid already bailed out —
             * but kept total: an unresolvable type matches nothing. */
            return Collections.<Predicate<Entity>>singletonList(entity -> false);
        }

        return Collections.<Predicate<Entity>>singletonList(entity ->
            entity != null && Registries.ENTITY_TYPE.getId(entity.getType()).equals(id) != flag);
    }

    private static List<Predicate<Entity>> getXpLevelPredicates(Map<String, String> params)
    {
        List<Predicate<Entity>> list = new ArrayList<Predicate<Entity>>();
        final int i = getInt(params, "lm", -1);
        final int j = getInt(params, "l", -1);

        if (i > -1 || j > -1)
        {
            list.add(entity ->
            {
                if (!(entity instanceof PlayerEntity))
                {
                    return false;
                }

                int level = ((PlayerEntity) entity).experienceLevel;

                return (i <= -1 || level >= i) && (j <= -1 || level <= j);
            });
        }

        return list;
    }

    private static List<Predicate<Entity>> getGamemodePredicates(Map<String, String> params)
    {
        List<Predicate<Entity>> list = new ArrayList<Predicate<Entity>>();
        String s = params.get("m");

        if (s != null)
        {
            /* Gamemode data isn't available client-side — like the legacy
             * client copy (EntityPlayerMP check), matches nothing */
            list.add(entity -> false);
        }

        return list;
    }

    private static List<Predicate<Entity>> getTeamPredicates(Map<String, String> params)
    {
        List<Predicate<Entity>> list = new ArrayList<Predicate<Entity>>();
        String s = params.get("team");
        final boolean flag = s != null && s.startsWith("!");

        if (flag)
        {
            s = s.substring(1);
        }

        if (s != null)
        {
            final String s_f = s;

            list.add(entity ->
            {
                if (!(entity instanceof LivingEntity))
                {
                    return false;
                }

                AbstractTeam team = ((LivingEntity) entity).getScoreboardTeam();
                String name = team == null ? "" : team.getName();

                return name.equals(s_f) != flag;
            });
        }

        return list;
    }

    private static List<Predicate<Entity>> getNamePredicates(Map<String, String> params)
    {
        List<Predicate<Entity>> list = new ArrayList<Predicate<Entity>>();
        String s = params.get("name");
        final boolean flag = s != null && s.startsWith("!");

        if (flag)
        {
            s = s.substring(1);
        }

        if (s != null)
        {
            final String s_f = s;

            list.add(entity -> entity != null && entity.getName().getString().equals(s_f) != flag);
        }

        return list;
    }

    private static List<Predicate<Entity>> getTagPredicates(Map<String, String> params)
    {
        List<Predicate<Entity>> list = new ArrayList<Predicate<Entity>>();
        String s = params.get("tag");
        final boolean flag = s != null && s.startsWith("!");

        if (flag)
        {
            s = s.substring(1);
        }

        if (s != null)
        {
            final String s_f = s;

            list.add(entity -> entity == null ? false : ("".equals(s_f) ? entity.getCommandTags().isEmpty() != flag : entity.getCommandTags().contains(s_f) != flag));
        }

        return list;
    }

    private static List<Predicate<Entity>> getRadiusPredicates(Map<String, String> params, final Vec3d pos)
    {
        double d0 = getInt(params, "rm", -1);
        double d1 = getInt(params, "r", -1);
        final boolean flag = d0 < -0.5D;
        final boolean flag1 = d1 < -0.5D;

        if (flag && flag1)
        {
            return Collections.<Predicate<Entity>>emptyList();
        }

        double d2 = Math.max(d0, 1.0E-4D);
        final double d3 = d2 * d2;
        double d4 = Math.max(d1, 1.0E-4D);
        final double d5 = d4 * d4;

        return Collections.<Predicate<Entity>>singletonList(entity ->
        {
            if (entity == null)
            {
                return false;
            }

            double d6 = pos.squaredDistanceTo(entity.getX(), entity.getY(), entity.getZ());

            return (flag || d6 >= d3) && (flag1 || d6 <= d5);
        });
    }

    private static List<Predicate<Entity>> getRotationsPredicates(Map<String, String> params)
    {
        List<Predicate<Entity>> list = new ArrayList<Predicate<Entity>>();

        if (params.containsKey("rym") || params.containsKey("ry"))
        {
            final int i = MathHelper.wrapDegrees(getInt(params, "rym", 0));
            final int j = MathHelper.wrapDegrees(getInt(params, "ry", 359));

            list.add(entity ->
            {
                if (entity == null)
                {
                    return false;
                }

                int yaw = MathHelper.wrapDegrees(MathHelper.floor(entity.getYaw()));

                return i > j ? yaw >= i || yaw <= j : yaw >= i && yaw <= j;
            });
        }

        if (params.containsKey("rxm") || params.containsKey("rx"))
        {
            final int k = MathHelper.wrapDegrees(getInt(params, "rxm", 0));
            final int l = MathHelper.wrapDegrees(getInt(params, "rx", 359));

            list.add(entity ->
            {
                if (entity == null)
                {
                    return false;
                }

                int pitch = MathHelper.wrapDegrees(MathHelper.floor(entity.getPitch()));

                return k > l ? pitch >= k || pitch <= l : pitch >= k && pitch <= l;
            });
        }

        return list;
    }

    private static <T extends Entity> List<T> filterResults(Map<String, String> params, Class<? extends T> entityClass, List<Predicate<Entity>> inputList, String type, Iterable<Entity> candidates, BlockPos position)
    {
        List<T> list = new ArrayList<T>();
        boolean playersOnly = !type.equals("e");
        int i = getInt(params, "dx", 0);
        int j = getInt(params, "dy", 0);
        int k = getInt(params, "dz", 0);
        int l = getInt(params, "r", -1);

        Box aabb = null;

        if (params.containsKey("dx") || params.containsKey("dy") || params.containsKey("dz"))
        {
            aabb = getAABB(position, i, j, k);
        }
        else if (l >= 0)
        {
            aabb = new Box(position.getX() - l, position.getY() - l, position.getZ() - l, position.getX() + l + 1, position.getY() + l + 1, position.getZ() + l + 1);
        }

        for (Entity entity : candidates)
        {
            if (entity == null || !entityClass.isAssignableFrom(entity.getClass()))
            {
                continue;
            }

            if (playersOnly && !(entity instanceof PlayerEntity))
            {
                continue;
            }

            if (!entity.isAlive())
            {
                continue;
            }

            if (aabb != null && !aabb.intersects(entity.getBoundingBox()))
            {
                continue;
            }

            boolean matched = true;

            for (Predicate<Entity> predicate : inputList)
            {
                if (!predicate.test(entity))
                {
                    matched = false;

                    break;
                }
            }

            if (matched)
            {
                list.add(entityClass.cast(entity));
            }
        }

        return list;
    }

    private static <T extends Entity> List<T> getEntitiesFromPredicates(List<T> matchingEntities, Map<String, String> params, Entity sender, Class<? extends T> targetClass, String type, final Vec3d pos)
    {
        int i = getInt(params, "c", !type.equals("a") && !type.equals("e") ? 1 : 0);

        if (!type.equals("p") && !type.equals("a") && !type.equals("e"))
        {
            if (type.equals("r"))
            {
                Collections.shuffle(matchingEntities);
            }
        }
        else
        {
            Collections.sort(matchingEntities, new Comparator<Entity>()
            {
                @Override
                public int compare(Entity a, Entity b)
                {
                    return Double.compare(a.squaredDistanceTo(pos.x, pos.y, pos.z), b.squaredDistanceTo(pos.x, pos.y, pos.z));
                }
            });
        }

        if (sender != null && targetClass.isAssignableFrom(sender.getClass()) && i == 1 && matchingEntities.contains(sender) && !"r".equals(type))
        {
            matchingEntities = new ArrayList<T>(Collections.singletonList(targetClass.cast(sender)));
        }

        if (i != 0)
        {
            if (i < 0)
            {
                Collections.reverse(matchingEntities);
            }

            matchingEntities = matchingEntities.subList(0, Math.min(Math.abs(i), matchingEntities.size()));
        }

        return matchingEntities;
    }

    private static Box getAABB(BlockPos pos, int x, int y, int z)
    {
        boolean flag = x < 0;
        boolean flag1 = y < 0;
        boolean flag2 = z < 0;
        int i = pos.getX() + (flag ? x : 0);
        int j = pos.getY() + (flag1 ? y : 0);
        int k = pos.getZ() + (flag2 ? z : 0);
        int l = pos.getX() + (flag ? 0 : x) + 1;
        int i1 = pos.getY() + (flag1 ? 0 : y) + 1;
        int j1 = pos.getZ() + (flag2 ? 0 : z) + 1;

        return new Box(i, j, k, l, i1, j1);
    }

    private static BlockPos getBlockPosFromArguments(Map<String, String> params, BlockPos pos)
    {
        return new BlockPos(getInt(params, "x", pos.getX()), getInt(params, "y", pos.getY()), getInt(params, "z", pos.getZ()));
    }

    private static Vec3d getPosFromArguments(Map<String, String> params, Vec3d pos)
    {
        return new Vec3d(getCoordinate(params, "x", pos.x, true), getCoordinate(params, "y", pos.y, false), getCoordinate(params, "z", pos.z, true));
    }

    private static double getCoordinate(Map<String, String> params, String key, double defaultD, boolean offset)
    {
        return params.containsKey(key) ? parseInt(params.get(key), MathHelper.floor(defaultD)) + (offset ? 0.5D : 0.0D) : defaultD;
    }

    private static int getInt(Map<String, String> params, String key, int defaultI)
    {
        return params.containsKey(key) ? parseInt(params.get(key), defaultI) : defaultI;
    }

    /** 1.12 {@code MathHelper.getInt(String, default)} */
    private static int parseInt(String value, int defaultI)
    {
        try
        {
            return Integer.parseInt(value);
        }
        catch (Exception e)
        {
            return defaultI;
        }
    }

    private static Map<String, String> getArgumentMap(String argumentString) throws Exception
    {
        Map<String, String> map = new HashMap<String, String>();

        if (argumentString == null)
        {
            return map;
        }

        for (String s : COMMA_SPLITTER.split(argumentString))
        {
            Iterator<String> iterator = EQUAL_SPLITTER.split(s).iterator();
            String s1 = iterator.next();

            if (!isValidArgument(s1))
            {
                throw new Exception("Invalid selector argument: '" + s + "'");
            }

            map.put(s1, iterator.hasNext() ? iterator.next() : "");
        }

        return map;
    }

    private static boolean isValidArgument(String argument)
    {
        return argument != null && (VALID_ARGUMENTS.contains(argument) || argument.length() > "score_".length() && argument.startsWith("score_"));
    }
}
