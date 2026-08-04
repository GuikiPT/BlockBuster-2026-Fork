package mchorse.metamorph.client.gui.creative;

import mchorse.mclib.client.gui.framework.GuiBase;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiIconElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.framework.tooltips.LabelTooltip;
import mchorse.mclib.client.gui.utils.Icons;
import mchorse.mclib.client.gui.utils.LegacyKeyCodes;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.network.IMessage;
import mchorse.metamorph.Metamorph;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import mchorse.metamorph.client.MetamorphClient;
import mchorse.metamorph.network.Dispatcher;
import mchorse.metamorph.network.common.creative.PacketAcquireMorph;
import mchorse.metamorph.network.common.creative.PacketMorph;
import mchorse.metamorph.util.MMIcons;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Creative morphs screen (roadmap P58) — what the
 * {@code key.metamorph.creative_menu} keybind (default {@code B}) opens.
 *
 * <p>1:1 port of Metamorph 1.4's
 * {@code mchorse.metamorph.client.gui.creative.GuiCreativeScreen}: the
 * full-screen creative picker. A 40 px top bar carries Morph / Acquire / Close
 * plus two icons — the OP-gated entity-selector panel toggle and a copy-command
 * button that only appears once something is selected — over a
 * {@link GuiCreativeMorphsList} filling the rest.</p>
 *
 * <h2>Behaviours worth not losing</h2>
 * <ul>
 *   <li><b>Morph closes, Acquire does not.</b> Morphing is a commitment;
 *       acquiring is something you do repeatedly, so the screen stays open.</li>
 *   <li><b>Both call {@code pane.finish()} first</b>, and so does
 *       {@link #closeScreen()}. That unwinds any open nested edit and re-picks
 *       a <i>copy</i> of the selection — without it you would send the morph
 *       instance the editor is still mutating, or send a half-edited one.</li>
 *   <li>{@code Enter} / {@code A} mirror Morph / Acquire but are inactive while
 *       the morph editor is open ({@code isEditMode}), where they mean
 *       something else; {@code S} toggles the selectors panel and is registered
 *       under the picker's own keybind category so it lists with the rest.</li>
 *   <li>Toggling the selector panel does not overlay it — it <b>reflows</b> the
 *       picker to start at x=140, then resizes both.</li>
 * </ul>
 *
 * <p>Boundary changes: {@code Minecraft} &rarr; {@code MinecraftClient};
 * LWJGL2 {@code Keyboard.KEY_*} &rarr; {@link LegacyKeyCodes};
 * {@code GuiScreen.setClipboardString} &rarr;
 * {@code MinecraftClient.keyboard.setClipboard}; {@code Metamorph.proxy
 * .canEditSelectors()} &rarr; {@link MetamorphClient#canEditSelectors()};
 * {@code Gui.drawRect} &rarr; {@link GuiDraw}; {@code drawScreen} &rarr;
 * {@link #render(DrawContext, int, int, float)} with the framework
 * {@link DrawContext} bound before the backgrounds are drawn.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/client/gui/creative/GuiCreativeScreen.java
 */
public class GuiCreativeScreen extends GuiBase
{
    /* Legacy kept these private; the port exposes them the way its other
     * ported screens do (GuiActor, GuiGun), so the widget tree is assertable
     * without reflection. */
    public GuiSelectorEditor selectors;
    public GuiIconElement icon;
    public GuiIconElement copy;
    public GuiButtonElement morph;
    public GuiButtonElement acquire;
    public GuiButtonElement close;
    public GuiCreativeMorphsList pane;

    public GuiCreativeScreen(MinecraftClient mc)
    {
        this.selectors = new GuiSelectorEditor(mc);
        this.selectors.setVisible(false);

        this.icon = new GuiIconElement(mc, MMIcons.PROPERTIES, this::toggleEntitySelector);
        this.icon.tooltip(IKey.lang("metamorph.gui.selectors.title"));
        this.icon.setVisible(MetamorphClient.canEditSelectors());
        this.copy = new GuiIconElement(mc, Icons.COPY, this::copyMorphCommand);
        this.copy.tooltip(IKey.lang("metamorph.gui.creative.command"));
        this.morph = new GuiButtonElement(mc, IKey.lang("metamorph.gui.morph"), (b) ->
        {
            this.pane.finish();

            AbstractMorph morph = this.pane.getSelected();

            if (morph != null)
            {
                this.send(new PacketMorph(morph));
                this.closeScreen();
            }
        });
        this.acquire = new GuiButtonElement(mc, IKey.lang("metamorph.gui.acquire"), (b) ->
        {
            this.pane.finish();

            AbstractMorph morph = this.pane.getSelected();

            if (morph != null)
            {
                this.send(new PacketAcquireMorph(morph));
            }
        });
        this.close = new GuiButtonElement(mc, IKey.lang("metamorph.gui.close"), (b) -> this.closeScreen());
        this.pane = new GuiCreativeMorphsList(mc, this::setMorph);
        this.pane.setSelected(currentMorph(mc));

        this.morph.flex().relative(this.viewport).set(0, 10, 60, 20).x(1F, -200);
        this.acquire.flex().relative(this.morph).set(65, 0, 60, 20);
        this.close.flex().relative(this.acquire).set(65, 0, 60, 20);
        this.icon.flex().relative(this.morph).set(-18, 2, 16, 16);
        this.copy.flex().relative(this.icon).set(-20, 0, 16, 16);
        this.pane.flex().relative(this.viewport).set(0, 40, 0, 0).w(1F, 0).h(1F, -40);
        this.selectors.flex().relative(this.viewport).wTo(this.pane.flex()).h(1F);

        this.root.add(this.pane, this.morph, this.acquire, this.close, this.selectors, this.icon, this.copy);

        this.root.keys().register(((LabelTooltip) this.icon.tooltip).label, LegacyKeyCodes.KEY_S, () -> this.icon.clickItself(this.context))
            .active(MetamorphClient.canEditSelectors()).category(this.pane.exitKey.category);
        this.root.keys().register(IKey.lang("metamorph.gui.creative.keys.acquire"), LegacyKeyCodes.KEY_A, () -> this.acquire.clickItself(this.context))
            .category(this.pane.exitKey.category).active(() -> !this.pane.isEditMode());
        this.root.keys().register(IKey.lang("metamorph.gui.creative.keys.morph"), LegacyKeyCodes.KEY_RETURN, () -> this.morph.clickItself(this.context))
            .category(this.pane.exitKey.category).active(() -> !this.pane.isEditMode());
    }

    /**
     * Outbound send seam. Production dispatches to the server; headless tests
     * override it to capture what the two buttons would have sent.
     */
    protected void send(IMessage packet)
    {
        Dispatcher.sendToServer(packet);
    }

    /**
     * The morph the picker opens on — the player's current one. Null-safe on
     * every hop (no player / no morphing component), because the screen is also
     * constructed by tests and by the keybind before a component exists.
     */
    private static AbstractMorph currentMorph(MinecraftClient mc)
    {
        if (mc == null || mc.player == null)
        {
            return null;
        }

        IMorphing morphing = Morphing.get(mc.player);

        return morphing == null ? null : morphing.getCurrentMorph();
    }

    @Override
    public boolean shouldPause()
    {
        return Metamorph.pauseGUIInSP.get();
    }

    private void copyMorphCommand(GuiIconElement button)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc != null && mc.keyboard != null)
        {
            mc.keyboard.setClipboard(GuiMorphSection.getMorphCommand(this.pane.getSelected()));
        }
    }

    protected void toggleEntitySelector(GuiIconElement button)
    {
        this.selectors.toggleVisible();

        if (this.selectors.isVisible())
        {
            this.pane.flex().x(140).wTo(this.root.flex(), 1F);
        }
        else
        {
            this.pane.flex().x(0).w(1F);
        }

        this.pane.resize();
        this.selectors.resize();
    }

    /**
     * The picker's selection callback: hand the morph to the (possibly hidden)
     * selector editor and show the copy-command icon only once there is
     * something to copy.
     */
    protected void setMorph(AbstractMorph morph)
    {
        this.selectors.setMorph(morph);
        this.copy.setVisible(morph != null);
    }

    @Override
    protected void closeScreen()
    {
        this.pane.finish();

        super.closeScreen();
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float partialTicks)
    {
        this.context.drawContext = drawContext;
        GuiDraw.bindDrawContext(drawContext);

        /* Draw panel backgrounds */
        GuiDraw.drawCustomBackground(0, 0, this.width, this.height);
        GuiDraw.drawRect(this.pane.area.x, 0, this.width, 40, 0xaa000000);

        super.render(drawContext, mouseX, mouseY, partialTicks);
    }
}
