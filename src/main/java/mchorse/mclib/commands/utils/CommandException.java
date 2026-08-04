package mchorse.mclib.commands.utils;

/**
 * Port of vanilla 1.12.2 {@code net.minecraft.command.CommandException} for
 * the bundled command framework (roadmap P120–P122). Brigadier has no checked
 * translation-key exception with varargs, so the legacy type is bundled;
 * {@link mchorse.mclib.commands.McCommandBase} catches it at the dispatcher
 * boundary and renders it exactly like 1.12.2's CommandHandler did.
 *
 * <p>Key routing (legacy behavior): keys starting with {@code commands.} (and
 * on 1.20.4 also {@code permissions.}, the modern "player required" key) are
 * vanilla translations shown red as-is; anything else is a mod key that goes
 * through {@code L10n.error} (which prefixes {@code <mod>.error.}).</p>
 */
public class CommandException extends Exception
{
    private final Object[] objects;

    public CommandException(String message, Object... objects)
    {
        super(message);

        this.objects = objects;
    }

    public Object[] getErrorObjects()
    {
        return this.objects;
    }

    /**
     * Whether this exception's message is a vanilla translation key (shown
     * as-is) rather than a mod l10n key (wrapped by {@code L10n.error}).
     */
    public boolean isVanilla()
    {
        String key = this.getMessage();

        return key.startsWith("commands.") || key.startsWith("permissions.") || key.startsWith("argument.");
    }
}
