package mchorse.metamorph.client.gui.survival;

import mchorse.mclib.client.gui.framework.GuiBase;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.GuiScrollElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiToggleElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiKeybindElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.Elements;
import mchorse.mclib.client.gui.utils.LegacyKeyCodes;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.utils.ColorUtils;
import mchorse.metamorph.Metamorph;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.creative.CreativeMorphNetwork;
import mchorse.metamorph.api.creative.ICreativeMorphNetwork;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import mchorse.metamorph.client.KeyboardHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Survival morph menu (roadmap P61) — what the
 * {@code key.metamorph.survival_menu} keybind (default {@code X}) opens.
 *
 * <p>1:1 port of Metamorph 1.4's
 * {@code mchorse.metamorph.client.gui.survival.GuiSurvivalScreen}: a centered
 * panel capped at 500&times;300 with a 140&nbsp;px sidebar (Morph / Remove, the
 * keybind capture field, the Favorite toggle) beside a
 * {@link GuiSurvivalMorphs} list, plus an Only-Favorites toggle in the top
 * right and a 20&nbsp;px title bar.</p>
 *
 * <h2>Behaviours worth not losing</h2>
 * <ul>
 *   <li><b>Two packet routes.</b> Acquired-list morphs act through index-based
 *       packets ({@code PacketSelectMorph} / {@code PacketRemoveMorph} /
 *       {@code PacketKeybind} / {@code PacketFavorite}) — the index is the
 *       position in the server's acquired list, which is why
 *       {@link #setSelected(AbstractMorph)} first remaps the selection to its
 *       equal instance inside that list. Category morphs (creative, or with
 *       {@code allowMorphingIntoCategoryMorphs} on) instead morph by full NBT
 *       and are edited/removed purely client-side.</li>
 *   <li><b>The demorph key cannot be assigned to a morph</b> — the capture
 *       field silently reverts, or you would have a key that both morphs and
 *       demorphs. {@code ESC} in the field clears the keybind ({@code -1})
 *       rather than cancelling.</li>
 *   <li><b>While the menu is open the world keybinds still work</b>: the
 *       demorph key demorphs, and any morph's own keybind morphs into it and
 *       closes the screen.</li>
 *   <li><b>The screen is cached</b> ({@link mchorse.metamorph.client.MetamorphClient#getSurvivalScreen()})
 *       and only rebuilds its sections when the creative flag, the config flag,
 *       or emptiness changes — otherwise reopening would lose scroll and
 *       selection every time.</li>
 * </ul>
 *
 * <p>The load-bearing decisions above are delegated to the headless
 * {@link SurvivalScreenLogic} (which is where their tests live); this class is
 * the widget tree and the wiring.</p>
 *
 * <p>Boundary changes: {@code Minecraft} &rarr; {@code MinecraftClient}; LWJGL2
 * {@code Keyboard.KEY_*} &rarr; {@link LegacyKeyCodes}; the {@code Dispatcher}
 * calls &rarr; the {@link ICreativeMorphNetwork} seam;
 * {@code ClientProxy.keys.keyDemorph.getKeyCode()} &rarr;
 * {@link KeyboardHandler#demorphLegacyKeyCode()}; {@code drawScreen} &rarr;
 * {@link #render(DrawContext, int, int, float)} with the framework
 * {@link DrawContext} bound before the backgrounds are drawn.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/client/gui/survival/GuiSurvivalScreen.java
 */
public class GuiSurvivalScreen extends GuiBase
{
    public GuiSurvivalMorphs morphs;
    public GuiElement sidebar;
    public GuiToggleElement onlyFavorite;

    public GuiButtonElement morph;
    public GuiButtonElement remove;
    public GuiKeybindElement keybind;
    public GuiToggleElement favorite;

    private boolean creative;
    private boolean allowed;

    public GuiSurvivalScreen(MinecraftClient mc)
    {
        this.morph = new GuiButtonElement(mc, IKey.lang("metamorph.gui.morph"), this::morph);
        this.remove = new GuiButtonElement(mc, IKey.lang("metamorph.gui.remove"), this::remove);
        this.keybind = new GuiKeybindElement(mc, this::setKeybind);
        this.keybind.tooltip(IKey.lang("metamorph.gui.survival.keybind_tooltip"));
        this.favorite = new GuiToggleElement(mc, IKey.lang("metamorph.gui.survival.favorite"), this::favorite);

        this.sidebar = new GuiScrollElement(mc);
        this.sidebar.flex().relative(this.root).y(20).w(140).hTo(this.root.resizer(), 1F).column(5).stretch().height(20).padding(10);
        this.sidebar.add(Elements.row(mc, 5, 0, 20, this.morph, this.remove), this.keybind, this.favorite);

        this.onlyFavorite = new GuiToggleElement(mc, IKey.lang("metamorph.gui.survival.only_favorites"), (button) -> this.morphs.setFavorite(button.isToggled()));
        this.onlyFavorite.flex().relative(this.root).x(1F).w(120).h(20).anchor(1F, 0F);

        this.morphs = new GuiSurvivalMorphs(mc);
        this.morphs.flex().relative(this.root).x(140).y(20).wTo(this.root.resizer(), 1F).hTo(this.root.resizer(), 1F).column(0).vertical().stretch().scroll();

        this.root.flex().xy(0.5F, 0.5F).wh(1F, 1F).anchor(0.5F, 0.5F).maxW(500).maxH(300);
        this.root.add(this.morphs, this.sidebar, this.onlyFavorite);

        /* Setup keybinds */
        IKey category = IKey.lang("metamorph.gui.survival.keys.category");

        this.root.keys().register(this.morph.label, LegacyKeyCodes.KEY_RETURN, () -> this.morph.clickItself(this.context)).category(category);
        this.root.keys().register(this.remove.label, LegacyKeyCodes.KEY_BACK, () -> this.remove.clickItself(this.context)).category(category);
        this.root.keys().register(this.favorite.label, LegacyKeyCodes.KEY_F, () -> this.favorite.clickItself(this.context)).category(category);
        this.root.keys().register(IKey.lang("metamorph.gui.survival.keys.toggle_favorites"), LegacyKeyCodes.KEY_O, () -> this.onlyFavorite.clickItself(this.context)).category(category);
        this.root.keys().register(IKey.lang("metamorph.gui.survival.keys.focus_keybind"), LegacyKeyCodes.KEY_K, () -> this.keybind.clickItself(this.context)).category(category);
    }

    /**
     * Outbound network seam. Production routes to the installed
     * {@link ICreativeMorphNetwork}; headless tests override it to capture what
     * the sidebar would have sent.
     */
    protected ICreativeMorphNetwork network()
    {
        return CreativeMorphNetwork.INSTANCE;
    }

    /**
     * The demorph key, in the legacy LWJGL2 keycode space — legacy
     * {@code ClientProxy.keys.keyDemorph.getKeyCode()}. A seam because the real
     * binding only exists once the client has registered it, and both callers
     * (the capture field's rejection and the screen's demorph handler) have to
     * agree on the same value.
     */
    protected int demorphKeyCode()
    {
        return KeyboardHandler.HANDLER.demorphLegacyKeyCode();
    }

    @Override
    public boolean shouldPause()
    {
        return Metamorph.pauseGUIInSP.get();
    }

    /**
     * Open the survival morph menu and update the morphs element. The section
     * rebuild is conditional (see
     * {@link SurvivalScreenLogic#shouldRebuild(boolean, boolean, boolean, boolean, boolean)})
     * because this screen instance is cached across openings.
     */
    public GuiSurvivalScreen open()
    {
        MinecraftClient mc = this.context.mc;
        ClientPlayerEntity player = mc == null ? null : mc.player;
        IMorphing cap = player == null ? null : Morphing.get(player);
        boolean creative = player != null && player.isCreative();
        boolean allowed = Metamorph.allowMorphingIntoCategoryMorphs.get();

        if (SurvivalScreenLogic.shouldRebuild(this.creative, this.allowed, creative, allowed, this.morphs.sections.isEmpty()))
        {
            this.creative = creative;
            this.allowed = allowed;
            this.morphs.setupSections(creative, (section) -> this.fill(section.morph));
        }

        this.setSelected(cap == null ? null : cap.getCurrentMorph());

        return this;
    }

    /**
     * Set given morph selected. Goes through {@link GuiSurvivalMorphs} so the
     * acquired equal-instance remap happens before {@link #indexOf} is ever
     * asked for a position.
     */
    public void setSelected(AbstractMorph morph)
    {
        this.morphs.setSelected(morph);
        this.fill(this.morphs.getSelected());
    }

    /**
     * Fill the fields with the data from current morph
     */
    public void fill(AbstractMorph morph)
    {
        this.morph.setEnabled(morph != null);
        this.remove.setEnabled(morph != null);
        this.keybind.setEnabled(morph != null);
        this.keybind.setKeybind(LegacyKeyCodes.KEY_NONE);
        this.favorite.setEnabled(morph != null);

        if (morph != null)
        {
            this.favorite.toggled(morph.favorite);
            this.keybind.setKeybind(morph.keybind);
        }
    }

    /**
     * Morph player into currently selected morph
     */
    private void morph(GuiButtonElement button)
    {
        AbstractMorph morph = this.morphs.getSelected();

        if (SurvivalScreenLogic.morph(this.network(), this.morphs.isAcquiredSelected(), this.indexOf(morph), morph))
        {
            this.closeScreen();
        }
    }

    /**
     * Remove currently selected morph
     */
    private void remove(GuiButtonElement button)
    {
        AbstractMorph morph = this.morphs.getSelected();

        if (morph == null)
        {
            return;
        }

        if (!SurvivalScreenLogic.remove(this.network(), this.morphs.isAcquiredSelected(), this.indexOf(morph), morph))
        {
            this.morphs.selected.category.remove(morph);
        }

        this.setSelected(null);
    }

    /**
     * Set keybind of the current morph
     */
    private void setKeybind(int keybind)
    {
        AbstractMorph morph = this.morphs.getSelected();

        if (morph == null)
        {
            return;
        }

        SurvivalScreenLogic.KeybindResult result = SurvivalScreenLogic.resolveKeybind(keybind, this.demorphKeyCode(), morph.keybind);

        if (!result.accepted)
        {
            this.keybind.setKeybind(result.keybind);

            return;
        }

        morph.keybind = result.keybind;

        if (result.keybind == -1)
        {
            this.keybind.setKeybind(LegacyKeyCodes.KEY_NONE);
        }

        if (!SurvivalScreenLogic.applyKeybind(this.network(), this.morphs.isAcquiredSelected(), this.indexOf(morph), result.keybind))
        {
            this.morphs.selected.category.edit(morph);
        }
    }

    /**
     * Favorite or unfavorite current morph
     */
    private void favorite(GuiToggleElement button)
    {
        AbstractMorph morph = this.morphs.getSelected();

        if (morph == null)
        {
            return;
        }

        if (!SurvivalScreenLogic.favorite(this.network(), this.morphs.isAcquiredSelected(), this.indexOf(morph)))
        {
            morph.favorite = button.isToggled();
            this.morphs.selected.category.edit(morph);
        }
    }

    private int indexOf(AbstractMorph morph)
    {
        return this.morphs.acquired == null ? -1 : this.morphs.acquired.getMorphs().indexOf(morph);
    }

    @Override
    public void keyPressed(char typedChar, int keyCode)
    {
        if (SurvivalScreenLogic.isDemorphKey(keyCode, this.demorphKeyCode()))
        {
            this.network().selectMorph(SurvivalScreenLogic.DEMORPH_INDEX);
        }
        else if (this.context.mc != null && this.context.mc.player != null
            && MorphManager.INSTANCE.list.keyTyped(this.context.mc.player, keyCode))
        {
            this.closeScreen();
        }
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float partialTicks)
    {
        this.context.drawContext = drawContext;
        GuiDraw.bindDrawContext(drawContext);

        GuiDraw.drawCustomBackground(this.root.area.x, this.root.area.y, this.root.area.w, this.root.area.h);
        this.sidebar.area.draw(ColorUtils.HALF_BLACK);
        GuiDraw.drawRect(this.root.area.x, this.root.area.y, this.root.area.ex(), this.root.area.y + 20, 0xcc000000);
        GuiDraw.drawStringWithShadow(this.context.font, IKey.lang("metamorph.gui.survival.title").get(),
            this.root.area.x + 6, this.root.area.y + 10 - GuiDraw.fontHeight(this.context.font) / 2, 0xffffff);

        super.render(drawContext, mouseX, mouseY, partialTicks);
    }
}
