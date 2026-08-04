/**
 * Bundled McLib 2.4.3 — reimplemented inside this mod, original namespace kept.
 *
 * <p>Legacy source of truth:
 * {@code .tools/legacy-src/mclib/src/main/java/mchorse/mclib/}. Subpackages
 * mirror the legacy layout: {@code client}, {@code commands}, {@code config},
 * {@code events}, {@code math}, {@code network}, {@code permissions},
 * {@code utils}. The legacy {@code core} coremod package is NOT recreated (its
 * 4 ASM patches move to mixins — see {@code docs/COREMOD_LEDGER.md}).</p>
 *
 * <p>Keeping the {@code mchorse.mclib.*} names (rather than a
 * {@code mchorse.blockbuster.bundled.*} rename) maximizes diff-ability against
 * the legacy checkout; there is no classpath collision risk because the legacy
 * McLib jar is never on the same classpath. Ported across stages S1/S6.</p>
 */
package mchorse.mclib;
