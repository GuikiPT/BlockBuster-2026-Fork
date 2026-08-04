/**
 * Blockbuster mod root — the port of Blockbuster 2.7.2 (Forge 1.12.2).
 *
 * <p>Legacy source of truth:
 * {@code blockbuster-1.12/src/main/java/mchorse/blockbuster/}. Package and
 * class names mirror the 1.12.2 tree 1:1 for diff-ability. Subpackages:
 * {@code api}, {@code audio}, {@code capabilities}, {@code commands},
 * {@code common}, {@code events}, {@code network}, {@code recording},
 * {@code utils}, plus {@code aperture} (Aperture integration glue) and
 * {@code legacy} (id-translation shim). The legacy {@code core} coremod
 * package is deliberately NOT recreated — its launchwrapper/ASM patches are
 * replaced by the {@code mixin} / {@code mixin.client} packages (see
 * {@code docs/COREMOD_LEDGER.md}, P2.1).</p>
 *
 * <p>{@code ClientProxy}/{@code CommonProxy} responsibilities dissolve into
 * the two Fabric entrypoints ({@code Blockbuster} main, {@code BlockbusterClient}
 * client) and per-stage managers — do not port them as literal proxy classes.</p>
 */
package mchorse.blockbuster;
