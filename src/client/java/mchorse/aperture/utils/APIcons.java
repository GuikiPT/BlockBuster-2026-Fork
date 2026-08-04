package mchorse.aperture.utils;

import mchorse.aperture.Aperture;
import mchorse.mclib.client.gui.utils.Icon;
import mchorse.mclib.utils.resources.ResourceLocation;

/**
 * Aperture's own GUI icon sheet (P183). Ported verbatim; the location type
 * is the bundled McLib {@link ResourceLocation} (converted to a vanilla
 * {@code Identifier} at bind time by {@link Icon}).
 *
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/utils/APIcons.java
 */
public class APIcons
{
    public static final ResourceLocation ICONS = new ResourceLocation(Aperture.MOD_ID, "textures/gui/icons.png");

    public static final Icon FORWARD = new Icon(ICONS, 32, 0);
    public static final Icon BACKWARD = new Icon(ICONS, 48, 0);
    public static final Icon FRAME_NEXT = new Icon(ICONS, 64, 0);
    public static final Icon FRAME_PREV = new Icon(ICONS, 80, 0);
    public static final Icon INTERACTIVE = new Icon(ICONS, 160, 0);
    public static final Icon PLANE = new Icon(ICONS, 176, 0);
    public static final Icon HELICOPTER = new Icon(ICONS, 192, 0);
    public static final Icon ENVELOPE = new Icon(ICONS, 208, 0);
    public static final Icon MINEMA = new Icon(ICONS, 224, 0);

    public static final Icon RECORD = new Icon(ICONS, 0, 16);
    public static final Icon KEYFRAMES = new Icon(ICONS, 16, 16);
    public static final Icon ORBIT = new Icon(ICONS, 32, 16);
}
