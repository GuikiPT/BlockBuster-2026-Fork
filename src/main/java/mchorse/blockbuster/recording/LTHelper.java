package mchorse.blockbuster.recording;

import mchorse.blockbuster.recording.actions.InteractBlockAction;
import mchorse.blockbuster.recording.data.Frame;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Method;
import java.util.List;

/**
 * LittleTiles helper method (roadmap S21 P221.1)
 *
 * <p>This bad boy is responsible contacting LittleTile API (introduced in
 * v1.5.0-pre199_31_mc1.12.2) to allow opening the doors. Big thanks to
 * CreativeMD for helping out with this issue!</p>
 *
 * <h2>What this is</h2>
 *
 * <p>{@link InteractBlockAction#apply} gives LittleTiles <b>first refusal</b> on
 * every recorded block interaction: the recorded frame is turned into an 8-block
 * ray and handed to the LittleTiles API, and only when that API declines
 * ({@code false}) does the vanilla {@code BlockState#onUse} activation run. That
 * is the 1.12.2 contract, verbatim, and it is reproduced here as
 * <b>reflection only</b> — the mod never compiles against LittleTiles.</p>
 *
 * <h2>Probe contract (load-bearing)</h2>
 *
 * <ul>
 *   <li><b>One-shot.</b> {@code method}/{@code weTried} is legacy's exact
 *       guard: the lookup is attempted once per JVM and never retried, so a
 *       LittleTiles hot-loaded later is never found (matches legacy) and a
 *       LittleTiles-less install pays for exactly one failed
 *       {@code Class.forName}.</li>
 *   <li><b>Silent lookup, loud invoke.</b> Lookup failures are swallowed
 *       without a word; invoke failures print a stack trace and return
 *       {@code false}. Keep the asymmetry — it is what surfaced real LT API
 *       breakage to users.</li>
 *   <li><b>Never throws.</b> Every failure path returns {@code false}, which
 *       is precisely "LittleTiles is not installed" and therefore the vanilla
 *       activation.</li>
 * </ul>
 *
 * <h2>Platform reality</h2>
 *
 * <p>Modern LittleTiles is a Forge/NeoForge mod; there is no Fabric 1.20.4
 * build. On this platform the probe therefore <i>always</i> fails and this
 * class is a zero-cost silent fallback — which is exactly how 1.12.2 behaved
 * without LittleTiles installed. The bridge nevertheless ships whole so that
 * the NeoForge target (P221) only has to fill in one row of
 * {@link #API_CLASSES}.</p>
 *
 * @link https://www.curseforge.com/minecraft/mc-mods/littletiles/files/2960578
 * @see InteractBlockAction#apply(net.minecraft.entity.LivingEntity)
 */
public class LTHelper
{
    /**
     * The API method legacy bound to, by name. Unchanged across the LittleTiles
     * rewrite as far as the port can tell from source — but see
     * {@link #API_CLASSES}: <i>unverified</i> for the modern line.
     */
    public static final String METHOD = "playerRightClickServer";

    /**
     * Ray length in blocks. Legacy scaled the look vector by 8 — not by the
     * player's reach — so the contract is a fixed 8-block ray regardless of
     * attributes.
     */
    public static final double RAY_LENGTH = 8;

    /**
     * One candidate entry point for the LittleTiles API.
     *
     * @param name    a class name to probe, or (when {@code probed} is false) a
     *                package/namespace marker that is <b>not</b> a class name
     *                and is never passed to {@code Class.forName}.
     * @param probed  whether {@link #lookup()} actually tries this entry. Only
     *                entries whose signature has been verified against a real
     *                shipped artifact are ever probed; a guessed name would
     *                bind the port to an API that may not exist.
     * @param note    why the entry is (or is not) probed.
     */
    public record ApiCandidate(String name, boolean probed, String note)
    {}

