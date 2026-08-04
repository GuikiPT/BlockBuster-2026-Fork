package mchorse.metamorph.client.gui.creative;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.mclib.client.gui.framework.GuiBase;
import mchorse.mclib.client.gui.framework.elements.GuiDelegateElement;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.GuiModelRenderer;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.framework.elements.context.GuiSimpleContextMenu;
import mchorse.mclib.client.gui.framework.elements.input.GuiTextElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDrawable;
import mchorse.mclib.client.gui.utils.Area;
import mchorse.mclib.client.gui.utils.Keybind;
import mchorse.mclib.client.gui.utils.LegacyKeyCodes;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.utils.Color;
import mchorse.mclib.utils.Timer;
import mchorse.metamorph.api.MorphUtils;
import mchorse.metamorph.api.creative.categories.MorphCategory;
import mchorse.metamorph.api.creative.categories.UserCategory;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.client.gui.editor.GuiAbstractMorph;
import mchorse.metamorph.client.gui.editor.IMorphEditorHost;
import mchorse.metamorph.client.gui.editor.MorphEditorRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.function.Consumer;

import javax.vecmath.Vector3d;
import javax.vecmath.Vector3f;

/**
 * Scroll list of available morphs — the retained-mode creative morph picker
 * (roadmap P58).
 *
 * <p>1:1 port of Metamorph 1.4's
 * {@code mchorse.metamorph.client.gui.creative.GuiCreativeMorphsList}: the
 * embeddable element composing the multi-section {@link GuiCreativeMorphs} list,
 * the bottom bar (search + Edit), the {@link GuiQuickEditor} slide-in panel, the
 * per-morph editor delegate, the nested-edit stack, onion-skin ghosts and the
 * selected-morph overlay label. This is the unblocker for the body-part editor
 * (P135), scene replays (P158), the nested initial-morph editor (P161) and the
 * gun morph (P198); its public surface is kept identical to legacy.</p>
 *
 * <p>SEAM(P58): legacy coupled the morph editor ({@code GuiAbstractMorph}) and
 * the quick editor directly to this class; the Fabric split routes them through
 * {@link IMorphEditorHost} (exit / getSelected / markDirty, plus the nested-edit
 * surface: {@link #nestEdit} and the two onion-skin lists), which this class
 * implements with the same semantics. Editor discovery moved from
 * {@code MorphManager.registerMorphEditors} to the client-source
 * {@link MorphEditorRegistry} (same reverse-registration ordering).</p>
 *
 * <p>Boundary changes from 1.12.2, all mechanical:</p>
 * <ul>
 *   <li>{@code Minecraft} &rarr; {@code MinecraftClient}; {@code NBTTagCompound}
 *       &rarr; {@code NbtCompound}; {@code Gui.drawRect} / {@code font.*} &rarr;
 *       {@link GuiDraw} statics (no-op headless); {@code I18n.format} &rarr;
 *       {@link I18n#translate(String)}.</li>
 *   <li>LWJGL2 {@code Keyboard.KEY_*} &rarr; {@link LegacyKeyCodes} (the
 *       framework translates GLFW codes back). {@code KEY_Q} is
 *       {@link LegacyKeyCodes#KEY_Q} (LWJGL2 value {@code 16}).</li>
 *   <li>The onion-skin ghost pass ({@link #renderOnionSkin}) keeps its data flow
 *       verbatim — {@link #onionSkins} / {@link #lastOnionSkins}, the
 *       {@link OnionSkin} struct, {@link #haveOnionSkin()} and the
 *       {@link #doRenderOnionSkin} toggle — and the ghost tint that legacy got
 *       from {@code assets/metamorph/shaders/onionskin.*} is now vanilla's
 *       {@code ColorModulator}, which computes the same product; no shader
 *       asset is ported. See {@link #tintOf(OnionSkin)} for the derivation.</li>
 *   <li>Legacy kept a private {@code Stack<NestedEdit>} of its own inner
 *       {@link NestedEdit} (carrying the {@link GuiMorphSection} reference and the
 *       accumulated onion skins), and so does this class. An early port also
 *       shipped a headless {@code NestedEditStack} helper alongside it; S22 P226
 *       deleted that as a wrong duplicate — it snapshotted the <i>morph's</i>
 *       NBT, whereas legacy (and {@link #nestEdit}) snapshot the <i>editor
 *       GUI's</i> state via {@code editor.delegate.toNBT()} — the preview
 *       viewport and the active panel — and restore it with
 *       {@code editor.delegate.fromNBT}. Restoring a morph's own NBT would have
 *       reverted every edit made inside the nested session.</li>
 * </ul>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/client/gui/creative/GuiCreativeMorphsList.java
 */
