package mchorse.metamorph.client.gui.editor;

import mchorse.mclib.client.gui.framework.GuiBase;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.GuiModelRenderer;
import mchorse.mclib.client.gui.framework.elements.GuiPanelBase;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiIconElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTextElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.Elements;
import mchorse.mclib.client.gui.utils.GuiUtils;
import mchorse.mclib.client.gui.utils.Icon;
import mchorse.mclib.client.gui.utils.Icons;
import mchorse.mclib.client.gui.utils.Label;
import mchorse.mclib.client.gui.utils.LegacyKeyCodes;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.utils.MathUtils;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.utils.Animation;
import mchorse.metamorph.api.morphs.utils.IAnimationProvider;
import mchorse.metamorph.client.gui.creative.GuiMorphRenderer;
import mchorse.metamorph.util.MMIcons;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base morph editor GUI (port of Metamorph 1.4's {@code GuiAbstractMorph},
 * roadmap P59).
 *
 * <p>A {@link GuiPanelBase} of {@link GuiMorphPanel}s over a full-screen model
 * renderer background, with a close icon in the bottom-left corner. TAB cycles
 * panels (Shift reverses). Each panel gets a {@code startEditing}/
 * {@code finishEditing} lifecycle; the body-part panel auto-registers a
 * {@code B} hotkey. Which concrete editor is picked for a morph is decided by
 * the first {@link #canEdit} that returns true (the selector iterates editors
 * in registration order — SEAM P58). Editor viewport + panel selection persist
 * across nested edits as NBT ({@code MX/MY/MZ/MS/MRX/MRY} plus {@code Panel} +
 * {@code PanelHash}, the panel keyed by its runtime {@code hashCode}).</p>
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class GuiAbstractMorph<T extends AbstractMorph> extends GuiPanelBase<GuiMorphPanel>
{
    public static final IKey KEY_CATEGORY = IKey.lang("metamorph.gui.editor.keys.category");

    /**
     * SEAM(P58): the creative morph selector that hosts this editor. Legacy
     * typed this as {@code GuiCreativeMorphsList}; the Fabric split narrows it
     * to {@link IMorphEditorHost} (exit / getSelected / markDirty).
     */
    public IMorphEditorHost morphs;

    public GuiIconElement finish;
    public GuiModelRenderer renderer;
    public GuiSettingsPanel settings;

    protected GuiMorphPanel defaultPanel;

    public T morph;

    public GuiAbstractMorph(MinecraftClient mc)
    {
        super(mc);

        this.finish = new GuiIconElement(mc, Icons.CLOSE, (b) -> this.morphs.exit());
        this.finish.flex().relative(this).set(0, 0, 20, 20).y(1F, -20);
        this.renderer = this.createMorphRenderer(mc);
        this.renderer.flex().relative(this).wh(1F, 1F);
        this.defaultPanel = this.settings = new GuiSettingsPanel(mc, this);

        this.registerPanel(this.settings, IKey.lang("metamorph.gui.editor.settings"), MMIcons.PROPERTIES);
        this.prepend(this.renderer);

        this.add(this.finish);

        this.keys().register(IKey.lang("metamorph.gui.editor.keys.cycle"), LegacyKeyCodes.KEY_TAB, this::cycle).category(KEY_CATEGORY);
    }

    protected void cycle()
    {
        int index = -1;

        for (int i = 0; i < this.panels.size(); i ++)
        {
            if (this.view.delegate == this.panels.get(i))
            {
                index = i;

                break;
            }
        }

        index += GuiUtils.isShiftKeyDown() ? 1 : -1;
        index = MathUtils.cycler(index, 0, this.panels.size() - 1);

        this.buttons.elements.get(index).clickItself(GuiBase.getCurrent());
    }

    protected GuiModelRenderer createMorphRenderer(MinecraftClient mc)
    {
        return new GuiMorphRenderer(mc);
    }

    public void setMorphs(IMorphEditorHost morphs)
    {
        this.morphs = morphs;
    }

    /**
     * Switch current morph panel to given one
     */
    @Override
    public void setPanel(GuiMorphPanel panel)
    {
        if (this.view.delegate != null)
        {
            this.view.delegate.finishEditing();
        }

        super.setPanel(panel);
        panel.startEditing();
    }

    public boolean canEdit(AbstractMorph morph)
    {
        return morph != null;
    }

    public void startEdit(T morph)
    {
        this.morph = morph;
        this.setupRenderer(morph);

        for (GuiMorphPanel panel : this.panels)
        {
            panel.fillData(morph);
        }

        this.setPanel(this.defaultPanel);
    }

    protected void setupRenderer(T morph)
    {
        this.renderer.reset();

        /* Subclasses may swap in a renderer of their own (GuiCustomMorph uses a
         * GuiBBModelRenderer), so this stays an instanceof rather than a cast. */
        if (this.renderer instanceof GuiMorphRenderer)
        {
            ((GuiMorphRenderer) this.renderer).morph = morph;
        }
    }

    public void finishEdit()
    {
        if (this.view.delegate != null)
        {
            this.view.delegate.finishEditing();
        }
    }

    /**
     * Get presets
     */
    public List<Label<NbtCompound>> getPresets(T morph)
    {
        return Collections.emptyList();
    }

    protected void addPreset(AbstractMorph morph, List<Label<NbtCompound>> list, String label, String json)
    {
        try
        {
            this.addPreset(morph, list, label, StringNbtReader.parse(json));
        }
        catch (Exception e)
        {}
    }

    protected void addPreset(AbstractMorph morph, List<Label<NbtCompound>> list, String label, NbtCompound tag)
    {
        NbtCompound morphTag = morph.toNBT();

        morphTag.copyFrom(tag);
        list.add(new Label<NbtCompound>(IKey.str(label), morphTag));
    }

    /**
     * Get quick access editing fields
     */
    public List<GuiElement> getFields(MinecraftClient mc, IMorphEditorHost morphs, T morph)
    {
        List<GuiElement> elements = new ArrayList<GuiElement>();
        GuiTextElement displayName = new GuiTextElement(mc, (name) ->
        {
            morphs.getSelected().displayName = name;
            morphs.markDirty();
        });

        displayName.setText(morph.displayName);
        elements.add(Elements.label(IKey.lang("metamorph.gui.editor.display_name")));
        elements.add(displayName);

        return elements;
    }

    /**
     * Get current tick for preview
     */
    public int getCurrentTick()
    {
        int tick = 0;

        if (this.morph instanceof IAnimationProvider)
        {
            Animation animation = ((IAnimationProvider) this.morph).getAnimation();

            if (animation.animates)
            {
                tick = animation.duration;
            }
        }

        return tick;
    }

    @Override
    public GuiIconElement registerPanel(GuiMorphPanel panel, IKey tooltip, Icon icon)
    {
        GuiIconElement button = super.registerPanel(panel, tooltip, icon);

        /* SEAM(P59.1): the legacy check was
         * {@code if (panel instanceof GuiBodyPartEditor)}. The body-part
         * editor lands in P59.1; the concrete editor subclass overrides
         * {@link #wantsBodyPartKey} to return true for it, keeping the B
         * hotkey wiring here in the base. */
        if (this.wantsBodyPartKey(panel))
        {
            this.registerKeybind(button, IKey.lang("metamorph.gui.body_parts.open"), LegacyKeyCodes.KEY_B).category(KEY_CATEGORY);
        }

        return button;
    }

    /**
     * Whether the given panel is the body-part editor and thus wants the
     * {@code B} hotkey. SEAM(P59.1): overridden once {@code GuiBodyPartEditor}
     * exists.
     */
    protected boolean wantsBodyPartKey(GuiMorphPanel panel)
    {
        return false;
    }

    @Override
    protected void drawBackground(GuiContext context, int x, int y, int w, int h)
    {
        GuiDraw.drawRect(x, y, x + w, y + h, 0xee000000);
    }

    /* Saving and restoring the store */

    public void fromNBT(NbtCompound tag)
    {
        /* Restore model renderer's position */
        this.renderer.setPosition(tag.getFloat("MX"), tag.getFloat("MY"), tag.getFloat("MZ"));
        this.renderer.setScale(tag.getFloat("MS"));
        this.renderer.setRotation(tag.getFloat("MRY"), tag.getFloat("MRX"));

        /* Restore panel */
        int hash = tag.getInt("PanelHash");

        for (GuiMorphPanel panel : this.panels)
        {
            if (panel.hashCode() == hash)
            {
                this.setPanel(panel);
                panel.fromNBT(tag.getCompound("Panel"));

                return;
            }
        }
    }

    public NbtCompound toNBT()
    {
        NbtCompound tag = new NbtCompound();

        /* Save model renderer's position */
        tag.putFloat("MX", this.renderer.pos.x);
        tag.putFloat("MY", this.renderer.pos.y);
        tag.putFloat("MZ", this.renderer.pos.z);
        tag.putFloat("MS", this.renderer.scale);
        tag.putFloat("MRX", this.renderer.pitch);
        tag.putFloat("MRY", this.renderer.yaw);

        /* Save panel */
        tag.put("Panel", this.view.delegate.toNBT());
        tag.putInt("PanelHash", this.view.delegate.hashCode());

        return tag;
    }
}