    /**
     * The lookup list, in probe order.
     *
     * <p>Row 1 is the legacy entry point, verified against the 1.12.2 source
     * this port is a rewrite of ({@code LTHelper} bound
     * {@code com.creativemd.littletiles.common.api.LittleTileAPI
     * #playerRightClickServer(EntityPlayer, Vec3d, Vec3d)}).</p>
     *
     * <p>Row 2 is the modern namespace. LittleTiles moved from
     * {@code com.creativemd.*} to {@code team.creative.*} in its 1.16+ rewrite,
     * but <b>no modern LittleTiles artifact is available to this workspace</b>
     * (nothing under the reference MultiMC instances, and the mod ships
     * Forge/NeoForge only, so the Fabric build could not load it anyway).
     * Writing a guessed class + descriptor here would look verified and would
     * silently bind to nothing, so the row carries the namespace only and is
     * never probed. <b>P221 must resolve the real entry point against the
     * shipped NeoForge jar</b> — class name, method name, and whether the first
     * parameter is still the player type (the mapped
     * {@code net.minecraft.world.entity.player.Player}, not yarn's
     * {@link PlayerEntity}) — and flip {@code probed} to true.</p>
     */
    public static final List<ApiCandidate> API_CLASSES = List.of(
        new ApiCandidate(
            "com.creativemd.littletiles.common.api.LittleTileAPI",
            true,
            "LittleTiles v1.5.0-pre199_31_mc1.12.2+ — the entry point 1.12.2 Blockbuster bound to."
        ),
        new ApiCandidate(
            "team.creative.littletiles",
            false,
            "Modern LittleTiles namespace. Entry point unverified (no artifact available); P221 must resolve it against the shipped NeoForge jar before this is probed."
        )
    );

    private static Method method;
    private static boolean weTried;

    /** Headless-test seam: how many {@code Class.forName} attempts happened. */
    private static int lookups;

    /**
     * Ray endpoints for a recorded interaction — the pure half of
     * {@link #playerRightClickServer}, and the only half that can be exercised
     * headlessly (a {@link PlayerEntity} cannot be constructed without a
     * world).
     *
     * <p>Legacy:</p>
     *
     * <pre>
     * Vec3d pos = new Vec3d(frame.x, frame.y, frame.z);
     * Vec3d look = player.getLookVec().scale(8);
     * pos = pos.addVector(0, player.getEyeHeight(), 0);
     * ... method.invoke(null, player, pos, pos.add(look));
     * </pre>
     *
     * <p>The origin is the <b>frame</b> position (not the entity position — the
     * entity may have drifted from the recording) raised by the eye height, and
     * the end is the origin plus the look vector times {@link #RAY_LENGTH}.</p>
     *
     * <p>{@link Vec3d#fromPolar(float, float)} is byte-for-byte 1.12.2's
     * {@code Entity#getVectorForRotation} (verified with {@code javap -c} on the
     * yarn-named 1.20.4 jar: same {@code 0.017453292f}, same {@code -PI} phase
     * shift, same {@code MathHelper} sine table), and 1.12.2's
     * {@code getLookVec()} was {@code getVectorForRotation(rotationPitch,
     * rotationYaw)} — so this is the legacy look vector, not a re-derivation.</p>
     *
     * @return {@code {origin, end}}
     */
    public static Vec3d[] ray(float pitch, float yaw, double x, double y, double z, double eyeHeight)
    {
        Vec3d pos = new Vec3d(x, y, z).add(0, eyeHeight, 0);
        Vec3d look = Vec3d.fromPolar(pitch, yaw).multiply(RAY_LENGTH);

        return new Vec3d[] {pos, pos.add(look)};
    }

