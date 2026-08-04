package mchorse.chameleon.metamorph.editor;

/**
 * A morph-editor panel that can accept a bone picked out of the 3D preview
 * (ctrl+click stencil picking).
 *
 * <p>Chameleon's equivalent of Blockbuster's {@code ILimbSelector}: the editor
 * routes each pick to whichever panel is currently shown, if it implements
 * this.</p>
 *
 * Legacy source: chameleon/.../metamorph/editor/IBonePicker.java
 */
public interface IBonePicker
{
    public void pickBone(String bone);
}
