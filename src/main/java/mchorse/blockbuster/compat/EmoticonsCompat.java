package mchorse.blockbuster.compat;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Emoticons interop — roadmap P220.
 *
 * <p>Blockbuster 2.7.2 ships <b>zero</b> Emoticons-specific classes. The entire
 * hard-coded integration surface is one token in the Forge {@code @Mod}
 * dependencies string (legacy {@code Blockbuster.java} line 78):</p>
 *
 * <pre>before:emoticons@[%EMOTICONS%,)</pre>
 *
 * <p>i.e. <b>Blockbuster loads before Emoticons</b>. The version placeholder was
 * build-substituted and is Forge-only — it is deliberately not cargo-culted into
 * {@code fabric.mod.json} (see the phase's quirk list).</p>
 *
 * <h2>Why the ordering mattered, and what it maps to here</h2>
 *
 * <p>{@code MorphManager.morphFromNBT} walks {@code factories} in <b>reverse</b>
 * registration order, so the <i>last</i> factory registered wins a {@code Name}
 * collision. "Blockbuster before Emoticons" therefore means Emoticons' factory
 * is registered later and takes priority for any morph name both packs claim.
 * That is the only observable consequence of the token.</p>
 *
 * <p>Fabric Loader has no {@code before:} primitive: ordering is expressed by the
 * <i>dependent</i> side. The documented expectation for any Fabric-era Emoticons
 * is therefore:</p>
 *
 * <ul>
 *   <li>Emoticons declares {@code "depends"} (or {@code "recommends"}) on
 *       {@code "blockbuster"} in its own {@code fabric.mod.json}, which puts
 *       Blockbuster earlier in the loader's sort and reproduces
 *       {@code before:emoticons} exactly;</li>
 *   <li>Emoticons registers its morph factory from its own initializer, i.e.
 *       after {@code Blockbuster#onInitialize} has run
 *       {@code registerBlockbusterMorphs()};</li>
 *   <li>Blockbuster's side is a {@code "suggests"} entry only ({@link #MOD_ID}) —
 *       it must never become a dependency, because the mod's optionality promise
 *       (see {@code HandshakeTest#fabricModJsonDeclaresNoExtraDependencies})
 *       forbids any hard dependency beyond loader/minecraft/java/fabric-api.</li>
 * </ul>
 *
 * <h2>Until such a build exists</h2>
 *
 * <p>Emoticons morphs embedded in imported 1.12.2 scenes, records and sequencers
 * are simply unknown {@code Name}s. They resolve to the
 * {@link mchorse.metamorph.api.morphs.ForeignMorph} placeholder, which is inert
 * at runtime and round-trips the foreign compound byte-for-byte, so nothing is
 * lost while waiting (P220's passthrough guarantee — it holds for <i>any</i>
 * third-party Metamorph-style pack, not just Emoticons).</p>
 *
 * <p>The {@link #isLoaded()} probe exists so the guarantee can be asserted and so
 * a future integration has one place to hook; nothing in the port branches on it
 * today, matching 1.12.2, which also had no runtime Emoticons check.</p>
 */
public final class EmoticonsCompat
{
    /**
     * Mod id token. Confirmed against the legacy Forge dependency string
     * ({@code before:emoticons@[…]}) and mchorse's Forge {@code @Mod} id for
     * Emoticons — {@code emoticons}, lowercase, no namespace. The same token is
     * what {@code fabric.mod.json}'s {@code "suggests"} block carries.
     */
    public static final String MOD_ID = "emoticons";

    private EmoticonsCompat()
    {}

    /**
     * Whether an Emoticons build is present. Always {@code false} on 1.20.4 as
     * of this writing — kept as the single seam a real integration would use.
     */
    public static boolean isLoaded()
    {
        return FabricLoader.getInstance().isModLoaded(MOD_ID);
    }
}
