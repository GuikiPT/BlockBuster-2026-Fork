package mchorse.chameleon.metamorph.editor;

import mchorse.metamorph.bodypart.BodyPart;
import mchorse.metamorph.bodypart.GuiBodyPartEditor;
import mchorse.metamorph.client.gui.editor.GuiAbstractMorph;
import net.minecraft.client.MinecraftClient;

/**
 * Metamorph's body-part editor, specialised for Chameleon: selecting or picking
 * a part cross-highlights its bone in the 3D preview.
 *
 * <p>{@link #setupNewBodyPart} pre-rotates a fresh part by 180° on Y and 0° on
 * X, overriding Metamorph's default. That is not cosmetic — Chameleon's bone
 * space is mirrored on X (see {@code ModelParser}), so a part added with
 * Metamorph's default orientation faces backwards.</p>
 *
 * Legacy source: chameleon/.../metamorph/editor/GuiCustomBodyPartEditor.java
 */
public class GuiCustomBodyPartEditor extends GuiBodyPartEditor implements IBonePicker
{
    public GuiCustomBodyPartEditor(MinecraftClient mc, GuiAbstractMorph editor)
    {
        super(mc, editor);
    }

    @Override
    protected void setPart(BodyPart part)
    {
        super.setPart(part);

        if (part != null)
        {
            GuiChameleonMorph parent = (GuiChameleonMorph) this.editor;

            parent.chameleonModelRenderer.boneName = part.limb;
        }
    }

    @Override
    protected void setupNewBodyPart(BodyPart part)
    {
        super.setupNewBodyPart(part);

        part.rotate.x = 0;
        part.rotate.y = 180;
    }

    @Override
    protected void pickLimb(String limbName)
    {
        GuiChameleonMorph parent = (GuiChameleonMorph) this.editor;

        super.pickLimb(limbName);
        parent.chameleonModelRenderer.boneName = limbName;
    }

    @Override
    public void pickBone(String limb)
    {
        try
        {
            this.pickLimb(limb);
            this.limbs.setCurrent(limb);
        }
        catch (Exception e)
        {}
    }
}
