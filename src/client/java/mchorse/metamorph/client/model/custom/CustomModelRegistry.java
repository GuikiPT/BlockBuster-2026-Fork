package mchorse.metamorph.client.model.custom;

import java.util.HashMap;
import java.util.Map;

import mchorse.blockbuster.client.model.ModelCustom;
import mchorse.metamorph.Metamorph;

/**
 * Legacy custom-model class-name resolver (roadmap P81).
 *
 * <p>Blockbuster 2.7.2 resolved a model's {@code "model"} JSON field to an
 * animator class with {@code Class.forName(model.model)}
 * ({@code ModelLazyLoaderJSON#loadClientModel}). Doing {@code Class.forName} on
 * a disk-supplied string is an arbitrary-class-load footgun, so the port keeps
 * an explicit name&rarr;class allow-list of exactly the legacy animator classes.
 * Every legacy fully-qualified name is registered (renaming would break existing
 * files); an unknown / unresolvable name logs a warning and the caller falls
 * back to a plain {@link ModelCustom} — the total-reader rule.</p>
 */
public final class CustomModelRegistry
{
    /** Legacy fully-qualified class name &rarr; animator class. */
    private static final Map<String, Class<? extends ModelCustom>> CLASSES = new HashMap<String, Class<? extends ModelCustom>>();

    static
    {
        register(ModelExtended.class);
        register(ModelBat.class);
        register(ModelBlaze.class);
        register(ModelChicken.class);
        register(ModelGhast.class);
        register(ModelGuardian.class);
        register(ModelIronGolem.class);
        register(ModelSilverfish.class);
        register(ModelSlime.class);
        register(ModelSpider.class);
        register(ModelSquid.class);
    }

    private CustomModelRegistry()
    {}

    private static void register(Class<? extends ModelCustom> clazz)
    {
        CLASSES.put(clazz.getName(), clazz);
    }

    /**
     * Resolve a legacy {@code model.model} class-name string to an animator
     * class. Blank / unknown names return {@code null} (caller uses plain
     * {@link ModelCustom}); unknown non-blank names additionally log a warning,
     * so a mistyped or dropped animator never crashes the loader.
     */
    public static Class<? extends ModelCustom> resolve(String name)
    {
        if (name == null || name.isEmpty())
        {
            return null;
        }

        Class<? extends ModelCustom> clazz = CLASSES.get(name);

        if (clazz == null)
        {
            Metamorph.log("Unknown custom model class '" + name + "' — falling back to plain ModelCustom.");
        }

        return clazz;
    }

    /** Whether a legacy animator class name is known. */
    public static boolean has(String name)
    {
        return name != null && CLASSES.containsKey(name);
    }
}
