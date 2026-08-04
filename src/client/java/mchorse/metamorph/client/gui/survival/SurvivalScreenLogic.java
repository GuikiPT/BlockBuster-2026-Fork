package mchorse.metamorph.client.gui.survival;

import mchorse.mclib.client.gui.utils.LegacyKeyCodes;
import mchorse.metamorph.api.creative.ICreativeMorphNetwork;
import mchorse.metamorph.api.creative.categories.MorphCategory;
import mchorse.metamorph.api.morphs.AbstractMorph;

/**
 * Headless, pure decision logic for the survival morph menu (roadmap P61).
 *
 * <p>{@link GuiSurvivalScreen} and {@link GuiSurvivalMorphs} are the retained-mode
 * widgets; the load-bearing, parity-sensitive decisions they make are extracted
 * into this helper so they can be unit-tested on their own, without a running
 * client and without going through the widget tree:</p>
 *
 * <ul>
 *   <li>the <b>index-vs-full-NBT packet routing</b> matrix: acquired-list morphs
 *       act through index-based packets ({@code PacketSelectMorph} /
 *       {@code PacketRemoveMorph} / {@code PacketKeybind} / {@code PacketFavorite}),
 *       non-acquired category morphs morph via full-NBT {@code PacketMorph} and
 *       edit locally;</li>
 *   <li>the <b>keybind capture rules</b>: assigning the demorph key is rejected
 *       (display reverts), {@code ESC} clears to {@code -1};</li>
 *   <li>the <b>visibility rule</b>: creative or
 *       {@code allowMorphingIntoCategoryMorphs} shows the whole user section,
 *       otherwise only the acquired category;</li>
 *   <li>the <b>rebuild trigger</b> and the {@code setSelected} equal-instance
 *       remap so {@code indexOf} finds the acquired instance.</li>
 * </ul>
 *
 * Legacy source:
 * .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/client/gui/survival/{GuiSurvivalScreen,GuiSurvivalMorphs}.java
 */
public final class SurvivalScreenLogic
{
    private SurvivalScreenLogic()
    {}

    /* ------------------------------------------------------------------ *
     * Visibility / rebuild                                               *
     * ------------------------------------------------------------------ */

    /**
     * Whether the whole user section (recent + all custom categories) should be
     * shown. Legacy: {@code creative || allowMorphingIntoCategoryMorphs}. When
     * false only the player's acquired category is shown.
     */
    public static boolean showWholeUserSection(boolean creative, boolean allowConfig)
    {
        return creative || allowConfig;
    }

    /**
     * Whether {@code open()} should rebuild the section list. Legacy rebuilt
     * when the creative flag changed, the allow-config flag changed, the player
     * is currently creative (always refresh recents/categories), or no sections
     * exist yet.
     */
    public static boolean shouldRebuild(boolean prevCreative, boolean prevAllowed,
        boolean creative, boolean allowed, boolean sectionsEmpty)
    {
        return prevCreative != creative || prevAllowed != allowed || creative || sectionsEmpty;
    }

    /**
     * Remap a selected morph to its {@code equals}-equal instance inside the
     * acquired category, so index-based packets ({@code indexOf}) resolve to the
     * acquired-list instance rather than the picker's copy. Returns the acquired
     * instance when found, otherwise the original selection (P47 equals parity).
     */
    public static AbstractMorph remapToAcquired(AbstractMorph selected, MorphCategory acquired)
    {
        if (selected == null || acquired == null)
        {
            return selected;
        }

        AbstractMorph found = acquired.getEqual(selected);

        return found != null ? found : selected;
    }

    /* ------------------------------------------------------------------ *
     * Morph / remove routing                                             *
     * ------------------------------------------------------------------ */

    /**
     * Route the "morph" button. Acquired selections send an index-based
     * {@code PacketSelectMorph}; non-acquired category selections send a
     * full-NBT {@code PacketMorph}. Returns {@code true} (the screen closes)
     * when a morph was selected.
     */
    public static boolean morph(ICreativeMorphNetwork net, boolean acquired, int index, AbstractMorph morph)
    {
        if (morph == null)
        {
            return false;
        }

        if (acquired)
        {
            net.selectMorph(index);
        }
        else
        {
            net.morph(morph);
        }

        return true;
    }

