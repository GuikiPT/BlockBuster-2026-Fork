package mchorse.metamorph.util;

import mchorse.mclib.client.gui.utils.Icon;
import mchorse.mclib.utils.resources.ResourceLocation;

/**
 * Metamorph panel-tab icons (port of Metamorph 1.4's {@code MMIcons},
 * roadmap P59). The icon sheet lives at the same 16x16 grid offsets as
 * 1.12.2 so the bundled {@code metamorph:textures/gui/icons.png} slices
 * identically.
 */
public class MMIcons
{
    public static final ResourceLocation PANEL_ICONS = new ResourceLocation("metamorph", "textures/gui/icons.png");

    public static final Icon USER = new Icon(PANEL_ICONS, 0, 0);
    public static final Icon PROPERTIES = new Icon(PANEL_ICONS, 16, 0);
    public static final Icon ITEM = new Icon(PANEL_ICONS, 32, 0);
    public static final Icon LABEL = new Icon(PANEL_ICONS, 48, 0);
}
