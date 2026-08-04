package mchorse.blockbuster_pack.client.gui;

/**
 * Shared limb-selection contract (port of Blockbuster 2.7.2, roadmap P144;
 * the S6 limb-pick render seam that unblocks P158).
 *
 * <p>Legacy source:
 * {@code blockbuster-1.12/src/main/java/mchorse/blockbuster_pack/client/gui/ILimbSelector.java}
 * — a single-method interface, declared verbatim below. It is implemented by
 * every editor panel that owns a "current limb": {@code GuiPosePanel} (S14) and
 * {@code GuiCustomBodyPartEditor} (S14), and (in the S12 model editor) the limb
 * tab. Each implementation reacts by selecting the limb in its own list
 * <b>and</b> assigning
 * {@code editor.bbRenderer.limb = morph.model.limbs.get(limb)} so the viewport
 * highlights it.</p>
 *
 * <p>The producer side is
 * {@link mchorse.blockbuster.client.gui.dashboard.panels.model_editor.utils.GuiBBModelRenderer}:
 * a Ctrl+click in the preview reads back the stencil pixel, maps it to a limb
 * name and hands it to the {@code picker(Consumer<String>)} callback. Legacy
 * {@code GuiCustomMorph} installed that callback as:</p>
 *
 * <pre>
 * this.bbRenderer.picker((limb) -&gt;
 * {
 *     if (this.view.delegate instanceof ILimbSelector)
 *     {
 *         ((ILimbSelector) this.view.delegate).setLimb(limb);
 *     }
 * });
 * </pre>
 *
 * <p>{@link #select(Object, String)} is that body, factored out so both
 * {@code GuiCustomMorph} and
 * {@code GuiBBModelRenderer#pickLimbs(java.util.function.Supplier)} share one
 * implementation. It is <b>additive</b> — the interface's abstract method and
 * its signature are unchanged, so existing implementors keep compiling.</p>
 */
public interface ILimbSelector
{
    public void setLimb(String limb);

    /**
     * Route a picked limb name into a panel/delegate when it can consume one.
     *
     * <p>Non-{@link ILimbSelector} delegates (and nulls) are ignored, and any
     * exception thrown by {@link #setLimb(String)} is swallowed — legacy
     * {@code GuiCustomBodyPartEditor.setLimb} wrapped its whole body in
     * {@code try { … } catch (Exception e) {}} because a stale limb name (model
     * hot-reloaded under the editor, or a limb that has no list entry) must not
     * take the GUI down. Centralising the guard here keeps that behaviour for
     * every implementor, including ones whose own {@code setLimb} does not
     * catch.</p>
     *
     * @param delegate the currently shown panel (legacy {@code view.delegate})
     * @param limb the limb name resolved by the viewport's stencil pick
     */
    public static void select(Object delegate, String limb)
    {
        if (delegate instanceof ILimbSelector)
        {
            try
            {
                ((ILimbSelector) delegate).setLimb(limb);
            }
            catch (Exception e)
            {
                /* Legacy swallows limb-selection failures — see javadoc. */
            }
        }
    }
}