    /**
     * Route the "remove" button. Acquired selections send an index-based
     * {@code PacketRemoveMorph}; non-acquired category selections are removed
     * locally (returns {@code false} so the caller performs the local
     * {@code category.remove}). Returns {@code true} when handled remotely.
     */
    public static boolean remove(ICreativeMorphNetwork net, boolean acquired, int index, AbstractMorph morph)
    {
        if (morph == null)
        {
            return false;
        }

        if (acquired)
        {
            net.removeMorph(index);

            return true;
        }

        return false;
    }

    /* ------------------------------------------------------------------ *
     * Keybind capture                                                    *
     * ------------------------------------------------------------------ */

    /**
     * Apply the keybind capture rules to a raw captured keycode.
     *
     * <ul>
     *   <li>capturing the demorph key is <b>rejected</b> — the field reverts to
     *       the morph's current keybind and nothing is sent;</li>
     *   <li>{@code ESC} normalizes to {@code -1} (clears the keybind);</li>
     *   <li>anything else is accepted verbatim.</li>
     * </ul>
     *
     * @param raw the raw captured (legacy LWJGL2) keycode
     * @param demorphKeycode the currently bound demorph key
     * @param currentKeybind the morph's existing keybind (used as the revert
     *        value on rejection)
     */
    public static KeybindResult resolveKeybind(int raw, int demorphKeycode, int currentKeybind)
    {
        if (raw == demorphKeycode)
        {
            return KeybindResult.rejected(currentKeybind);
        }

        int keybind = raw == LegacyKeyCodes.KEY_ESCAPE ? -1 : raw;

        return KeybindResult.accepted(keybind);
    }

    /**
     * Route an accepted keybind assignment. Acquired selections send an
     * index-based {@code PacketKeybind}; non-acquired category selections are
     * edited locally (returns {@code false}). Returns {@code true} when handled
     * remotely.
     */
    public static boolean applyKeybind(ICreativeMorphNetwork net, boolean acquired, int index, int keybind)
    {
        if (acquired)
        {
            net.keybind(index, keybind);

            return true;
        }

        return false;
    }

    /* ------------------------------------------------------------------ *
     * Favorite                                                           *
     * ------------------------------------------------------------------ */

    /**
     * Route the favorite toggle. Acquired selections send an index-based
     * {@code PacketFavorite}; non-acquired category selections toggle the
     * morph's {@code favorite} flag locally (returns {@code false}). Returns
     * {@code true} when handled remotely.
     */
    public static boolean favorite(ICreativeMorphNetwork net, boolean acquired, int index)
    {
        if (acquired)
        {
            net.favorite(index);

            return true;
        }

        return false;
    }

    /* ------------------------------------------------------------------ *
     * Screen-level key handling                                          *
     * ------------------------------------------------------------------ */

    /**
     * Whether a screen-level keypress is the demorph key (which, while the
     * survival screen is open, sends {@code PacketSelectMorph(-1)} to demorph).
     */
    public static boolean isDemorphKey(int keyCode, int demorphKeycode)
    {
        return keyCode == demorphKeycode;
    }

    /** The demorph index sent when the demorph key is pressed while open. */
    public static final int DEMORPH_INDEX = -1;

    /**
     * Result of a keybind capture: either accepted (with the normalized
     * keybind) or rejected (revert the field to {@link #keybind} without
     * sending anything).
     */
    public static final class KeybindResult
    {
        public final boolean accepted;
        public final int keybind;

        private KeybindResult(boolean accepted, int keybind)
        {
            this.accepted = accepted;
            this.keybind = keybind;
        }

        public static KeybindResult accepted(int keybind)
        {
            return new KeybindResult(true, keybind);
        }

        public static KeybindResult rejected(int revertTo)
        {
            return new KeybindResult(false, revertTo);
        }
    }
}
