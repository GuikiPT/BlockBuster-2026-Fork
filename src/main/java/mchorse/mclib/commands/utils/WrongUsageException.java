package mchorse.mclib.commands.utils;

import net.minecraft.text.Text;

/**
 * Port of vanilla 1.12.2 {@code WrongUsageException} as used by McLib's
 * command wrapper: carries the pre-built usage {@link Text} (syntax lines +
 * usage translation) that gets displayed through the
 * {@code mclib.commands.wrapper} ("%s") lang key.
 */
public class WrongUsageException extends CommandException
{
    private final Text component;

    public WrongUsageException(Text component)
    {
        super("mclib.commands.wrapper");

        this.component = component;
    }

    public Text getComponent()
    {
        return this.component;
    }
}