public class GuiCreativeMorphsList extends GuiElement implements IMorphEditorHost
{
    /**
     * Morph consumer
     */
    public Consumer<AbstractMorph> callback;

    /**
     * Available morph editors
     */
    private List<GuiAbstractMorph> editors;

    /**
     * Morph editor
     */
    public GuiDelegateElement<GuiAbstractMorph> editor;

    public GuiElement bar;
    public GuiTextElement search;
    public GuiButtonElement edit;

    public GuiElement screen;
    public GuiQuickEditor quickEditor;
    public GuiCreativeMorphs morphs;

    public List<OnionSkin> onionSkins = new ArrayList<OnionSkin>();
    public List<OnionSkin> lastOnionSkins;

    private Timer timer = new Timer(100);
    private Stack<NestedEdit> nestedEdits = new Stack<NestedEdit>();

    protected Keybind exitKey;

    protected boolean keepViewport;

    protected Vector3f lastPos = new Vector3f();
    protected float lastYaw;
    protected float lastPitch;
    protected float lastScale;

    protected boolean doRenderOnionSkin;

    /** Lazily created ghost-render entity — see {@link #onionSkinEntity()}. */
    private LivingEntity onionSkinEntity;

    /**
     * Initiate this GUI.
     *
     * Compile the categories list and compute the scroll height of this scroll pane
     */
    public GuiCreativeMorphsList(MinecraftClient mc, Consumer<AbstractMorph> callback)
    {
        super(mc);

        this.callback = callback;
        this.editor = new GuiDelegateElement<GuiAbstractMorph>(mc, null);
        this.editor.flex().relative(this).wh(1F, 1F);

        this.screen = new GuiElement(mc);
        this.screen.flex().relative(this).wh(1F, 1F);

        /* Create quick editor */
        this.quickEditor = new GuiQuickEditor(mc, this);
        this.quickEditor.flex().relative(this).x(1F, -MorphPickerLayout.QUICK_EDITOR_WIDTH).wTo(this.flex(), 1F).h(1F);
        this.quickEditor.setVisible(false);

        /* Create morph panels */
        this.morphs = new GuiCreativeMorphs(mc, this);
        this.morphs.flex().relative(this).wh(1F, 1F).column(0).vertical().stretch().scroll();

        /* Initiate bottom bar */
        this.bar = new GuiElement(mc);
        this.search = new GuiTextElement(mc, this.morphs::setFilter);
        this.edit = new GuiButtonElement(mc, IKey.lang("metamorph.gui.edit"), (b) -> this.enterEditMorph());
        this.edit.setEnabled(false);
        this.edit.flex().w(60);

        this.bar.flex().relative(this.morphs).set(10, 0, 0, 20).y(1, -30).w(1, -20).row(5).preferred(0).height(20);
        this.bar.add(this.search, this.edit);

        this.screen.add(this.morphs, this.bar, this.quickEditor);
        this.add(this.screen, new GuiDrawable(this::drawOverlay), this.editor);

        /* Onion skin */
        this.doRenderOnionSkin = true;

        /* Legacy compiled assets/metamorph/shaders/onionskin.vert/.frag into a
         * static Shader here and cached its "onionskin" uniform location. The
         * port has no shader to compile — the tint is vanilla's ColorModulator,
         * set per ghost; see #tintOf. */

        /* Morph editor keybinds */
        IKey category = IKey.lang("metamorph.gui.creative.keys.category");

        this.exitKey = this.keys().register(IKey.lang("metamorph.gui.creative.keys.exit"), LegacyKeyCodes.KEY_ESCAPE, this::exit).category(category).active(this::updateExitKey);

        this.reload();

        this.morphs.keys().register(IKey.lang("metamorph.gui.creative.keys.edit"), LegacyKeyCodes.KEY_E, this::enterEditMorph).category(category);
        this.morphs.keys().register(IKey.lang("metamorph.gui.creative.keys.quick"), LegacyKeyCodes.KEY_Q, this::toggleQuickEdit).category(category);
        this.morphs.keys().register(IKey.lang("metamorph.gui.creative.keys.focus"), LegacyKeyCodes.KEY_F, () -> GuiBase.getCurrent().focus(this.search, true)).held(LegacyKeyCodes.KEY_LCONTROL).category(category);

        this.keys().register(IKey.lang("metamorph.gui.creative.keys.onionskin"), LegacyKeyCodes.KEY_Q, () -> this.doRenderOnionSkin = !this.doRenderOnionSkin).active(() -> this.isEditMode() && this.haveOnionSkin()).category(category);
    }

