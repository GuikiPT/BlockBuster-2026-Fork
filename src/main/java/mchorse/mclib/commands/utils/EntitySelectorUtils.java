package mchorse.mclib.commands.utils;

import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import mchorse.mclib.McLib;
import net.minecraft.command.EntitySelector;
import net.minecraft.command.EntitySelectorReader;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server-side target-selector resolution for the string-argument commands
 * (P273).
 *
 * <h2>Why this class exists</h2>
 *
 * <p>Legacy 1.12.2 commands resolved a {@code <target>} argument through
 * vanilla's {@code CommandBase.getEntity} / {@code CommandBase.getPlayer} /
 * {@code EntitySelector.matchEntities}, all of which <b>parse</b> the token
 * before matching. The port's first cut mapped those onto
 * {@code new EntitySelectorReader(new StringReader(target)).build()} — which
 * looks like the parse call but is not one. In 1.20.4 the parsing entry point
 * is {@link EntitySelectorReader#read()} (it runs {@code readAtVariable} /
 * {@code readRegular} / {@code buildPredicate} and only <i>then</i> calls
 * {@code build()}); {@link EntitySelectorReader#build()} on its own just
 * assembles an {@link EntitySelector} out of the reader's <b>untouched
 * defaults</b> — {@code limit = 0}, {@code includesNonPlayers = false},
 * {@code playerName = null}, {@code usesAt = false}, no predicates.</p>
 *
 * <p>The consequence was silent and total: the target string was never even
 * looked at. {@code EntitySelector.getPlayers} walks the player manager and
 * returns as soon as {@code list.size() >= limit}, so with {@code limit == 0}
 * <b>every</b> token — {@code @r}, {@code @e[type=…]}, a username, garbage —
 * resolved to the first online player. In singleplayer that is always "the
 * player", which is exactly the reported symptom (<i>"@r does not work in
 * target, where it targets the player"</i>).</p>
 *
 * <h2>Semantics</h2>
 *
 * <ul>
 * <li>{@link #parse(String)} is <b>total</b>: an unparseable or trailing-junk
 * token logs a warning and yields {@code null} — never a crash, never a
 * half-parsed selector. 1.12's {@code TOKEN_PATTERN} was anchored
 * ({@code ^@([pare])(?:\[[^ ]*\])?$}), so rejecting trailing input matches
 * the legacy bar.</li>
 * <li>The full vanilla set works again: {@code @p} (nearest player),
 * {@code @a} (all players), {@code @r} (random player), {@code @s} (the
 * sender), {@code @e} (all entities), each with the {@code [..]} argument
 * block, plus a bare username and a bare UUID.</li>
 * <li>{@link #selectorSource(ServerCommandSource)} raises the source to
 * op-level 2 for the duration of the match. 1.20.4's
 * {@code EntitySelector.checkSourcePermission} rejects any {@code @}-selector
 * from a source below level 2; 1.12.2 had no such per-selector gate — the
 * command's own {@code getRequiredPermissionLevel} was the only authority, and
 * a sender allowed to run the command was allowed to use selectors in it.
 * Every command that reaches this class has already passed that gate, so
 * elevating restores the legacy authority without widening who may run
 * anything.</li>
 * </ul>
 *
 * Legacy source: {@code net.minecraft.command.CommandBase#getEntity/getPlayer}
 * and {@code net.minecraft.command.EntitySelector#matchEntities} (1.12.2).
 */
public final class EntitySelectorUtils
{
    private EntitySelectorUtils()
    {}

    /**
     * Parse a raw target token into a vanilla selector.
     *
     * @return the parsed selector, or {@code null} when the token is empty,
     *         malformed or carries trailing input (warning logged).
     */
    public static EntitySelector parse(String target)
    {
        if (target == null || target.isEmpty())
        {
            McLib.LOGGER.warn("Empty entity target selector");

            return null;
        }

        try
        {
            StringReader reader = new StringReader(target);
            EntitySelector selector = new EntitySelectorReader(reader).read();

            if (reader.canRead())
            {
                McLib.LOGGER.warn("Trailing input in entity target selector: '" + target + "'");

                return null;
            }

            return selector;
        }
        catch (CommandSyntaxException e)
        {
            McLib.LOGGER.warn("Malformed entity target selector: '" + target + "' (" + e.getMessage() + ")");

            return null;
        }
    }

    /**
     * The source selectors are matched against — see the class javadoc for why
     * it is raised to op-level 2.
     */
    public static ServerCommandSource selectorSource(ServerCommandSource sender)
    {
        return sender == null ? null : sender.withMaxLevel(2);
    }

    /**
     * Legacy {@code CommandBase.getEntity}: resolve exactly one entity.
     *
     * @return the matched entity, or {@code null} when the token doesn't parse
     *         or doesn't match exactly one entity.
     */
    public static Entity getEntity(ServerCommandSource sender, String target)
    {
        EntitySelector selector = parse(target);

        if (selector == null || sender == null)
        {
            return null;
        }

        try
        {
            return selector.getEntity(selectorSource(sender));
        }
        catch (CommandSyntaxException e)
        {
            return null;
        }
    }

    /**
     * Legacy {@code CommandBase.getPlayer}: resolve exactly one player.
     *
     * @return the matched player, or {@code null}.
     */
    public static ServerPlayerEntity getPlayer(ServerCommandSource sender, String target)
    {
        EntitySelector selector = parse(target);

        if (selector == null || sender == null)
        {
            return null;
        }

        try
        {
            return selector.getPlayer(selectorSource(sender));
        }
        catch (CommandSyntaxException e)
        {
            return null;
        }
    }

    /**
     * Legacy {@code EntitySelector.matchEntities}: resolve every match.
     *
     * @return the matched entities, or an empty list (legacy returned an empty
     *         list for a non-selector token too).
     */
    public static List<? extends Entity> getEntities(ServerCommandSource sender, String target)
    {
        EntitySelector selector = parse(target);

        if (selector == null || sender == null)
        {
            return Collections.emptyList();
        }

        try
        {
            return selector.getEntities(selectorSource(sender));
        }
        catch (CommandSyntaxException e)
        {
            return Collections.emptyList();
        }
    }
}
