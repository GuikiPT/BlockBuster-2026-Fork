package mchorse.blockbuster.client.compat.iris;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Per-pack option deny-list (S21 P218) — <b>deliberately empty</b>.
 *
 * <p>The mechanism exists because rewriting a {@code #define} into a uniform is
 * not always sound: a pack can use an option in a context the eligibility rules
 * in {@link ShaderUniformOption} do not model (a loop bound the driver must
 * unroll, an array index folded at compile time behind a macro the demotion
 * pass never saw), and the symptom is a shader that fails to compile the moment
 * a pack is selected. BBS carries such a list — its
 * {@code ShaderCurves.prohibitedVariables} names photon's
 * {@code WATER_WAVE_ITERATIONS} among others.</p>
 *
 * <h2>Why it ships empty (S21 open question 2, closed 2026-07-25)</h2>
 *
 * <p>Seeding from BBS's list was considered and rejected. BBS rewrites
 * <i>different</i> sources with <i>different</i> rules — its prefix is
 * {@code bbs_}, it has no {@code #if}/{@code case}/array demotion pass, and it
 * does not gate on "every allowed value parses as a number". An entry that is
 * load-bearing there may be entirely handled here by
 * {@link ShaderUniformOption#checkArray(String)}, and copying it would silently
 * remove a working curve id from a user's profile — a parity regression against
 * 1.12.2, which had no deny-list at all. Legacy is the behaviour bar: it shipped
 * the eligibility rules and nothing else, so this port does too, and the list
 * grows one entry at a time from real reports with the failing pack named.</p>
 *
 * <p>Matching is by pack name (as Iris reports it, case-insensitively, with any
 * {@code .zip} suffix ignored) plus option name. {@link #GLOBAL} denies an
 * option in every pack.</p>
 */
public final class ShaderPackDenyList
{
    /** Pseudo pack name denying an option regardless of which pack is loaded. */
    public static final String GLOBAL = "*";

    /**
     * pack name (lower case, {@code .zip} stripped) → denied option names.
     *
     * <p>Empty by design. Add entries as:</p>
     *
     * <pre>{@code
     * deny("photon", "WATER_WAVE_ITERATIONS"); // <issue link> — <symptom>
     * }</pre>
     */
    private static final Map<String, Set<String>> DENIED = new LinkedHashMap<>();

    static
    {
        /* Intentionally empty — see the class javadoc. */
    }

    private ShaderPackDenyList()
    {}

    /** Register a denial. Package-visible so tests can exercise the mechanism. */
    static void deny(String pack, String option)
    {
        DENIED.computeIfAbsent(normalize(pack), k -> new LinkedHashSet<>()).add(option);
    }

    /** Drop every registered denial (tests only; production never calls this). */
    static void clear()
    {
        DENIED.clear();
    }

    /**
     * Is {@code option} forbidden from becoming a uniform under {@code pack}?
     * A {@code null} pack name only ever matches {@link #GLOBAL} entries.
     */
    public static boolean isDenied(String pack, String option)
    {
        if (option == null)
        {
            return false;
        }

        Set<String> global = DENIED.get(GLOBAL);

        if (global != null && global.contains(option))
        {
            return true;
        }

        if (pack == null)
        {
            return false;
        }

        Set<String> denied = DENIED.get(normalize(pack));

        return denied != null && denied.contains(option);
    }

    /** The names denied for a pack, for diagnostics. Never {@code null}. */
    public static Set<String> denied(String pack)
    {
        Set<String> denied = DENIED.get(normalize(pack));

        return denied == null ? Collections.emptySet() : Collections.unmodifiableSet(denied);
    }

    /** True when nothing at all is denied — the shipped state. */
    public static boolean isEmpty()
    {
        return DENIED.isEmpty();
    }

    private static String normalize(String pack)
    {
        if (pack == null)
        {
            return "";
        }

        String name = pack.trim().toLowerCase(Locale.ROOT);

        if (name.endsWith(".zip"))
        {
            name = name.substring(0, name.length() - 4);
        }

        return name;
    }
}
