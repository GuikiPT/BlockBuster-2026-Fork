package mchorse.blockbuster.client.gui.dashboard.panels.model_editor.utils;

import mchorse.blockbuster.api.Model;
import mchorse.blockbuster.api.ModelLimb;
import mchorse.blockbuster.client.gui.dashboard.panels.model_editor.utils.ModelEditorMath.LimbKind;

/**
 * P137 — headless limb-editor guard logic extracted from {@code GuiModelLimbs}
 * (dupe / rename / remove). The GUI resolves the limb's renderer class into a
 * {@link LimbKind} and shows the matching message modal for anything other than
 * {@link Guard#OK}; the guard rules themselves are golden-tested here.
 *
 * <p>1:1 port of {@code blockbuster-1.12/.../model_editor/tabs/GuiModelLimbs.java}
 * ({@code dupeLimb}, {@code removeLimb}, {@code renameLimb}).</p>
 *
 * <p>S22/P243: {@code GuiModelLimbs} used to re-implement all three guards plus
 * the {@code _copy} uniquifier inline, so the tested copy was not the running
 * one. It now calls straight into here. Every guard tests
 * {@code kind == LimbKind.VANILLA}, which is legacy's
 * {@code getLimbClass(limb) == ModelCustomRenderer.class} — note that includes
 * {@link LimbKind#NONE} (no compiled renderer) among the <i>refusals</i>, as
 * legacy's {@code != } test did.</p>
 */
public final class LimbEditorOps
{
    private LimbEditorOps()
    {}

    /**
     * Outcome of a limb edit guard.
     *
     * <ul>
     *   <li>{@link #OK} — the edit may proceed.</li>
     *   <li>{@link #OBJ_LIMB} — dupe/rename/remove refused on a non-vanilla
     *   (OBJ/VOX) limb → {@code blockbuster.gui.me.limbs.obj_limb} modal.</li>
     *   <li>{@link #LAST_LIMB} — remove refused because the limb's subtree is the
     *   whole model → {@code blockbuster.gui.me.limbs.last_limb} modal.</li>
     * </ul>
     */
    public enum Guard
    {
        OK, OBJ_LIMB, LAST_LIMB
    }

    /** Dupe/rename are refused only for non-vanilla (OBJ/VOX) limbs. */
    public static Guard canDupe(LimbKind kind)
    {
        return kind == LimbKind.VANILLA ? Guard.OK : Guard.OBJ_LIMB;
    }

    /** Rename shares the dupe guard (OBJ/VOX refused). */
    public static Guard canRename(LimbKind kind)
    {
        return kind == LimbKind.VANILLA ? Guard.OK : Guard.OBJ_LIMB;
    }

    /**
     * Remove is refused for non-vanilla limbs (OBJ_LIMB) and, checked second, when
     * the limb's subtree count equals the whole model — i.e. removing it would
     * empty the model (LAST_LIMB). Mirrors the legacy order: the OBJ check comes
     * first.
     */
    public static Guard canRemove(LimbKind kind, Model model, ModelLimb limb)
    {
        if (kind != LimbKind.VANILLA)
        {
            return Guard.OBJ_LIMB;
        }

        if (model.limbs.size() == model.getLimbCount(limb))
        {
            return Guard.LAST_LIMB;
        }

        return Guard.OK;
    }

    /**
     * Port of the {@code dupeLimb} unique-name loop: append {@code _copy} to the
     * clone's name until it no longer collides with an existing limb.
     */
    public static String uniqueDupeName(Model model, String baseName)
    {
        String name = baseName;

        while (model.limbs.containsKey(name))
        {
            name += "_copy";
        }

        return name;
    }
}
