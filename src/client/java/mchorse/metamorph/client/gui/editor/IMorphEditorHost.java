package mchorse.metamorph.client.gui.editor;

import java.util.List;
import java.util.function.Consumer;

import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.client.gui.creative.GuiCreativeMorphsList.OnionSkin;

/**
 * Seam (SEAM P58) between the morph editor base GUI ({@link GuiAbstractMorph})
 * and the creative morph selector that hosts it.
 *
 * <p>Legacy Metamorph had no interface here at all: {@code GuiAbstractMorph.morphs}
 * was typed {@code GuiCreativeMorphsList} outright
 * (<code>.tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/client/gui/editor/GuiAbstractMorph.java:43</code>),
 * so <b>the whole class was the contract</b> and every panel simply reached
 * through the field. The Fabric split narrows it to this interface; the
 * narrowing is only sound as long as the interface carries everything the
 * panels actually use.</p>
 *
 * <p>It did not. The first cut carried only {@code exit}/{@code getSelected}/
 * {@code markDirty}, and the four panels that dive into a nested morph
 * ({@code GuiBodyPartEditor}, {@code GuiRecordMorph}, {@code GuiParticleMorph},
 * {@code GuiSequencerMorph}) recovered {@code nestEdit} and the onion-skin
 * lists with a {@code (GuiCreativeMorphsList)} cast — a latent
 * {@code ClassCastException} the moment an editor is hosted by anything else.
 * The nested-edit surface below is that missing half, restored from the legacy
 * members the panels called:</p>
 *
 * <ul>
 *   <li>{@code nestEdit(morph, editing, callback)} and
 *       {@code nestEdit(morph, editing, keepViewport, callback)} — legacy
 *       {@code GuiCreativeMorphsList:277}/{@code :282}, same names, same
 *       argument order, the 3-arg one still delegating to the 4-arg one with
 *       {@code keepViewport = false};</li>
 *   <li>{@link #getOnionSkins()} / {@link #getLastOnionSkins()} /
 *       {@link #setLastOnionSkins(List)} — accessors for legacy's two
 *       <i>public fields</i> {@code onionSkins} and {@code lastOnionSkins}
 *       ({@code GuiCreativeMorphsList:79-80}). An interface cannot carry a
 *       field, and this is the only place the port deviates from the legacy
 *       spelling; the fields themselves stay public on
 *       {@code GuiCreativeMorphsList} so the class still diffs 1:1.</li>
 * </ul>
 *
 * <p>Implementors that are not a morph picker (a dashboard panel, a test
 * harness) implement the nested-edit half however they like — but they have to
 * <i>say</i> so at compile time, which is the point: there is no longer a path
 * on which a body-part editor crashes the client because its host was the
 * wrong concrete class.</p>
 */
public interface IMorphEditorHost
{
    /**
     * Close/exit the morph selector (the editor's bottom-left close icon).
     */
    void exit();

    /**
     * The currently selected morph in the selector.
     */
    AbstractMorph getSelected();

    /**
     * Flag the current selection as changed so the host re-saves it.
     */
    void markDirty();

    /**
     * Dive into a nested morph: push the current editor session, then either
     * open the editor on {@code selected} ({@code editing = true}) or the
     * picker ({@code editing = false}). {@code callback} receives the morph
     * chosen on the way back out.
     *
     * <p>{@code keepViewport} preserves the preview camera (and merges the
     * host's live onion skins into {@code lastOnionSkins}) across the dive.</p>
     */
    void nestEdit(AbstractMorph selected, boolean editing, boolean keepViewport, Consumer<AbstractMorph> callback);

    /**
     * Legacy convenience overload — {@code keepViewport = false}.
     */
    default void nestEdit(AbstractMorph selected, boolean editing, Consumer<AbstractMorph> callback)
    {
        this.nestEdit(selected, editing, false, callback);
    }

    /**
     * The host's live onion-skin ghost list (legacy public field
     * {@code onionSkins}). Mutated in place by the sequencer editor, which
     * rebuilds it from the sequence's neighbours. Never null.
     */
    List<OnionSkin> getOnionSkins();

    /**
     * The onion skins carried across a nested edit (legacy public field
     * {@code lastOnionSkins}). May be null — that is legacy's "no ghosts".
     */
    List<OnionSkin> getLastOnionSkins();

    /**
     * @see #getLastOnionSkins()
     */
    void setLastOnionSkins(List<OnionSkin> skins);
}
