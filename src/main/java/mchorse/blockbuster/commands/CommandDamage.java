package mchorse.blockbuster.commands;

import mchorse.mclib.commands.utils.CommandException;
import mchorse.mclib.commands.utils.EntitySelectorUtils;
import net.minecraft.command.EntitySelector;
import net.minecraft.command.EntitySelectorReader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;

/**
 * Command {@code /damage} — port of legacy {@code commands.CommandDamage}
 * (roadmap P113).
 *
 * <p>{@code /damage <entity> <amount>}: a negative amount <b>heals</b> living
 * entities ({@code setHealth(health + |amount|)} — the documented legacy trick
 * for reviving actors); a positive amount deals out-of-world damage
 * ({@code damage(outOfWorld(), amount)}).</p>
 *
 * <p>1.20.4 mappings: the legacy vanilla {@code getEntity(server, sender,
 * name)} single-entity selector resolution becomes
 * {@link EntitySelectorReader} + {@link EntitySelector#getEntity}; the fixed
 * {@code DamageSource.OUT_OF_WORLD} becomes
 * {@code entity.getDamageSources().outOfWorld()}.</p>
 */
public class CommandDamage extends BBCommandBase
{
    @Override
    public String getName()
    {
        return "damage";
    }

    @Override
    public String getUsage(ServerCommandSource sender)
    {
        return "blockbuster.commands.damage.help";
    }

    @Override
    public String getSyntax()
    {
        return "{l}{6}/{r}damage {7}<entity> <amount>{r}";
    }

    @Override
    public int getRequiredPermissionLevel()
    {
        return 2;
    }

    @Override
    public int getRequiredArgs()
    {
        return 2;
    }

    @Override
    public void executeCommand(MinecraftServer server, ServerCommandSource sender, String[] args) throws CommandException
    {
        float damage = (float) parseDouble(args[1]);
        Entity entity = getEntity(sender, args[0]);

        if (damage < 0 && entity instanceof LivingEntity)
        {
            LivingEntity target = (LivingEntity) entity;

            target.setHealth(target.getHealth() + Math.abs(damage));
        }
        else if (damage > 0)
        {
            entity.damage(entity.getDamageSources().outOfWorld(), damage);
        }
    }

    /**
     * Resolve a single entity from a selector token (legacy vanilla
     * {@code CommandBase.getEntity}). Accepts a player name, UUID or a
     * {@code @}-selector; a parse/no-match failure surfaces as the vanilla
     * error the underlying selector reader raises.
     *
     * <p>P273: goes through {@link EntitySelectorUtils}, which actually
     * <i>parses</i> the token ({@code EntitySelectorReader.read()}) — the
     * earlier {@code .build()} call skipped parsing entirely and resolved
     * every token to the first online player.</p>
     */
    private static Entity getEntity(ServerCommandSource sender, String target) throws CommandException
    {
        Entity entity = EntitySelectorUtils.getEntity(sender, target);

        if (entity == null)
        {
            /* Legacy surfaced the vanilla "no entity found" CommandException;
             * the 1.20.4 equivalent key renders red as-is (isVanilla). */
            throw new CommandException("argument.entity.notfound.entity");
        }

        return entity;
    }
}