    public void reload()
    {
        this.morphs.setupSections(this, this::pickMorph);
        this.search.setText("");
    }

    public void exit()
    {
        if (this.isEditMode())
        {
            this.exitEditMorph(this.nestedEdits.isEmpty(), false);
        }
        else
        {
            this.restoreEdit();
        }

        GuiBase.getCurrent().setContextMenu(null);
    }

    protected boolean updateExitKey()
    {
        return this.editor.delegate != null || !this.nestedEdits.isEmpty();
    }

    public Runnable showGlobalMorphs(AbstractMorph morph)
    {
        if (this.morphs.user.global.isEmpty() || morph == null)
        {
            return null;
        }

        return () ->
        {
            GuiSimpleContextMenu contextMenu = new GuiSimpleContextMenu(this.mc);

            for (UserCategory category : this.morphs.user.global)
            {
                contextMenu.action(IKey.str(category.getTitle()), () ->
                {
                    AbstractMorph added = morph.copy();

                    category.add(added);
                    this.setSelected(added);
                });
            }

            GuiBase.getCurrent().replaceContextMenu(contextMenu);
        };
    }

    public void markDirty()
    {
        this.timer.mark();
    }

    public void disableDirty()
    {
        if (this.timer.enabled)
        {
            this.timer.enabled = false;
            this.morphs.syncSelected();
        }
    }

    /* Quick mode */

    public void toggleQuickEdit()
    {
        if (this.isEditMode() || !this.quickEditor.isVisible() && this.getSelected() == null)
        {
            return;
        }

        this.quickEditor.toggleVisible();

        if (this.quickEditor.isVisible())
        {
            AbstractMorph morph = this.getSelected();

            if (!this.isSelectedMorphIsEditable())
            {
                morph = this.morphs.copyToRecent(morph);
            }

            this.quickEditor.setMorph(morph, this.getMorphEditor(morph));
            this.morphs.flex().wTo(this.quickEditor.flex());
        }
        else
        {
            this.morphs.flex().w(1F);
        }

        this.resize();
    }

    /* Nested editing */

    public boolean isNested()
    {
        return !this.nestedEdits.isEmpty();
    }

    /**
     * The two onion-skin lists are public fields, exactly as legacy had them
     * ({@code GuiCreativeMorphsList:79-80}); these accessors exist only because
     * {@link IMorphEditorHost} cannot declare a field. Panels go through the
     * interface — see {@code IMorphEditorHost}'s note on the P58 narrowing.
     */
    @Override
    public List<OnionSkin> getOnionSkins()
    {
        return this.onionSkins;
    }

    @Override
    public List<OnionSkin> getLastOnionSkins()
    {
        return this.lastOnionSkins;
    }

    @Override
    public void setLastOnionSkins(List<OnionSkin> skins)
    {
        this.lastOnionSkins = skins;
    }

