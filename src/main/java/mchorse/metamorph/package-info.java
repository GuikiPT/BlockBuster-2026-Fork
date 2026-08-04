/**
 * Bundled Metamorph 1.4 — reimplemented inside this mod, original namespace kept.
 *
 * <p>Legacy source of truth:
 * {@code .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/}. The
 * {@code AbstractMorph} NBT model (morphs discriminated by the {@code Name}
 * string tag) lives here. Ported across stages S6/S7.</p>
 *
 * <p>Namespace-invasion note: legacy Blockbuster itself ships classes INSIDE
 * this namespace — {@code mchorse.metamorph.client.model.custom.Model*}
 * (Blaze, Spider, Squid, …). Those are Blockbuster-owned but kept in the
 * Metamorph package for diff-ability, and live in the {@code src/client}
 * source set. The bundled-Metamorph port (S6) must not double-create them.
 * Metamorph's own builtin morph pack is the root-level {@code mchorse.vanilla_pack}
 * package.</p>
 */
package mchorse.metamorph;
