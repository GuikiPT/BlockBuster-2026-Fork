package mchorse.mclib.client.gui.framework.elements.input;

import mchorse.mclib.McLib;
import mchorse.mclib.client.gui.framework.elements.GuiScrollElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.Keybind;
import mchorse.mclib.client.gui.utils.keys.IKey;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The F9 keybind overlay (port of McLib 2.4.3's {@code GuiKeybinds},
 * roadmap P39; the element-side surface landed with P29).
 *
 * Contracts kept: the keybind map is rebuilt (cleared) every frame from
 * {@code KeybindManager.add} calls; the general {@code ""} category is
 * always drawn first; the overlay's scrollbar is on the opposite (left)
 * side; toggled by F9 (registered in {@code GuiBase}).
 */
public class GuiKeybinds extends GuiScrollElement
{
    public Map<String, KeybindCategory> keybinds = new HashMap<String, KeybindCategory>();

    public GuiKeybinds(MinecraftClient mc)
    {
        super(mc);

        this.hideTooltip();
        this.scroll.opposite = true;
    }

    public void addKeybind(Keybind keybind)
    {
        KeybindCategory category = this.keybinds.get(keybind.category.get());

        if (category == null)
        {
            category = new KeybindCategory(keybind.category);
            this.keybinds.put(keybind.category.get(), category);
        }

        category.add(keybind);
    }

    @Override
    public void draw(GuiContext context)
    {
        int cx = 40;
        GuiDraw.drawRect(this.area.x, this.area.y, cx, this.area.ey(), 0xdd000000);
        GuiDraw.drawHorizontalGradientRect(cx, this.area.y, this.area.ex(), this.area.ey(), 0xdd000000, 0);

        super.draw(context);
    }

    @Override
    protected void preDraw(GuiContext context)
    {
        super.preDraw(context);

        int x = this.area.x + 10;
        int y = this.area.y + 10;
        int i = 0;

        KeybindCategory general = this.keybinds.get("");

        /* Legacy assumed the "" category always exists (the F9 keybind
         * itself lives there); guard for safety — total-input rule */
        if (general != null)
        {
            i = general.draw(context, x, y, i) + 10;
        }

        for (KeybindCategory category : this.keybinds.values())
        {
            if (category != general)
            {
                i = category.draw(context, x, y, i) + 10;
            }
        }

        this.keybinds.clear();
        this.scroll.scrollSize = i + 3;
        this.scroll.clamp();
    }

    public static class KeybindCategory
    {
        public IKey title;
        public List<Keybind> keybinds = new ArrayList<Keybind>();
        public boolean shouldClean;

        public KeybindCategory(IKey title)
        {
            this.title = title;
        }

        public void add(Keybind keybind)
        {
            if (this.shouldClean)
            {
                this.keybinds.clear();
                this.shouldClean = false;
            }

            this.keybinds.add(keybind);
        }

        public int draw(GuiContext context, int x, int y, int i)
        {
            int color = 0xff000000 + McLib.primaryColor.get();

            String title = this.title.get();

            if (!title.isEmpty())
            {
                GuiDraw.drawRect(x - 10, y + i - 2, x + GuiDraw.textWidth(context.font, title) + 2, y + i + GuiDraw.fontHeight(context.font) + 2, color);
                GuiDraw.drawString(context.font, title, x, y + i, 0xffffff);
                i += 14;
            }

            for (Keybind keybind : this.keybinds)
            {
                String combo = keybind.getKeyCombo();
                int w = GuiDraw.textWidth(context.font, combo);

                GuiDraw.drawRect(x - 2, y + i - 2, x + w + 2, y + i + GuiDraw.fontHeight(context.font) + 2, color);
                GuiDraw.drawString(context.font, combo, x, y + i, 0xffffff);
                GuiDraw.drawStringWithShadow(context.font, keybind.label.get(), x + w + 5, y + i, 0xffffff);
                i += 14;
            }

            return i;
        }
    }
}