    @Override
    public void nestEdit(AbstractMorph selected, boolean editing, Consumer<AbstractMorph> callback)
    {
        this.nestEdit(selected, editing, false, callback);
    }

    @Override
    public void nestEdit(AbstractMorph selected, boolean editing, boolean keepViewport, Consumer<AbstractMorph> callback)
    {
        NestedEdit edit = new NestedEdit(this.morphs.filter, this.editor.delegate.morph, this.editor.delegate.toNBT(), this.callback, this.morphs.selected, editing, this.keepViewport, this.lastOnionSkins);
        this.callback = callback;
        this.keepViewport = keepViewport;

        if (keepViewport)
        {
            this.saveViewport();

            this.lastOnionSkins = this.lastOnionSkins == null ? new ArrayList<OnionSkin>() : new ArrayList<OnionSkin>(this.lastOnionSkins);
            this.lastOnionSkins.addAll(this.onionSkins);
        }
        else
        {
            this.lastOnionSkins = null;
        }

        this.nestedEdits.add(edit);
        this.updateExitKey();

        if (editing)
        {
            this.enterEditMorph(selected);
        }
        else
        {
            this.exitEditMorph(false, true);
            this.morphs.setFilter("");
            this.setSelected(selected);
        }
    }

    public void restoreEdit()
    {
        if (this.nestedEdits.isEmpty())
        {
            return;
        }

        NestedEdit edit = this.nestedEdits.pop();

        if (!edit.editing)
        {
            this.pickMorph(this.getSelected());
        }
        this.morphs.setFilter("");
        this.morphs.setSelectedDirect(edit.selected, edit.selectedMorph, edit.selectedCategory);
        this.callback = edit.callback;
        this.morphs.scrollTo();

        this.enterEditMorph(edit.editMorph);
        this.editor.delegate.fromNBT(edit.data);

        if (this.keepViewport)
        {
            this.loadViewport();
        }

        this.keepViewport = edit.keepViewport;
        this.lastOnionSkins = edit.lastOnionSkins;
    }

    /* Edit mode */

    public boolean isEditMode()
    {
        return this.editor.delegate != null;
    }

    public void enterEditMorph()
    {
        AbstractMorph morph = this.getSelected();

        if (morph == null)
        {
            return;
        }

        if (!this.isSelectedMorphIsEditable() || !this.nestedEdits.isEmpty())
        {
            morph = morph.copy();
            this.pickMorph(morph);
        }

        this.enterEditMorph(morph);
    }

    public void enterEditMorph(AbstractMorph morph)
    {
        if (morph == null)
        {
            return;
        }

        this.disableDirty();

        this.onionSkins.clear();

        GuiAbstractMorph editor = this.getMorphEditor(morph);

        if (editor != null)
        {
            this.setEditor(editor);

            if (this.keepViewport)
            {
                this.loadViewport();
            }

            editor.renderer.beforeRender = this::beforeRenderModel;
            editor.renderer.afterRender = this::afterRenderModel;
        }
    }

    public void exitEditMorph(boolean add, boolean ignore)
    {
        if (!this.isEditMode())
        {
            return;
        }

        this.editor.delegate.renderer.beforeRender = null;
        this.editor.delegate.renderer.afterRender = null;

        if (this.keepViewport)
        {
            this.saveViewport();
        }

        this.editor.delegate.finishEdit();

        AbstractMorph edited = this.editor.delegate.morph;

        if (!this.nestedEdits.isEmpty() && !ignore)
        {
            this.pickMorph(edited);
            this.restoreEdit();

            return;
        }

        this.morphs.syncSelected();

        if (add && edited != null && !this.isSelectedMorphIsEditable())
        {
            this.setSelected(edited);
        }

        this.setEditor(null);
    }

    protected void setEditor(GuiAbstractMorph editor)
    {
        this.editor.setDelegate(editor);
        this.screen.setVisible(editor == null);
        this.updateExitKey();
    }

