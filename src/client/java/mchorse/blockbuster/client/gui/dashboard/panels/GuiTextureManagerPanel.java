package mchorse.blockbuster.client.gui.dashboard.panels;

import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.blockbuster.client.gui.dashboard.GuiBlockbusterPanel;
import mchorse.blockbuster.client.textures.MipmapTexture;
import mchorse.blockbuster.utils.BlockbusterPaths;
import mchorse.blockbuster.utils.TextureUtils;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiIconElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiToggleElement;
import mchorse.mclib.client.gui.framework.elements.list.GuiListElement;
import mchorse.mclib.client.gui.framework.elements.list.GuiResourceLocationListElement;
import mchorse.mclib.client.gui.framework.elements.list.GuiSearchListElement;
import mchorse.mclib.client.gui.framework.elements.modals.GuiMessageModal;
import mchorse.mclib.client.gui.framework.elements.modals.GuiModal;
import mchorse.mclib.client.gui.framework.elements.modals.GuiPromptModal;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.mclib.GuiDashboard;
import mchorse.mclib.client.gui.utils.Elements;
import mchorse.mclib.client.gui.utils.GuiUtils;
import mchorse.mclib.client.gui.utils.Icons;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.utils.Direction;
import mchorse.mclib.utils.ReflectionUtils;
import mchorse.mclib.utils.resources.RLUtils;
import mchorse.mclib.utils.resources.ResourceLocation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import org.apache.commons.io.FilenameUtils;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Texture manager panel (roadmap P140) — port of Blockbuster 2.7.2's
 * {@code mchorse.blockbuster.client.gui.dashboard.panels.GuiTextureManagerPanel}.
 *
 * <p>Views and manages textures loaded by the vanilla {@link TextureManager}:
 * a searchable resource-location list, a checkerboard-backed aspect-fit
 * preview, linear/mipmap filter toggles, remove, replace (aliasing), export to
 * PNG and copy-id-to-clipboard.</p>
 *
 * <p>Legacy source of truth:
 * blockbuster-1.12/.../dashboard/panels/GuiTextureManagerPanel.java. The pure,
 * GL-free bits (filter constant table, list fill/sort, replace-alias, remove
 * re-index) live in {@link TextureManagerLogic} so they are unit-tested; the GL
 * calls here (bind / getTexParameter / getTexImage / delete) are verified via
 * the P146 in-game checklist.</p>
 *
 * <p><b>1.20.4 boundary:</b> the legacy list held vanilla
 * {@code ResourceLocation}s and the texture map was reached via reflection; here
 * the list holds the bundled {@link ResourceLocation} and the map is the real
 * {@code Map<Identifier, AbstractTexture>} exposed by {@link ReflectionUtils}
 * (S7 accessor mixin). Ids convert losslessly at the map boundary.</p>
 *
 * <p><b>Quirks preserved:</b> {@link #isClientSideOnly()} is {@code true} (the
 * only panel usable without OP); the first-ever {@link #pickRL} skips GL
 * introspection; replace-aliasing can leave a dangling id after remove (the
 * shared GL id is deleted) — legacy behavior, not fixed.</p>
 */
public class GuiTextureManagerPanel extends GuiBlockbusterPanel
{
    public GuiSearchResourceLocationList textures;
    public GuiToggleElement linear;
    public GuiToggleElement mipmap;
    public GuiButtonElement remove;
    public GuiButtonElement replace;
    public GuiButtonElement export;
    public GuiIconElement copy;

    private ResourceLocation rl;
    private IKey title = IKey.lang("blockbuster.gui.texture.title");
    private IKey subtitle = IKey.lang("blockbuster.gui.texture.subtitle");

    public GuiTextureManagerPanel(MinecraftClient mc, GuiDashboard dashboard)
    {
        super(mc, dashboard);

        this.textures = new GuiSearchResourceLocationList(mc, (rl) -> this.pickRL(rl.get(0)));
        this.textures.list.background();
        this.linear = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.texture.linear"), false, (b) -> this.setLinear(b.isToggled()));
        this.linear.tooltip(IKey.lang("blockbuster.gui.texture.linear_tooltip"), Direction.LEFT);
        this.mipmap = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.texture.mipmap"), false, (b) -> this.setMipmap(b.isToggled()));
        this.mipmap.tooltip(IKey.lang("blockbuster.gui.texture.mipmap_tooltip"), Direction.LEFT);
        this.remove = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.remove"), (b) -> this.remove());
        this.replace = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.texture.replace"), (b) -> this.replace());
        this.export = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.texture.export"), (b) -> this.export());
        this.copy = new GuiIconElement(mc, Icons.COPY, (b) -> this.copy());
        this.copy.tooltip(IKey.lang("blockbuster.gui.texture.copy"), Direction.TOP).flex().wh(20, 20);

        GuiElement element = new GuiElement(mc);

        element.flex().relative(this).xy(1F, 1F).w(148).anchor(1F, 1F).column(5).vertical().stretch().padding(10);
        this.textures.flex().relative(this.area).set(10, 50, 0, 0).w(1, -30 - 128).h(1, -60);

        element.add(Elements.row(mc, 5, 0, this.export, this.copy));
        element.add(this.replace, this.remove, this.linear, this.mipmap);
        this.add(this.textures, element);
    }

    @Override
    public boolean isClientSideOnly()
    {
        return true;
    }

    private Map<Identifier, AbstractTexture> map()
    {
        return ReflectionUtils.getTextures(this.mc.getTextureManager());
    }

    /**
     * Bind the given id for GL introspection and for a subsequent
     * {@link GuiDraw#drawBillboard} (which samples shader texture 0). Legacy
     * used {@code mc.renderEngine.bindTexture(rl)}; on 1.20.4 we pull the
     * {@link AbstractTexture} out of the map and bind its GL id directly.
     * Returns {@code null} when the id is not (or no longer) loaded.
     */
    private AbstractTexture bind(Identifier id)
    {
        AbstractTexture texture = this.map().get(id);

        if (texture == null)
        {
            return null;
        }

        RenderSystem.setShaderTexture(0, id);
        RenderSystem.bindTexture(texture.getGlId());

        return texture;
    }

    private void copy()
    {
        ResourceLocation location = this.textures.list.getCurrentFirst();

        if (location == null)
        {
            return;
        }

        GuiUtils.setClipboardString(location.toString());
    }

    private void export()
    {
        ResourceLocation location = this.textures.list.getCurrentFirst();

        if (location == null)
        {
            return;
        }

        String name = FilenameUtils.getBaseName(location.getResourcePath());
        File folder = BlockbusterPaths.configRoot().resolve("export").toFile();
        File file = TextureUtils.getFirstAvailableFile(folder, name);

        folder.mkdirs();

        if (this.bind(TextureManagerLogic.toIdentifier(location)) == null)
        {
            return;
        }

        int w = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int h = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        ByteBuffer buffer = ByteBuffer.allocateDirect(w * h * 4);

        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);

        byte[] rgba = new byte[w * h * 4];
        buffer.get(rgba);

        try
        {
            ImageIO.write(TextureUtils.imageFromRgba(rgba, w, h), "png", file);
            GuiModal.addFullModal(this, () ->
            {
                GuiMessageModal modal = new GuiMessageModal(this.mc, IKey.format("blockbuster.gui.texture.export_modal", file.getName()));
                GuiButtonElement open = new GuiButtonElement(this.mc, IKey.lang("blockbuster.gui.texture.open_folder"), (b) ->
                {
                    modal.removeFromParent();
                    GuiUtils.openFolder(BlockbusterPaths.configRoot().resolve("export").toFile().getAbsolutePath());
                });

                modal.bar.add(open);

                return modal;
            });
        }
        catch (Exception e)
        {
            e.printStackTrace();
            GuiModal.addFullModal(this, () -> new GuiMessageModal(this.mc, IKey.lang("blockbuster.gui.texture.export_error")));
        }
    }

    private void pickRL(ResourceLocation rl)
    {
        if (this.rl == null)
        {
            this.linear.toggled(false);
            this.mipmap.toggled(false);
            this.rl = rl;
        }
        else
        {
            try
            {
                Identifier id = TextureManagerLogic.toIdentifier(rl);
                AbstractTexture texture = this.bind(id);

                int filter = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER);

                boolean mipmap = texture instanceof MipmapTexture;
                boolean linear = TextureManagerLogic.isLinear(filter);

                this.linear.toggled(linear);
                this.mipmap.toggled(mipmap);
                this.rl = rl;
            }
            catch (Exception e)
            {}
        }
    }

    private void setLinear(boolean linear)
    {
        if (this.rl == null)
        {
            return;
        }

        this.bind(TextureManagerLogic.toIdentifier(this.rl));

        boolean mipmap = this.mipmap.isToggled();

        int min = TextureManagerLogic.minFilter(linear, mipmap);
        int mag = TextureManagerLogic.magFilter(linear);

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, min);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, mag);
    }

    private void setMipmap(boolean mipmap)
    {
        if (this.rl == null)
        {
            return;
        }

        Map<Identifier, AbstractTexture> map = this.map();
        Identifier id = TextureManagerLogic.toIdentifier(this.rl);
        AbstractTexture tex = map.get(id);

        boolean mipmapped = tex instanceof MipmapTexture;

        /* Add or remove mipmap */
        if (mipmap && !mipmapped)
        {
            AbstractTexture removed = map.remove(id);

            if (removed != null)
            {
                TextureUtil.releaseTextureId(removed.getGlId());
            }

            try
            {
                /* Load texture manually */
                MipmapTexture mip = new MipmapTexture(id);
                mip.load(this.mc.getResourceManager());

                map.put(id, mip);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        else if (!mipmap && mipmapped)
        {
            AbstractTexture removed = map.remove(id);

            if (removed != null)
            {
                TextureUtil.releaseTextureId(removed.getGlId());
            }
        }
    }

    private void remove()
    {
        if (this.rl == null)
        {
            return;
        }

        Map<Identifier, AbstractTexture> map = this.map();
        AbstractTexture removed = map.remove(TextureManagerLogic.toIdentifier(this.rl));

        if (removed != null)
        {
            TextureUtil.releaseTextureId(removed.getGlId());
        }

        this.pickRL(TextureManagerLogic.removeAndReindex(this.textures.list, this.rl));
    }

    private void replace()
    {
        if (this.rl == null || GuiModal.hasModal(this))
        {
            return;
        }

        GuiModal.addModal(this, () ->
        {
            GuiPromptModal modal = new GuiPromptModal(this.mc, IKey.lang("blockbuster.gui.texture.replace_modal"), this::replace);

            modal.text.field.setMaxStringLength(2000);
            modal.setValue(this.rl.toString());
            modal.flex().relative(this.area).set(10, 50, 0, 0).w(1, -30 - 128).h(1, -60);

            return modal;
        });
    }

    private void replace(String string)
    {
        if (this.rl.toString().equals(string))
        {
            return;
        }

        Identifier current = TextureManagerLogic.toIdentifier(this.rl);
        Identifier other = TextureManagerLogic.toIdentifier(RLUtils.create(string));

        TextureManagerLogic.alias(this.map(), current, other);
    }

    @Override
    public void open()
    {
        TextureManagerLogic.fill(this.textures.list, this.map().keySet());

        this.pickRL(this.rl);
        this.textures.list.setCurrent(this.rl);
    }

    @Override
    public void draw(GuiContext context)
    {
        GuiDraw.drawString(this.font, this.title.get(), this.area.x + 10, this.area.y + 10, 0xffffff);
        GuiDraw.drawMultiText(this.font, this.subtitle.get(), this.area.x + 10, this.area.y + 26, 0xcccccc, this.area.w - 158);

        /* Draw preview */
        if (this.rl != null)
        {
            Identifier id = TextureManagerLogic.toIdentifier(this.rl);

            if (this.bind(id) != null)
            {
                int w = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
                int h = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);

                int x = this.area.ex();
                int y = this.area.y + 10;
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

                GuiDraw.resetColor();
                Icons.CHECKBOARD.renderArea(x, y, fw, fh);

                this.bind(id);
                GuiDraw.drawBillboard(x, y, 0, 0, fw, fh, fw, fh);
            }
        }

        super.draw(context);
    }

    public static class GuiSearchResourceLocationList extends GuiSearchListElement<ResourceLocation>
    {
        public GuiSearchResourceLocationList(MinecraftClient mc, Consumer<List<ResourceLocation>> callback)
        {
            super(mc, callback);
        }

        @Override
        protected GuiListElement<ResourceLocation> createList(MinecraftClient mc, Consumer<List<ResourceLocation>> callback)
        {
            return new GuiResourceLocationListElement(mc, callback);
        }
    }
}
