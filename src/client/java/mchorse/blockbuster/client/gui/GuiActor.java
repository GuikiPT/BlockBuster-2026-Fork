package mchorse.blockbuster.client.gui;

import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.PacketModifyActor;
import mchorse.mclib.client.gui.framework.GuiBase;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.ScreenOpener;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.metamorph.client.gui.creative.GuiCreativeMorphsMenu;
import mchorse.metamorph.client.gui.creative.GuiMorphRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Actor configuration screen (roadmap P123).
 *
 * <p>Full port of 1.12.2 {@code client/gui/GuiActor.java}: a <b>non-pausing</b>
 * full-screen morph preview with a centered "Pick morph" button that overlays
 * the creative morphs menu, syncing the chosen morph back to the actor on
 * close via {@link PacketModifyActor}. Layout constants are 1:1 with the
 * legacy source (renderer full-viewport; pick button
 * {@code x(0.5).y(1,-10).w(100).anchor(0.5,1)}; title at y=16).</p>
 *
 * <p>The two P58 stand-ins this screen used to carry — a preview that drew
 * nothing and a morph picker that only held the callback wiring, both working
 * in raw NBT because {@link EntityActor#morph} did — are gone: it drives the
 * real {@link GuiMorphRenderer} and {@link GuiCreativeMorphsMenu} against the
 * actor's live {@code Morph}, exactly as legacy did.</p>
 *
 * <p>Opening flow (server → client) rides the P100 {@code PacketOpenGui}
 * plumbing; {@link #open(MinecraftClient, EntityActor)} is the client-side
 * entry point it dispatches to.</p>
 */
public class GuiActor extends GuiBase
{
    public GuiMorphRenderer renderer;
    public GuiButtonElement pick;
    public GuiCreativeMorphsMenu morphs;

    private final EntityActor actor;

    public GuiActor(MinecraftClient mc, EntityActor actor)
    {
        this.actor = actor;

        this.renderer = new GuiMorphRenderer(mc);
        this.renderer.morph = actor.morph.get();
        this.renderer.flex().reset().relative(this.viewport).wh(1F, 1F);

        this.pick = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.pick"), (button) ->
        {
            this.morphs.resize();
            this.morphs.setSelected(this.renderer.morph);
            this.root.add(this.morphs);
        });
        this.pick.flex().relative(this.viewport).x(0.5F).y(1F, -10).w(100).anchor(0.5F, 1F);

        this.morphs = new GuiCreativeMorphsMenu(mc, (morph) -> this.renderer.morph = morph);
        this.morphs.flex().reset().relative(this.viewport).wh(1F, 1F);

        this.root.add(this.renderer, this.pick);
    }

    /**
     * Client-side entry point — opens the actor config screen. Displayed
     * through the {@link ScreenOpener} seam (S22/P228) so the {@code
     * GuiHandler.ACTOR} route is assertable headlessly.
     */
    public static void open(MinecraftClient mc, EntityActor actor)
    {
        ScreenOpener.open(new GuiActor(mc, actor));
    }

    @Override
    public boolean shouldPause()
    {
        /* Legacy doesGuiPauseGame() = false — actors keep playing behind the
         * screen so the preview shows the live morph. */
        return false;
    }

    @Override
    protected void closeScreen()
    {
        /* Legacy actor.morph.setDirect(renderer.morph): apply WITHOUT the merge
         * machinery so the local state matches the packet snapshot exactly (a
         * `set` could merge and leave the two sides disagreeing). */
        this.actor.morph.setDirect(this.renderer.morph);
        this.sync(new PacketModifyActor(this.actor));

        super.closeScreen();
    }

    /**
     * Send seam — the outbound close-sync packet. Production sends it to the
     * server; headless tests override to capture it.
     */
    protected void sync(PacketModifyActor packet)
    {
        Dispatcher.sendToServer(packet);
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float partialTicks)
    {
        this.context.drawContext = drawContext;
        GuiDraw.bindDrawContext(drawContext);

        /* Legacy drawDefaultBackground() */
        this.renderBackground(drawContext, mouseX, mouseY, partialTicks);

        /* Legacy drawCenteredString(font, title, width/2, 16, 0xffffff) */
        GuiDraw.drawCenteredString(this.context.font, IKey.lang("blockbuster.gui.actor.title").get(), this.width / 2, 16, 0xffffff);

        super.render(drawContext, mouseX, mouseY, partialTicks);
    }
}