    public void finish()
    {
        int i = 0;

        while (this.isNested() || this.isEditMode())
        {
            this.exit();

            i ++;
        }

        if (i > 0)
        {
            this.pickMorph(MorphUtils.copy(this.getSelected()));
        }

        this.keepViewport = false;
        this.lastOnionSkins = null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private GuiAbstractMorph getMorphEditor(AbstractMorph morph)
    {
        if (this.editors == null)
        {
            this.editors = MorphEditorRegistry.INSTANCE.getMorphEditors(this.mc);
        }

        for (GuiAbstractMorph editor : this.editors)
        {
            if (editor.canEdit(morph))
            {
                editor.setMorphs(this);
                editor.startEdit(morph);

                return editor;
            }
        }

        return null;
    }

    private void saveViewport()
    {
        GuiModelRenderer renderer = this.editor.delegate.renderer;

        this.lastPos.set(renderer.pos);
        this.lastPitch = renderer.pitch;
        this.lastYaw = renderer.yaw;
        this.lastScale = renderer.scale;
    }

    private void loadViewport()
    {
        GuiModelRenderer renderer = this.editor.delegate.renderer;

        renderer.setPosition(this.lastPos.x, this.lastPos.y, this.lastPos.z);
        renderer.setRotation(this.lastYaw, this.lastPitch);
        renderer.setScale(this.lastScale);
    }

    protected void beforeRenderModel(GuiContext context)
    {}

    protected void afterRenderModel(GuiContext context)
    {
        this.renderOnionSkin(context);
    }

    /* Onion skin */

    public boolean haveOnionSkin()
    {
        return !this.onionSkins.isEmpty() || this.lastOnionSkins != null && !this.lastOnionSkins.isEmpty();
    }

    private void renderOnionSkin(GuiContext context)
    {
        if (!this.doRenderOnionSkin)
        {
            return;
        }

        LivingEntity entity = this.onionSkinEntity();

        if (entity == null)
        {
            return;
        }

        LivingEntity source = this.editor.delegate == null || this.editor.delegate.renderer == null
            ? null : this.editor.delegate.renderer.getEntity();

        if (source != null)
        {
            entity.age = source.age;
        }

        /* Legacy bound the compiled onionskin.vert/.frag program around the whole
         * ghost loop and re-set its "onionskin" uniform per skin; the port sets
         * the equivalent uniform per skin instead (see #tintOf) — same net
         * effect, since the value only ever changes per skin. */
        GuiModelRenderer.disableRenderingFlag();

        if (this.lastOnionSkins != null)
        {
            for (OnionSkin skin : this.lastOnionSkins)
            {
                this.renderSingleOnionSkin(entity, skin, context.partialTicks);
            }
        }

        for (OnionSkin skin : this.onionSkins)
        {
            this.renderSingleOnionSkin(entity, skin, context.partialTicks);
        }
    }

    /**
     * The dummy entity the ghost morphs are drawn on. Legacy held its own
     * {@code EntityLivingBase}; this port reuses the framework's
     * {@link GuiModelRenderer#dummyEntityFactory} seam (S4 installs it), so the
     * ghosts are simply not drawn when no factory is registered.
     */
    private LivingEntity onionSkinEntity()
    {
        if (this.onionSkinEntity == null && GuiModelRenderer.dummyEntityFactory != null && this.mc != null)
        {
            this.onionSkinEntity = GuiModelRenderer.dummyEntityFactory.apply(this.mc);
        }

        return this.onionSkinEntity;
    }

    /**
     * The ghost tint, as the RGBA the draw is multiplied by.
     *
     * <h2>What replaced the shader, and why it is the same thing</h2>
     *
     * <p>Legacy compiled {@code assets/metamorph/shaders/onionskin.vert/.frag}
     * once into a static {@code Shader} and bound it around the ghost loop. Its
     * <b>entire</b> body was {@code color = gl_Color * onionskin} in the vertex
     * stage and {@code gl_FragColor = texture2D(texture, texcoord) * color} in
     * the fragment stage — i.e. multiply the textured, vertex-coloured result by
     * one RGBA uniform.</p>
     *
     * <p>Neither half of that survives literally on 1.20.4. The source is GLSL
     * 120 built on {@code ftransform()}/{@code gl_Color}/{@code gl_TextureMatrix},
     * none of which exist in a core profile; and more decisively, <b>the port
     * does not own the program that draws a morph</b> — geometry goes through
     * vanilla {@code RenderLayer}s, each carrying its own {@code ShaderProgram},
     * so a picker-owned program could never be the one in effect.</p>
     *
     * <p>But the quantity the shader contributed is already a uniform in every
     * vanilla rendertype shader: {@code ColorModulator}, applied at exactly the
     * same point ({@code color *= vertexColor * ColorModulator}, after the
     * texture fetch). So the port sets {@code ColorModulator} — via
     * {@code RenderSystem.setShaderColor} — instead of shipping a program, and
     * the arithmetic is identical rather than approximated. Nothing under
     * {@code assets/metamorph/shaders/} is needed.</p>
     *
     * <p>The one thing this <i>does</i> impose is ordering: vanilla reads the
     * uniform at {@code ShaderProgram.bind()}, i.e. at buffer flush, not at the
     * {@code VertexConsumer} call. Each ghost therefore draws inside its own
     * frame, which flushes before the colour is restored — see
     * {@link GuiMorphRenderer#renderInFrame}.</p>
     *
     * <p>Pure, so the mapping is pinned by a test rather than only by the draw.</p>
     */
    public static float[] tintOf(OnionSkin skin)
    {
        if (skin == null || skin.color == null)
        {
            return new float[] {1F, 1F, 1F, 1F};
        }

        return new float[] {skin.color.r, skin.color.g, skin.color.b, skin.color.a};
    }

    private void renderSingleOnionSkin(LivingEntity entity, OnionSkin skin, float partialTicks)
    {
        if (skin == null || skin.morph == null)
        {
            return;
        }

        entity.prevPitch = skin.pitch;
        entity.setPitch(skin.pitch);
        entity.prevBodyYaw = entity.bodyYaw = skin.yawBody;
        entity.prevHeadYaw = entity.headYaw = skin.yawHead;

        MatrixStack modelView = RenderSystem.getModelViewStack();
        float[] tint = tintOf(skin);

        modelView.push();

        try
        {
            RenderSystem.setShaderColor(tint[0], tint[1], tint[2], tint[3]);

            try
            {
                GuiMorphRenderer.renderInFrame(this.mc, skin.morph, entity, skin.offset.x, skin.offset.y, skin.offset.z, 0, partialTicks);
            }
            finally
            {
                /* The frame above already flushed, so the ghost is on the
                 * framebuffer with its own tint and the reset cannot bleed into
                 * it — nor the tint into whatever draws next. */
                RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            }
        }
        finally
        {
            modelView.pop();
            RenderSystem.applyModelViewMatrix();
        }
    }

    /* Morph selection and filtering */

    /**
     * Get currently selected morph
     */
    public AbstractMorph getSelected()
    {
        if (this.isEditMode())
        {
            AbstractMorph morph = this.editor.delegate.morph;

            if (morph != null)
            {
                return morph;
            }
        }

        return this.morphs.getSelected();
    }

    public void pickMorph(GuiMorphSection selected)
    {
        this.disableDirty();

        this.morphs.setSelectedDirect(selected);

        this.pickMorph(selected.morph);
        this.syncQuickEditor();
    }

    public void pickMorph(AbstractMorph morph)
    {
        this.edit.setEnabled(morph != null);

        if (this.callback != null)
        {
            this.callback.accept(morph);
        }
    }

    /**
     * Set selected morph
     */
    public AbstractMorph setSelected(AbstractMorph morph)
    {
        this.disableDirty();
        this.morphs.setSelected(morph);
        this.syncQuickEditor();

        morph = this.getSelected();

        this.edit.setEnabled(morph != null);

        return morph;
    }

    protected void syncQuickEditor()
    {
        if (this.quickEditor.isVisible())
        {
            AbstractMorph morph = this.getSelected();

            if (morph != null && this.isSelectedMorphIsEditable())
            {
                this.quickEditor.setMorph(morph, this.getMorphEditor(morph));
            }
            else
            {
                this.toggleQuickEdit();
            }
        }
    }

    protected boolean isSelectedMorphIsEditable()
    {
        return this.morphs.isSelectedMorphIsEditable();
    }

    /* Element overrides */

    @Override
    public void draw(GuiContext context)
    {
        if (this.timer.checkReset())
        {
            this.morphs.syncSelected();
        }

        super.draw(context);
    }

    private void drawOverlay(GuiContext context)
    {
        /* Draw the name of the morph */
        if (!this.isEditMode())
        {
            AbstractMorph morph = this.getSelected();
            String selected = morph != null ? morph.getDisplayName() : I18n.translate("metamorph.gui.no_morph");
            boolean error = morph != null && morph.errorRendering;

            if (error)
            {
                selected = I18n.translate("metamorph.gui.morph_render_error");
            }

            Area area = this.search.area;
            int w = Math.max(GuiDraw.textWidth(this.font, selected), morph != null ? GuiDraw.textWidth(this.font, morph.name) : 0);

            if (morph != null && !morph.errorRendering)
            {
                GuiDraw.drawRect(area.x, area.y - 27, area.x + w + 8, area.y, 0xdd000000);
                GuiDraw.drawStringWithShadow(this.font, selected, area.x + 4, area.y - 23, 0xffffffff);
                GuiDraw.drawStringWithShadow(this.font, morph.name, area.x + 4, area.y - 12, 0x888888);
            }
            else
            {
                GuiDraw.drawRect(area.x, area.y - 16, area.x + w + 8, area.y, 0xdd000000);
                GuiDraw.drawStringWithShadow(this.font, selected, area.x + 4, area.y - 12, error ? 0xff1833 : 0xffffff);
            }
        }

        if (!this.isEditMode() && !this.search.field.isFocused() && this.search.field.getText().isEmpty())
        {
            GuiDraw.drawStringWithShadow(this.font, I18n.translate("metamorph.gui.search"), this.search.area.x + 5, this.search.area.y + 6, 0x888888);
        }
    }

    /**
     * Data stored about currently nested editing
     */
    public static class NestedEdit
    {
        public String filter;
        public NbtCompound data;
        public Consumer<AbstractMorph> callback;

        public GuiMorphSection selected;
        public MorphCategory selectedCategory;
        public AbstractMorph selectedMorph;
        public AbstractMorph editMorph;
        public boolean editing;
        public boolean keepViewport;
        public List<OnionSkin> lastOnionSkins;

        public NestedEdit(String filter, AbstractMorph editMorph, NbtCompound data, Consumer<AbstractMorph> callback, GuiMorphSection selected, boolean editing, boolean keepViewport, List<OnionSkin> lastOnionSkins)
        {
            this.filter = filter;
            this.data = data;
            this.editMorph = editMorph;
            this.callback = callback;
            this.editing = editing;
            this.keepViewport = keepViewport;
            this.lastOnionSkins = lastOnionSkins;

            this.selected = selected;
            this.selectedCategory = selected == null ? null : selected.category;
            this.selectedMorph = selected == null ? null : selected.morph;
        }
    }

    /**
     * Onion skin data
     */
    public static class OnionSkin
    {
        public Color color = new Color();

        public AbstractMorph morph;

        public Vector3d offset = new Vector3d(0, 0, 0);

        public float pitch = 0f;

        public float yawHead = 0f;

        public float yawBody = 0f;

        public OnionSkin color(float r, float g, float b, float a)
        {
            this.color.set(r, g, b, a);
            return this;
        }

        public OnionSkin morph(AbstractMorph morph)
        {
            this.morph = morph;
            return this;
        }

        public OnionSkin offset(double x, double y, double z, float pitch, float yawHead, float yawBody)
        {
            this.offset.set(x, y, z);
            this.pitch = pitch;
            this.yawHead = yawHead;
            this.yawBody = yawBody;
            return this;
        }
    }
}
