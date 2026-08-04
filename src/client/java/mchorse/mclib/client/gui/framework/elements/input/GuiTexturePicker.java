package mchorse.mclib.client.gui.framework.elements.input;

import mchorse.mclib.McLib;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiIconElement;
import mchorse.mclib.client.gui.framework.elements.context.GuiSimpleContextMenu;
import mchorse.mclib.client.gui.framework.elements.input.multiskin.GuiMultiSkinEditor;
import mchorse.mclib.client.gui.framework.elements.list.GuiFolderEntryListElement;
import mchorse.mclib.client.gui.framework.elements.list.GuiListElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.GuiUtils;
import mchorse.mclib.client.gui.utils.Icons;
import mchorse.mclib.client.gui.utils.LegacyKeyCodes;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.utils.ColorUtils;
import mchorse.mclib.utils.NBTUtils;
import mchorse.mclib.utils.Timer;
import mchorse.mclib.utils.files.FileTree;
import mchorse.mclib.utils.files.GlobalTree;
import mchorse.mclib.utils.files.entries.AbstractEntry;
import mchorse.mclib.utils.files.entries.FileEntry;
import mchorse.mclib.utils.files.entries.FolderEntry;
import mchorse.mclib.utils.resources.FilteredResourceLocation;
import mchorse.mclib.utils.resources.MultiResourceLocation;
import mchorse.mclib.utils.resources.RLUtils;
import mchorse.mclib.utils.resources.ResourceLocation;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Texture picker GUI
 *
 * This bad boy allows picking a texture from the file browser, and also
 * it allows creating multi-skins. See {@link MultiResourceLocation} for
 * more information.
 *
 * <p>Port of McLib 2.4.3's {@code GuiTexturePicker} (roadmap P40). Pure
 * composition of the P33/P35/P32 widgets over the P17 {@link FileTree};
 * differences from legacy are behind the {@link #textureBinder} seam:</p>
 *
 * <ul>
 * <li>legacy validated a selection with
 * {@code mc.renderEngine.bindTexture(rl)} and read preview sizes with
 * {@code glGetTexLevelParameteri} — both now go through
 * {@link TextureBinder}; S7's texture/multiskin module installs the real
 * binder (routing multi-skins through its {@code TextureProcessor} cache).
 * With no binder installed (headless, or before S7) selections are accepted
 * unvalidated and the preview quad is skipped.</li>
 * <li>clipboard {@code {RL:...}} SNBT goes through
 * {@code NBTUtils.parseSnbtCompound} — tolerant of legacy 1.12.2 indexed
 * list syntax.</li>
 * </ul>
 */
public class GuiTexturePicker extends GuiElement
{
    /**
     * P40→S7 seam: texture binding/size queries for selection validation and
     * preview rendering. Null until S7 installs the real implementation.
     */
    public static TextureBinder textureBinder;

    public GuiElement right;
    public GuiTextElement text;
    public GuiButtonElement close;
    public GuiButtonElement folder;
    public GuiFolderEntryListElement picker;

    public GuiButtonElement multi;
    public GuiFRLListElement multiList;
    public GuiMultiSkinEditor editor;

    public GuiElement buttons;
    public GuiIconElement add;
    public GuiIconElement remove;
    public GuiIconElement edit;

    public Consumer<ResourceLocation> callback;

    public MultiResourceLocation multiRL;
    public FilteredResourceLocation currentFRL;
    public ResourceLocation current;
    public FileTree tree = GlobalTree.TREE;

    private Timer lastTyped = new Timer(1000);
    private Timer lastChecked = new Timer(1000);
    private String typed = "";

    public GuiTexturePicker(MinecraftClient mc, Consumer<ResourceLocation> callback)
    {
        super(mc);

        this.right = new GuiElement(mc);
        this.text = new GuiTextElement(mc, 1000, (str) -> this.selectCurrent(str.isEmpty() ? null : RLUtils.create(str)));
        this.text.context(() ->
        {
            GuiSimpleContextMenu menu = new GuiSimpleContextMenu(mc);
            ResourceLocation location = this.parseRL();

            menu.action(Icons.COPY, IKey.lang("mclib.gui.context.multi_skin.copy"), this::copyRL);

            if (location != null)
            {
                menu.action(Icons.PASTE, IKey.lang("mclib.gui.context.multi_skin.paste"), () -> this.pasteRL(location));
            }

            return menu;
        });
        this.close = new GuiButtonElement(mc, IKey.str("X"), (b) -> this.close());
        this.folder = new GuiButtonElement(mc, IKey.lang("mclib.gui.open_folder"), (b) -> this.openFolder());
        this.picker = new GuiFolderEntryListElement(mc, (entry) ->
        {
            ResourceLocation rl = entry.resource;

            this.selectCurrent(rl);
            this.text.setText(rl == null ? "" : rl.toString());
        }) {
            @Override
            public void setFolder(FolderEntry folder)
            {
                super.setFolder(folder);

                GuiTexturePicker.this.updateFolderButton();
            }
        };
        this.picker.cancelScrollEdge();

        this.multi = new GuiButtonElement(mc, IKey.lang("mclib.gui.multi_skin"), (b) -> this.toggleMultiSkin());
        this.multiList = new GuiFRLListElement(mc, (list) -> this.setFRL(list.get(0)));

        this.editor = new GuiMultiSkinEditor(mc, this);
        this.editor.setVisible(false);

        this.buttons = new GuiElement(mc);
        this.add = new GuiIconElement(mc, Icons.ADD, (b) -> this.addMultiSkin());
        this.remove = new GuiIconElement(mc, Icons.REMOVE, (b) -> this.removeMultiSkin());
        this.edit = new GuiIconElement(mc, Icons.EDIT, (b) -> this.toggleEditor());

        this.right.flex().relative(this).wh(1F, 1F);
        this.text.flex().relative(this.multi).x(1F, 20).wTo(this.close.flex(), -5).h(20);
        this.close.flex().relative(this).set(0, 10, 20, 20).x(1, -30);
        this.folder.flex().relative(this.right).set(0, 0, 80, 20).x(1, -10).y(1, -10).anchor(1F, 1F);
        this.picker.flex().relative(this.right).set(10, 30, 0, 0).w(1, -10).h(1, -30);

        this.multi.flex().relative(this).set(10, 10, 100, 20);
        this.multiList.flex().relative(this).set(10, 35, 100, 0).hTo(this.buttons.flex());
        this.editor.flex().relative(this).set(120, 0, 0, 0).w(1F, -120).h(1F);

        this.buttons.flex().relative(this).y(1F, -20).wTo(this.right.area).h(20);
        this.add.flex().relative(this.buttons).set(0, 0, 20, 20);
        this.remove.flex().relative(this.add).set(20, 0, 20, 20);
        this.edit.flex().relative(this.buttons).wh(20, 20).x(1F, -20);

        this.right.add(this.text, this.picker, this.folder);
        this.buttons.add(this.add, this.remove, this.edit);
        this.add(this.multi, this.multiList, this.close, this.right, this.editor, this.buttons);

        this.callback = callback;

        this.fill(null);
        this.markContainer();
    }

    private ResourceLocation parseRL()
    {
        ResourceLocation location = null;

        try
        {
            NbtCompound compound = NBTUtils.parseSnbtCompound(GuiUtils.getClipboardString());

            if (compound != null)
            {
                location = RLUtils.create(compound.get("RL"));
            }
        }
        catch (Exception e)
        {}

        return location;
    }

    private void copyRL()
    {
        NbtElement location = RLUtils.writeNbt(this.multiRL != null ? this.multiRL : this.current);

        if (location == null)
        {
            GuiUtils.setClipboardString("");
        }
        else
        {
            NbtCompound tag = new NbtCompound();

            tag.put("RL", location);

            GuiUtils.setClipboardString(tag.toString());
        }
    }

    private void pasteRL(ResourceLocation location)
    {
        this.setMultiSkin(location, true);
    }

    public void close()
    {
        boolean wasVisible = this.getParent() != null;

        this.editor.close();
        this.removeFromParent();

        if (this.callback != null && wasVisible)
        {
            if (this.multiRL != null)
            {
                this.multiRL.recalculateId();
            }

            this.callback.accept(this.multiRL != null ? this.multiRL : this.current);
        }
    }

    public void refresh()
    {
        this.picker.update();
        this.updateFolderButton();
    }

    public void openFolder()
    {
        if (this.picker.parent != null && this.picker.parent.file != null)
        {
            GuiUtils.openFolder(this.picker.parent.file.getAbsolutePath());
        }
    }

    public void updateFolderButton()
    {
        this.folder.setEnabled(this.picker.parent != null && this.picker.parent.file != null);
    }

    public void fill(ResourceLocation skin)
    {
        this.setMultiSkin(skin, false);
    }

    /**
     * Add a {@link ResourceLocation} to the multiRL
     */
    private void addMultiSkin()
    {
        FilteredResourceLocation rl = this.currentFRL.copy();

        this.multiList.add(rl);
        this.multiList.setIndex(this.multiList.getList().size() - 1);
        this.setFRL(this.multiList.getCurrent().get(0));
    }

    /**
     * Remove currently selected {@link ResourceLocation} from multiRL
     */
    private void removeMultiSkin()
    {
        int index = this.multiList.getIndex();

        if (index >= 0 && this.multiList.getList().size() > 1)
        {
            this.multiList.getList().remove(index);
            this.multiList.update();
            this.multiList.setIndex(index - 1);

            if (this.multiList.getIndex() >= 0)
            {
                this.setFRL(this.multiList.getCurrent().get(0));
            }
        }
    }

    private void setFRL(FilteredResourceLocation location)
    {
        this.currentFRL = location;
        this.displayCurrent(location.path);
        this.editor.setLocation(location);
    }

    private void toggleEditor()
    {
        this.editor.toggleVisible();
        this.right.setVisible(!this.editor.isVisible());

        if (this.editor.isVisible())
        {
            this.editor.resetView();
        }
    }

    /**
     * Display current resource location (it's just for visual, not
     * logic)
     */
    protected void displayCurrent(ResourceLocation rl)
    {
        this.current = rl;
        this.picker.rl = rl;
        this.text.setText(rl == null ? "" : rl.toString());
        this.text.field.setCursorPositionZero();

        if (this.tree != null)
        {
            FolderEntry folder = this.tree.getByPath(rl == null ? "" : rl.getResourceDomain() + "/" + rl.getResourcePath());

            if (folder != this.tree.root || this.picker.getList().isEmpty())
            {
                this.picker.setList(folder.getEntries());
                this.picker.parent = folder;
                this.picker.setCurrent(rl);
                this.picker.update();

                this.updateFolderButton();
            }
        }
    }

    /**
     * Select current resource location
     */
    protected void selectCurrent(ResourceLocation rl)
    {
        try
        {
            /* If this code causes an exception, that means that there
             * is some issue with the texture, so we should rather not
             * use it (legacy bindTexture validation — S7 binder seam) */
            if (rl != null && textureBinder != null)
            {
                textureBinder.bind(rl);
            }
        }
        catch (Exception e)
        {
            return;
        }

        this.current = rl;

        if (this.multiRL != null)
        {
            if (rl == null && this.multiRL.children.size() == 1)
            {
                this.currentFRL.path = rl;
                this.toggleMultiSkin();
            }
            else
            {
                this.currentFRL.path = rl;
            }
        }
        else if (this.callback != null)
        {
            this.callback.accept(rl);
        }

        this.picker.rl = rl;
    }

    protected void toggleMultiSkin()
    {
        if (this.multiRL != null)
        {
            this.setMultiSkin(this.multiRL.children.get(0).path, true);
        }
        else if (this.current != null)
        {
            this.setMultiSkin(new MultiResourceLocation(this.current.toString()), true);
        }
        else
        {
            ResourceLocation rl = this.picker.getCurrentResource();

            if (rl != null)
            {
                this.setMultiSkin(rl, true);
            }
        }
    }

    protected void setMultiSkin(ResourceLocation skin, boolean notify)
    {
        if (this.editor.isVisible())
        {
            this.toggleEditor();
        }

        boolean show = skin instanceof MultiResourceLocation;

        if (show)
        {
            this.multiRL = (MultiResourceLocation) ((MultiResourceLocation) skin).copy();
            this.setFRL(this.multiRL.children.get(0));

            this.multiList.setIndex(this.multiRL.children.isEmpty() ? -1 : 0);
            this.multiList.setList(this.multiRL.children);

            if (this.current != null)
            {
                this.multiList.setIndex(0);
            }

            this.right.flex().x(120).w(1F, -120);
        }
        else
        {
            this.multiRL = null;

            this.right.flex().x(0).w(1F);
            this.displayCurrent(skin);
        }

        if (notify)
        {
            if (show && this.callback != null)
            {
                this.multiRL.recalculateId();
                this.callback.accept(skin);
            }
            else
            {
                this.selectCurrent(skin);
            }
        }

        this.multiList.setVisible(show);
        this.buttons.setVisible(show);

        this.resize();
        this.updateFolderButton();
    }

    @Override
    public boolean mouseClicked(GuiContext context)
    {
        /* Necessary measure to avoid triggering buttons when you press
         * on a text field, for example */
        return super.mouseClicked(context) || (this.isVisible() && this.area.isInside(context));
    }

    @Override
    public boolean keyTyped(GuiContext context)
    {
        if (super.keyTyped(context))
        {
            return true;
        }

        int keyCode = context.keyCode;

        if (keyCode == LegacyKeyCodes.KEY_RETURN)
        {
            List<AbstractEntry> selected = this.picker.getCurrent();
            AbstractEntry entry = selected.isEmpty() ? null : selected.get(0);

            if (entry instanceof FolderEntry)
            {
                this.picker.setFolder((FolderEntry) entry);
            }
            else if (entry instanceof FileEntry)
            {
                this.selectCurrent(((FileEntry) entry).resource);
            }

            this.typed = "";

            return true;
        }
        else if (keyCode == LegacyKeyCodes.KEY_UP)
        {
            return this.moveCurrent(-1, GuiUtils.isShiftKeyDown());
        }
        else if (keyCode == LegacyKeyCodes.KEY_DOWN)
        {
            return this.moveCurrent(1, GuiUtils.isShiftKeyDown());
        }
        else if (keyCode == LegacyKeyCodes.KEY_ESCAPE)
        {
            this.close();

            return true;
        }

        return this.pickByTyping(context.typedChar);
    }

    protected boolean moveCurrent(int factor, boolean top)
    {
        int index = this.picker.getIndex() + factor;
        int length = this.picker.getList().size();

        if (index < 0) index = length - 1;
        else if (index >= length) index = 0;

        if (top) index = factor > 0 ? length - 1 : 0;

        this.picker.setIndex(index);
        this.picker.scroll.scrollIntoView(index * this.picker.scroll.scrollItemSize);
        this.typed = "";

        return true;
    }

    protected boolean pickByTyping(char typedChar)
    {
        if (!SharedConstants.isValidChar(typedChar))
        {
            return false;
        }

        if (this.lastTyped.checkReset())
        {
            this.typed = "";
        }

        this.typed += Character.toString(typedChar);
        this.lastTyped.mark();

        for (AbstractEntry entry : this.picker.getList())
        {
            if (entry.title.startsWith(this.typed))
            {
                this.picker.setCurrentScroll(entry);

                return true;
            }
        }

        return true;
    }

    @Override
    public void draw(GuiContext context)
    {
        /* Refresh the list */
        if (this.lastChecked.checkRepeat())
        {
            FolderEntry folder = this.picker.parent;

            if (folder != null && folder.isTop())
            {
                folder = folder.top;
            }

            if (folder != null && folder.hasChanged())
            {
                this.picker.setDirectFolder(folder);
            }
        }

        /* Draw the background */
        GuiDraw.drawVerticalGradientRect(this.area.x, this.area.y, this.area.ex(), this.area.ey(), ColorUtils.HALF_BLACK, 0xff000000);

        if (this.multiList.isVisible())
        {
            GuiDraw.drawRect(this.area.x, this.area.y, this.area.x + 120, this.area.ey(), 0xff181818);
            GuiDraw.drawRect(this.area.x, this.area.y, this.area.x + 120, this.area.y + 30, 0x44000000);
            GuiDraw.drawVerticalGradientRect(this.area.x, this.area.ey() - 20, this.buttons.area.ex(), this.area.ey(), 0, ColorUtils.HALF_BLACK);
        }

        if (this.editor.isVisible())
        {
            this.edit.area.draw(ColorUtils.HALF_BLACK + McLib.primaryColor.get());
        }

        super.draw(context);

        /* Draw the overlays */
        if (this.right.isVisible())
        {
            if (this.picker.getList().isEmpty())
            {
                GuiDraw.drawCenteredString(this.font, I18n.translate("mclib.gui.no_data"), this.picker.area.mx(), this.picker.area.my() - 8, 0xffffff);
            }

            if (!this.lastTyped.check() && this.lastTyped.enabled)
            {
                int w = GuiDraw.textWidth(this.font, this.typed);
                int x = this.text.area.x;
                int y = this.text.area.ey();

                GuiDraw.drawRect(x, y, x + w + 4, y + 4 + GuiDraw.fontHeight(this.font), ColorUtils.HALF_BLACK + McLib.primaryColor.get());
                GuiDraw.drawStringWithShadow(this.font, this.typed, x + 2, y + 2, 0xffffff);
            }

            ResourceLocation loc = this.current;

            /* Draw preview (S7 binder seam supplies the texture + size) */
            if (loc != null && textureBinder != null)
            {
                try
                {
                    GuiDraw.resetColor();
                    textureBinder.bind(loc);
                }
                catch (Exception e)
                {
                    return;
                }

                int w = textureBinder.getWidth();
                int h = textureBinder.getHeight();

                int x = this.area.ex();
                int y = this.area.ey();
                int fw = w;
                int fh = h;

                if (fw > 128 || fh > 128)
                {
                    fw = fh = 128;

                    if (w > h)
                    {
                        fh = (int) ((h / (float) w) * fw);
                    }
                    else if (h > w)
                    {
                        fw = (int) ((w / (float) h) * fh);
                    }
                }

                x -= fw + 10;
                y -= fh + 35;

                Icons.CHECKBOARD.renderArea(x, y, fw, fh);

                try
                {
                    textureBinder.bind(loc);
                }
                catch (Exception e)
                {
                    return;
                }

                GuiDraw.drawBillboard(x, y, 0, 0, fw, fh, fw, fh);
            }
        }
    }

    /**
     * P40→S7 texture-binding seam: binds an arbitrary (possibly multi-skin)
     * {@link ResourceLocation} for subsequent textured draws and reports the
     * bound texture's size. Legacy equivalents:
     * {@code mc.renderEngine.bindTexture(rl)} +
     * {@code glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH/HEIGHT)}.
     */
    public interface TextureBinder
    {
        /**
         * Bind the texture (also routing it into
         * {@code RenderSystem.setShaderTexture(0, ...)} for GuiDraw
         * billboards); throw when the texture cannot be loaded — the picker
         * treats that as "don't use this selection", like legacy.
         */
        void bind(ResourceLocation location) throws Exception;

        /** Width in pixels of the last successfully bound texture */
        int getWidth();

        /** Height in pixels of the last successfully bound texture */
        int getHeight();
    }

    public static class GuiFRLListElement extends GuiListElement<FilteredResourceLocation>
    {
        public GuiFRLListElement(MinecraftClient mc, Consumer<List<FilteredResourceLocation>> callback)
        {
            super(mc, callback);

            this.scroll.scrollItemSize = 16;
        }

        @Override
        protected boolean sortElements()
        {
            Collections.sort(this.list, (a, b) -> a.toString().compareToIgnoreCase(b.toString()));

            return true;
        }
    }
}
