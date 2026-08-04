package mchorse.mclib.utils;

/**
 * Port of McLib 2.4.3's {@code ModHelper} (roadmap P39).
 *
 * <p>Legacy walked the stack with Forge's {@code Loader} to find the mod
 * container of the calling class — used only by {@code KeybindManager} to
 * group rebindable keys per mod in {@code config/mclib/keybinds.json}. In the
 * bundled single-jar port the "mods" are the bundled subsystems, which kept
 * their original packages, so the same grouping falls out of a package →
 * modid map over a {@link StackWalker} walk. The returned ids match the
 * section names a legacy keybinds.json used ({@code mclib},
 * {@code blockbuster}, {@code metamorph}, {@code aperture}).</p>
 */
public class ModHelper
{
    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    /**
     * Get the modid of the immediate caller of
     * {@code KeybindManager.register} — legacy used a FIXED stack offset
     * ({@code getCallerClass(2)}) and mapped the caller's jar to a Forge
     * {@code ModContainer}; in the bundled single-jar build the package is
     * the equivalent discriminator. Unknown packages → {@code ""} (legacy
     * returned null → empty modid).
     */
    public static String getCallerModId()
    {
        return WALKER.walk(frames -> frames
            .map(frame -> frame.getDeclaringClass().getName())
            .filter(name -> !name.equals("mchorse.mclib.utils.ModHelper"))
            .filter(name -> !name.equals("mchorse.mclib.client.gui.utils.KeybindManager"))
            .findFirst()
            .map(ModHelper::modIdOf)
            .orElse(""));
    }

    public static String modIdOf(String className)
    {
        if (className.startsWith("mchorse.blockbuster."))
        {
            return "blockbuster";
        }
        else if (className.startsWith("mchorse.metamorph."))
        {
            return "metamorph";
        }
        else if (className.startsWith("mchorse.aperture."))
        {
            return "aperture";
        }
        else if (className.startsWith("mchorse.mclib."))
        {
            return "mclib";
        }

        return "";
    }
}