    /**
     * Apply the frame rotation to the (fake) player and build its ray.
     *
     * <p>Ordering is load-bearing: pitch/yaw are written <b>before</b> the look
     * vector is computed, and they are deliberately <b>not</b> restored — the
     * rest of the tick's action processing sees the frame rotation, exactly as
     * in 1.12.2.</p>
     *
     * <p>Legacy's {@code player.getEyeHeight()} maps to
     * {@link net.minecraft.entity.Entity#getStandingEyeHeight()} (the pose-aware
     * cached eye height that {@code getEyeY()} itself uses; verified with
     * {@code javap} — the field is recomputed from
     * {@code getEyeHeight(pose, dimensions)} on every dimension/pose change),
     * the same mapping {@code Point}/{@code ManualFixture} use.</p>
     */
    public static Vec3d[] ray(PlayerEntity player, Frame frame)
    {
        player.setPitch(frame.pitch);
        player.setYaw(frame.yaw);

        return ray(player.getPitch(), player.getYaw(), frame.x, frame.y, frame.z, player.getStandingEyeHeight());
    }

    /**
     * Hand the ray to LittleTiles, if it is there.
     *
     * @return whether LittleTiles handled the interaction. {@code false} means
     *         "not installed, or it declined, or it blew up" — all three are
     *         the caller's fall-through to vanilla block activation.
     */
    public static boolean playerRightClickServer(PlayerEntity player, Frame frame)
    {
        lookup();

        if (method == null)
        {
            return false;
        }

        Vec3d[] ray = ray(player, frame);

        return invoke(player, ray[0], ray[1]);
    }

    /**
     * The reflective call itself, split out so the found-API path is testable
     * without a live {@link PlayerEntity} (reflection happily passes
     * {@code null} for a reference parameter).
     *
     * <p>Legacy caught {@code Exception}; this catches {@link Throwable} so a
     * half-present LittleTiles ({@code NoClassDefFoundError}, {@code LinkageError})
     * degrades to vanilla activation instead of killing the playback tick — the
     * same widening P218.2 applied to the ReplayMod probe. The stack trace stays:
     * an installed-but-broken LT API must be visible.</p>
     */
    static boolean invoke(PlayerEntity player, Vec3d origin, Vec3d end)
    {
        if (method == null)
        {
            return false;
        }

        try
        {
            Object object = method.invoke(null, player, origin, end);

            return object instanceof Boolean && ((Boolean) object).booleanValue();
        }
        catch (Throwable t)
        {
            t.printStackTrace();
        }

        return false;
    }

    /**
     * Legacy's one-shot {@code method == null && !weTried} guard, generalised
     * over {@link #API_CLASSES}. Silent by contract. (Package-visible as a
     * headless-test seam — the probe is otherwise only reachable through a call
     * that needs a live player.)
     */
    static void lookup()
    {
        if (method != null || weTried)
        {
            return;
        }

        weTried = true;

        for (ApiCandidate candidate : API_CLASSES)
        {
            if (!candidate.probed())
            {
                continue;
            }

            try
            {
                lookups++;

                Class<?> clazz = Class.forName(candidate.name());

                method = clazz.getMethod(METHOD, PlayerEntity.class, Vec3d.class, Vec3d.class);

                return;
            }
            catch (Throwable t)
            {
                /* Silent: "LittleTiles is not installed" is the normal case. */
            }
        }
    }

    /* ------------------------------------------------------------------ *
     *  Headless-test seams                                                *
     * ------------------------------------------------------------------ */

    /** Headless-test seam: number of {@code Class.forName} attempts so far. */
    static int lookups()
    {
        return lookups;
    }

    /** Headless-test seam: whether the one-shot probe has run. */
    static boolean weTried()
    {
        return weTried;
    }

    /**
     * Headless-test seam: pretend LittleTiles was found (or forget it again),
     * short-circuiting the one-shot probe the way a real hit would.
     */
    static void bind(Method found)
    {
        method = found;
        weTried = true;
    }

    /** Headless-test seam: back to a never-probed JVM. */
    static void reset()
    {
        method = null;
        weTried = false;
        lookups = 0;
    }
}
