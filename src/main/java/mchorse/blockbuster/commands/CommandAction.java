package mchorse.blockbuster.commands;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.commands.action.SubCommandActionAppend;
import mchorse.blockbuster.commands.action.SubCommandActionCancel;
import mchorse.blockbuster.commands.action.SubCommandActionClear;
import mchorse.blockbuster.commands.action.SubCommandActionPlay;
import mchorse.blockbuster.commands.action.SubCommandActionRecord;
import mchorse.blockbuster.commands.action.SubCommandActionRequest;
import mchorse.blockbuster.commands.action.SubCommandActionStop;
import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.mclib.commands.SubCommandBase;
import mchorse.mclib.commands.utils.CommandException;
import mchorse.mclib.commands.utils.L10n;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.world.World;

/**
 * Command /action — port of legacy {@code commands.CommandAction} (P120).
 *
 * This command is responsible for recording player actions or playbacking
 * already recorded player actions.
 */
public class CommandAction extends SubCommandBase
{
    public CommandAction()
    {
        /* Register sub-commands in alphabetical order */
        this.add(new SubCommandActionAppend());
        this.add(new SubCommandActionCancel());
        this.add(new SubCommandActionClear());
        this.add(new SubCommandActionPlay());
        this.add(new SubCommandActionRecord());
        this.add(new SubCommandActionRequest());
        this.add(new SubCommandActionStop());
    }

    @Override
    public L10n getL10n()
    {
        return Blockbuster.l10n;
    }

    @Override
    public String getName()
    {
        return "action";
    }

    @Override
    public String getUsage(ServerCommandSource sender)
    {
        return "blockbuster.commands.action.help";
    }

    @Override
    public int getRequiredPermissionLevel()
    {
        return 2;
    }

    /**
     * Create an actor from command line arguments (i.e. String array).
     * Description of every element in array:
     *
     * 1. Ignored (since it's filename)
     * 2. Invincible flag (boolean)
     * 3+. NBT data of the morph
     *
     * Morph NBT parse failures are silent (actor spawns morphless) — legacy
     * quirk, kept.
     */
    public static EntityActor actorFromArgs(String[] args, World world) throws CommandException
    {
        EntityActor actor;
        AbstractMorph morph = null;

        boolean invincible = args.length >= 2 && parseBoolean(args[1]);
        String model = args.length >= 3 ? String.join(" ", SubCommandBase.dropFirstArguments(args, 2)) : null;

        if (model != null)
        {
            try
            {
                morph = MorphManager.INSTANCE.morphFromNBT(StringNbtReader.parse(model));
            }
            catch (Exception e)
            {}
        }

        actor = new EntityActor(world);
        actor.modify(morph, false, true);
        actor.setInvulnerable(invincible);

        return actor;
    }
}
