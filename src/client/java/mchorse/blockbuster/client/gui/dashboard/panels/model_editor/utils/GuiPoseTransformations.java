package mchorse.blockbuster.client.gui.dashboard.panels.model_editor.utils;

import mchorse.blockbuster.api.ModelTransform;
import mchorse.mclib.client.gui.framework.elements.input.GuiTransformations;
import net.minecraft.client.MinecraftClient;

/**
 * Pose transformations editor (roadmap P137; blocker for P158/P198/B3).
 *
 * <p>The reusable per-limb translate/scale/rotate widget: a McLib
 * {@link GuiTransformations} (P41) bound to a live {@link ModelTransform}
 * whose {@code translate}/{@code scale}/{@code rotate} float arrays it edits
 * <b>in place</b>. Selecting a limb re-binds it through {@link #set} — nothing
 * is copied, so every trackpad drag mutates the pose the viewport renders.</p>
 *
 * <p>Legacy source (ported 1:1):
 * {@code blockbuster-1.12/src/main/java/mchorse/blockbuster/client/gui/dashboard/panels/model_editor/utils/GuiPoseTransformations.java}.</p>
 *
 * <p><b>Parity notes.</b></p>
 * <ul>
 * <li><b>In-place editing, no null guards.</b> {@link #setT}/{@link #setS}/
 * {@link #setR}/{@link #localTranslate} dereference {@link #trans} exactly as
 * legacy did. When no limb is bound ({@code trans == null}) a trackpad edit
 * throws inside {@code GuiTransformations.internalSetT/S/R}, which
 * catches + {@code printStackTrace()}s it — that swallow is the legacy
 * behaviour and is preserved rather than "fixed" with a null check, since a
 * silent no-op would hide the same bug differently. {@link #set} is the only
 * method that is safe to call unbound — {@link #reset()} is <b>not</b>, in
 * either branch: both {@code super.reset()} and the {@code oldTransform}
 * branch go through {@code fillSetT/S/R}, which reach {@link #setT}
 * <i>directly</i> rather than through the guarded {@code internalSet*}, so
 * the NPE escapes to the caller. Legacy carries the exact same landmine (the
 * reset action is only reachable from a context menu on a bound editor), so
 * it is reproduced rather than papered over.</li>
 * <li><b>{@code oldTransform} snapshot.</b> {@link #reset()} restores the
 * optional {@code oldTransform} (via {@code fillSetT/S/R}, i.e. it both fills
 * the trackpads and writes through to {@link #trans}) instead of zeroing to
 * the identity transform; with no snapshot bound it falls back to
 * {@code super.reset()} (0/1/0). The snapshot is <b>not</b> copied — the
 * caller owns it, and legacy passed e.g. the model's default pose transform,
 * so the reset target follows that object if it later changes.</li>
 * <li><b>{@link #localTranslate}</b> routes through
 * {@link ModelTransform#addTranslation}, which honours the <i>static</i>
 * (shared across every open transform editor) orientation toggle: LOCAL
 * rotates the delta through the transform's own XYZ rotation matrix, GLOBAL
 * adds it verbatim. The bundled {@code addTranslation} takes a
 * {@code boolean local} instead of legacy's
 * {@code GuiTransformations.TransformOrientation} (main source-set can't see
 * client GUI types — see the SEAM(S13) note there); the mapping
 * {@code LOCAL -> true} lives here.</li>
 * <li><b>float truncation.</b> Trackpads are doubles, {@link ModelTransform}
 * arrays are floats — the {@code (float)} casts are legacy's, so pose values
 * round-trip at float precision exactly like 1.12.2.</li>
 * <li><b>Fields are public</b> ({@link #trans}, {@link #oldTransform}) because
 * legacy consumers read/replace them directly.</li>
 * </ul>
 *
 * <p>Consumers: the S12 model editor's poses tab (which subclasses this to
 * call {@code panel.dirty()} on every T/S/R change), the P158 morph pose
 * panels, and the P198 gun editor (a {@code gun} + {@code projectile} pair on
 * the Transforms tab and a single {@code gunFirstPerson} on the first-person
 * tab).</p>
 */
public class GuiPoseTransformations extends GuiTransformations
{
    public ModelTransform trans;
    public ModelTransform oldTransform;

    public GuiPoseTransformations(MinecraftClient mc)
    {
        super(mc);
    }

    public void set(ModelTransform trans)
    {
        this.set(trans, null);
    }

    public void set(ModelTransform trans, ModelTransform oldTransform)
    {
        this.trans = trans;
        this.oldTransform = oldTransform;

        if (trans != null)
        {
            this.fillT(trans.translate[0], trans.translate[1], trans.translate[2]);
            this.fillS(trans.scale[0], trans.scale[1], trans.scale[2]);
            this.fillR(trans.rotate[0], trans.rotate[1], trans.rotate[2]);
        }
    }

    @Override
    public void localTranslate(double x, double y, double z)
    {
        this.trans.addTranslation(x, y, z, GuiStaticTransformOrientation.getOrientation() == TransformOrientation.LOCAL);

        this.fillT(this.trans.translate[0], this.trans.translate[1], this.trans.translate[2]);
    }

    @Override
    public void setT(double x, double y, double z)
    {
        this.trans.translate[0] = (float) x;
        this.trans.translate[1] = (float) y;
        this.trans.translate[2] = (float) z;
    }

    @Override
    public void setS(double x, double y, double z)
    {
        this.trans.scale[0] = (float) x;
        this.trans.scale[1] = (float) y;
        this.trans.scale[2] = (float) z;
    }

    @Override
    public void setR(double x, double y, double z)
    {
        this.trans.rotate[0] = (float) x;
        this.trans.rotate[1] = (float) y;
        this.trans.rotate[2] = (float) z;
    }

    @Override
    protected void reset()
    {
        if (this.oldTransform == null)
        {
            super.reset();
        }
        else
        {
            this.fillSetT(this.oldTransform.translate[0], this.oldTransform.translate[1], this.oldTransform.translate[2]);
            this.fillSetS(this.oldTransform.scale[0], this.oldTransform.scale[1], this.oldTransform.scale[2]);
            this.fillSetR(this.oldTransform.rotate[0], this.oldTransform.rotate[1], this.oldTransform.rotate[2]);
        }
    }
}
