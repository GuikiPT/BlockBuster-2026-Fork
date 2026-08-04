package mchorse.metamorph.client.gui.creative;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.mclib.client.gui.framework.GuiBase;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.LegacyKeyCodes;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.network.Dispatcher;
import mchorse.metamorph.network.common.creative.PacketAcquireMorph;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.opengl.GL11;

import java.util.function.Consumer;

/**
 * Creative morph menu, but with a close button (roadmap P58).
 *
 * <p>1:1 port of Metamorph 1.4's
 * {@code mchorse.metamorph.client.gui.creative.GuiCreativeMorphsMenu} — the
 * embeddable variant of {@link GuiCreativeMorphsList} that Blockbuster drops into
 * its dashboards (S12). It adds an <em>Acquire</em> button (sends
 * {@link PacketAcquireMorph} and stays open) and, outside "menu" mode, an "X"
 * close button; swallows clicks/scrolls that land inside its area so they don't
 * fall through to whatever it overlays; clears the depth buffer before drawing
 * (dashboard overlap); and in "menu" mode restricts the exit key to editing /
 * nested contexts and editing to the Recent category only.</p>
 *
 * <p>Boundary changes from 1.12.2 (mechanical): {@code Minecraft} &rarr;
 * {@code MinecraftClient}; {@code GL11.glClear(GL_DEPTH_BUFFER_BIT)} &rarr;
 * {@link RenderSystem#clear(int, boolean)} (the established mclib idiom);
 * {@code Gui.drawRect} &rarr; {@link GuiDraw#drawRect}; LWJGL2 {@code Keyboard.KEY_A}
 * &rarr; {@link LegacyKeyCodes#KEY_A}.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/client/gui/creative/GuiCreativeMorphsMenu.java
 */
public class GuiCreativeMorphsMenu extends GuiCreativeMorphsList
{
    private GuiButtonElement close;
    private GuiButtonElement acquire;
    private boolean menu;
    private boolean pickUponExit;

    public GuiCreativeMorphsMenu(MinecraftClient mc, Consumer<AbstractMorph> callback)
    {
        this(mc, false, callback);
    }

    public GuiCreativeMorphsMenu(MinecraftClient mc, boolean menu, Consumer<AbstractMorph> callback)
    {
        super(mc, callback);

        this.menu = menu;
        this.acquire = new GuiButtonElement(mc, IKey.lang("metamorph.gui.acquire"), (b) ->
        {
            AbstractMorph cell = this.getSelected();

            if (cell != null)
            {
                Dispatcher.sendToServer(new PacketAcquireMorph(cell));
            }
        });

        this.close = new GuiButtonElement(mc, IKey.str("X"), (b) -> this.exit());

        this.acquire.flex().w(60);
        this.close.flex().w(20);

        this.bar.flex().row(0).preferred(1);
        this.bar.prepend(this.acquire);

        if (!this.menu)
        {
            this.bar.add(this.close);
        }

        this.markContainer();

        this.keys().register(IKey.lang("metamorph.gui.creative.keys.acquire"), LegacyKeyCodes.KEY_A, () -> this.acquire.clickItself(GuiBase.getCurrent())).category(this.exitKey.category).active(() -> !this.isEditMode());
    }

    public GuiCreativeMorphsMenu pickUponExit()
    {
        this.pickUponExit = true;

        return this;
    }

    @Override
    public void exit()
    {
        if (!this.menu && !this.isEditMode() && !this.isNested())
        {
            this.finish();
            this.removeFromParent();

            if (this.pickUponExit)
            {
                this.pickMorph(this.getSelected());
            }

            GuiBase.getCurrent().setContextMenu(null);
        }
        else
        {
            super.exit();
        }
    }

    @Override
    protected boolean updateExitKey()
    {
        if (this.menu)
        {
            return this.isEditMode() || this.isNested();
        }

        return true;
    }

    @Override
    public boolean isSelectedMorphIsEditable()
    {
        return this.morphs.selected != null && this.morphs.selected.category == this.morphs.user.recent;
    }

    /* Don't let click event pass through the background... */

    @Override
    public boolean mouseClicked(GuiContext context)
    {
        return super.mouseClicked(context) || this.area.isInside(context.mouseX, context.mouseY);
    }

    @Override
    public boolean mouseScrolled(GuiContext context)
    {
        return super.mouseScrolled(context) || this.area.isInside(context.mouseX, context.mouseY);
    }

    @Override
    public void draw(GuiContext context)
    {
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);

        if (!this.menu)
        {
            GuiDraw.drawRect(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xaa000000);
        }

        super.draw(context);
    }
}
