/**
 * Legacy hardcoded model-animation shims (roadmap P81).
 *
 * <p>Direct port of Blockbuster 2.7.2's
 * {@code mchorse.metamorph.client.model.custom} package — the first-iteration
 * Metamorph mob animators. Old {@code model.json} files reference these
 * fully-qualified class names in their {@code "model"} field as the animator;
 * there are still such models on the internet, so the class names <b>and</b> the
 * per-class public {@code ModelCustomRenderer} field names are a de-facto
 * file-format contract that must not change.</p>
 *
 * <p>{@code CustomModelRegistry} resolves the legacy class-name string to a
 * class via an explicit allow-list (no {@code Class.forName} on user input);
 * unknown names log and fall back to a plain {@code ModelCustom} (total-reader
 * rule).</p>
 */
package mchorse.metamorph.client.model.custom;
